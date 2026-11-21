package com.library.ui;

import com.library.domain.Book;
import com.library.domain.BookCopy;
import com.library.domain.Loan;
import com.library.domain.Member;
import com.library.domain.MemberType;
import com.library.domain.Reservation;
import com.library.repository.InMemoryLibraryRepository;
import com.library.service.CirculationService;
import com.library.service.LibraryException;

import java.time.LocalDate;
import java.util.List;

/**
 * PRESENTATION LAYER (console stand-in for the Staff Desk UI).
 * Walks through the modeled use cases so the design can be seen working.
 */
public class LibraryDemo {

    public static void main(String[] args) {
        InMemoryLibraryRepository repo = new InMemoryLibraryRepository();
        CirculationService circulation = new CirculationService(repo,
                (member, msg) -> System.out.println("    [notify " + member.getEmail() + "] " + msg));

        // --- setup (UC-10 Register Member, UC-12 Manage Catalogue) ---
        Member amina = new Member("M001", "Amina", "amina@uni.edu", MemberType.STUDENT);
        Member okafor = new Member("M002", "Dr. Okafor", "okafor@uni.edu", MemberType.FACULTY);
        repo.saveMember(amina);
        repo.saveMember(okafor);

        Book cleanCode = new Book("978-0132350884", "Clean Code", List.of("Robert C. Martin"), "Software");
        cleanCode.addCopy("C-0001", "S1-A");
        Book patterns = new Book("978-0201633610", "Design Patterns", List.of("Gamma", "Helm", "Johnson", "Vlissides"), "Software");
        patterns.addCopy("C-0002", "S1-B");
        repo.saveBook(cleanCode);
        repo.saveBook(patterns);

        LocalDate day = LocalDate.of(2026, 11, 1);

        title("UC-01 Search Book: \"design\"");
        for (Book b : repo.searchBooks("design")) {
            System.out.println("  " + b.getTitle() + " by " + String.join(", ", b.getAuthors())
                    + " - " + b.availableCopies() + "/" + b.getCopies().size() + " available");
        }

        title("UC-03 Issue Book");
        Loan loan = circulation.issueBook("M001", "C-0002", day);
        System.out.println("  " + loan.getLoanId() + ": '" + loan.getCopy().getBook().getTitle() + "' to Amina, due "
                + loan.getDueDate() + "  (copy is now " + loan.getCopy().getStatus() + ")");

        title("UC-03 Issue Book: copy already issued (alternative flow 4a)");
        attempt(() -> circulation.issueBook("M002", "C-0002", day));

        title("UC-06 Reserve Book: Dr. Okafor joins the queue");
        Reservation hold = circulation.reserve("M002", "978-0201633610", day);
        System.out.println("  Reserved. Queue position #" + circulation.queuePosition(hold) + " (" + hold.getStatus() + ")");

        title("UC-17 Scheduler: daily overdue check on " + day.plusDays(16));
        int flagged = circulation.flagOverdueLoans(day.plusDays(16));
        System.out.println("  Loans flagged overdue: " + flagged + "  (copy is now " + loan.getCopy().getStatus() + ")");

        title("UC-04 Return Book: 4 days late, someone is waiting");
        CirculationService.ReturnResult r = circulation.returnBook("C-0002", day.plusDays(18), false);
        System.out.println("  Returned " + r.daysLate + " day(s) late -> fine " + r.fine + " (14-day student loan, 10/day)");
        System.out.println("  Copy " + r.copyOutcome + "  (copy is now " + r.loan.getCopy().getStatus()
                + ", reservation " + hold.getStatus() + ")");
        System.out.println("  Amina's unpaid fines: " + amina.getUnpaidFines());

        title("UC-03 Issue Book: Amina can't take the copy held for Dr. Okafor");
        attempt(() -> circulation.issueBook("M001", "C-0002", day.plusDays(18)));

        title("UC-03 Issue Book: Dr. Okafor collects his reservation");
        Loan second = circulation.issueBook("M002", "C-0002", day.plusDays(19));
        System.out.println("  " + second.getLoanId() + " due " + second.getDueDate() + " (30-day faculty loan), reservation "
                + hold.getStatus());

        title("State machine guard: illegal transition is refused");
        BookCopy copy = cleanCode.getCopies().get(0);
        try {
            copy.markReturned();   // AVAILABLE -> RETURNED is not an arrow in the state diagram
        } catch (IllegalStateException e) {
            System.out.println("  [X] " + e.getMessage());
        }
    }

    private static void title(String text) {
        System.out.println("\n== " + text + " ==");
    }

    private static void attempt(Runnable action) {
        try {
            action.run();
            System.out.println("  (unexpectedly allowed)");
        } catch (LibraryException e) {
            System.out.println("  [X] " + e.getMessage());
        }
    }
}
