# Spring Batch ETL — S3 to Redshift / PostgreSQL

Production-ready Spring Batch 5 application that reads denormalized order data from a **local CSV file** or **AWS S3** (single file or folder), transforms it into normalised **Customer**, **Order**, and **Order Transaction** entities, and batch-inserts them into **Amazon Redshift** (production) or **PostgreSQL** (local development) via JPA repositories.

## Tech Stack

| Component     | Version / Library                        |
|---------------|------------------------------------------|
| Java          | 17                                       |
| Spring Boot   | 3.2.5                                    |
| Spring Batch  | 5.1.x (managed by Boot)                 |
| Spring Data JPA | (managed by Boot)                      |
| AWS S3 access | Spring Cloud AWS 3.1.1                   |
| DB – local    | PostgreSQL (`org.postgresql`)            |
| DB – prod     | Amazon Redshift (`redshift-jdbc42`)      |
| Build         | Maven                                    |

## Project Structure

```
src/main/java/com/example/springbatchdemo/
├── config/        # BatchConfig, S3Properties, BatchProperties
├── domain/        # RawOrderRecord, Customer, Order, OrderTransaction, OrderDataComposite
├── job/           # Job/step config, reader factory, processor, JPA composite writer
├── listener/      # Job execution listener
└── repository/    # JPA repositories
src/main/resources/
├── application.yml          # base config
├── application-local.yml    # PostgreSQL + local schema init
├── application-prod.yml     # Redshift + S3
└── schema/
    ├── schema-postgresql.sql
    └── schema-redshift.sql
sample-data/
└── orders_sample.csv        # sample input for local runs
```

---

## Prerequisites

- **Java 17+**
- **Maven 3.8+** (or use the included `./mvnw` wrapper)
- **PostgreSQL 14+** (for `local` profile)
- **AWS credentials** (for `prod` profile with S3)

---

## Setup & Run

### Step 1: Build

```bash
./mvnw clean package -DskipTests
# or: mvn clean package -DskipTests
```

### Step 2: Create Database & Schema (local only)

```bash
createdb batchdb
psql -U postgres -d batchdb -f src/main/resources/schema/schema-postgresql.sql
```

### Step 3: Run — Local (PostgreSQL + local CSV file)

The `local` profile uses PostgreSQL and expects a local CSV file. You **must** set:

- `app.s3.use-local-file=true` — read from filesystem instead of S3
- `app.s3.key` — path to the CSV file (absolute or relative to working directory)

```bash
java -jar target/springbatch-demo-1.0.0-SNAPSHOT.jar \
  --spring.profiles.active=local \
  --app.s3.use-local-file=true \
  --app.s3.key=sample-data/orders_sample.csv
```

Or with environment variables:

```bash
export SPRING_PROFILES_ACTIVE=local
export APP_S3_USE_LOCAL_FILE=true
export APP_S3_KEY=sample-data/orders_sample.csv
java -jar target/springbatch-demo-1.0.0-SNAPSHOT.jar
```

### Step 4: Run — Production (Redshift + S3)

**Single S3 file**

```bash
export AWS_ACCESS_KEY_ID=your-access-key
export AWS_SECRET_ACCESS_KEY=your-secret-key
export AWS_REGION=us-east-1
export S3_BUCKET=my-prod-bucket
export S3_KEY=data/orders.csv
export SPRING_DATASOURCE_URL=jdbc:redshift://your-cluster.region.redshift.amazonaws.com:5439/your_db
export SPRING_DATASOURCE_USERNAME=admin
export SPRING_DATASOURCE_PASSWORD=secret

java -jar target/springbatch-demo-1.0.0-SNAPSHOT.jar --spring.profiles.active=prod
```

**S3 folder (all `.csv` files under a prefix)**

```bash
export S3_BUCKET=my-prod-bucket
export S3_PREFIX=data/orders/
# ... other env vars as above ...

java -jar target/springbatch-demo-1.0.0-SNAPSHOT.jar --spring.profiles.active=prod --app.s3.use-folder=true
```

