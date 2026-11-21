package com.library.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * A registered borrower. As the "information expert" (it owns its loans
 * and fines), Member decides whether it can borrow: canBorrow() is the
 * "Verify Member Eligibility" use case.
 */
public class Member {

    public enum Status { ACTIVE, BLOCKED, EXPIRED }

    private final String memberId;
    private final String name;
    private final String email;
    private final MemberType type;
    private Status status = Status.ACTIVE;
    private final List<Loan> loans = new ArrayList<>();
    private long unpaidFines;

    public Member(String memberId, String name, String email, MemberType type) {
        this.memberId = memberId;
        this.name = name;
        this.email = email;
        this.type = type;
    }

    /** UC-03 guard: active, below the loan limit and not owing too much. */
    public boolean canBorrow(LoanPolicy policy) {
        return status == Status.ACTIVE
                && activeLoanCount() < policy.getMaxLoans()
                && unpaidFines < policy.getMaxUnpaidFines();
    }

    /** Explains a refusal (shown to the librarian). */
    public String whyCannotBorrow(LoanPolicy policy) {
        if (status != Status.ACTIVE) return name + " is " + status;
        if (activeLoanCount() >= policy.getMaxLoans()) return name + " already has " + policy.getMaxLoans() + " books";
        if (unpaidFines >= policy.getMaxUnpaidFines()) return name + " owes " + unpaidFines + " in fines";
        return "";
    }

    public long activeLoanCount() {
        return loans.stream().filter(Loan::isOpen).count();
    }

    void addLoan(Loan loan) { loans.add(loan); }

    public void addFine(long amount, LoanPolicy policy) {
        unpaidFines += amount;
        if (unpaidFines >= policy.getMaxUnpaidFines()) status = Status.BLOCKED;   // Member state diagram
    }

    public void payFines(long amount, LoanPolicy policy) {
        unpaidFines = Math.max(0, unpaidFines - amount);
        if (status == Status.BLOCKED && unpaidFines < policy.getMaxUnpaidFines()) status = Status.ACTIVE;
    }

    public void block()   { status = Status.BLOCKED; }
    public void unblock() { status = Status.ACTIVE; }

    public String getMemberId() { return memberId; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public MemberType getType() { return type; }
    public Status getStatus() { return status; }
    public long getUnpaidFines() { return unpaidFines; }
    public List<Loan> getLoans() { return List.copyOf(loans); }
}
