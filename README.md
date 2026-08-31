# Sunrise Dental Clinic — Appointment & Patient Management System

A web-based appointment and patient management system for a private dental clinic in Colombo,
replacing a manual paper-and-notebook process that caused double bookings, lost patient records,
long waiting times and billing errors.

Built for **CIS6003 Advanced Programming (WRIT1)**, ICBT Campus / Cardiff Metropolitan University.

---

## Technology stack

| Layer | Technology |
|---|---|
| Language | Java 21 (compiled on JDK 22) |
| Framework | Spring Boot 4.1.1 |
| Build | Maven (wrapper included) |
| Presentation | Spring MVC + Thymeleaf, plus a REST API from M6 |
| Business logic | Spring `@Service` layer |
| Persistence | Spring Data JPA / Hibernate |
| Database | MySQL 8 (authoritative); H2 in MySQL mode for development and tests |
| Schema management | Flyway, with vendor-split migrations |
| Security | Spring Security — BCrypt, role-based access |
| Testing | JUnit 5, Mockito, MockMvc |

---

## Architecture

A three-tier layered architecture. Each package carries a `package-info.java` documenting its
responsibility and the rules about what it may depend on.

```
lk.icbt.dentalclinic
├── controller   Presentation tier  — HTTP concerns only, no business rules
├── service      Business logic tier — domain rules, transaction boundaries
├── repository   Data access tier   — Spring Data JPA, Repository pattern
├── model        Domain entities    — mirrors the Task A class diagram
├── dto          Data Transfer Objects across the API boundary
├── security     Authentication and authorisation
├── exception    Domain exceptions and centralised handlers
└── config       Cross-cutting Spring configuration
```

The dependency rule is one-directional: `controller → service → repository`. Controllers never
touch repositories directly.

---

## Planned roles

| Role | Capabilities |
|---|---|
| `ADMIN` | Full CRUD on appointments, patients, dentists and treatments; billing; all reports |
| `DENTIST` | View own schedule and assigned patients; mark treatments complete |
| `PATIENT` | Self-register; book, reschedule and cancel own appointments; view own history and bills |

The brief specifies staff-only access. The patient self-service portal is a documented,
justified extension — see the Task A design decisions.

---

## Prerequisites

- **JDK 21 or later.** `JAVA_HOME` must point at a real JDK, for example
  `C:\Program Files\Java\jdk-22`.
- No database server is required to run or test the application: it defaults to in-memory H2
  in MySQL compatibility mode. MySQL is needed only for the `mysql` profile.

Verify the toolchain:

```bash
java -version
mvn -v
```

## Running

```bash
# run the application
./mvnw spring-boot:run

# or build a jar and run it
./mvnw clean verify
java -jar target/dental-clinic-0.0.1-SNAPSHOT.jar
```

Then open <http://localhost:8080>.

| Endpoint | Purpose |
|---|---|
| `/` | Public landing page |
| `/login` | Form login |
| `/h2-console` | Development database console |
| `/actuator/health` | Health check |

Until M3 wires the seeded users into Spring Security, authentication still uses the default
generated password printed at startup as `Using generated security password: …`.

---

## Database

Flyway owns the schema; nothing is created by hand and Hibernate runs with
`ddl-auto=validate`, so a mapping that drifts from the migrations fails at startup rather than
silently altering tables.

Migrations are split by vendor. Portable table DDL and seed data live in `common` and run
everywhere. Objects that cannot be written portably live under `{vendor}`:

| Object | Purpose |
|---|---|
| `uq_dentist_slot` | Unique index that structurally prevents double booking |
| `trg_appointment_audit_insert` / `_update` | Write every appointment change to `audit_log` |
| `fn_calculate_bill_total` | One definition of the bill arithmetic, shared by SQL and Java |
| `sp_daily_revenue_report` | Set-based daily revenue aggregation |

The trigger pair and the stored procedure are MySQL-only. H2 has no procedural SQL, so the H2
migration creates only the function and records the gap — a known limitation carried into the
M7 test plan.

### Running against MySQL

```sql
CREATE DATABASE sunrise_dental CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
```

```bash
./mvnw spring-boot:run "-Dspring-boot.run.profiles=mysql"
```

Credentials come from `DB_USERNAME` / `DB_PASSWORD` and are never committed.

### Seeded development accounts

| Username | Password | Role |
|---|---|---|
| `admin` | `Admin@123` | Administrator |
| `dr.perera` | `Dentist@123` | Dentist |
| `dr.silva` | `Dentist@123` | Dentist |
| `n.fernando` | `Patient@123` | Patient |
| `s.jayawardena` | `Patient@123` | Patient |

Demonstration credentials only. They must be removed or rotated before any real deployment.

---

## Testing

```bash
./mvnw test
```

---

## Repository layout

```
.
├── src/main/java        application code
├── src/main/resources   templates, static assets, configuration, Flyway migrations
│   └── db/migration
│       ├── common       portable table DDL and seed data
│       ├── mysql        triggers, function, stored procedure
│       └── h2           H2 counterpart, documenting what it cannot express
├── src/test/java        automated tests
├── pom.xml              Maven build
└── my-docs/             assessment brief, plan and report working files (git-ignored)
```

`my-docs/` is deliberately excluded from version control: it holds copyrighted course material
and report drafts that are submitted through Turnitin rather than published here.

---

## Academic note

This repository is coursework submitted for assessment. It is published publicly because Task D
of the brief requires a public repository demonstrating version control practice.
