package com.loopers.product.interfaces;

import com.loopers.support.fixture.CommerceFixture;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import static com.loopers.support.http.ApiHttp.customer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MySqlTestContainersConfig.class)
class ProductSplitHttpTest {
    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactions;
    CommerceFixture fixture;
    @BeforeEach void setup() { fixture = new CommerceFixture(em, transactions); }
    @AfterEach void cleanup() { fixture.truncateRemainingTables(); }

    @Test void separatesBasicInformationFromCountsAndStock() throws Exception {
        var user = fixture.user();
        var brand = fixture.brand("split-brand");
        var product = fixture.product(brand, "split-product", 100L, 0);
        fixture.like(user, product);
        mvc.perform(get("/api/v1/products/{id}", product.getId()).with(customer(user)))
            .andExpect(com.loopers.support.http.ApiHttp.success(org.springframework.http.HttpStatus.OK)).andExpect(jsonPath("$.data.name").value("split-product"))
            .andExpect(jsonPath("$.data.likeCount").doesNotExist()).andExpect(jsonPath("$.data.soldOut").doesNotExist());
        mvc.perform(get("/api/v1/products/like-counts").param("ids", product.getId()+",999999,"+product.getId()).with(customer(user)))
            .andExpect(com.loopers.support.http.ApiHttp.success(org.springframework.http.HttpStatus.OK)).andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].productId").value(product.getId()))
            .andExpect(jsonPath("$.data[0].likeCount").value(1));
        mvc.perform(get("/api/v1/products/stocks").param("ids", product.getId().toString()).with(customer(user)))
            .andExpect(com.loopers.support.http.ApiHttp.success(org.springframework.http.HttpStatus.OK)).andExpect(jsonPath("$.data[0].soldOut").value(true))
            .andExpect(jsonPath("$.data[0].quantity").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(strings = {"like-counts", "stocks"})
    void validatesIdsAndAuthentication(String path) throws Exception {
        var user = fixture.user();
        String url = "/api/v1/products/" + path;
        for (String invalid : new String[]{"", "0", "-1", "abc", "1,,2", "9223372036854775808",
            LongStream.rangeClosed(1,101).mapToObj(Long::toString).collect(Collectors.joining(","))}) {
            mvc.perform(get(url).param("ids", invalid).with(customer(user))).andExpect(status().isBadRequest());
        }
        mvc.perform(get(url).with(customer(user))).andExpect(status().isBadRequest());
        mvc.perform(get(url).param("ids", "999999")).andExpect(status().isUnauthorized());
        mvc.perform(get(url).param("ids", "999999").with(customer(user)))
            .andExpect(com.loopers.support.http.ApiHttp.success(org.springframework.http.HttpStatus.OK)).andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(get(url).param("ids", LongStream.rangeClosed(100000,100099).mapToObj(Long::toString)
                .collect(Collectors.joining(","))).with(customer(user)))
            .andExpect(com.loopers.support.http.ApiHttp.success(org.springframework.http.HttpStatus.OK)).andExpect(jsonPath("$.data.length()").value(0));
    }
}
