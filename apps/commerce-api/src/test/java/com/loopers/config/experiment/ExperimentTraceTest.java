package com.loopers.config.experiment;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExperimentTraceTest {
    @AfterEach void clear() { ExperimentTrace.clear(); }

    @Test void recordsFailedJdbcCallWithoutReplacingItsException() throws Exception {
        Connection raw = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        SQLException failure = new SQLException("failure", "42000");
        when(raw.prepareStatement("select ?")).thenReturn(statement);
        when(statement.executeQuery()).thenThrow(failure);
        ExperimentTrace trace = new ExperimentTrace();
        ExperimentTrace.begin(trace);
        PreparedStatement wrapped = ExperimentTrace.wrap(raw).prepareStatement("select ?");
        assertThatThrownBy(wrapped::executeQuery).isSameAs(failure);
        assertThat(trace.queries).hasSize(1);
        assertThat(trace.queries.getFirst().sql()).isEqualTo("select ?");
        assertThat(trace.queries.getFirst().durationMs()).isGreaterThanOrEqualTo(0);
        assertThat(trace.queries.getFirst().success()).isFalse();
        ExperimentTrace.clear();
        assertThatThrownBy(wrapped::executeQuery).isSameAs(failure);
        assertThat(trace.queries).hasSize(1);
    }

    @Test void rejectsUnboundedOrUnsafeCorrelationHeaders() {
        assertThat(ExperimentConfiguration.TraceValve.validId("baseline-01.1_2")).isTrue();
        assertThat(ExperimentConfiguration.TraceValve.validId("bad\"\nheader")).isFalse();
        assertThat(ExperimentConfiguration.TraceValve.validId("a".repeat(121))).isFalse();
        assertThat(ExperimentConfiguration.TraceValve.validId(null)).isFalse();
    }
}
