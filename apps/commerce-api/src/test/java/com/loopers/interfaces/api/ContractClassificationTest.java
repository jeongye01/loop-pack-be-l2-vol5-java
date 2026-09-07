package com.loopers.interfaces.api;

import com.loopers.domain.example.ExampleModel;
import com.loopers.infrastructure.example.ExampleJpaRepository;
import com.loopers.interfaces.api.example.ExampleV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ContractClassificationTest {

    private static final String EXAMPLES_ENDPOINT = "/api/v1/examples/";
    private static final String UNMAPPED_ENDPOINT = "/api/v1/unmapped";
    private static final String EXAMPLE_NAME = "예시 제목";
    private static final String EXAMPLE_DESCRIPTION = "예시 설명";

    private final TestRestTemplate testRestTemplate;
    private final ExampleJpaRepository exampleJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    ContractClassificationTest(
        TestRestTemplate testRestTemplate,
        ExampleJpaRepository exampleJpaRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.exampleJpaRepository = exampleJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("존재하는 숫자 ID는 성공 계약으로 분류한다.")
    @Test
    void classifiesExistingNumericIdAsSuccess() {
        // arrange
        ExampleModel example = exampleJpaRepository.save(new ExampleModel(EXAMPLE_NAME, EXAMPLE_DESCRIPTION));

        // act
        ResponseEntity<ApiResponse<ExampleV1Dto.ExampleResponse>> response = get(EXAMPLES_ENDPOINT + example.getId());
        ApiResponse<ExampleV1Dto.ExampleResponse> body = response.getBody();

        // assert
        assertAll(
            () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
            () -> assertThat(body).isNotNull(),
            () -> assertThat(body.meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
            () -> assertThat(body.meta().errorCode()).isNull(),
            () -> assertThat(body.data()).isNotNull(),
            () -> assertThat(body.data().id()).isEqualTo(example.getId()),
            () -> assertThat(body.data().name()).isEqualTo(EXAMPLE_NAME),
            () -> assertThat(body.data().description()).isEqualTo(EXAMPLE_DESCRIPTION)
        );
    }

    @DisplayName("숫자가 아닌 ID는 문법 오류 계약으로 분류한다.")
    @Test
    void classifiesNonNumericIdAsBadRequest() {
        // act
        ResponseEntity<ApiResponse<ExampleV1Dto.ExampleResponse>> response = get(EXAMPLES_ENDPOINT + "abc");
        ApiResponse<ExampleV1Dto.ExampleResponse> body = response.getBody();

        // assert
        assertFailureContract(response, body, HttpStatus.BAD_REQUEST, "Bad Request");
    }

    @DisplayName("존재하지 않는 숫자 ID는 자원 미존재 계약으로 분류한다.")
    @Test
    void classifiesMissingNumericIdAsNotFound() {
        // act
        ResponseEntity<ApiResponse<ExampleV1Dto.ExampleResponse>> response = get(EXAMPLES_ENDPOINT + -1L);
        ApiResponse<ExampleV1Dto.ExampleResponse> body = response.getBody();

        // assert
        assertFailureContract(response, body, HttpStatus.NOT_FOUND, "Not Found");
    }

    @DisplayName("연결되지 않은 URL은 미매핑 계약으로 분류한다.")
    @Test
    void classifiesUnmappedUrlAsNotFound() {
        // act
        ResponseEntity<ApiResponse<ExampleV1Dto.ExampleResponse>> response = get(UNMAPPED_ENDPOINT);
        ApiResponse<ExampleV1Dto.ExampleResponse> body = response.getBody();

        // assert
        assertFailureContract(response, body, HttpStatus.NOT_FOUND, "Not Found");
    }

    private ResponseEntity<ApiResponse<ExampleV1Dto.ExampleResponse>> get(String endpoint) {
        ParameterizedTypeReference<ApiResponse<ExampleV1Dto.ExampleResponse>> responseType =
            new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(endpoint, HttpMethod.GET, new HttpEntity<>(null), responseType);
    }

    private void assertFailureContract(
        ResponseEntity<ApiResponse<ExampleV1Dto.ExampleResponse>> response,
        ApiResponse<ExampleV1Dto.ExampleResponse> body,
        HttpStatus expectedStatus,
        String expectedErrorCode
    ) {
        assertAll(
            () -> assertThat(response.getStatusCode()).isEqualTo(expectedStatus),
            () -> assertThat(body).isNotNull(),
            () -> assertThat(body.meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
            () -> assertThat(body.meta().errorCode()).isEqualTo(expectedErrorCode),
            () -> assertThat(body.data()).isNull()
        );
    }
}
