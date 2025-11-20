# Trades Capture Service

A Spring Boot microservice for processing trade instructions from CSV or JSON uploads and Kafka.  
The service transforms raw trade data into a canonical format, applies masking and validation, generates platform-specific JSON, and publishes results to an outbound Kafka topic.

This README includes clear steps to **import the project into IntelliJ**, **run tests**, **start Kafka**, and **run the application** locally or using Docker.

---

## 0. Prerequisites

Make sure you have the following installed:

- **Java JDK 17** (required)
- **Maven 3.8+**
- **Docker Desktop** (for local Kafka + Docker image)
- Optional: **Postman** or **curl** for testing the REST API

---

## 1. Importing the Project into IntelliJ IDEA

1. Open **IntelliJ IDEA**.
2. Go to **File → New → Project from Existing Sources…**
3. Select the project folder:
   ```text
   trades-capture-service-final/
   ```
4. Choose **Import as Maven Project**.
5. Set **Project SDK = JDK 17**.
6. Wait for IntelliJ to finish downloading Maven dependencies and indexing.

---

## 2. Running Tests

To run all unit and integration tests from the command line:

```bash
mvn test
```

> Note: The integration test for the controller expects Kafka to be reachable on `localhost:9092`.  
> For a completely green build, make sure Kafka is running via Docker Compose (see Section 3).

You can also run tests from IntelliJ:

1. Open the **Maven** tool window.
2. Run **Lifecycle → test**  
   or right‑click the `test` folder and choose **Run 'All Tests'**.

---

## 3. Starting Kafka Using Docker Compose

Download docker : https://www.docker.com/products/docker-desktop/
check docker version in terminal docker --version 


In terminal : Kafka configuration for local development is already provided inside:

```text
kafka/local/docker-compose.yml
```

### Steps:

1. Navigate into the Kafka directory:

   ```bash
   cd kafka/local
   ```

2. Start Kafka and Zookeeper:

   ```bash
   docker compose up -d
   ```

3. Verify everything is running:

   ```bash
   docker ps
   ```

You should see **zookeeper** and **kafka** containers active and listening on `localhost:9092`.

---

## 4. Running the Application (Dev Mode)

### Option A — Run via IntelliJ

1. Open `InstructionsCaptureApplication.java`.
2. Click **Run ▶**.
3. Ensure the JVM options include the active profile:
   ```text
   -Dspring.profiles.active=dev
   ```

### Option B — Run via Maven

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

The application will start on:

```text
http://localhost:8080
```

---

## 5. REST API — Upload Trades

### 5.1 Upload a CSV file

```bash
curl -X POST "http://localhost:8080/api/trades/upload" \
  -H "Content-Type: multipart/form-data" \
  -F "file=@../sample-trades.csv"
```

### 5.2 Upload a JSON file

```bash
curl -X POST "http://localhost:8080/api/trades/upload" \
  -H "Content-Type: multipart/form-data" \
  -F "file=@../sample-trades.json"
```

The service responds with a list of transformed **PlatformTrade** JSON objects in the following shape:

```json
[
  {
    "platform_id": "ACCT123",
    "trade": {
      "account": "XXXXX1234",
      "security": "ABC123",
      "type": "B",
      "amount": 100000,
      "timestamp": "2025-08-04T21:15:33Z"
    }
  }
]
```

---

## 6. Kafka Flow and Retry Logic

The service supports both **REST file uploads** and **Kafka-based ingestion**:

- REST `/api/trades/upload`:
    - Parses CSV/JSON file.
    - Converts each row to a canonical `TradeInstruction`.
    - Transforms to `PlatformTrade` JSON.
    - Publishes each result to the outbound Kafka topic.

- Kafka Listener (`instructions.inbound`):
    - Listens for raw trade JSON messages.
    - Converts to canonical `TradeInstruction` (also stored in an in‑memory `ConcurrentHashMap`).
    - Transforms to `PlatformTrade`.
    - Publishes to the outbound Kafka topic with **simple retry logic**:
        - Up to 3 attempts.
        - Linear backoff between attempts.
        - Logs failures while avoiding message loss where possible.

Canonical records are kept in memory for **auditing** or potential **retry** if downstream delivery fails. This design keeps the core logic simple but demonstrates how the service could be extended with explicit retry endpoints or scheduled replays.

---

## 7. Swagger UI

Interactive API documentation is available at:

```text
http://localhost:8080/swagger-ui.html
```

You can use Swagger UI to upload a CSV/JSON file directly from the browser and inspect the exact JSON response structure.

---

## 8. Docker Support

### 8.1 Build the Docker image

From the project root:

```bash
mvn clean package -DskipTests
docker build -t trades-capture-service:latest .
```

### 8.2 Run the application in Docker

```bash
docker run --rm \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=dev \
  trades-capture-service:latest
```

> Make sure Kafka (from `kafka/local/docker-compose.yml`) is also running so the service can connect to `localhost:9092`.

---

## 9. Postman Collection

A minimal Postman collection for the upload endpoint looks like:

```json
{
  "info": {
    "name": "Trades Capture Service",
    "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
  },
  "item": [
    {
      "name": "Upload Trades CSV",
      "request": {
        "method": "POST",
        "header": [],
        "body": {
          "mode": "formdata",
          "formdata": [
            {
              "key": "file",
              "type": "file",
              "src": "sample-trades.csv"
            }
          ]
        },
        "url": {
          "raw": "http://localhost:8080/api/trades/upload",
          "protocol": "http",
          "host": [
            "localhost"
          ],
          "port": "8080",
          "path": [
            "api",
            "trades",
            "upload"
          ]
        }
      },
      "response": []
    }
  ]
}
```

To use it:

1. Save the JSON above to a file, for example:
   ```text
   TradesCaptureService.postman_collection.json
   ```
2. Open **Postman → Import → File** and select the file.
3. Choose `sample-trades.csv` as the file when sending the request.

---

## 10. Security and Performance Notes

The service is designed with security and performance best practices in mind:

- **Sensitive Data Masking**
    - Raw `account_number` is never exposed directly; only a masked version (e.g., `XXXXX1234`) is returned or published.

- **Input Validation**
    - `security_id` is upper‑cased and validated against a strict `[A-Z0-9]+` pattern.
    - `trade_type` is normalized to `B`/`S` and rejects unknown values.

- **Canonical In-Memory Store**
    - Canonical `TradeInstruction` objects are kept in a thread‑safe `ConcurrentHashMap` for fast lookup, auditing, and potential retry.


- **Kafka Retry Logic**
    - Outbound Kafka publishing from the listener uses bounded retries with backoff to avoid message loss while preventing excessive load.

---

## 11. Sample Input Files and Expected Output

Sample CSV/JSON input files and expected accounting output are provided in the **parent folder** of this project:

- `../sample-trades.csv`
- `../sample-trades.json`
- `../expected-accounting-output.json`

You can use these files to quickly test the `/api/trades/upload` endpoint and verify the transformed `platform_id` + `trade` JSON structure.
