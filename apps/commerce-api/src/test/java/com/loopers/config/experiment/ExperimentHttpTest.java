package com.loopers.config.experiment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"spring.jpa.show-sql=false", "management.server.port=0"})
@ActiveProfiles({"test", "experiment"})
@Import(MySqlTestContainersConfig.class)
@ExtendWith(OutputCaptureExtension.class)
class ExperimentHttpTest {
    @LocalServerPort int port;

    @Test void recordsActualTomcatTimeAndSqlOnAnHttpError(CapturedOutput output) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/products"))
            .header("X-USER-ID", "999999999")
            .header("X-Run-ID", "test-run").header("X-Request-ID", "test-request").GET().build();
        long start = System.nanoTime();
        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        double clientMs = (System.nanoTime() - start) / 1_000_000.0;
        assertThat(response.statusCode()).isEqualTo(401);
        String line = null;
        for (int i = 0; i < 50 && line == null; i++) {
            line = output.getAll().lines().filter(s -> s.contains("EXPERIMENT_JSON ") && s.contains("test-request"))
                .findFirst().orElse(null);
            if (line == null) Thread.sleep(20);
        }
        assertThat(line).isNotNull();
        var event = new ObjectMapper().readTree(line.substring(line.indexOf("EXPERIMENT_JSON ") + 16));
        assertThat(event.get("serverDurationMs").asDouble()).isPositive().isLessThan(clientMs);
        assertThat(event.get("httpStatus").asInt()).isEqualTo(401);
        assertThat(event.get("queries").size()).isGreaterThan(0);
        assertThat(event.get("queries").get(0).get("sql").asText()).containsIgnoringCase("select");
        assertThat(event.get("queries").get(0).get("durationMs").asDouble()).isGreaterThanOrEqualTo(0);
        assertThat(event.get("sqlTruncated").asBoolean()).isFalse();
    }
}
