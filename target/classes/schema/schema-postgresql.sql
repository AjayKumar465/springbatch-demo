-- ============================================================
-- PostgreSQL schema for Order ETL pipeline
-- Run: psql -U <user> -d batchdb -f schema-postgresql.sql
-- ============================================================

CREATE TABLE IF NOT EXISTS customer (
    id              BIGSERIAL       PRIMARY KEY,
    external_id     VARCHAR(50)     NOT NULL,
    name            VARCHAR(255)    NOT NULL,
    email           VARCHAR(255)    NOT NULL,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_customer_external_id UNIQUE (external_id)
);

CREATE INDEX IF NOT EXISTS idx_customer_external_id ON customer (external_id);

CREATE TABLE IF NOT EXISTS customer_order (
    id                  BIGSERIAL       PRIMARY KEY,
    customer_id         BIGINT          NOT NULL,
    external_order_id   VARCHAR(50)     NOT NULL,
    order_date          DATE            NOT NULL,
    total_amount        NUMERIC(15,2)   NOT NULL,
    status              VARCHAR(30)     NOT NULL,
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_order_customer FOREIGN KEY (customer_id) REFERENCES customer (id),
    CONSTRAINT uq_order_external_id UNIQUE (external_order_id)
);

CREATE INDEX IF NOT EXISTS idx_order_external_id ON customer_order (external_order_id);
CREATE INDEX IF NOT EXISTS idx_order_customer_id ON customer_order (customer_id);

CREATE TABLE IF NOT EXISTS order_transaction (
    id                      BIGSERIAL       PRIMARY KEY,
    order_id                BIGINT          NOT NULL,
    external_transaction_id VARCHAR(50)     NOT NULL,
    transaction_type        VARCHAR(30)     NOT NULL,
    amount                  NUMERIC(15,2)   NOT NULL,
    status                  VARCHAR(30)     NOT NULL,
    created_at              TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP       NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_txn_order FOREIGN KEY (order_id) REFERENCES customer_order (id),
    CONSTRAINT uq_txn_external_id UNIQUE (external_transaction_id)
);

CREATE INDEX IF NOT EXISTS idx_txn_external_id ON order_transaction (external_transaction_id);
CREATE INDEX IF NOT EXISTS idx_txn_order_id ON order_transaction (order_id);
