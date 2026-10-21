package com.loopers.batch.payment;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class JdbcPaymentReconciliationRepository implements PaymentReconciliationRepository {
    private static final RowMapper<PaymentRecord> PAYMENT_RECORD_MAPPER = (resultSet, rowNumber) ->
        new PaymentRecord(resultSet.getLong("id"), resultSet.getString("provider_order_id"),
            resultSet.getString("status"));
    private static final RowMapper<PaymentOrderState> PAYMENT_ORDER_STATE_MAPPER = (resultSet, rowNumber) ->
        new PaymentOrderState(resultSet.getString("payment_status"), resultSet.getLong("order_id"),
            resultSet.getString("order_status"), resultSet.getString("reservation_status"));
    private static final RowMapper<OrderItemStock> ORDER_ITEM_STOCK_MAPPER = (resultSet, rowNumber) ->
        new OrderItemStock(resultSet.getLong("product_id"), resultSet.getInt("quantity"));

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public JdbcPaymentReconciliationRepository(JdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @Override
    public List<PaymentRecord> findRecentTerminalPayments(LocalDateTime from, LocalDateTime until, long afterId,
                                                         int limit) {
        return jdbcTemplate.query(
            "select id, provider_order_id, status from payments"
                + " where status in ('SUCCESS', 'FAILED') and updated_at >= ? and updated_at < ?"
                + " and deleted_at is null and id > ? order by id limit ?",
            PAYMENT_RECORD_MAPPER,
            java.sql.Timestamp.valueOf(from), java.sql.Timestamp.valueOf(until), afterId, limit
        );
    }

    @Override
    public List<PaymentRecord> findCancelProcessingPayments(long afterId, int limit) {
        return jdbcTemplate.query(
            "select id, provider_order_id, status from payments"
                + " where status = 'CANCEL_PROCESSING' and deleted_at is null and id > ? order by id limit ?",
            PAYMENT_RECORD_MAPPER, afterId, limit
        );
    }

    @Override
    public int markCanceled(long paymentId, String rawProviderResponse) {
        return jdbcTemplate.update(
            "update payments set status = 'CANCELED', raw_provider_response = ?, updated_at = current_timestamp"
                + " where id = ? and status = 'CANCEL_PROCESSING' and deleted_at is null",
            rawProviderResponse, paymentId
        );
    }

    @Override
    public int compensateSucceededPaymentAsFailed(long paymentId, String rawProviderResponse) {
        Integer updated = transactionTemplate.execute(transaction -> {
            List<PaymentOrderState> states = jdbcTemplate.query(
                "select p.status as payment_status, o.id as order_id, o.status as order_status,"
                    + " o.stock_reservation_status as reservation_status"
                    + " from payments p join orders o on o.payment_id = p.id where p.id = ? for update",
                PAYMENT_ORDER_STATE_MAPPER, paymentId);
            if (states.isEmpty() || !isCompensatable(states.getFirst())) {
                return 0;
            }

            long orderId = states.getFirst().orderId();
            List<OrderItemStock> items = jdbcTemplate.query(
                "select product_id, sum(quantity) as quantity from orders_items"
                    + " where order_id = ? group by product_id",
                ORDER_ITEM_STOCK_MAPPER, orderId);
            if (items.isEmpty()) {
                return 0;
            }
            for (OrderItemStock item : items) {
                int restored = jdbcTemplate.update(
                    "update stock set quantity = quantity + ? where product_id = ?",
                    item.quantity(), item.productId());
                if (restored != 1) {
                    throw new IllegalStateException("Missing stock for payment reconciliation: " + item.productId());
                }
            }

            int orderUpdated = jdbcTemplate.update(
                "update orders set status = 'CANCELED', stock_reservation_status = null,"
                    + " updated_at = current_timestamp where id = ? and status = 'CONFIRMED'"
                    + " and stock_reservation_status = 'CONSUMED'",
                orderId);
            int paymentUpdated = jdbcTemplate.update(
                "update payments set status = 'FAILED', raw_provider_response = ?,"
                    + " updated_at = current_timestamp where id = ? and status = 'SUCCESS'",
                rawProviderResponse, paymentId);
            if (orderUpdated != 1 || paymentUpdated != 1) {
                throw new IllegalStateException("Payment reconciliation compensation lost its state guard");
            }
            return 1;
        });
        return updated == null ? 0 : updated;
    }

    private boolean isCompensatable(PaymentOrderState state) {
        return "SUCCESS".equals(state.paymentStatus()) && "CONFIRMED".equals(state.orderStatus())
            && "CONSUMED".equals(state.reservationStatus());
    }

    private record PaymentOrderState(String paymentStatus, long orderId, String orderStatus,
                                     String reservationStatus) {
    }

    private record OrderItemStock(long productId, int quantity) {
    }
}
