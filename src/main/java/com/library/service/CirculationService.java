package com.library.service;

import com.library.domain.Book;
import com.library.domain.BookCopy;
import com.library.domain.BookStatus;
import com.library.domain.Loan;
import com.library.domain.LoanPolicy;
import com.library.domain.Member;
import com.library.domain.MemberType;
import com.library.domain.Reservation;
import com.library.repository.LibraryRepository;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * BUSINESS LOGIC LAYER: circulation and reservations.
 *
 * Each public method implements one use case and follows the matching
 * sequence diagram in docs/05-sequence-diagrams.md step by step.
 * It coordinates domain objects; the rules themselves live in the
 * domain classes (Member.canBorrow, BookCopy.issue, Loan.calculateFine).
 */
public class CirculationService {

    private final LibraryRepository repo;
    private final Notifier notifier;
    private final Map<MemberType, LoanPolicy> policies = new EnumMap<>(MemberType.class);

    public CirculationService(LibraryRepository repo, Notifier notifier) {
        this.repo = repo;
        this.notifier = notifier;
        for (MemberType t : MemberType.values()) policies.put(t, LoanPolicy.defaultFor(t));
    }

    public LoanPolicy policyFor(MemberType type) { return policies.get(type); }

    /** UC-14: Administrator changes a policy (applies to new loans). */
    public void updatePolicy(MemberType type, LoanPolicy policy) { policies.put(type, policy); }

    // ------------------------------------------------------------------
    // UC-03 Issue Book (sequence diagram 5.1)
    // ------------------------------------------------------------------
    public Loan issueBook(String memberId, String barcode, LocalDate today) {
        Member member = repo.findMemberById(memberId)
                .orElseThrow(() -> new LibraryException("Unknown member " + memberId));
        LoanPolicy policy = policyFor(member.getType());

        if (!member.canBorrow(policy)) {                                  // «include» Verify Eligibility
            throw new LibraryException("Refused: " + member.whyCannotBorrow(policy));
        }

        BookCopy copy = repo.findCopyByBarcode(barcode)
                .orElseThrow(() -> new LibraryException("Unknown copy " + barcode));
        if (!copy.isAvailableFor(member)) {                               // «include» Check Copy Availability
            throw new LibraryException("Refused: copy " + barcode + " is " + copy.getStatus());
        }

        // If the copy was on hold for this member, that reservation is now fulfilled
        if (copy.getStatus() == BookStatus.RESERVED) {
            repo.findReadyReservation(member, copy).ifPresent(Reservation::fulfil);
        }

        copy.issue(member);                                               // AVAILABLE/RESERVED -> ISSUED
        Loan loan = new Loan(member, copy, today, policy);
        repo.saveLoan(loan);                                              // one transaction in a real DB
        return loan;
    }

    // ------------------------------------------------------------------
    // UC-04 Return Book (sequence diagram 5.2)
    // ------------------------------------------------------------------
    public ReturnResult returnBook(String barcode, LocalDate today, boolean damaged) {
        Loan loan = repo.findOpenLoanByBarcode(barcode)
                .orElseThrow(() -> new LibraryException("Copy " + barcode + " is not on loan"));
        Member member = loan.getMember();
        BookCopy copy = loan.getCopy();
        LoanPolicy policy = policyFor(member.getType());

        loan.close(today);
        copy.markReturned();                                              // ISSUED/OVERDUE -> RETURNED

        long fine = loan.calculateFine(today, policy);                    // «extend» Calculate Fine
        if (fine > 0) member.addFine(fine, policy);

        String outcome;
        if (damaged) {
            copy.markDamaged();
            outcome = "copy sent for repair";
        } else {
            List<Reservation> queue = repo.findWaitingReservations(copy.getBook().getIsbn());
            if (!queue.isEmpty()) {                                       // «extend» Notify Next Reservation
                Reservation next = queue.get(0);
                copy.reserveFor(next.getMember());                        // RETURNED -> RESERVED
                next.markReady(copy, today);
                notifier.send(next.getMember(), "'" + copy.getBook().getTitle()
                        + "' is ready for you. Please collect it by " + next.getHoldUntil() + ".");
                outcome = "held for " + next.getMember().getName();
            } else {
                copy.markAvailable();                                     // RETURNED -> AVAILABLE
                outcome = "back on the shelf";
            }
        }
        repo.saveLoan(loan);
        return new ReturnResult(loan, fine, loan.daysLate(today), outcome);
    }

    // ------------------------------------------------------------------
    // UC-06 Reserve Book (sequence diagram 5.4)
    // ------------------------------------------------------------------
    public Reservation reserve(String memberId, String isbn, LocalDate today) {
        Member member = repo.findMemberById(memberId)
                .orElseThrow(() -> new LibraryException("Unknown member " + memberId));
        Book book = repo.findBookByIsbn(isbn)
                .orElseThrow(() -> new LibraryException("Unknown ISBN " + isbn));
        if (book.availableCopies() > 0) {
            throw new LibraryException("A copy of '" + book.getTitle() + "' is on the shelf: borrow it instead");
        }
        Reservation reservation = new Reservation(member, book, today);
        repo.saveReservation(reservation);
        return reservation;
    }

    public int queuePosition(Reservation reservation) {
        return repo.findWaitingReservations(reservation.getBook().getIsbn()).indexOf(reservation) + 1;
    }

    // ------------------------------------------------------------------
    // UC-17 Send Overdue Notices (Scheduler, daily)
    // ------------------------------------------------------------------
    public int flagOverdueLoans(LocalDate today) {
        int count = 0;
        for (Loan loan : repo.findOverdueLoans(today)) {
            if (loan.getStatus() == Loan.Status.ACTIVE) {
                loan.markOverdue();
                loan.getCopy().markOverdue();                             // ISSUED -> OVERDUE
                notifier.send(loan.getMember(), "OVERDUE: '" + loan.getCopy().getBook().getTitle()
                        + "' was due on " + loan.getDueDate() + ".");
                count++;
            }
        }
        return count;
    }

    // ------------------------------------------------------------------

    /** Result object shown to the librarian after a return. */
    public static final class ReturnResult {
        public final Loan loan;
        public final long fine;
        public final long daysLate;
        public final String copyOutcome;

        ReturnResult(Loan loan, long fine, long daysLate, String copyOutcome) {
            this.loan = loan;
            this.fine = fine;
            this.daysLate = daysLate;
            this.copyOutcome = copyOutcome;
        }
    }
}
