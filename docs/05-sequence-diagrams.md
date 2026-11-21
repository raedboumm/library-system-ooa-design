# 5. Sequence Diagrams

[← Use Case Diagrams](04-use-case-diagrams.md) · [README](../README.md) · Next: [State Diagrams →](06-state-diagrams.md)

Sequence diagrams show **which objects talk to each other, in what order**, to carry out one use case. Each one follows the [layers](02-system-architecture.md): UI → Service → Domain objects / Repositories → Database.

Notation: solid arrows `->>` are calls, dashed arrows `-->>` are returns, `alt` boxes are if/else, and `opt` boxes are optional steps.

## 5.1 Issue Book (UC-03)

```mermaid
sequenceDiagram
    autonumber
    actor Librarian
    participant UI as Staff Desk UI
    participant CS as CirculationService
    participant MR as MemberRepository
    participant PS as PolicyService
    participant M as :Member
    participant CR as BookCopyRepository
    participant C as :BookCopy
    participant L as :Loan
    participant LR as LoanRepository

    Librarian->>UI: scan member card + copy barcode
    UI->>+CS: issueBook(memberId, barcode)

    CS->>+MR: findById(memberId)
    MR-->>-CS: member
    CS->>+PS: policyFor(member.type)
    PS-->>-CS: policy
    CS->>+M: canBorrow(policy)
    Note right of M: active? loans < maxLoans?<br/>unpaid fines < limit?
    M-->>-CS: true / false

    alt member NOT eligible
        CS-->>UI: error "member cannot borrow: reason"
        UI-->>Librarian: show refusal reason
    else member eligible
        CS->>+CR: findByBarcode(barcode)
        CR-->>-CS: copy
        CS->>+C: isAvailableFor(member)
        C-->>-CS: true / false

        alt copy not available
            CS-->>UI: error "copy is ISSUED / RESERVED / LOST"
            UI-->>Librarian: show refusal reason
        else copy available
            CS->>C: issue(member)
            Note right of C: status AVAILABLE → ISSUED
            CS->>L: «create» Loan(member, copy, today, today + policy.loanDays)
            CS->>+LR: save(loan)
            LR-->>-CS: ok
            CS->>CR: save(copy)
            CS-->>-UI: loan (with dueDate)
            UI-->>Librarian: print receipt "due on dueDate"
        end
    end
```

**Key design points**
- The **eligibility rule** lives in `Member.canBorrow()` (the information expert), not in the UI.
- The **status change** happens inside `BookCopy.issue()`, which refuses illegal transitions (see the [state diagram](06-state-diagrams.md)).
- Steps 17–21 (change the copy's status, create the loan and save both) run in **one database transaction**, so a copy can never be issued twice.

## 5.2 Return Book (UC-04), with fine and reservation hand-off

```mermaid
sequenceDiagram
    autonumber
    actor Librarian
    participant UI as Staff Desk UI
    participant CS as CirculationService
    participant LR as LoanRepository
    participant L as :Loan
    participant PS as PolicyService
    participant FS as FineService
    participant RS as ReservationService
    participant C as :BookCopy
    participant N as Notifier

    Librarian->>UI: scan copy barcode
    UI->>+CS: returnBook(barcode)
    CS->>+LR: findOpenByBarcode(barcode)
    LR-->>-CS: loan

    alt no open loan
        CS-->>UI: error "copy is not on loan"
    else open loan found
        CS->>L: close(today)
        CS->>C: markReturned()
        Note right of C: ISSUED / OVERDUE → RETURNED

        CS->>+PS: policyFor(loan.member.type)
        PS-->>-CS: policy
        CS->>+L: calculateFine(today, policy)
        L-->>-CS: amount

        opt amount > 0 (returned late)
            CS->>+FS: createFine(loan, amount)
            FS-->>-CS: fine
        end

        CS->>+RS: nextWaiting(copy.isbn)
        RS-->>-CS: reservation or none

        alt a reservation is waiting
            CS->>C: reserveFor(reservation.member)
            Note right of C: RETURNED → RESERVED
            CS->>RS: markReady(reservation, copy)
            RS->>N: send(member, "your book is ready, collect within 3 days")
        else nobody waiting
            CS->>C: markAvailable()
            Note right of C: RETURNED → AVAILABLE
        end

        CS->>LR: save(loan)
        CS-->>-UI: loan summary (+ fine if any)
        UI-->>Librarian: "Returned. Fine: amount"
    end
```

## 5.3 Search Book (UC-01)

```mermaid
sequenceDiagram
    autonumber
    actor Member
    participant UI as Member Web Portal
    participant CAT as CatalogService
    participant BR as BookRepository
    participant DB as Database
    participant B as :Book

    Member->>UI: enter "design patterns", filter: category = Software
    UI->>+CAT: search(criteria)
    CAT->>+BR: search(criteria)
    BR->>+DB: SELECT ... WHERE title LIKE ... AND category = ...
    DB-->>-BR: rows
    BR-->>-CAT: List of Book (with copies)

    loop for each book
        CAT->>+B: availableCopies()
        B-->>-CAT: count
    end

    CAT-->>-UI: results [title, authors, available / total]
    alt results found
        UI-->>Member: show result list
    else no results
        UI-->>Member: "No matches. Try another keyword."
    end
```

## 5.4 Reserve Book (UC-06)

```mermaid
sequenceDiagram
    autonumber
    actor Member
    participant UI as Member Web Portal
    participant RS as ReservationService
    participant CAT as CatalogService
    participant B as :Book
    participant RR as ReservationRepository
    participant R as :Reservation

    Member->>UI: click "Reserve" on a title
    UI->>+RS: reserve(memberId, isbn)
    RS->>+CAT: findByIsbn(isbn)
    CAT-->>-RS: book
    RS->>+B: availableCopies()
    B-->>-RS: 0

    alt a copy is available
        RS-->>UI: error "a copy is on the shelf, borrow it instead"
    else none available
        RS->>+RR: existsActive(memberId, isbn)
        RR-->>-RS: false
        RS->>R: «create» Reservation(member, book, today, WAITING)
        RS->>+RR: save(reservation)
        RR-->>-RS: ok
        RS->>+RR: queuePosition(reservation)
        RR-->>-RS: 2
        RS-->>-UI: reservation, position 2
        UI-->>Member: "Reserved. You are #2 in the queue."
    end
```
