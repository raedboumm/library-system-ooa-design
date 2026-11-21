package com.library.repository;

import com.library.domain.Book;
import com.library.domain.BookCopy;
import com.library.domain.Loan;
import com.library.domain.Member;
import com.library.domain.Reservation;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** In-memory implementation of the data access layer, used by the demo and for testing. */
public class InMemoryLibraryRepository implements LibraryRepository {

    private final Map<String, Book> books = new LinkedHashMap<>();
    private final Map<String, Member> members = new LinkedHashMap<>();
    private final List<Loan> loans = new ArrayList<>();
    private final List<Reservation> reservations = new ArrayList<>();

    @Override public void saveBook(Book book) { books.put(book.getIsbn(), book); }
    @Override public Optional<Book> findBookByIsbn(String isbn) { return Optional.ofNullable(books.get(isbn)); }

    @Override
    public List<Book> searchBooks(String keyword) {
        return books.values().stream().filter(b -> b.matches(keyword)).collect(Collectors.toList());
    }

    @Override
    public Optional<BookCopy> findCopyByBarcode(String barcode) {
        return books.values().stream()
                .flatMap(b -> b.getCopies().stream())
                .filter(c -> c.getBarcode().equals(barcode))
                .findFirst();
    }

    @Override public void saveMember(Member member) { members.put(member.getMemberId(), member); }
    @Override public Optional<Member> findMemberById(String id) { return Optional.ofNullable(members.get(id)); }

    @Override public void saveLoan(Loan loan) { if (!loans.contains(loan)) loans.add(loan); }

    @Override
    public Optional<Loan> findOpenLoanByBarcode(String barcode) {
        return loans.stream().filter(l -> l.isOpen() && l.getCopy().getBarcode().equals(barcode)).findFirst();
    }

    @Override
    public List<Loan> findOverdueLoans(LocalDate today) {
        return loans.stream().filter(l -> l.isOverdue(today)).collect(Collectors.toList());
    }

    @Override public void saveReservation(Reservation r) { if (!reservations.contains(r)) reservations.add(r); }

    @Override
    public List<Reservation> findWaitingReservations(String isbn) {
        return reservations.stream()
                .filter(r -> r.getStatus() == Reservation.Status.WAITING && r.getBook().getIsbn().equals(isbn))
                .collect(Collectors.toList());   // insertion order = first come, first served
    }

    @Override
    public Optional<Reservation> findReadyReservation(Member member, BookCopy heldCopy) {
        return reservations.stream()
                .filter(r -> r.getStatus() == Reservation.Status.READY
                        && r.getMember() == member && r.getHeldCopy() == heldCopy)
                .findFirst();
    }
}
