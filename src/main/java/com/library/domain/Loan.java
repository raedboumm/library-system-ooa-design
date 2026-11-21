package com.library.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * One borrowing of one copy by one member. Follows the Loan state
 * diagram (docs/09 section 9.4): ACTIVE -> OVERDUE -> RETURNED / LOST.
 */
public final class Loan {

    public enum Status { ACTIVE, OVERDUE, RETURNED, LOST }

    private static int sequence = 1;

    private final String loanId;
    private final Member member;
    private final BookCopy copy;
    private final LocalDate issueDate;
    private LocalDate dueDate;
    private LocalDate returnDate;
    private int renewCount;
    private Status status = Status.ACTIVE;

    /** Created only by CirculationService.issueBook(). */
    public Loan(Member member, BookCopy copy, LocalDate issueDate, LoanPolicy policy) {
        this.loanId = "LN-" + sequence++;
        this.member = member;
        this.copy = copy;
        this.issueDate = issueDate;
        this.dueDate = issueDate.plusDays(policy.getLoanDays());
        member.addLoan(this);
    }

    public boolean isOpen() {
        return status == Status.ACTIVE || status == Status.OVERDUE;
    }

    public boolean isOverdue(LocalDate today) {
        return isOpen() && today.isAfter(dueDate);
    }

    public long daysLate(LocalDate date) {
        return Math.max(0, ChronoUnit.DAYS.between(dueDate, date));
    }

    /** fine = days late x fine per day (FR-5) */
    public long calculateFine(LocalDate returnDate, LoanPolicy policy) {
        return daysLate(returnDate) * policy.getFinePerDay();
    }

    public void renew(LoanPolicy policy) {
        if (status != Status.ACTIVE) throw new IllegalStateException("Only an active, not-overdue loan can be renewed");
        if (renewCount >= policy.getMaxRenewals()) throw new IllegalStateException("No renewals left");
        renewCount++;
        dueDate = dueDate.plusDays(policy.getLoanDays());
    }

    public void markOverdue() {
        if (status != Status.ACTIVE) throw new IllegalStateException("Loan " + loanId + " is " + status);
        status = Status.OVERDUE;
    }

    public void close(LocalDate date) {
        if (!isOpen()) throw new IllegalStateException("Loan " + loanId + " is already " + status);
        returnDate = date;
        status = Status.RETURNED;
    }

    public String getLoanId() { return loanId; }
    public Member getMember() { return member; }
    public BookCopy getCopy() { return copy; }
    public LocalDate getIssueDate() { return issueDate; }
    public LocalDate getDueDate() { return dueDate; }
    public LocalDate getReturnDate() { return returnDate; }
    public int getRenewCount() { return renewCount; }
    public Status getStatus() { return status; }
}
