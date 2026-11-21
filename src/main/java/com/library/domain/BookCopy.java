package com.library.domain;

/**
 * One physical copy of a Book.
 *
 * Encapsulation: the status field is private and can only change through
 * the methods below. Each method asks BookStatus whether the transition is
 * allowed, so the object can never reach a state the state diagram forbids.
 */
public class BookCopy {

    private final String barcode;
    private final Book book;
    private final String shelfLocation;
    private BookStatus status = BookStatus.AVAILABLE;
    private Member reservedFor;            // set only while RESERVED

    BookCopy(String barcode, Book book, String shelfLocation) {
        this.barcode = barcode;
        this.book = book;
        this.shelfLocation = shelfLocation;
    }

    // ---- events from the state diagram ----

    /** AVAILABLE -> ISSUED, or RESERVED -> ISSUED (only for the reserving member). */
    public void issue(Member borrower) {
        if (!isAvailableFor(borrower)) {
            throw new IllegalStateException("Copy " + barcode + " cannot be issued: it is " + status);
        }
        reservedFor = null;
        moveTo(BookStatus.ISSUED);
    }

    public void markOverdue()   { moveTo(BookStatus.OVERDUE); }
    public void markReturned()  { moveTo(BookStatus.RETURNED); }
    public void markAvailable() { reservedFor = null; moveTo(BookStatus.AVAILABLE); }
    public void markDamaged()   { moveTo(BookStatus.DAMAGED); }
    public void markLost()      { moveTo(BookStatus.LOST); }
    public void withdraw()      { moveTo(BookStatus.WITHDRAWN); }

    public void reserveFor(Member member) {
        moveTo(BookStatus.RESERVED);
        this.reservedFor = member;
    }

    /** Guard used by "Check Copy Availability" (UC-03). */
    public boolean isAvailableFor(Member member) {
        return status == BookStatus.AVAILABLE
                || (status == BookStatus.RESERVED && reservedFor == member);
    }

    private void moveTo(BookStatus next) {
        if (!status.canMoveTo(next)) {
            throw new IllegalStateException("Illegal transition for copy " + barcode + ": " + status + " -> " + next);
        }
        status = next;
    }

    public String getBarcode() { return barcode; }
    public Book getBook() { return book; }
    public String getShelfLocation() { return shelfLocation; }
    public BookStatus getStatus() { return status; }
}
