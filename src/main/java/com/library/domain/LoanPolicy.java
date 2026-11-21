package com.library.domain;

/**
 * Lending rules for one member type (table loan_policy).
 * Immutable: the Administrator replaces a policy rather than editing it,
 * so existing loans keep the rules they were issued under.
 */
public final class LoanPolicy {

    private final int maxLoans;
    private final int loanDays;
    private final int maxRenewals;
    private final long finePerDay;
    private final long maxUnpaidFines;

    public LoanPolicy(int maxLoans, int loanDays, int maxRenewals, long finePerDay, long maxUnpaidFines) {
        this.maxLoans = maxLoans;
        this.loanDays = loanDays;
        this.maxRenewals = maxRenewals;
        this.finePerDay = finePerDay;
        this.maxUnpaidFines = maxUnpaidFines;
    }

    /** Default policies from docs/01 section 1.7. */
    public static LoanPolicy defaultFor(MemberType type) {
        return type == MemberType.STUDENT
                ? new LoanPolicy(3, 14, 1, 10, 500)
                : new LoanPolicy(10, 30, 3, 5, 500);
    }

    public int getMaxLoans() { return maxLoans; }
    public int getLoanDays() { return loanDays; }
    public int getMaxRenewals() { return maxRenewals; }
    public long getFinePerDay() { return finePerDay; }
    public long getMaxUnpaidFines() { return maxUnpaidFines; }
}
