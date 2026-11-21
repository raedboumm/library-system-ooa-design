# 4. Use Case Diagrams

[← Class Diagram](03-class-diagram.md) · [README](../README.md) · Next: [Sequence Diagrams →](05-sequence-diagrams.md)

> **Notation.** Mermaid has no native UML use case diagram, so these follow UML conventions with a flowchart:
> **actors** are the figures outside the box, **use cases** are the rounded ovals inside the **system boundary**,
> solid lines are **associations** and dotted arrows are `«include»` / `«extend»` relationships.
> The use case IDs match the [requirements](01-requirements-analysis.md#13-key-use-cases).

## 4.1 Overall system use case diagram

```mermaid
flowchart LR
    Member(["👤 Member"])
    Student(["👤 Student"])
    Faculty(["👤 Faculty"])
    Librarian(["👤 Librarian"])
    Admin(["👤 Administrator"])
    Scheduler(["⏰ Scheduler"])
    Gateway(["✉️ Notification<br/>Gateway"])

    subgraph LMS["Library Management System"]
        direction TB
        UC16(["UC-16 Log In / Log Out"])
        UC01(["UC-01 Search Book"])
        UC02(["UC-02 View Book Details"])
        UC06(["UC-06 Reserve Book"])
        UC07(["UC-07 Cancel Reservation"])
        UC08(["UC-08 View My Account"])
        UC05(["UC-05 Renew Loan"])
        UC09(["UC-09 Pay Fine"])
        UC03(["UC-03 Issue Book"])
        UC04(["UC-04 Return Book"])
        UC10(["UC-10 Register Member"])
        UC11(["UC-11 Manage Members"])
        UC12(["UC-12 Manage Catalogue"])
        UC13(["UC-13 Manage Staff"])
        UC14(["UC-14 Configure Loan Policy"])
        UC15(["UC-15 Generate Reports"])
        UC17(["UC-17 Send Overdue Notices"])
        UC18(["UC-18 Expire Reservations"])
    end

    Student -. is a .-> Member
    Faculty -. is a .-> Member
    Admin -. is a .-> Librarian

    Member --- UC16
    Member --- UC01
    Member --- UC02
    Member --- UC06
    Member --- UC07
    Member --- UC08
    Member --- UC05
    Member --- UC09

    Librarian --- UC16
    Librarian --- UC01
    Librarian --- UC03
    Librarian --- UC04
    Librarian --- UC05
    Librarian --- UC09
    Librarian --- UC10
    Librarian --- UC11
    Librarian --- UC12

    Admin --- UC13
    Admin --- UC14
    Admin --- UC15

    Scheduler --- UC17
    Scheduler --- UC18
    UC17 --- Gateway
    UC04 --- Gateway
```

**Reading it:** a *Student* or *Faculty* is a kind of *Member* (actor generalisation), so they inherit every member use case. The *Administrator* is a kind of *Librarian*, so it gets all librarian use cases plus three of its own. The *Scheduler* and *Notification Gateway* are non-human secondary actors.

## 4.2 Circulation use cases in detail (`«include»` and `«extend»`)

```mermaid
flowchart LR
    Librarian(["👤 Librarian"])
    Member(["👤 Member"])
    Gateway(["✉️ Notification<br/>Gateway"])

    subgraph LMS["Library Management System: Circulation"]
        direction TB
        Issue(["Issue Book"])
        Return(["Return Book"])
        Renew(["Renew Loan"])
        Reserve(["Reserve Book"])
        Search(["Search Book"])
        Verify(["Verify Member<br/>Eligibility"])
        CheckCopy(["Check Copy<br/>Availability"])
        CalcFine(["Calculate Fine"])
        NotifyNext(["Notify Next<br/>Reservation"])
        CheckQueue(["Check Reservation<br/>Queue"])
    end

    Librarian --- Issue
    Librarian --- Return
    Librarian --- Renew
    Member --- Renew
    Member --- Reserve
    Member --- Search

    Issue -. "«include»" .-> Verify
    Issue -. "«include»" .-> CheckCopy
    Renew -. "«include»" .-> Verify
    Renew -. "«include»" .-> CheckQueue
    Reserve -. "«include»" .-> Search
    CalcFine -. "«extend»<br/>[return is late]" .-> Return
    NotifyNext -. "«extend»<br/>[someone is waiting]" .-> Return
    NotifyNext --- Gateway
```

| Relationship | Meaning here |
|---|---|
| `Issue Book «include» Verify Member Eligibility` | **Always** happens: every issue checks the member is active, under the limit and not owing too much |
| `Issue Book «include» Check Copy Availability` | **Always** happens: the copy must be `AVAILABLE`, or `RESERVED` for this member |
| `Calculate Fine «extend» Return Book` | **Only sometimes**, when the return date is after the due date (extension point *after recording return*) |
| `Notify Next Reservation «extend» Return Book` | **Only sometimes**, when the title has a waiting reservation |
| `Reserve Book «include» Search Book` | The member must find the title first |

> Note the arrow direction: `«include»` points **from** the base use case **to** the included one, while `«extend»` points **from** the extension **to** the base use case. That's the standard UML convention.

## 4.3 Administration use cases

```mermaid
flowchart LR
    Admin(["👤 Administrator"])
    Librarian(["👤 Librarian"])

    subgraph LMS["Library Management System: Administration"]
        direction TB
        Staff(["Manage Staff"])
        Policy(["Configure Loan Policy"])
        Reports(["Generate Reports"])
        Members(["Manage Members"])
        Catalogue(["Manage Catalogue"])
        Login(["Log In"])
        AddCopy(["Add Book / Copy"])
        Withdraw(["Withdraw Copy"])
        Lost(["Report Copy Lost / Damaged"])
    end

    Admin -. is a .-> Librarian
    Admin --- Staff
    Admin --- Policy
    Admin --- Reports
    Librarian --- Members
    Librarian --- Catalogue

    Staff -. "«include»" .-> Login
    Policy -. "«include»" .-> Login
    Reports -. "«include»" .-> Login
    AddCopy -. "«extend»" .-> Catalogue
    Withdraw -. "«extend»" .-> Catalogue
    Lost -. "«extend»" .-> Catalogue
```
