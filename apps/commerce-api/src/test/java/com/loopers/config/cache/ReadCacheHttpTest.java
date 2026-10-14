package com.loopers.config.cache;

import com.loopers.brand.application.BrandUseCase;
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
import org.springframework.transaction.support.TransactionTemplate;
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
    @Autowired CacheVersions versions;
    @Autowired LikeUseCase likes;
    @Autowired com.loopers.order.application.OrderUseCase orders;
    @Autowired BrandUseCase brands;
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

    @Test void cachesCanonicalListAndStillAuthenticatesHit() throws Exception {
        mvc.perform(get("/api/v1/products").with(customer(user))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/products?sort=latest&page=0&size=20").with(customer(user))).andExpect(status().isOk());
        verify(products, times(1)).findBasicPage(null, null, 0, 20);
        verify(products, times(0)).findBasicPage(null, ProductSort.LATEST, 0, 20);
        mvc.perform(get("/api/v1/products")).andExpect(status().isUnauthorized());
        assertThat(redis.getExpire(key(CacheKeys.PRODUCTS, CacheKeys.products("latest", null, 0, 20), CacheKeys.CATALOG_REVISION))).isBetween(269L, 330L);
    }

    @Test void updateRollbackDeletionAndLateOldFillCannotExposeStaleProduct() throws Exception {
        String original = detail();
        String old = key(CacheKeys.PRODUCTS, CacheKeys.product(product.getId()), CacheKeys.CATALOG_REVISION);
        new TransactionTemplate(tm).executeWithoutResult(tx -> {
            products.update(product.getId(), "rollback", 200L, null); tx.setRollbackOnly();
        });
        assertThat(detail()).isEqualTo(original);
        products.update(product.getId(), "after", 300L, null);
        redis.opsForValue().set(old, original); // old reader completes after commit
        assertThat(detail()).contains("after").doesNotContain("before");
        products.delete(product.getId());
        redis.opsForValue().set(old, original);
        mvc.perform(get("/api/v1/products/{id}", product.getId()).with(customer(user))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/products").with(customer(user))).andExpect(jsonPath("$.data.content.length()").value(0));
    }

    @Test void countTtlAndOwnLikesOwnershipAreSeparate() throws Exception {
        String url = "/api/v1/products/like-counts?ids="+product.getId();
        mvc.perform(get(url).with(customer(user))).andExpect(jsonPath("$.data[0].likeCount").value(0));
        String key = key(CacheKeys.COUNTS, CacheKeys.batch(CacheKeys.COUNTS, java.util.List.of(product.getId())), CacheKeys.CATALOG_REVISION);
        assertThat(redis.getExpire(key)).isBetween(269L, 330L);
        mvc.perform(get("/api/v1/users/{id}/likes", user.getId()).with(customer(user))).andExpect(jsonPath("$.data.content.length()").value(0));
        likes.register(user.getId(), product.getId());
        mvc.perform(get(url).with(customer(user))).andExpect(jsonPath("$.data[0].likeCount").value(0));
        mvc.perform(get("/api/v1/users/{id}/likes", user.getId()).with(customer(user))).andExpect(jsonPath("$.data.content.length()").value(1));
        var other = fixture.user();
        mvc.perform(get("/api/v1/users/{id}/likes", user.getId()).with(customer(other))).andExpect(status().isNotFound());
        redis.expire(key, Duration.ofMillis(1));
        Thread.sleep(10);
        mvc.perform(get(url).with(customer(user))).andExpect(jsonPath("$.data[0].likeCount").value(1));
        likes.cancel(user.getId(), product.getId());
        mvc.perform(get("/api/v1/users/{id}/likes", user.getId()).with(customer(user))).andExpect(jsonPath("$.data.content.length()").value(0));
    }

    @Test void adminBrandCacheReflectsWritesAndKeepsAdminBoundary() throws Exception {
        String url = "/api-admin/v1/brands";
        mvc.perform(get(url).with(com.loopers.support.http.ApiHttp.admin())).andExpect(jsonPath("$.data.content[0].name").value("cache-split"));
        brands.update(brand.getId(), "renamed brand");
        mvc.perform(get(url).with(com.loopers.support.http.ApiHttp.admin())).andExpect(jsonPath("$.data.content[0].name").value("renamed brand"));
        mvc.perform(get(url).with(customer(user))).andExpect(status().isForbidden());
        assertThat(redis.getExpire(key(CacheKeys.BRANDS, CacheKeys.brands(0, 20), CacheKeys.CATALOG_REVISION))).isBetween(269L, 330L);
    }

    @Test void malformedNonExpiringCacheIsRemovedAndRefilled() throws Exception {
        String key = key(CacheKeys.PRODUCTS, CacheKeys.product(product.getId()), CacheKeys.CATALOG_REVISION);
        redis.opsForValue().set(key, "broken JSON");
        assertThat(detail()).contains("before");
        assertThat(redis.hasKey(key)).isFalse();
        detail();
        assertThat(redis.opsForValue().get(key)).contains("before");
    }

    @Test void redisReadAndWriteFailuresFallBackToCurrentDatabaseResult() throws Exception {
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

    @Test void rankingExpiresButCatalogChangesInvalidateItImmediately() throws Exception {
        String url = "/api/v1/products?sort=likes_desc";
        mvc.perform(get(url).with(customer(user))).andExpect(status().isOk());
        String key = key(CacheKeys.RANKINGS, CacheKeys.products("likes_desc", null, 0, 20), CacheKeys.CATALOG_REVISION);
        assertThat(redis.getExpire(key)).isBetween(269L, 330L);
        products.update(product.getId(), "rank renamed", 500L, null);
        mvc.perform(get(url).with(customer(user))).andExpect(jsonPath("$.data.content[0].name").value("rank renamed"));
        products.delete(product.getId());
        mvc.perform(get(url).with(customer(user))).andExpect(jsonPath("$.data.content.length()").value(0));
    }

    @Test void confirmingOrderInvalidatesCachedStockUsingExistingWritePath() throws Exception {
        var buyer = fixture.userWithPoint(1000L);
        String url = "/api/v1/products/stocks?ids=" + product.getId();
        mvc.perform(get(url).with(customer(buyer))).andExpect(jsonPath("$.data[0].soldOut").value(false));
        var order = orders.create(buyer.getId(), java.util.List.of(
            new com.loopers.order.application.OrderUseCase.ItemCommand(product.getId(), 1)));
        orders.confirm(buyer.getId(), order.getId());
        mvc.perform(get(url).with(customer(buyer))).andExpect(jsonPath("$.data[0].soldOut").value(true));
    }

    @Test void cacheHitDoesNotRenewTheRemainingTtl() throws Exception {
        String original = detail();
        String key = key(CacheKeys.PRODUCTS, CacheKeys.product(product.getId()), CacheKeys.CATALOG_REVISION);
        redis.expire(key, Duration.ofSeconds(120));
        clearInvocations(products);
        assertThat(detail()).isEqualTo(original);
        verify(products, times(0)).findBasicProduct(product.getId());
        assertThat(redis.getExpire(key)).isBetween(119L, 120L);
    }

    @Test void everyCacheBucketUsesExpiringJitteredEntries() {
        for (String bucket : CacheKeys.NAMES) {
            cacheManager.getCache(bucket).put("ttl-policy-test", "value");
            assertThat(redis.getExpire(bucket + "::ttl-policy-test")).as(bucket).isBetween(269L, 330L);
        }
    }

    @Test void stockMutationAndBrandDeletionInvalidateBatches() throws Exception {
        String stockUrl = "/api/v1/products/stocks?ids="+product.getId();
        String countsUrl = "/api/v1/products/like-counts?ids="+product.getId();
        mvc.perform(get(stockUrl).with(customer(user))).andExpect(jsonPath("$.data[0].soldOut").value(false));
        mvc.perform(get(countsUrl).with(customer(user))).andExpect(jsonPath("$.data.length()").value(1));
        products.changeStock(product.getId(), 0);
        mvc.perform(get(stockUrl).with(customer(user))).andExpect(jsonPath("$.data[0].soldOut").value(true));
        brands.delete(brand.getId());
        mvc.perform(get(stockUrl).with(customer(user))).andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(get(countsUrl).with(customer(user))).andExpect(jsonPath("$.data.length()").value(0));
    }
}
