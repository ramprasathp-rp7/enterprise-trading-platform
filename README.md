# Enterprise Trading Platform

> **Team:** `chennai-capstone-SE2-team1`  
> Enterprise Trading Platform capstone project context and repository structure up to Sprint 5.

---

## Table of Contents
1. [Tech Stack & Tooling](#-tech-stack--tooling)
2. [Repository Structure](#-repository-structure)
3. [Sprint Progress & Roadmap](#-sprint-progress--roadmap)
4. [Getting Started & Local Setup](#-getting-started--local-setup)

---

## Tech Stack & Tooling

| Layer | Technologies & Tools |
| :--- | :--- |
| **Domain Engine (Sprint 5)** | Pure Java 21 (Zero frameworks, Jakarta Bean Validation, Maven Enforcer rule) |
| **Analytics ETL (Sprint 4)** | Python 3.11+, DuckDB (OLAP), Pandas, Plotly (`full_html=True`), Pytest |
| **Trade Database (Sprint 3)** | PostgreSQL 15+ (3NF Schema, `VARCHAR` + `CHECK` constraints, DB-level unique constraints) |
| **Testing & Build** | JUnit 5, Surefire, Pytest, Maven Enforcer (`bannedDependencies`) |
| **Market Data API** | Fauxnance REST API (`X-Api-Key`, candles, quotes, usage, health) |
| **Diagrams & Documentation** | Mermaid.js (`classDiagram`, `sequenceDiagram`, `erDiagram` in Markdown) |

---

## Repository Structure

```text
.
├── sprint-03-trade-database/   # PostgreSQL schema (migrations, seed, design, probes, queries, scripts)
├── sprint-04-analytics-etl/    # Installable Python package (extract, transform, load, claims, tests)
├── sprint-05-domain-engine/    # Standalone pure-Java 21 Maven module (entities, enums, services, tests)
├── manifest.env                 # Harness manifest consumed by scripts/check.sh
├── .example.env                 # Shared environment variable template
└── README.md                   # Project documentation
```

---

## Sprint Progress & Roadmap

| Sprint | Module | Core Deliverables | Status |
| :---: | :--- | :--- | :---: |
| **03** | **Trade Database** | 3NF PostgreSQL schema, numbered migrations, `VARCHAR` + `CHECK` constraints, DB-level idempotency (`23505`), index justifications, live constraint violation probes. | **Complete** |
| **04** | **Analytics ETL** | Installable Python package, DuckDB star schema target, Plotly dashboard (3+ business claims), 4-way error handling, quarantine pattern for defective rows, Fauxnance caching. | **Complete** |
| **05** | **Domain Engine** | Framework-free Java 21 module, 4 entities, 4 enums, `PlaceOrderRequest` DTO validation, 7 domain exceptions, 8 order placement rules, `IdempotencyKeyChecker` seam interface, 66 JUnit 5 tests. | **Complete** |

---

## Getting Started & Local Setup

### Prerequisites
* **Java Development Kit (JDK):** 21
* **Python:** 3.11 or higher
* **PostgreSQL:** 15+
* **Maven:** 3.9+

### Environment Configuration
Copy the template environment file and set your local credentials:
```bash
cp .example.env .env
```
> **Note:** Never commit `.env` or expose your `FAUXNANCE_API_KEY`.

### 1. Database Setup (Sprint 3)
```bash
cd sprint-03-trade-database
./apply.sh
```

### 2. Analytics Pipeline Setup (Sprint 4)
```bash
cd sprint-04-analytics-etl
python -m venv .venv
source .venv/bin/activate  # On Windows (PowerShell): .venv\Scripts\Activate.ps1
pip install -e '.[dev]'
pytest
```

### 3. Domain Engine Build & Test (Sprint 5)
```bash
cd sprint-05-domain-engine
mvn clean verify
```