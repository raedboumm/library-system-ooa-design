package com.library.domain;

import java.time.LocalDate;

/** A hold on a title. Follows the Reservation state diagram (docs/09 section 9.5). */
public class Reservation {

    public enum Status { WAITING, READY, FULFILLED, CANCELLED, EXPIRED }

    public static final int HOLD_DAYS = 3;

    private final Member member;
    private final Book book;
    private final LocalDate reservedOn;
    private Status status = Status.WAITING;
    private BookCopy heldCopy;
    private LocalDate holdUntil;

    public Reservation(Member member, Book book, LocalDate reservedOn) {
        this.member = member;
        this.book = book;
        this.reservedOn = reservedOn;
    }

    public void markReady(BookCopy copy, LocalDate today) {
        require(Status.WAITING);
        heldCopy = copy;
        holdUntil = today.plusDays(HOLD_DAYS);
        status = Status.READY;
    }

    public void fulfil() { require(Status.READY); status = Status.FULFILLED; }
    public void expire() { require(Status.READY); status = Status.EXPIRED; }

    public void cancel() {
        if (status != Status.WAITING && status != Status.READY) throw new IllegalStateException("Cannot cancel a " + status + " reservation");
        status = Status.CANCELLED;
    }

    private void require(Status expected) {
        if (status != expected) throw new IllegalStateException("Reservation is " + status + ", expected " + expected);
    }

    public Member getMember() { return member; }
    public Book getBook() { return book; }
    public LocalDate getReservedOn() { return reservedOn; }
    public Status getStatus() { return status; }
    public BookCopy getHeldCopy() { return heldCopy; }
    public LocalDate getHoldUntil() { return holdUntil; }
}
