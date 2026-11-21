-- =====================================================================
-- Library Management System: relational schema (data model)
-- Portable SQL: runs on SQLite and PostgreSQL (MySQL with minor tweaks).
-- Mirrors the ER diagram in docs/07-data-model.md
-- =====================================================================

-- Lending rules per member type (UC-14 Configure Loan Policy)
CREATE TABLE loan_policy (
    member_type      VARCHAR(10)   PRIMARY KEY CHECK (member_type IN ('STUDENT', 'FACULTY')),
    max_loans        INTEGER       NOT NULL CHECK (max_loans > 0),
    loan_days        INTEGER       NOT NULL CHECK (loan_days > 0),
    max_renewals     INTEGER       NOT NULL CHECK (max_renewals >= 0),
    fine_per_day     DECIMAL(8,2)  NOT NULL CHECK (fine_per_day >= 0),
    max_unpaid_fines DECIMAL(10,2) NOT NULL CHECK (max_unpaid_fines >= 0)
);

CREATE TABLE member (
    member_id   VARCHAR(20)  PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    email       VARCHAR(120) NOT NULL UNIQUE,
    phone       VARCHAR(30),
    member_type VARCHAR(10)  NOT NULL REFERENCES loan_policy(member_type),
    status      VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE'
                CHECK (status IN ('ACTIVE', 'BLOCKED', 'EXPIRED')),
    join_date   DATE         NOT NULL
);

-- Librarians and administrators
CREATE TABLE staff (
    staff_id      VARCHAR(20)  PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,
    email         VARCHAR(120) NOT NULL UNIQUE,
    role          VARCHAR(15)  NOT NULL CHECK (role IN ('LIBRARIAN', 'ADMINISTRATOR')),
    password_hash VARCHAR(255) NOT NULL
);

CREATE TABLE book (
    isbn      VARCHAR(17)  PRIMARY KEY,
    title     VARCHAR(200) NOT NULL,
    publisher VARCHAR(100),
    pub_year  INTEGER,
    category  VARCHAR(50)
);

CREATE TABLE author (
    author_id VARCHAR(20)  PRIMARY KEY,
    name      VARCHAR(100) NOT NULL
);

-- Resolves the many-to-many Book <-> Author association
CREATE TABLE book_author (
    isbn      VARCHAR(17) NOT NULL REFERENCES book(isbn) ON DELETE CASCADE,
    author_id VARCHAR(20) NOT NULL REFERENCES author(author_id),
    PRIMARY KEY (isbn, author_id)
);

-- One row per physical copy; status follows the state diagram (docs/06)
CREATE TABLE book_copy (
    barcode        VARCHAR(20) PRIMARY KEY,
    isbn           VARCHAR(17) NOT NULL REFERENCES book(isbn) ON DELETE CASCADE,
    shelf_location VARCHAR(20),
    status         VARCHAR(10) NOT NULL DEFAULT 'AVAILABLE'
                   CHECK (status IN ('AVAILABLE', 'RESERVED', 'ISSUED', 'OVERDUE',
                                     'RETURNED', 'DAMAGED', 'LOST', 'WITHDRAWN'))
);

CREATE TABLE loan (
    loan_id     VARCHAR(20) PRIMARY KEY,
    member_id   VARCHAR(20) NOT NULL REFERENCES member(member_id),
    barcode     VARCHAR(20) NOT NULL REFERENCES book_copy(barcode),
    issued_by   VARCHAR(20) NOT NULL REFERENCES staff(staff_id),
    issue_date  DATE        NOT NULL,
    due_date    DATE        NOT NULL,
    return_date DATE,
    renew_count INTEGER     NOT NULL DEFAULT 0,
    status      VARCHAR(10) NOT NULL DEFAULT 'ACTIVE'
                CHECK (status IN ('ACTIVE', 'OVERDUE', 'RETURNED', 'LOST')),
    CHECK (due_date >= issue_date),
    CHECK (return_date IS NULL OR return_date >= issue_date)
);

-- Business rule "a copy can't be issued twice": at most ONE open loan per copy
CREATE UNIQUE INDEX one_open_loan_per_copy
    ON loan (barcode) WHERE return_date IS NULL;

CREATE TABLE reservation (
    reservation_id VARCHAR(20) PRIMARY KEY,
    member_id      VARCHAR(20) NOT NULL REFERENCES member(member_id),
    isbn           VARCHAR(17) NOT NULL REFERENCES book(isbn),
    reserved_on    DATE        NOT NULL,
    hold_barcode   VARCHAR(20) REFERENCES book_copy(barcode),
    hold_until     DATE,
    status         VARCHAR(10) NOT NULL DEFAULT 'WAITING'
                   CHECK (status IN ('WAITING', 'READY', 'FULFILLED', 'CANCELLED', 'EXPIRED'))
);

CREATE TABLE fine (
    fine_id   VARCHAR(20)   PRIMARY KEY,
    member_id VARCHAR(20)   NOT NULL REFERENCES member(member_id),
    loan_id   VARCHAR(20)   REFERENCES loan(loan_id),
    amount    DECIMAL(10,2) NOT NULL CHECK (amount > 0),
    reason    VARCHAR(20)   NOT NULL CHECK (reason IN ('LATE_RETURN', 'DAMAGE', 'LOST')),
    paid      BOOLEAN       NOT NULL DEFAULT FALSE,
    paid_on   DATE
);

-- Indexes for the most common lookups
CREATE INDEX idx_book_title       ON book (title);
CREATE INDEX idx_copy_isbn        ON book_copy (isbn);
CREATE INDEX idx_loan_member      ON loan (member_id);
CREATE INDEX idx_reservation_isbn ON reservation (isbn, status, reserved_on);
CREATE INDEX idx_fine_member      ON fine (member_id, paid);

-- Default policy (business rules from docs/01 section 1.7)
INSERT INTO loan_policy VALUES ('STUDENT', 3, 14, 1, 10.00, 500.00);
INSERT INTO loan_policy VALUES ('FACULTY', 10, 30, 3, 5.00, 500.00);
