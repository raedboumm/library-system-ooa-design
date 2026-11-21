# 2. System Architecture Design

[← Requirements](01-requirements-analysis.md) · [README](../README.md) · Next: [Class Diagram →](03-class-diagram.md)

## 2.1 Architectural style: layered (n-tier)

The system uses a **layered architecture**. Each layer has one responsibility and only talks to the layer directly below it:

| Layer | Responsibility | Main components |
|---|---|---|
| **1. Presentation (UI)** | Show screens, collect input, show results. **No business rules.** | Member Web Portal, Staff Desk App, REST API controllers |
| **2. Business logic (service)** | Apply library rules: eligibility, due dates, fines, reservation queue | `CatalogService`, `CirculationService`, `MemberService`, `ReservationService`, `FineService`, `ReportService`, `AuthService`, `PolicyService` |
| **3. Domain model** | The objects of the problem: what they are and what they can do | `Book`, `BookCopy`, `Member`, `Loan`, `Reservation`, `Fine`, `LoanPolicy` |
| **4. Data access** | Save and load domain objects, hiding SQL from the layers above | `BookRepository`, `MemberRepository`, `LoanRepository`, `ReservationRepository`, `FineRepository`, `StaffRepository` |
| **5. Data storage** | Persistent storage | Relational database (see the [data model](07-data-model.md)) |

**Cross-cutting concerns** used by every layer: authentication and role-based access, audit logging, and configuration.
**External systems:** the Notification Gateway (email/SMS) and the Scheduler (a daily job).

### Why layered?

- **Separation of concerns:** the UI can change (web → mobile) without touching the business rules.
- **Testability:** services can be tested with in-memory repositories instead of a real database.
- **Maintainability:** each rule lives in exactly one place. For example, the fine calculation is only in `FineService` and `Loan`.
- **Replaceability:** the database or the notification provider can be swapped behind an interface.

## 2.2 High-level architecture diagram

```mermaid
flowchart TB
    subgraph Actors
        M([Member])
        L([Librarian])
        A([Administrator])
    end

    subgraph PL["1 - Presentation Layer"]
        WP["Member Web Portal"]
        SD["Staff Desk App"]
        API["REST API Controllers"]
    end

    subgraph BL["2 - Business Logic Layer"]
        AUTH["AuthService"]
        CAT["CatalogService"]
        CIRC["CirculationService"]
        RES["ReservationService"]
        MEM["MemberService"]
        FINE["FineService"]
        POL["PolicyService"]
        REP["ReportService"]
    end

    subgraph DM["3 - Domain Model"]
        DOM["Book · BookCopy · Member · Loan · Reservation · Fine · LoanPolicy"]
    end

    subgraph DAL["4 - Data Access Layer"]
        BR["BookRepository"]
        MR["MemberRepository"]
        LR["LoanRepository"]
        RR["ReservationRepository"]
        FR["FineRepository"]
    end

    DB[("5 - Relational Database")]

    subgraph EXT["External Systems"]
        NOTIF["Notification Gateway<br/>email / SMS"]
        SCHED["Scheduler<br/>daily job"]
    end

    M --> WP
    L --> SD
    A --> SD
    WP --> API
    SD --> API

    API --> AUTH
    API --> CAT
    API --> CIRC
    API --> RES
    API --> MEM
    API --> FINE
    API --> POL
    API --> REP

    CIRC --> FINE
    CIRC --> RES
    CIRC --> POL
    RES --> NOTIF
    CIRC --> NOTIF
    SCHED --> CIRC
    SCHED --> RES

    CAT --> DOM
    CIRC --> DOM
    RES --> DOM
    MEM --> DOM
    FINE --> DOM

    CAT --> BR
    CIRC --> LR
    CIRC --> BR
    CIRC --> MR
    RES --> RR
    MEM --> MR
    FINE --> FR
    REP --> LR
    REP --> FR

    BR --> DB
    MR --> DB
    LR --> DB
    RR --> DB
    FR --> DB
```

## 2.3 Component responsibilities

| Component | Responsibility | Used by use cases |
|---|---|---|
| `AuthService` | Log in and out, password hashing, role checks | UC-16 |
| `CatalogService` | Search titles, view details, add/edit/withdraw books and copies | UC-01, UC-02, UC-12 |
| `CirculationService` | **Issue, return and renew** loans; enforce limits; calculate due dates | UC-03, UC-04, UC-05, UC-17 |
| `ReservationService` | Reservation queue per title; hold copies; expire holds | UC-06, UC-07, UC-18 |
| `MemberService` | Register, update, block and unblock members; account overview | UC-08, UC-10, UC-11 |
| `FineService` | Create fines, record payments, check the unpaid total | UC-04, UC-09 |
| `PolicyService` | Read and update `LoanPolicy` per member type | UC-14 |
| `ReportService` | Read-only statistics | UC-15 |
| Repositories | CRUD for one aggregate each; the only code that talks SQL | all |
| Notification Gateway | Sends messages; called through a `Notifier` interface | UC-04, UC-17 |
| Scheduler | Calls `CirculationService.flagOverdueLoans()` and `ReservationService.expireHolds()` every night | UC-17, UC-18 |

## 2.4 How a request flows through the layers (Issue Book)

```mermaid
flowchart LR
    U["Librarian scans<br/>member + copy"] --> C["IssueController<br/>(Presentation)"]
    C --> S["CirculationService.issueBook()<br/>(Business logic)"]
    S --> D["Member.canBorrow()<br/>BookCopy.issue()<br/>new Loan()<br/>(Domain)"]
    S --> R["MemberRepository<br/>BookCopyRepository<br/>LoanRepository<br/>(Data access)"]
    R --> DB[("Database")]
    S --> C
    C --> V["Receipt with due date"]
```

The detailed message-by-message version is in the [sequence diagrams](05-sequence-diagrams.md#51-issue-book-uc-03).

## 2.5 Deployment view

```mermaid
flowchart LR
    subgraph Client["Client devices"]
        B1["Browser<br/>(members)"]
        B2["Desk PC + barcode scanner<br/>(staff)"]
    end
    subgraph Server["Application server"]
        APP["Library application<br/>(Presentation + Business + Data access)"]
        JOB["Scheduler job"]
    end
    subgraph Data["Database server"]
        SQL[("PostgreSQL / MySQL")]
    end
    MAIL["Email / SMS provider"]

    B1 -- HTTPS --> APP
    B2 -- HTTPS --> APP
    APP -- SQL/TCP --> SQL
    JOB --> APP
    APP -- SMTP / API --> MAIL
```

## 2.6 Design principles applied

| Principle | How |
|---|---|
| **Single Responsibility** | One service per business area; one repository per aggregate |
| **Encapsulation** | State changes go through methods (`BookCopy.issue(member)`), never by setting fields directly |
| **Dependency Inversion** | Services depend on repository **interfaces**, so the database can be swapped |
| **Low coupling / high cohesion** | The UI never touches repositories, and repositories contain no rules |
| **Open/Closed** | New member types or notification channels are added without editing existing services |
