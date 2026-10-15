package com.loopers.config.cache;

import com.loopers.like.application.LikeUseCase;
import com.loopers.product.application.ProductUseCase;
import com.loopers.product.domain.ProductSort;
import com.loopers.support.cache.CacheKeys;
import com.loopers.support.cache.CacheVersions;
import com.loopers.support.fixture.CommerceFixture;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.config.redis.RedisConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import java.time.Duration;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static com.loopers.support.http.ApiHttp.customer;

@SpringBootTest(properties = "catalog.cache.enabled=true")
@AutoConfigureMockMvc
@Import(MySqlTestContainersConfig.class)
class ReadCacheHttpTest {
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.0")).withExposedPorts(6379);
    static { REDIS.start(); }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("datasource.redis.master.host", REDIS::getHost);
        r.add("datasource.redis.master.port", REDIS::getFirstMappedPort);
        r.add("datasource.redis.replicas[0].host", REDIS::getHost);
        r.add("datasource.redis.replicas[0].port", REDIS::getFirstMappedPort);
    }
    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager tm;
    @MockitoSpyBean CacheVersions versions;
    @Autowired LikeUseCase likes;
    @MockitoSpyBean ProductUseCase products;
    @MockitoSpyBean org.springframework.cache.CacheManager cacheManager;
    @Autowired @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redis;
    CommerceFixture fixture;
    com.loopers.user.domain.User user;
    com.loopers.product.domain.Product product;
    com.loopers.brand.domain.Brand brand;
    @BeforeEach void setup() {
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
        fixture = new CommerceFixture(em, tm);
        user = fixture.user(); brand = fixture.brand("cache-split"); product = fixture.product(brand, "before", 100L, 1);
        clearInvocations(products);
    }
    @AfterEach void cleanup() { fixture.truncateRemainingTables(); }
    String detail() throws Exception {
        return mvc.perform(get("/api/v1/products/{id}", product.getId()).with(customer(user)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }
    String key(String bucket, String api, String... namespaces) { return bucket + "::" + versions.key(api, namespaces); }

    /** 20개 목록의 miss는 각 일괄 조회 1회, hit은 원본 호출 0회. 재고 변경은 상품 캐시를 재사용한다. */
    @Test
    @DisplayName("통합 목록은 20개를 일괄 합성하고 재고 변경 후에도 기본 정보 캐시를 재사용한다")
    void combinesTwentyProductsWithoutPerProductReads() throws Exception {
        var ids = new java.util.ArrayList<Long>();
        ids.add(product.getId());
        for (int i = 1; i < 20; i++) ids.add(fixture.product(brand, "batch-" + i, 100L, 1).getId());
        ids.sort(Long::compareTo);
        mvc.perform(get("/api/v1/products").with(customer(user))).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content.length()").value(20));
        verify(products, times(1)).findLikeCounts(ids);
        verify(products, times(1)).findStockStatuses(ids);
        verify(products, times(0)).findBasicProduct(org.mockito.ArgumentMatchers.anyLong());
        clearInvocations(products);
        mvc.perform(get("/api/v1/products").with(customer(user))).andExpect(status().isOk());
        verify(products, times(0)).findBasicPage(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
        verify(products, times(0)).findLikeCounts(org.mockito.ArgumentMatchers.anyList());
        verify(products, times(0)).findStockStatuses(org.mockito.ArgumentMatchers.anyList());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api-admin/v1/products/" + product.getId() + "/stock")
            .with(com.loopers.support.http.ApiHttp.admin()).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"quantity\":0}")).andExpect(status().isOk());
        String response = mvc.perform(get("/api/v1/products").with(customer(user))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var content = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).path("data").path("content");
        assertThat(java.util.stream.StreamSupport.stream(content.spliterator(), false)
            .filter(item -> item.path("id").asLong() == product.getId()).findFirst().orElseThrow().path("soldOut").asBoolean()).isTrue();
        verify(products, times(0)).findBasicPage(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
        verify(products, times(0)).findLikeCounts(org.mockito.ArgumentMatchers.anyList());
        verify(products, times(1)).findStockStatuses(ids);
    }

    /**
     * 준비: 기본 목록을 조회해 캐시를 채운다.
     * 실행: 동일한 기본값을 명시해 재조회하고 사용자 헤더 없이도 요청한다.
     * 확인: 원본 목록 조회는 1회이며, 헤더 없는 요청은 401이고 TTL 범위도 유지된다.
     */
    @Test
    @DisplayName("동일한 목록 조건은 캐시를 공유하지만 사용자 헤더 없는 요청은 401이다")
    void cachesCanonicalListAndStillAuthenticatesHit() throws Exception {
        mvc.perform(get("/api/v1/products").with(customer(user))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/products?sort=latest&page=0&size=20").with(customer(user))).andExpect(status().isOk());
        verify(products, times(1)).findBasicPage(null, null, 0, 20);
        verify(products, times(0)).findBasicPage(null, ProductSort.LATEST, 0, 20);
        mvc.perform(get("/api/v1/products")).andExpect(status().isUnauthorized());
        assertThat(redis.getExpire(key(CacheKeys.PRODUCTS, CacheKeys.products("latest", null, 0, 20), CacheKeys.CATALOG_REVISION))).isBetween(269L, 330L);
    }

    /** 같은 fixture·HTTP 입력에서 캐시 비활성 → miss → hit을 비교한다. 읽기 사이에는 쓰지 않는다. */
    @org.junit.jupiter.params.ParameterizedTest(name = "{0}, brand filter={1}")
    @org.junit.jupiter.params.provider.CsvSource({
        "latest,false", "latest,true", "price_asc,false", "price_asc,true", "likes_desc,false", "likes_desc,true"
    })
    @DisplayName("[R-CATALOG-02/04/05] DB-only·miss·hit의 정렬·페이지·응답은 같고 hit은 원본 조회를 생략한다")
    void databaseOnlyMissAndHitReturnSamePages(String sort, boolean filtered) throws Exception {
        var second = fixture.product(brand, "second", 100L, 0);
        var third = fixture.product(brand, "third", 200L, 1);
        var other = fixture.product(fixture.brand("other"), "other-product", 50L, 1);
        fixture.deletedProduct(brand, "deleted", 1L);
        fixture.like(user, second);
        fixture.like(user, third);
        fixture.like(user, other);
        Long brandId = filtered ? brand.getId() : null;
        ProductSort requested = ProductSort.valueOf(sort.toUpperCase(java.util.Locale.ROOT));
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var collected = new java.util.ArrayList<Long>();
        for (int page = 0; page < 3; page++) {
            String url = "/api/v1/products?sort=" + sort + "&page=" + page + "&size=2"
                + (filtered ? "&brandId=" + brandId : "");
            String bucket = sort.equals("likes_desc") ? CacheKeys.RANKINGS : CacheKeys.PRODUCTS;
            String cacheKey = key(bucket, CacheKeys.products(sort, brandId, page, 2), CacheKeys.CATALOG_REVISION);
            assertThat(redis.hasKey(cacheKey)).isFalse();
            // Exercise ResponseCache's real disabled branch; only the feature switch is stubbed.
            org.mockito.Mockito.doReturn(false).when(versions).isEnabled();
            clearInvocations(products);
            String databaseOnly;
            try {
                databaseOnly = mvc.perform(get(url).with(customer(user))).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
                verify(products, times(1)).findBasicPage(brandId, requested, page, 2);
                assertThat(redis.hasKey(cacheKey)).as("DB-only must not populate Redis").isFalse();
            } finally {
                org.mockito.Mockito.doCallRealMethod().when(versions).isEnabled();
            }
            clearInvocations(products);
            String miss = mvc.perform(get(url).with(customer(user))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
            verify(products, times(1)).findBasicPage(brandId, requested, page, 2);
            assertThat(redis.hasKey(cacheKey)).isTrue();
            assertThat(redis.getExpire(cacheKey)).isBetween(269L, 330L);
            clearInvocations(products);
            String hit = mvc.perform(get(url).with(customer(user))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
            verify(products, times(0)).findBasicPage(brandId, requested, page, 2);
            assertThat(mapper.readTree(miss)).isEqualTo(mapper.readTree(databaseOnly));
            assertThat(mapper.readTree(hit)).isEqualTo(mapper.readTree(databaseOnly));
            var data = mapper.readTree(hit).path("data");
            assertThat(data.path("page").asInt()).isEqualTo(page);
            assertThat(data.path("size").asInt()).isEqualTo(2);
            assertThat(data.path("last").asBoolean()).isEqualTo(page >= 1);
            for (var item : data.path("content")) collected.add(item.path("id").asLong());
        }
        var expected = new java.util.ArrayList<>(java.util.List.of(product.getId(), second.getId(), third.getId()));
        if (!filtered) expected.add(other.getId());
        assertThat(collected).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(expected);
    }

    /**
     * 준비: 상품 상세 키에 만료 없는 잘못된 JSON을 저장한다.
     * 실행: 상세를 조회하고 한 번 더 조회한다.
     * 확인: 첫 조회는 DB 응답을 반환하고 깨진 키를 지우며, 다음 조회는 정상 JSON을 저장한다.
     */
    @Test
    @DisplayName("깨진 JSON 캐시는 제거하고 원본 조회 뒤 정상 캐시로 다시 채운다")
    void malformedNonExpiringCacheIsRemovedAndRefilled() throws Exception {
        String key = key(CacheKeys.PRODUCTS, CacheKeys.product(product.getId()), CacheKeys.CATALOG_REVISION);
        redis.opsForValue().set(key, "broken JSON");
        assertThat(detail()).contains("before");
        assertThat(redis.hasKey(key)).isFalse();
        detail();
        assertThat(redis.opsForValue().get(key)).contains("before");
    }

    /**
     * 준비: 상세 캐시를 채우고 캐시 get·put이 예외를 던지게 한다.
     * 실행: 상품을 수정한 뒤 조회하고 삭제한 뒤 다시 조회한다.
     * 확인: 수정된 이름과 삭제 후 404를 반환한다.
     */
    @Test
    @DisplayName("Redis 읽기·저장 오류가 나면 현재 DB 결과로 응답한다")
    void redisReadAndWriteFailuresFallBackToCurrentDatabaseResult() throws Exception {
        detail();
        var broken = org.mockito.Mockito.mock(org.springframework.cache.Cache.class);
        org.mockito.Mockito.when(broken.get(org.mockito.ArgumentMatchers.any())).thenThrow(new IllegalStateException("Redis down"));
        org.mockito.Mockito.doThrow(new IllegalStateException("Redis down")).when(broken)
            .put(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.doReturn(broken).when(cacheManager).getCache(CacheKeys.PRODUCTS);
        products.update(product.getId(), "during outage", 400L, null);
        assertThat(detail()).contains("during outage");
        products.delete(product.getId());
        mvc.perform(get("/api/v1/products/{id}", product.getId()).with(customer(user))).andExpect(status().isNotFound());
    }

    /**
     * 준비: 상세 캐시의 남은 TTL을 120초로 줄인다.
     * 실행: 같은 상세를 다시 조회한다.
     * 확인: 응답이 같고 원본 상세 조회는 0회이며 남은 TTL은 119~120초다.
     */
    @Test
    @DisplayName("상품 상세 cache hit은 DB 원본을 다시 읽거나 TTL을 연장하지 않는다")
    void cacheHitDoesNotRenewTheRemainingTtl() throws Exception {
        String original = detail();
        String key = key(CacheKeys.PRODUCTS, CacheKeys.product(product.getId()), CacheKeys.CATALOG_REVISION);
        redis.expire(key, Duration.ofSeconds(120));
        clearInvocations(products);
        assertThat(detail()).isEqualTo(original);
        verify(products, times(0)).findBasicProduct(product.getId());
        assertThat(redis.getExpire(key)).isBetween(119L, 120L);
    }

    /**
     * 준비: 설정된 모든 캐시 이름을 순회한다.
     * 실행: 각 캐시에 값을 저장한다.
     * 확인: Redis 잔여 TTL이 269~330초다. 초 단위 경과를 1초 허용한다.
     */
    @Test
    @DisplayName("모든 캐시 종류에 5분 ±30초 TTL 정책이 적용된다")
    void everyCacheBucketUsesExpiringJitteredEntries() {
        for (String bucket : CacheKeys.NAMES) {
            cacheManager.getCache(bucket).put("ttl-policy-test", "value");
            assertThat(redis.getExpire(bucket + "::ttl-policy-test")).as(bucket).isBetween(269L, 330L);
        }
    }

    // 캐시 조회 시나리오 12가지. 목록 6가지 + 상세·내 좋아요·좋아요 수·재고와 고객·관리자 브랜드 목록.
    java.util.List<ReadRoute> readRoutes() {
        var routes = new java.util.ArrayList<ReadRoute>();
        for (String sort : java.util.List.of("latest", "price_asc", "likes_desc")) {
            for (Long filter : java.util.Arrays.asList(null, brand.getId())) {
                String url = "/api/v1/products?sort=" + sort + (filter == null ? "" : "&brandId=" + filter);
                routes.add(new ReadRoute(url, "likes_desc".equals(sort) ? CacheKeys.RANKINGS : CacheKeys.PRODUCTS,
                    CacheKeys.products(sort, filter, 0, 20), new String[]{CacheKeys.CATALOG_REVISION}));
            }
        }
        routes.add(new ReadRoute("/api/v1/products/" + product.getId(), CacheKeys.PRODUCTS,
            CacheKeys.product(product.getId()), new String[]{CacheKeys.CATALOG_REVISION}));
        routes.add(new ReadRoute("/api/v1/users/" + user.getId() + "/likes", CacheKeys.MY_LIKES,
            CacheKeys.myLikes(user.getId(), 0, 20), new String[]{CacheKeys.CATALOG_REVISION, CacheKeys.userRevision(user.getId())}));
        routes.add(new ReadRoute("/api-admin/v1/brands", CacheKeys.BRANDS,
            CacheKeys.brands(0, 20), new String[]{CacheKeys.CATALOG_REVISION}));
        routes.add(new ReadRoute("/api/v1/brands", CacheKeys.BRANDS,
            CacheKeys.customerBrands(0, 20), new String[]{CacheKeys.CATALOG_REVISION}));
        for (String bucket : java.util.List.of(CacheKeys.COUNTS, CacheKeys.STOCKS)) {
            routes.add(new ReadRoute("/api/v1/products/" + product.getId(), bucket,
                CacheKeys.batch(bucket, java.util.List.of(product.getId())), bucket.equals(CacheKeys.STOCKS)
                    ? new String[]{CacheKeys.CATALOG_REVISION, CacheKeys.STOCK_REVISION} : new String[]{CacheKeys.CATALOG_REVISION}));
        }
        return routes;
    }

    String read(ReadRoute route) throws Exception {
        return mvc.perform(get(route.url()).with(route.url().startsWith("/api-admin")
                ? com.loopers.support.http.ApiHttp.admin() : customer(user)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    record ReadRoute(String url, String bucket, String apiKey, String[] namespaces) { }

    /**
     * 준비: 목록 3종 × 브랜드 필터 유무, 상세, 내 좋아요, 브랜드 목록, 숫자·재고를 조회한다.
     * 실행: 각 키의 TTL을 120초로 줄여 hit을 확인한 뒤 1ms로 줄여 실제 만료시킨다.
     * 확인: hit은 같은 응답과 잔여 TTL을 유지하고, 만료 뒤에는 같은 응답을 새 TTL로 채운다.
     */
    @Test
    @DisplayName("조회 12가지 모두 TTL을 지키고 hit 때 비연장하며 만료 후 재적재한다")
    void allReadVariantsExpireAndHitsNeverExtendTtl() throws Exception {
        for (ReadRoute route : readRoutes()) {
            String original = read(route);
            String cacheKey = key(route.bucket(), route.apiKey(), route.namespaces());
            assertThat(redis.getExpire(cacheKey)).as(route.url() + " initial TTL").isBetween(269L, 330L);
            redis.expire(cacheKey, Duration.ofSeconds(120));
            assertThat(read(route)).as(route.url() + " hit response").isEqualTo(original);
            assertThat(redis.getExpire(cacheKey)).as(route.url() + " hit TTL").isBetween(119L, 120L);
            redis.expire(cacheKey, Duration.ofMillis(1));
            Thread.sleep(10);
            assertThat(redis.hasKey(cacheKey)).as(route.url() + " expired").isFalse();
            assertThat(read(route)).as(route.url() + " refill response").isEqualTo(original);
            assertThat(redis.getExpire(cacheKey)).as(route.url() + " refill TTL").isBetween(269L, 330L);
        }
    }

    @Test
    @DisplayName("좋아요 0개 캐시는 등록 직후 0을 유지하고 만료 후 1을 반환한다")
    void zeroLikeCountRefreshesOnlyAfterExpiry() throws Exception {
        String url = "/api/v1/products/" + product.getId();
        mvc.perform(get(url).with(customer(user))).andExpect(status().isOk()).andExpect(jsonPath("$.data.likeCount").value(0));
        String cacheKey = key(CacheKeys.COUNTS, CacheKeys.batch(CacheKeys.COUNTS, java.util.List.of(product.getId())), CacheKeys.CATALOG_REVISION);
        assertThat(redis.getExpire(cacheKey)).isBetween(269L, 330L);
        likes.register(user.getId(), product.getId());
        mvc.perform(get(url).with(customer(user))).andExpect(status().isOk()).andExpect(jsonPath("$.data.likeCount").value(0));
        redis.expire(cacheKey, Duration.ofMillis(1));
        Thread.sleep(10);
        mvc.perform(get(url).with(customer(user))).andExpect(status().isOk()).andExpect(jsonPath("$.data.likeCount").value(1));
    }

    @Test
    @DisplayName("본인 좋아요 목록이 캐시되어 있어도 다른 사용자의 접근은 404다")
    void ownLikesCacheDoesNotBypassOwnership() throws Exception {
        likes.register(user.getId(), product.getId());
        String url = "/api/v1/users/" + user.getId() + "/likes";
        mvc.perform(get(url).with(customer(user))).andExpect(status().isOk()).andExpect(jsonPath("$.data.content.length()").value(1));
        mvc.perform(get(url).with(customer(fixture.user()))).andExpect(status().isNotFound());
        mvc.perform(get(url).with(customer(user))).andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].id").value(product.getId()));
    }

    @Test
    @DisplayName("관리자 브랜드 목록이 캐시되어 있어도 고객의 접근은 403이다")
    void adminCacheDoesNotBypassRole() throws Exception {
        mvc.perform(get("/api-admin/v1/brands").with(com.loopers.support.http.ApiHttp.admin())).andExpect(status().isOk());
        mvc.perform(get("/api-admin/v1/brands").with(customer(user))).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("고객·관리자 브랜드 목록은 캐시 키와 응답을 분리하고 고객 hit에도 사용자 헤더를 요구한다")
    void customerAndAdminBrandKeysAreSeparate() throws Exception {
        mvc.perform(get("/api-admin/v1/brands").with(com.loopers.support.http.ApiHttp.admin())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/brands").with(customer(user)))
            .andExpect(com.loopers.support.http.ApiHttp.success(org.springframework.http.HttpStatus.OK))
            .andExpect(jsonPath("$.data.content[0].deleted").doesNotExist());
        String customerKey = key(CacheKeys.BRANDS, CacheKeys.customerBrands(0, 20), CacheKeys.CATALOG_REVISION);
        String adminKey = key(CacheKeys.BRANDS, CacheKeys.brands(0, 20), CacheKeys.CATALOG_REVISION);
        assertThat(customerKey).isNotEqualTo(adminKey);
        assertThat(redis.hasKey(customerKey)).isTrue();
        assertThat(redis.hasKey(adminKey)).isTrue();
        redis.expire(customerKey, Duration.ofSeconds(120));
        mvc.perform(get("/api/v1/brands?page=0&size=20").with(customer(user))).andExpect(status().isOk());
        assertThat(redis.getExpire(customerKey)).isBetween(119L, 120L);
        mvc.perform(get("/api/v1/brands")).andExpect(status().isUnauthorized());
    }

}
