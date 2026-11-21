package com.library.repository;

import com.library.domain.Book;
import com.library.domain.BookCopy;
import com.library.domain.Loan;
import com.library.domain.Member;
import com.library.domain.Reservation;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * DATA ACCESS LAYER (interface).
 *
 * Services depend on this interface, not on a database (Dependency
 * Inversion). In production it would be implemented with SQL against
 * sql/schema.sql; for the demo, InMemoryLibraryRepository is used.
 *
 * A real system would split this into one repository per aggregate
 * (MemberRepository, LoanRepository, ...) as shown in the class diagram
 * (docs/03 section 3.5). It is one interface here only to keep the example short.
 */
public interface LibraryRepository {

    void saveBook(Book book);
    Optional<Book> findBookByIsbn(String isbn);
    List<Book> searchBooks(String keyword);
    Optional<BookCopy> findCopyByBarcode(String barcode);

    void saveMember(Member member);
    Optional<Member> findMemberById(String memberId);

    void saveLoan(Loan loan);
    Optional<Loan> findOpenLoanByBarcode(String barcode);
    List<Loan> findOverdueLoans(LocalDate today);

    void saveReservation(Reservation reservation);
    List<Reservation> findWaitingReservations(String isbn);   // oldest first
    Optional<Reservation> findReadyReservation(Member member, BookCopy heldCopy);
}
