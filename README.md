# Spring Batch ETL — S3 to Redshift / PostgreSQL

Production-ready Spring Batch 5 application that reads denormalized order data from an AWS S3 CSV file, transforms it into normalised **Customer**, **Order**, and **Order Transaction** entities, and batch-inserts them into **Amazon Redshift** (production) or **PostgreSQL** (local development).

## Tech Stack

| Component     | Version / Library                        |
|---------------|------------------------------------------|
| Java          | 17                                       |
| Spring Boot   | 3.2.5                                    |
| Spring Batch  | 5.1.x (managed by Boot)                 |
| AWS S3 access | Spring Cloud AWS 3.1.1                   |
| DB – local    | PostgreSQL (`org.postgresql`)            |
| DB – prod     | Amazon Redshift (`redshift-jdbc42`)      |
| Build         | Maven                                    |

## Project Structure

```
src/main/java/com/example/springbatchdemo/
├── config/        # BatchConfig, S3Properties, BatchProperties
├── domain/        # RawOrderRecord, Customer, Order, OrderTransaction, OrderDataComposite
├── job/           # Job/step config, reader factory, processor, composite writer
└── listener/      # Job execution listener

src/main/resources/
├── application.yml          # base config
├── application-local.yml    # PostgreSQL + local file reader
├── application-prod.yml     # Redshift + S3 reader
└── schema/
    ├── schema-postgresql.sql
    └── schema-redshift.sql
```

## Prerequisites

- Java 17+
- Maven 3.8+
- PostgreSQL 14+ running locally (for `local` profile)
- AWS credentials configured (for `prod` profile)

## Database Setup

### PostgreSQL (local)

```bash
createdb batchdb
psql -U postgres -d batchdb -f src/main/resources/schema/schema-postgresql.sql
```

### Redshift (prod)

Run `src/main/resources/schema/schema-redshift.sql` against your Redshift cluster via SQL Workbench or `psql`.

## Configuration

All settings are externalised and can be overridden via environment variables.

| Property                                | Env Variable               | Default                    |
|-----------------------------------------|----------------------------|----------------------------|
| `spring.datasource.url`                 | `SPRING_DATASOURCE_URL`    | `jdbc:postgresql://localhost:5432/batchdb` |
| `spring.datasource.username`            | `SPRING_DATASOURCE_USERNAME` | `postgres`               |
| `spring.datasource.password`            | `SPRING_DATASOURCE_PASSWORD` | `postgres`               |
| `spring.cloud.aws.credentials.access-key` | `AWS_ACCESS_KEY_ID`      | –                          |
| `spring.cloud.aws.credentials.secret-key` | `AWS_SECRET_ACCESS_KEY`  | –                          |
| `spring.cloud.aws.region.static`        | `AWS_REGION`               | `us-east-1`               |
| `spring.cloud.aws.s3.bucket-name`       | `S3_BUCKET`                | `my-etl-bucket`            |
| `app.s3.key`                            | `S3_KEY`                   | `data/orders.csv`          |
| `app.s3.use-local-file`      | –                          | `false` (base), `true` (local) |
| `app.s3.use-folder`          | –                          | `false` – when true, read all CSV files under prefix |
| `app.s3.prefix`              | `S3_PREFIX`                | `data/orders/` – S3 prefix (folder) when use-folder is true |
| `app.batch.chunk-size`       | –                          | `100`                      |
| `app.batch.skip-limit`       | –                          | `10`                       |
| `app.batch.retry-limit`      | –                          | `3`                        |

## Build

```bash
mvn clean package -DskipTests
```

## Run

### Local (PostgreSQL + local CSV file)

```bash
java -jar target/springbatch-demo-1.0.0-SNAPSHOT.jar --spring.profiles.active=local
```

### Production (Redshift + S3)

```bash
export AWS_ACCESS_KEY_ID=your-access-key
export AWS_SECRET_ACCESS_KEY=your-secret-key
export AWS_REGION=us-east-1
export S3_BUCKET=my-prod-bucket
export S3_KEY=data/orders.csv
export SPRING_DATASOURCE_URL=jdbc:redshift://your-cluster:5439/your_db
export SPRING_DATASOURCE_USERNAME=admin
export SPRING_DATASOURCE_PASSWORD=secret

java -jar target/springbatch-demo-1.0.0-SNAPSHOT.jar --spring.profiles.active=prod

# Or read all CSV files in an S3 folder (prefix):
export S3_PREFIX=data/orders/
java -jar target/springbatch-demo-1.0.0-SNAPSHOT.jar --spring.profiles.active=prod --app.s3.use-folder=true
```

Alternatively, use `application.yml`:

```yaml
spring:
  cloud:
    aws:
      credentials:
        access-key: YOUR_ACCESS_KEY
        secret-key: YOUR_SECRET_KEY
      region:
        static: us-east-1
      s3:
        bucket-name: my-etl-bucket
```

## Tests

```bash
mvn test
```

- **Unit tests** — `OrderDataItemProcessorTest` validates transformation and filtering logic.
- **Integration tests** — `OrderETLJobIntegrationTest` runs the full job against H2 in PostgreSQL compatibility mode and verifies row counts and idempotency.

## Input File Format

Denormalized CSV (one row per order-transaction):

```
customer_id,customer_name,customer_email,order_id,order_date,order_amount,order_status,transaction_id,transaction_type,transaction_amount,transaction_status
CUST001,John Doe,john.doe@example.com,ORD001,2025-01-15,250.00,COMPLETED,TXN001,PAYMENT,250.00,SUCCESS
```

A sample file is provided at `sample-data/orders_sample.csv`.

## N+1 Avoidance

| Layer         | Strategy                                                               |
|---------------|------------------------------------------------------------------------|
| Reader        | Single stream from S3 / local file                                     |
| Processor     | Pure in-memory transformation                                          |
| Writer        | JPA batch upserts via `saveAll()` with bulk `findByExternalIdIn`       |
| ID resolution | One bulk `SELECT ... WHERE external_id IN (...)` per entity per chunk  |

## Pipeline Flow

1. `FlatFileItemReader` streams the CSV row-by-row.
2. `OrderDataItemProcessor` maps each row into a `Customer` + `Order` + `OrderTransaction` composite, filtering invalid rows.
3. `OrderDataCompositeJpaItemWriter` (per chunk):
   - Batch upserts customers (ON CONFLICT).
   - Bulk-resolves customer DB IDs in one query.
   - Batch upserts orders with resolved `customer_id`.
   - Bulk-resolves order DB IDs in one query.
   - Batch upserts transactions with resolved `order_id`.
4. Spring Batch manages the chunk transaction, skip policy, and job metadata.