Before running prod, create the Redshift schema:

```bash
# Run via SQL Workbench or psql:
# src/main/resources/schema/schema-redshift.sql
```

---

## Configuration Reference

| Property                                 | Env Variable               | Default (base)              |
|------------------------------------------|----------------------------|-----------------------------|
| `spring.datasource.url`                  | `SPRING_DATASOURCE_URL`    | `jdbc:postgresql://localhost:5432/batchdb` |
| `spring.datasource.username`             | `SPRING_DATASOURCE_USERNAME` | `postgres`                |
| `spring.datasource.password`             | `SPRING_DATASOURCE_PASSWORD` | `postgres`                |
| `spring.cloud.aws.s3.bucket-name`        | `S3_BUCKET`                | `springbatch-demo`          |
| `app.s3.key`                             | `S3_KEY` / `APP_S3_KEY`    | *(required for local/single S3)* |
| `app.s3.use-local-file`                  | `APP_S3_USE_LOCAL_FILE`    | `false`                     |
| `app.s3.use-folder`                      | `APP_S3_USE_FOLDER`        | `false` (prod: `false`)     |
| `app.s3.prefix`                          | `S3_PREFIX`                | `data` (when use-folder)    |
| `app.batch.chunk-size`                   | –                          | `100` (prod: `500`)         |
| `app.batch.skip-limit`                   | –                          | `10`                        |
| `app.batch.retry-limit`                  | –                          | `3`                         |

**Input modes**

| Mode        | Conditions                          | `app.s3.key` / `app.s3.prefix` |
|-------------|-------------------------------------|--------------------------------|
| Local file  | `use-local-file=true`               | Path to CSV file               |
| S3 single   | `use-local-file=false`, `use-folder=false` | S3 object key            |
| S3 folder   | `use-folder=true`                   | `prefix` = S3 prefix (e.g. `data/orders/`) |

---

## Tests

```bash
./mvnw test
# or: mvn test
```

- **Unit tests** — `OrderDataItemProcessorTest` validates transformation and filtering logic.
- **Integration tests** — `OrderETLJobIntegrationTest` runs the full job against H2 (PostgreSQL mode) with local file input and verifies row counts and idempotency.

---

## Input File Format

Denormalized CSV with header (one row per order-transaction):

```
customer_id,customer_name,customer_email,order_id,order_date,order_amount,order_status,transaction_id,transaction_type,transaction_amount,transaction_status
CUST001,John Doe,john.doe@example.com,ORD001,2025-01-15,250.00,COMPLETED,TXN001,PAYMENT,250.00,SUCCESS
```

Sample file: `sample-data/orders_sample.csv`

## N+1 Avoidance

| Layer         | Strategy                                                               |
|---------------|------------------------------------------------------------------------|
| Reader        | Single stream from S3 / local file                                     |
| Processor     | Pure in-memory transformation                                          |
| Writer        | JPA batch upserts via `saveAll()` with bulk `findByExternalIdIn`       |
| ID resolution | One bulk `SELECT ... WHERE external_id IN (...)` per entity per chunk  |

## Pipeline Flow

1. `FlatFileItemReader` (or `MultiResourceItemReader` for S3 folder) streams CSV row-by-row.
2. `OrderDataItemProcessor` maps each row into a `Customer` + `Order` + `OrderTransaction` composite, filtering invalid rows (returns `null`).
3. `OrderDataCompositeJpaItemWriter` (per chunk):
   - Bulk-fetches existing customers/orders/transactions by external IDs.
   - Merges chunk data with existing entities (update) or creates new ones.
   - Calls `saveAll()` for each entity type; JPA persists within the step transaction.
4. Spring Batch manages chunking, retries (`TransientDataAccessException`), skips (`FlatFileParseException`), and job metadata.
