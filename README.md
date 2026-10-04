# ERP Migration Router

> 🚧 Work in progress — currently at phase P0 (project skeleton). See [`docs/04-development-plan.md`](docs/04-development-plan.md).

Demo integration platform showing how to migrate warehouses from **SAP ECC** to **SAP S/4HANA** one at a time
(strangler fig pattern): an nginx gateway routes each warehouse to the old or new ERP, mirrors traffic to S/4HANA
in shadow mode, and a reconciliation service measures whether both systems return the same business data before
each cutover.

## Modules

| Module | Type | Role |
|---|---|---|
| `canonical-model` | Library | Canonical order model shared by the adapter and reconciliation |
| `mock-ecc` | Spring Boot app | Fake SAP ECC |
| `mock-s4` | Spring Boot app | Fake SAP S/4HANA (OData v2), with seeded discrepancies |
| `order-adapter` | Spring Boot app | Calls one ERP, maps to canonical, records calls — runs as `adapter-ecc` and `adapter-s4` |
| `auth-validator` | Spring Boot app | Validates Keycloak JWTs for nginx `auth_request` |
| `reconciliation-service` | Spring Boot app | Compares ECC and S/4 answers, exposes warehouse readiness |

## Build

Requires JDK 25 and Maven 3.9+ (or the Maven Wrapper).

```bash
./mvnw clean package          # builds all modules
./mvnw -pl order-adapter -am package   # one module and the modules it depends on
```

## Design documents

- [Architecture](docs/01-architecture.md)
- [Database schema](docs/02-database-schema.md)
- [UML class design](docs/03-uml-class-design.md)
- [Development plan](docs/04-development-plan.md)
