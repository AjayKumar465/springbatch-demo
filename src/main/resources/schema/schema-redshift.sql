-- ============================================================
-- Amazon Redshift schema for Order ETL pipeline
-- Run via SQL Workbench or psql against Redshift cluster
-- Note: Redshift does not enforce UNIQUE/FK; uniqueness is
-- enforced at application level (JPA upsert by external_id).
-- ============================================================

CREATE TABLE IF NOT EXISTS customer (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    external_id     VARCHAR(50)     NOT NULL,
    name            VARCHAR(255)    NOT NULL,
    email           VARCHAR(255)    NOT NULL,
    created_at      TIMESTAMP       NOT NULL DEFAULT GETDATE(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT GETDATE()
)
DISTSTYLE KEY
DISTKEY (external_id)
SORTKEY (id);

CREATE TABLE IF NOT EXISTS customer_order (
    id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
    customer_id         BIGINT          NOT NULL,
    external_order_id   VARCHAR(50)     NOT NULL,
    order_date          DATE            NOT NULL,
    total_amount        DECIMAL(15,2)   NOT NULL,
    status              VARCHAR(30)     NOT NULL,
    created_at          TIMESTAMP       NOT NULL DEFAULT GETDATE(),
    updated_at          TIMESTAMP       NOT NULL DEFAULT GETDATE()
)
DISTSTYLE KEY
DISTKEY (customer_id)
SORTKEY (id);

-- Redshift does not enforce FK constraints but we document intent
-- ALTER TABLE customer_order ADD CONSTRAINT fk_order_customer
--     FOREIGN KEY (customer_id) REFERENCES customer (id);

CREATE TABLE IF NOT EXISTS order_transaction (
    id                      BIGINT IDENTITY(1,1) PRIMARY KEY,
    order_id                BIGINT          NOT NULL,
    external_transaction_id VARCHAR(50)     NOT NULL,
    transaction_type        VARCHAR(30)     NOT NULL,
    amount                  DECIMAL(15,2)   NOT NULL,
    status                  VARCHAR(30)     NOT NULL,
    created_at              TIMESTAMP       NOT NULL DEFAULT GETDATE(),
    updated_at              TIMESTAMP       NOT NULL DEFAULT GETDATE()
)
DISTSTYLE KEY
DISTKEY (order_id)
SORTKEY (id);

-- ALTER TABLE order_transaction ADD CONSTRAINT fk_txn_order
--     FOREIGN KEY (order_id) REFERENCES customer_order (id);
