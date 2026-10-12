package com.loopers.config.experiment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.sql.Connection;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.catalina.AccessLog;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("experiment")
public class ExperimentConfiguration {
    @Bean
    static BeanPostProcessor experimentDataSource() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String name) {
                if (!(bean instanceof HikariDataSource)) return bean;
                ProxyFactory factory = new ProxyFactory(bean);
                factory.setProxyTargetClass(true);
                factory.addAdvice((MethodInterceptor) invocation -> {
                    if (!invocation.getMethod().getName().equals("getConnection")) return invocation.proceed();
                    ExperimentTrace trace = ExperimentTrace.current();
                    long start = System.nanoTime();
                    try {
                        Connection connection = (Connection) invocation.proceed();
                        return trace == null ? connection : ExperimentTrace.wrap(connection);
                    } finally {
                        if (trace != null) trace.connectionWaitMs += (System.nanoTime() - start) / 1_000_000.0;
                    }
                });
                return factory.getProxy();
            }
        };
    }

    @Bean
    WebServerFactoryCustomizer<TomcatServletWebServerFactory> experimentAccessLog(MeterRegistry registry) {
        AtomicInteger active = registry.gauge("experiment.active.requests", new AtomicInteger());
        return factory -> factory.addContextValves(new TraceValve(active));
    }

    /** Tomcat calls AccessLog.log after response completion, including client disconnects. */
    static final class TraceValve extends ValveBase implements AccessLog {
        private static final Logger LOG = LoggerFactory.getLogger("EXPERIMENT_TRACE");
        private static final ObjectMapper JSON = new ObjectMapper();
        private static final String ATTRIBUTE = "experiment.trace";
        private final AtomicInteger active;
        private boolean attributesEnabled;

        TraceValve(AtomicInteger active) { super(true); this.active = active; }

        @Override
        public void invoke(Request request, Response response) throws IOException, ServletException {
            if (!validId(request.getHeader("X-Run-ID")) || !validId(request.getHeader("X-Request-ID"))
                || !request.getRequestURI().equals("/api/v1/products")) {
                getNext().invoke(request, response);
                return;
            }
            ExperimentTrace trace = new ExperimentTrace();
            request.setAttribute(ATTRIBUTE, trace);
            ExperimentTrace.begin(trace);
            active.incrementAndGet();
            try {
                getNext().invoke(request, response);
            } finally {
                ExperimentTrace.clear();
                active.decrementAndGet();
            }
        }

        static boolean validId(String value) {
            return value != null && value.matches("[A-Za-z0-9._-]{1,120}");
        }

        @Override
        public void log(Request request, Response response, long time) {
            if (!(request.getAttribute(ATTRIBUTE) instanceof ExperimentTrace trace)) return;
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("runId", request.getHeader("X-Run-ID"));
            event.put("requestId", request.getHeader("X-Request-ID"));
            event.put("finishedAt", Instant.now().toString());
            event.put("method", request.getMethod());
            event.put("path", request.getRequestURI());
            event.put("httpStatus", response.getStatus());
            // Tomcat 10.1 AccessLog receives nanoseconds from CoyoteAdapter.
            event.put("serverDurationMs", time / 1_000_000.0);
            event.put("connectionWaitMs", trace.connectionWaitMs);
            event.put("queries", trace.queries);
            event.put("sqlTruncated", trace.truncated);
            event.put("async", request.isAsync());
            event.put("serverError", response.getCoyoteResponse().getErrorException() == null ? null
                : response.getCoyoteResponse().getErrorException().getClass().getSimpleName());
            try {
                LOG.info("EXPERIMENT_JSON {}", JSON.writeValueAsString(event));
            } catch (IOException error) {
                LOG.warn("Experiment trace serialization failed", error);
            }
        }

        @Override public void setRequestAttributesEnabled(boolean value) { attributesEnabled = value; }
        @Override public boolean getRequestAttributesEnabled() { return attributesEnabled; }
    }
}
