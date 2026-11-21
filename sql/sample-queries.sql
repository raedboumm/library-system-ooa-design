-- =====================================================================
-- Sample data and queries showing how the data model answers the use cases.
-- Run after schema.sql.
-- =====================================================================

INSERT INTO staff  VALUES ('L01', 'Grace Librarian', 'grace@lib.org', 'LIBRARIAN', 'x');
INSERT INTO member VALUES ('M001', 'Amina Bello',  'amina@uni.edu', NULL, 'STUDENT', 'ACTIVE', '2026-09-01');
INSERT INTO member VALUES ('M002', 'Dr. Okafor',   'okafor@uni.edu', NULL, 'FACULTY', 'ACTIVE', '2026-09-01');

INSERT INTO book   VALUES ('978-0132350884', 'Clean Code', 'Prentice Hall', 2008, 'Software');
INSERT INTO book   VALUES ('978-0201633610', 'Design Patterns', 'Addison-Wesley', 1994, 'Software');
INSERT INTO author VALUES ('A1', 'Robert C. Martin');
INSERT INTO author VALUES ('A2', 'Erich Gamma');
INSERT INTO author VALUES ('A3', 'Richard Helm');
INSERT INTO book_author VALUES ('978-0132350884', 'A1');
INSERT INTO book_author VALUES ('978-0201633610', 'A2');
INSERT INTO book_author VALUES ('978-0201633610', 'A3');

INSERT INTO book_copy VALUES ('C-0001', '978-0132350884', 'S1-A', 'AVAILABLE');
INSERT INTO book_copy VALUES ('C-0002', '978-0132350884', 'S1-A', 'AVAILABLE');
INSERT INTO book_copy VALUES ('C-0003', '978-0201633610', 'S1-B', 'AVAILABLE');

-- UC-03 Issue Book: one transaction
BEGIN;
UPDATE book_copy SET status = 'ISSUED' WHERE barcode = 'C-0003' AND status = 'AVAILABLE';
INSERT INTO loan (loan_id, member_id, barcode, issued_by, issue_date, due_date)
VALUES ('LN-1', 'M001', 'C-0003', 'L01', '2026-11-01', '2026-11-15');
COMMIT;

-- UC-01 Search Book: titles matching a keyword with available / total copies
SELECT b.isbn, b.title,
       SUM(CASE WHEN c.status = 'AVAILABLE' THEN 1 ELSE 0 END) AS available,
       COUNT(c.barcode) AS total
FROM book b
JOIN book_copy c ON c.isbn = b.isbn
WHERE LOWER(b.title) LIKE '%design%'
GROUP BY b.isbn, b.title;

-- UC-08 View My Account: a member's open loans
SELECT l.loan_id, b.title, l.due_date
FROM loan l
JOIN book_copy c ON c.barcode = l.barcode
JOIN book b      ON b.isbn = c.isbn
WHERE l.member_id = 'M001' AND l.return_date IS NULL;

-- UC-17 Send Overdue Notices: loans past due on a given day
SELECT l.loan_id, m.email, b.title, l.due_date
FROM loan l
JOIN member m    ON m.member_id = l.member_id
JOIN book_copy c ON c.barcode = l.barcode
JOIN book b      ON b.isbn = c.isbn
WHERE l.return_date IS NULL AND l.due_date < '2026-11-20';

-- UC-04 Return Book (late): close loan, charge fine, free copy
BEGIN;
UPDATE loan SET return_date = '2026-11-18', status = 'RETURNED' WHERE loan_id = 'LN-1';
INSERT INTO fine (fine_id, member_id, loan_id, amount, reason)
VALUES ('F-1', 'M001', 'LN-1', 30.00, 'LATE_RETURN');   -- 3 days x 10.00
UPDATE book_copy SET status = 'AVAILABLE' WHERE barcode = 'C-0003';
COMMIT;

-- Eligibility check used by Member.canBorrow(): open loans and unpaid fines vs policy
SELECT m.member_id,
       (SELECT COUNT(*) FROM loan l WHERE l.member_id = m.member_id AND l.return_date IS NULL) AS open_loans,
       p.max_loans,
       (SELECT COALESCE(SUM(f.amount), 0) FROM fine f WHERE f.member_id = m.member_id AND f.paid = FALSE) AS unpaid,
       p.max_unpaid_fines
FROM member m
JOIN loan_policy p ON p.member_type = m.member_type
WHERE m.member_id = 'M001';
