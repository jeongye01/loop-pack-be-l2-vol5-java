package com.loopers.experiment;

import com.loopers.testcontainers.MySqlTestContainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(MySqlTestContainersConfig.class)
class IsolationProbeTest {

    @Autowired private DataSource dataSource;

    @Test
    void verifiesActualConnectionIsolation() throws Exception {
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT @@session.transaction_isolation")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).isEqualTo("SERIALIZABLE");
            assertThat(connection.getTransactionIsolation()).isEqualTo(Connection.TRANSACTION_SERIALIZABLE);
            System.out.println("EXPERIMENT_ISOLATION=SERIALIZABLE");
        }
    }
}
