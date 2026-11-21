package com.library.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * A title in the catalogue. Composes its physical copies: copies are
 * created through the book (addCopy), mirroring the composition
 * Book *-- BookCopy in the class diagram.
 */
public class Book {

    private final String isbn;
    private final String title;
    private final List<String> authors;
    private final String category;
    private final List<BookCopy> copies = new ArrayList<>();

    public Book(String isbn, String title, List<String> authors, String category) {
        this.isbn = isbn;
        this.title = title;
        this.authors = List.copyOf(authors);
        this.category = category;
    }

    public BookCopy addCopy(String barcode, String shelfLocation) {
        BookCopy copy = new BookCopy(barcode, this, shelfLocation);
        copies.add(copy);
        return copy;
    }

    public long availableCopies() {
        return copies.stream().filter(c -> c.getStatus() == BookStatus.AVAILABLE).count();
    }

    public Optional<BookCopy> findAvailableCopy() {
        return copies.stream().filter(c -> c.getStatus() == BookStatus.AVAILABLE).findFirst();
    }

    /** Used by Search Book (UC-01). */
    public boolean matches(String keyword) {
        String k = keyword.toLowerCase();
        return title.toLowerCase().contains(k) || isbn.equals(keyword)
                || authors.stream().anyMatch(a -> a.toLowerCase().contains(k));
    }

    public String getIsbn() { return isbn; }
    public String getTitle() { return title; }
    public List<String> getAuthors() { return authors; }
    public String getCategory() { return category; }
    public List<BookCopy> getCopies() { return Collections.unmodifiableList(copies); }
}
