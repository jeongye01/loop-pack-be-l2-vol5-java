package com.loopers.config.experiment;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Synchronous JDBC timings only; bind values and result rows are never logged. */
public final class ExperimentTrace {
    private static final ThreadLocal<ExperimentTrace> CURRENT = new ThreadLocal<>();
    private static final int MAX_QUERIES = 500;
    public final List<Query> queries = new ArrayList<>();
    public double connectionWaitMs;
    public boolean truncated;

    public record Query(String sql, double durationMs, boolean success, String errorType) { }

    static void begin(ExperimentTrace trace) { CURRENT.set(trace); }
    static void clear() { CURRENT.remove(); }
    static ExperimentTrace current() { return CURRENT.get(); }

    static Connection wrap(Connection connection) {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
            new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                Object result = call(connection, method, args);
                if (result instanceof Statement statement &&
                    (method.getName().equals("prepareStatement") || method.getName().equals("prepareCall")
                        || method.getName().equals("createStatement"))) {
                    String sql = args != null && args.length > 0 && args[0] instanceof String s ? s : null;
                    return statement(statement, sql);
                }
                return result;
            });
    }

    private static Object statement(Statement target, String preparedSql) {
        Class<?> type = target instanceof CallableStatement ? CallableStatement.class
            : target instanceof PreparedStatement ? PreparedStatement.class : Statement.class;
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            ExperimentTrace trace = CURRENT.get();
            if (trace == null || !method.getName().startsWith("execute")) return call(target, method, args);
            String sql = preparedSql != null ? preparedSql
                : args != null && args.length > 0 && args[0] instanceof String s ? s : "<batch>";
            long start = System.nanoTime();
            Throwable failure = null;
            try {
                return call(target, method, args);
            } catch (Throwable error) {
                failure = error;
                throw error;
            } finally {
                if (trace.queries.size() < MAX_QUERIES) {
                    trace.queries.add(new Query(sql, (System.nanoTime() - start) / 1_000_000.0,
                        failure == null, failure == null ? null : failure.getClass().getSimpleName()));
                } else {
                    trace.truncated = true;
                }
            }
        });
    }

    private static Object call(Object target, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException error) {
            throw error.getCause();
        }
    }
}
