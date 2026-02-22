package com.example.springbatchdemo.job;

import com.example.springbatchdemo.config.UpsertSqlProvider;
import com.example.springbatchdemo.domain.Customer;
import com.example.springbatchdemo.domain.Order;
import com.example.springbatchdemo.domain.OrderDataComposite;
import com.example.springbatchdemo.domain.OrderTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Custom chunk-level writer that batch-inserts customers, orders, and
 * transactions in the correct FK order, resolving DB-generated IDs via
 * one bulk SELECT per entity type per chunk (avoids N+1).
 *
 * All writes happen within the chunk transaction managed by Spring Batch.
 */
@Component
public class OrderDataCompositeItemWriter implements ItemWriter<OrderDataComposite> {

    private static final Logger log = LoggerFactory.getLogger(OrderDataCompositeItemWriter.class);

    private static final String SELECT_CUSTOMER_IDS =
            "SELECT id, external_id FROM customer WHERE external_id IN (:externalIds)";

    private static final String SELECT_ORDER_IDS =
            "SELECT id, external_order_id FROM customer_order WHERE external_order_id IN (:externalOrderIds)";

    private final NamedParameterJdbcTemplate jdbc;
    private final UpsertSqlProvider sqlProvider;

    public OrderDataCompositeItemWriter(NamedParameterJdbcTemplate jdbc, UpsertSqlProvider sqlProvider) {
        this.jdbc = jdbc;
        this.sqlProvider = sqlProvider;
    }

    @Override
    public void write(Chunk<? extends OrderDataComposite> chunk) {
        List<? extends OrderDataComposite> items = chunk.getItems();
        if (items.isEmpty()) {
            return;
        }

        log.debug("Writing chunk of {} composite items", items.size());

        batchUpsertCustomers(items);
        Map<String, Long> customerIdMap = bulkResolveCustomerIds(items);

        batchUpsertOrders(items, customerIdMap);
        Map<String, Long> orderIdMap = bulkResolveOrderIds(items);

        batchUpsertTransactions(items, orderIdMap);
    }

    private void batchUpsertCustomers(List<? extends OrderDataComposite> items) {
        Map<String, Customer> unique = new LinkedHashMap<>();
        for (OrderDataComposite item : items) {
            Customer c = item.getCustomer();
            unique.putIfAbsent(c.getExternalId(), c);
        }

        SqlParameterSource[] params = unique.values().stream()
                .map(this::customerParams)
                .toArray(SqlParameterSource[]::new);

        jdbc.batchUpdate(sqlProvider.customerUpsert(), params);
        log.debug("Upserted {} unique customers", unique.size());
    }

    private Map<String, Long> bulkResolveCustomerIds(List<? extends OrderDataComposite> items) {
        Set<String> externalIds = items.stream()
                .map(i -> i.getCustomer().getExternalId())
                .collect(Collectors.toSet());

        MapSqlParameterSource params = new MapSqlParameterSource("externalIds", externalIds);
        Map<String, Long> map = new HashMap<>();
        jdbc.query(SELECT_CUSTOMER_IDS, params, rs -> {
            map.put(rs.getString("external_id"), rs.getLong("id"));
        });
        return map;
    }

    private void batchUpsertOrders(List<? extends OrderDataComposite> items, Map<String, Long> customerIdMap) {
        Map<String, OrderWithCustomerId> unique = new LinkedHashMap<>();
        for (OrderDataComposite item : items) {
            Order o = item.getOrder();
            Long customerId = customerIdMap.get(o.getCustomerExternalId());
            if (customerId == null) {
                log.warn("Customer ID not found for external_id={}, skipping order {}",
                        o.getCustomerExternalId(), o.getExternalOrderId());
                continue;
            }
            unique.putIfAbsent(o.getExternalOrderId(), new OrderWithCustomerId(o, customerId));
        }

        SqlParameterSource[] params = unique.values().stream()
                .map(this::orderParams)
                .toArray(SqlParameterSource[]::new);

        jdbc.batchUpdate(sqlProvider.orderUpsert(), params);
        log.debug("Upserted {} unique orders", unique.size());
    }

    private Map<String, Long> bulkResolveOrderIds(List<? extends OrderDataComposite> items) {
        Set<String> externalOrderIds = items.stream()
                .map(i -> i.getOrder().getExternalOrderId())
                .collect(Collectors.toSet());

        MapSqlParameterSource params = new MapSqlParameterSource("externalOrderIds", externalOrderIds);
        Map<String, Long> map = new HashMap<>();
        jdbc.query(SELECT_ORDER_IDS, params, rs -> {
            map.put(rs.getString("external_order_id"), rs.getLong("id"));
        });
        return map;
    }

    private void batchUpsertTransactions(List<? extends OrderDataComposite> items, Map<String, Long> orderIdMap) {
        List<SqlParameterSource> paramList = new ArrayList<>();
        for (OrderDataComposite item : items) {
            OrderTransaction txn = item.getTransaction();
            Long orderId = orderIdMap.get(txn.getOrderExternalId());
            if (orderId == null) {
                log.warn("Order ID not found for external_order_id={}, skipping transaction {}",
                        txn.getOrderExternalId(), txn.getExternalTransactionId());
                continue;
            }
            paramList.add(transactionParams(txn, orderId));
        }

        jdbc.batchUpdate(sqlProvider.transactionUpsert(), paramList.toArray(SqlParameterSource[]::new));
        log.debug("Upserted {} transactions", paramList.size());
    }

    private MapSqlParameterSource customerParams(Customer c) {
        LocalDateTime now = LocalDateTime.now();
        return new MapSqlParameterSource()
                .addValue("externalId", c.getExternalId())
                .addValue("name", c.getName())
                .addValue("email", c.getEmail())
                .addValue("createdAt", Timestamp.valueOf(c.getCreatedAt() != null ? c.getCreatedAt() : now))
                .addValue("updatedAt", Timestamp.valueOf(now));
    }

    private MapSqlParameterSource orderParams(OrderWithCustomerId owc) {
        LocalDateTime now = LocalDateTime.now();
        Order o = owc.order;
        return new MapSqlParameterSource()
                .addValue("customerId", owc.customerId)
                .addValue("externalOrderId", o.getExternalOrderId())
                .addValue("orderDate", java.sql.Date.valueOf(o.getOrderDate()))
                .addValue("totalAmount", o.getTotalAmount())
                .addValue("status", o.getStatus())
                .addValue("createdAt", Timestamp.valueOf(o.getCreatedAt() != null ? o.getCreatedAt() : now))
                .addValue("updatedAt", Timestamp.valueOf(now));
    }

    private MapSqlParameterSource transactionParams(OrderTransaction txn, Long orderId) {
        LocalDateTime now = LocalDateTime.now();
        return new MapSqlParameterSource()
                .addValue("orderId", orderId)
                .addValue("externalTransactionId", txn.getExternalTransactionId())
                .addValue("transactionType", txn.getTransactionType())
                .addValue("amount", txn.getAmount())
                .addValue("status", txn.getStatus())
                .addValue("createdAt", Timestamp.valueOf(txn.getCreatedAt() != null ? txn.getCreatedAt() : now))
                .addValue("updatedAt", Timestamp.valueOf(now));
    }

    private record OrderWithCustomerId(Order order, Long customerId) {
    }
}
