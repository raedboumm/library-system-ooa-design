package com.library.service;

/** A business rule was broken (shown to the user as a refusal reason). */
public class LibraryException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public LibraryException(String message) {
        super(message);
    }
}
