# 10. From Abstraction to Implementation

[← Behavioral Model](09-behavioral-model.md) · [README](../README.md)

This section shows how each model becomes code. First as **language-neutral pseudocode**, then as **real Java** from [`src/`](../src/main/java/com/library), which compiles and runs (see [10.5](#105-running-the-example)).

## 10.1 Traceability: model → code

| Model element | Where it's defined | Implemented in |
|---|---|---|
| Layered architecture | [§2](02-system-architecture.md) | packages `ui` → `service` → `domain` / `repository` |
| `Book` *composes* `BookCopy` | [Class diagram §3.3](03-class-diagram.md#33-domain-class-diagram) | `Book.addCopy()` is the only way to create a copy |
| `BookCopy` state diagram | [§6](06-state-diagrams.md) | `BookStatus.allowedNext()` + `BookCopy.moveTo()` |
| `Loan` state diagram | [§9.4](09-behavioral-model.md#94-state-diagram-loan) | `Loan.Status`, `renew()`, `markOverdue()`, `close()` |
| `Reservation` state diagram | [§9.5](09-behavioral-model.md#95-state-diagram-reservation) | `Reservation.markReady() / fulfil() / expire() / cancel()` |
| Verify Member Eligibility (`«include»`) | [§4.2](04-use-case-diagrams.md#42-circulation-use-cases-in-detail-include-and-extend) | `Member.canBorrow(policy)` |
| Calculate Fine (`«extend»`) | [§4.2](04-use-case-diagrams.md#42-circulation-use-cases-in-detail-include-and-extend) | `Loan.calculateFine()`, called in `returnBook()` when late |
| Issue Book sequence | [§5.1](05-sequence-diagrams.md#51-issue-book-uc-03) | `CirculationService.issueBook()` |
| Return Book sequence | [§5.2](05-sequence-diagrams.md#52-return-book-uc-04-with-fine-and-reservation-hand-off) | `CirculationService.returnBook()` |
| Reserve Book sequence | [§5.4](05-sequence-diagrams.md#54-reserve-book-uc-06) | `CirculationService.reserve()` |
| `LoanPolicy` table | [§7](07-data-model.md) | `LoanPolicy.defaultFor(type)` |
| Data access layer | [§2](02-system-architecture.md), [§3.5](03-class-diagram.md#35-service-and-data-access-classes) | `LibraryRepository` interface + `InMemoryLibraryRepository` |
| Database schema | [§7](07-data-model.md) | [`sql/schema.sql`](../sql/schema.sql) |
| Notification Gateway | [§2](02-system-architecture.md) | `Notifier` interface (a lambda in the demo) |

## 10.2 Pseudocode

### Issue Book (UC-03)

```text
FUNCTION issueBook(memberId, barcode, today) RETURNS Loan
    member ← MemberRepository.findById(memberId)
    IF member IS NULL THEN FAIL "Unknown member"

    policy ← PolicyService.policyFor(member.type)
    IF NOT member.canBorrow(policy) THEN                    // «include» Verify Eligibility
        FAIL "Refused: " + member.whyCannotBorrow(policy)

    copy ← BookCopyRepository.findByBarcode(barcode)
    IF copy IS NULL THEN FAIL "Unknown copy"
    IF NOT copy.isAvailableFor(member) THEN                 // «include» Check Availability
        FAIL "Refused: copy is " + copy.status

    BEGIN TRANSACTION
        IF copy.status = RESERVED THEN
            reservation ← find READY reservation of member for copy
            reservation.fulfil()
        copy.issue(member)                                  // AVAILABLE|RESERVED → ISSUED
        loan ← NEW Loan(member, copy, today, today + policy.loanDays)
        LoanRepository.save(loan)
    COMMIT
    RETURN loan
END FUNCTION
```

### Return Book (UC-04)

```text
FUNCTION returnBook(barcode, today, damaged) RETURNS ReturnResult
    loan ← LoanRepository.findOpenByBarcode(barcode)
    IF loan IS NULL THEN FAIL "Copy is not on loan"

    policy ← PolicyService.policyFor(loan.member.type)
    BEGIN TRANSACTION
        loan.close(today)                                   // Loan → RETURNED
        loan.copy.markReturned()                            // ISSUED|OVERDUE → RETURNED

        fine ← MAX(0, today − loan.dueDate) × policy.finePerDay
        IF fine > 0 THEN                                    // «extend» Calculate Fine
            loan.member.addFine(fine)                       // may block the member

        IF damaged THEN
            loan.copy.markDamaged()                         // RETURNED → DAMAGED
        ELSE
            next ← first WAITING reservation for loan.copy.book
            IF next EXISTS THEN                             // «extend» Notify Next Reservation
                loan.copy.reserveFor(next.member)           // RETURNED → RESERVED
                next.markReady(loan.copy, holdUntil = today + 3)
                Notifier.send(next.member, "Your book is ready")
            ELSE
                loan.copy.markAvailable()                   // RETURNED → AVAILABLE
    COMMIT
    RETURN (loan, fine)
END FUNCTION
```

### Daily overdue check (UC-17)

```text
EVERY DAY AT 00:00:
    FOR EACH loan IN LoanRepository.findOpen() WHERE loan.dueDate < today
        IF loan.status = ACTIVE THEN
            loan.status ← OVERDUE
            loan.copy.status ← OVERDUE
            Notifier.send(loan.member, "Your book is overdue")
```

## 10.3 Code: the Book state machine

The state diagram from [§6](06-state-diagrams.md), with each arrow written as an allowed next state:

```java
public enum BookStatus {
    AVAILABLE, RESERVED, ISSUED, OVERDUE, RETURNED, DAMAGED, LOST, WITHDRAWN;

    public Set<BookStatus> allowedNext() {
        switch (this) {
            case AVAILABLE: return EnumSet.of(ISSUED, RESERVED, WITHDRAWN);
            case RESERVED:  return EnumSet.of(ISSUED, AVAILABLE);
            case ISSUED:    return EnumSet.of(ISSUED, OVERDUE, RETURNED, LOST); // ISSUED->ISSUED = renew
            case OVERDUE:   return EnumSet.of(RETURNED, LOST);
            case RETURNED:  return EnumSet.of(AVAILABLE, RESERVED, DAMAGED);
            case DAMAGED:   return EnumSet.of(AVAILABLE, WITHDRAWN);
            case LOST:      return EnumSet.of(AVAILABLE, WITHDRAWN);
            case WITHDRAWN: return EnumSet.noneOf(BookStatus.class);          // final state
            default:        throw new AssertionError(this);
        }
    }
}
```

`BookCopy` keeps `status` **private** and only changes it through event methods, which check the transition:

```java
public class BookCopy {
    private BookStatus status = BookStatus.AVAILABLE;
    private Member reservedFor;

    public void issue(Member borrower) {
        if (!isAvailableFor(borrower))
            throw new IllegalStateException("Copy " + barcode + " cannot be issued: it is " + status);
        reservedFor = null;
        moveTo(BookStatus.ISSUED);
    }

    public void markReturned()  { moveTo(BookStatus.RETURNED); }
    public void markAvailable() { reservedFor = null; moveTo(BookStatus.AVAILABLE); }

    private void moveTo(BookStatus next) {
        if (!status.canMoveTo(next))
            throw new IllegalStateException("Illegal transition for copy " + barcode + ": " + status + " -> " + next);
        status = next;
    }
}
```

## 10.4 Code: rules in the right object (information expert)

**Eligibility** lives in `Member`, because the member owns its loans and fines:

```java
public boolean canBorrow(LoanPolicy policy) {
    return status == Status.ACTIVE
            && activeLoanCount() < policy.getMaxLoans()
            && unpaidFines < policy.getMaxUnpaidFines();
}
```

**Fine calculation** lives in `Loan`, because the loan knows its due date:

```java
public long calculateFine(LocalDate returnDate, LoanPolicy policy) {
    return daysLate(returnDate) * policy.getFinePerDay();   // FR-5
}
```

The **service** only coordinates. Here's the start of `issueBook()`, which follows sequence diagram 5.1 line by line:

```java
public Loan issueBook(String memberId, String barcode, LocalDate today) {
    Member member = repo.findMemberById(memberId)
            .orElseThrow(() -> new LibraryException("Unknown member " + memberId));
    LoanPolicy policy = policyFor(member.getType());

    if (!member.canBorrow(policy))                       // «include» Verify Eligibility
        throw new LibraryException("Refused: " + member.whyCannotBorrow(policy));

    BookCopy copy = repo.findCopyByBarcode(barcode)
            .orElseThrow(() -> new LibraryException("Unknown copy " + barcode));
    if (!copy.isAvailableFor(member))                    // «include» Check Copy Availability
        throw new LibraryException("Refused: copy " + barcode + " is " + copy.getStatus());
    ...
    copy.issue(member);                                  // AVAILABLE/RESERVED -> ISSUED
    Loan loan = new Loan(member, copy, today, policy);
    repo.saveLoan(loan);
    return loan;
}
```

## 10.5 Running the example

You need JDK 17 or newer:

```bash
javac -d out $(find src -name "*.java")          # Windows PowerShell: see README
java -cp out com.library.ui.LibraryDemo
```

Output (generated by running the code):

```
== UC-01 Search Book: "design" ==
  Design Patterns by Gamma, Helm, Johnson, Vlissides - 1/1 available

== UC-03 Issue Book ==
  LN-1: 'Design Patterns' to Amina, due 2026-11-15  (copy is now ISSUED)

== UC-03 Issue Book: copy already issued (alternative flow 4a) ==
  [X] Refused: copy C-0002 is ISSUED

== UC-06 Reserve Book: Dr. Okafor joins the queue ==
  Reserved. Queue position #1 (WAITING)

== UC-17 Scheduler: daily overdue check on 2026-11-17 ==
    [notify amina@uni.edu] OVERDUE: 'Design Patterns' was due on 2026-11-15.
  Loans flagged overdue: 1  (copy is now OVERDUE)

== UC-04 Return Book: 4 days late, someone is waiting ==
    [notify okafor@uni.edu] 'Design Patterns' is ready for you. Please collect it by 2026-11-22.
  Returned 4 day(s) late -> fine 40 (14-day student loan, 10/day)
  Copy held for Dr. Okafor  (copy is now RESERVED, reservation READY)
  Amina's unpaid fines: 40

== UC-03 Issue Book: Amina can't take the copy held for Dr. Okafor ==
  [X] Refused: copy C-0002 is RESERVED

== UC-03 Issue Book: Dr. Okafor collects his reservation ==
  LN-2 due 2026-12-20 (30-day faculty loan), reservation FULFILLED

== State machine guard: illegal transition is refused ==
  [X] Illegal transition for copy C-0001: AVAILABLE -> RETURNED
```

Each line traces back to a model: the copy goes `ISSUED → OVERDUE → RETURNED → RESERVED → ISSUED` exactly as the [state diagram](06-state-diagrams.md) allows, the fine matches the [business rules](01-requirements-analysis.md#17-business-rules-default-policy) (4 days × 10), and the reservation follows `WAITING → READY → FULFILLED` from [§9.5](09-behavioral-model.md#95-state-diagram-reservation).

## 10.6 What a full implementation would add

- A `JdbcLibraryRepository` using [`sql/schema.sql`](../sql/schema.sql), with `issueBook` / `returnBook` each in one transaction
- REST controllers and web screens (presentation layer)
- `AuthService` with hashed passwords and role checks (UC-16)
- A real scheduler (cron or `ScheduledExecutorService`) calling `flagOverdueLoans()` and expiring holds
- Unit tests for every transition in the state tables
