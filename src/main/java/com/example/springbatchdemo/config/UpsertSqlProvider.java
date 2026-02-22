package com.example.springbatchdemo.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Resolves the database dialect at startup and provides the correct
 * upsert SQL for each entity. Keeps the writer free of dialect branching.
 */
@Component
public class UpsertSqlProvider {

    private static final Logger log = LoggerFactory.getLogger(UpsertSqlProvider.class);

    private final DatabaseDialect dialect;

    public UpsertSqlProvider(DataSource dataSource) {
        this.dialect = detectDialect(dataSource);
        log.info("Detected database dialect: {}", dialect);
    }

    public DatabaseDialect getDialect() {
        return dialect;
    }

    public String customerUpsert() {
        return switch (dialect) {
            case POSTGRESQL, REDSHIFT -> """
                    INSERT INTO customer (external_id, name, email, created_at, updated_at)
                    VALUES (:externalId, :name, :email, :createdAt, :updatedAt)
                    ON CONFLICT (external_id) DO UPDATE SET
                        name = EXCLUDED.name,
                        email = EXCLUDED.email,
                        updated_at = EXCLUDED.updatedAt
                    """;
            case H2 -> """
                    MERGE INTO customer (external_id, name, email, created_at, updated_at)
                    KEY (external_id)
                    VALUES (:externalId, :name, :email, :createdAt, :updatedAt)
                    """;
        };
    }

    public String orderUpsert() {
        return switch (dialect) {
            case POSTGRESQL, REDSHIFT -> """
                    INSERT INTO customer_order (customer_id, external_order_id, order_date, total_amount, status, created_at, updated_at)
                    VALUES (:customerId, :externalOrderId, :orderDate, :totalAmount, :status, :createdAt, :updatedAt)
                    ON CONFLICT (external_order_id) DO UPDATE SET
                        total_amount = EXCLUDED.total_amount,
                        status = EXCLUDED.status,
                        updated_at = EXCLUDED.updatedAt
                    """;
            case H2 -> """
                    MERGE INTO customer_order (customer_id, external_order_id, order_date, total_amount, status, created_at, updated_at)
                    KEY (external_order_id)
                    VALUES (:customerId, :externalOrderId, :orderDate, :totalAmount, :status, :createdAt, :updatedAt)
                    """;
        };
    }

    public String transactionUpsert() {
        return switch (dialect) {
            case POSTGRESQL, REDSHIFT -> """
                    INSERT INTO order_transaction (order_id, external_transaction_id, transaction_type, amount, status, created_at, updated_at)
                    VALUES (:orderId, :externalTransactionId, :transactionType, :amount, :status, :createdAt, :updatedAt)
                    ON CONFLICT (external_transaction_id) DO UPDATE SET
                        amount = EXCLUDED.amount,
                        status = EXCLUDED.status,
                        updated_at = EXCLUDED.updatedAt
                    """;
            case H2 -> """
                    MERGE INTO order_transaction (order_id, external_transaction_id, transaction_type, amount, status, created_at, updated_at)
                    KEY (external_transaction_id)
                    VALUES (:orderId, :externalTransactionId, :transactionType, :amount, :status, :createdAt, :updatedAt)
                    """;
        };
    }

    private static DatabaseDialect detectDialect(DataSource dataSource) {
        try (Connection conn = dataSource.getConnection()) {
            String product = conn.getMetaData().getDatabaseProductName().toLowerCase();
            if (product.contains("h2")) {
                return DatabaseDialect.H2;
            } else if (product.contains("redshift")) {
                return DatabaseDialect.REDSHIFT;
            }
            return DatabaseDialect.POSTGRESQL;
        } catch (SQLException e) {
            log.warn("Could not detect database dialect, defaulting to POSTGRESQL", e);
            return DatabaseDialect.POSTGRESQL;
        }
    }
}
