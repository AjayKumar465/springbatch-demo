-- ============================================================
-- Truncate all tables (application + Spring Batch metadata)
-- Run: psql -U postgres -d batchdb -f scripts/truncate-tables.sql
-- ============================================================

BEGIN;

-- Application tables (order matters: children before parents due to FK)
TRUNCATE TABLE order_transaction CASCADE;
TRUNCATE TABLE customer_order CASCADE;
TRUNCATE TABLE customer CASCADE;

-- Spring Batch metadata tables
TRUNCATE TABLE batch_step_execution_context CASCADE;
TRUNCATE TABLE batch_job_execution_context CASCADE;
TRUNCATE TABLE batch_step_execution CASCADE;
TRUNCATE TABLE batch_job_execution_params CASCADE;
TRUNCATE TABLE batch_job_execution CASCADE;
TRUNCATE TABLE batch_job_instance CASCADE;

COMMIT;
