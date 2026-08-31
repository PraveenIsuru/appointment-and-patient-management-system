# Sunrise Dental Clinic — Appointment & Patient Management System

A web-based appointment and patient management system for a private dental clinic in Colombo,
replacing a manual paper-and-notebook process that caused double bookings, lost patient records,
long waiting times and billing errors.

Built for **CIS6003 Advanced Programming (WRIT1)**, ICBT Campus / Cardiff Metropolitan University.

---

## Status

| Milestone | Scope | State |
|---|---|---|
| **M0** | Foundation & repo setup | ✅ Complete |
| M1 | UML diagrams & design decisions | ⬜ |
| M2 | Domain model & database | ⬜ |
| M3 | Security, roles & login | ⬜ |
| M4 | Appointments: book, search, validate | ⬜ |
| M5 | Billing & reports | ⬜ |
| M6 | REST API & design patterns | ⬜ |
| M7 | TDD & test automation | ⬜ |
| M8 | CI/CD & deployment | ⬜ |
| M9 | Report assembly | ⬜ |

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
| Database | H2 in-memory for development; MySQL 8 from M2 |
| Schema management | Flyway (enabled in M2) |
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
- No database is required yet — M0 and M1 run on in-memory H2.

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

Until M3 lands, authentication uses Spring Security's default generated password, printed to the
console at startup as `Using generated security password: …`.

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
