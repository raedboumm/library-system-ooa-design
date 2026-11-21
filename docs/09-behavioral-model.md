# 9. Behavioral Model: how the system behaves over time

[← Functional Model](08-functional-model.md) · [README](../README.md) · Next: [Implementation →](10-implementation.md)

The behavioral model shows **how objects and the system react to events over time**. It uses:

1. **Event-response table**: which event triggers which behaviour
2. **Activity diagrams**: the flow of steps and decisions in a process
3. **State diagrams**: the lifecycle of the objects that change over time (`BookCopy` is in [section 6](06-state-diagrams.md). This section covers `Loan`, `Reservation` and `Member`.)
4. **Timeline**: what happens to one loan day by day
5. **Sequence diagrams**: the interactions in time order are in [section 5](05-sequence-diagrams.md)

## 9.1 Event-response table

| Event | Source | System response | Objects whose state changes |
|---|---|---|---|
| Copy barcode scanned for issue | Librarian | Verify member, check copy, create loan | BookCopy → ISSUED, Loan → ACTIVE |
| Copy barcode scanned for return | Librarian | Close loan, compute fine, route copy | Loan → RETURNED, BookCopy → RETURNED → AVAILABLE/RESERVED, Fine created |
| Renew requested | Member / Librarian | Check renewals and queue, extend due date | Loan (dueDate, renewCount) |
| Reserve requested | Member | Queue the reservation | Reservation → WAITING |
| Midnight (daily tick) | Scheduler | Flag overdue loans, notify; expire old holds | Loan → OVERDUE, BookCopy → OVERDUE, Reservation → EXPIRED |
| Fine paid | Member / Librarian | Mark paid; unblock if under limit | Fine → PAID, Member → ACTIVE |
| Unpaid fines exceed limit | System (after a fine) | Block borrowing | Member → BLOCKED |
| Policy updated | Administrator | New values apply to **new** loans only | LoanPolicy |

## 9.2 Activity diagram: Issue Book

```mermaid
flowchart TD
    start((●)) --> scanM["Scan member card"]
    scanM --> found{"Member found?"}
    found -- no --> errM["Show 'unknown member'"] --> stop((◉))
    found -- yes --> elig{"Active AND loans < max<br/>AND unpaid fines < limit?"}
    elig -- no --> refuse["Show refusal reason"] --> stop
    elig -- yes --> scanC["Scan copy barcode"]
    scanC --> st{"Copy status?"}
    st -- "AVAILABLE" --> create
    st -- "RESERVED" --> same{"Reserved for<br/>this member?"}
    same -- yes --> fulfil["Mark reservation FULFILLED"] --> create
    same -- no --> refuseC["Show 'reserved for someone else'"] --> stop
    st -- "ISSUED / LOST / other" --> refuseS["Show 'copy not available'"] --> stop
    create["Create Loan<br/>dueDate = today + loanDays"] --> setC["Copy → ISSUED"]
    setC --> save["Save loan + copy<br/>in one transaction"]
    save --> receipt["Print receipt with due date"] --> stop
```

## 9.3 Activity diagram: Return Book

```mermaid
flowchart TD
    start((●)) --> scan["Scan copy barcode"]
    scan --> open{"Open loan<br/>for this copy?"}
    open -- no --> err["Show 'copy not on loan'"] --> stop((◉))
    open -- yes --> close["Close loan<br/>returnDate = today"]
    close --> late{"today > dueDate?"}
    late -- yes --> fine["fine = daysLate × finePerDay<br/>create Fine"] --> block{"Unpaid total<br/>> limit?"}
    block -- yes --> blk["Member → BLOCKED"] --> inspect
    block -- no --> inspect
    late -- no --> inspect["Inspect copy"]
    inspect --> cond{"Condition OK?"}
    cond -- no --> dmg["Copy → DAMAGED"] --> stop
    cond -- yes --> q{"Reservation<br/>waiting?"}
    q -- yes --> hold["Copy → RESERVED<br/>holdUntil = today + 3"] --> notify["Notify next member"] --> stop
    q -- no --> shelf["Copy → AVAILABLE"] --> stop
```

## 9.4 State diagram: Loan

```mermaid
stateDiagram-v2
    [*] --> Active : issueBook()
    Active --> Active : renew() [renewCount < maxRenewals and no reservation]
    Active --> Overdue : dailyCheck() [today > dueDate]
    Active --> Returned : returnBook() [on time]
    Overdue --> Returned : returnBook() / create Fine
    Overdue --> Lost : markLost() / replacement fine
    Active --> Lost : markLost() / replacement fine
    Returned --> [*]
    Lost --> [*]
```

## 9.5 State diagram: Reservation

```mermaid
stateDiagram-v2
    [*] --> Waiting : reserve() [no copy available]
    Waiting --> Ready : copy returned and first in queue / notify member
    Waiting --> Cancelled : cancel()
    Ready --> Fulfilled : member collects (issueBook)
    Ready --> Expired : holdExpired() [not collected in 3 days]
    Ready --> Cancelled : cancel()
    Fulfilled --> [*]
    Cancelled --> [*]
    Expired --> [*]
```

## 9.6 State diagram: Member

```mermaid
stateDiagram-v2
    [*] --> Active : registerMember()
    Active --> Blocked : unpaid fines > limit, or block() by librarian
    Blocked --> Active : fines paid below limit, or unblock()
    Active --> Expired : membership end date passed
    Expired --> Active : renewMembership()
    Blocked --> Expired : membership end date passed
    Expired --> [*] : account closed
```

## 9.7 Timeline: one student loan

```mermaid
sequenceDiagram
    autonumber
    participant S as Scheduler (daily)
    participant L as :Loan
    participant C as :BookCopy
    participant M as :Member
    participant N as Notifier

    Note over L,C: Day 0 (1 Nov): issueBook()
    L->>L: status = ACTIVE, dueDate = 15 Nov
    C->>C: status = ISSUED
    Note over S,N: Day 12 (13 Nov): reminder
    S->>N: "Clean Code is due in 2 days"
    N->>M: email reminder
    Note over S,N: Day 15 (16 Nov): due date passed
    S->>L: dailyCheck() → today > dueDate
    L->>L: status = OVERDUE
    S->>C: status = OVERDUE
    S->>N: send overdue notice
    N->>M: "Clean Code is overdue"
    Note over L,M: Day 18 (19 Nov): returnBook()
    L->>L: status = RETURNED, daysLate = 4
    L->>M: Fine(4 × 10 = 40)
    C->>C: status = RETURNED → AVAILABLE
```
