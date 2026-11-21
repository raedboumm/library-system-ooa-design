# 1. Requirement Analysis

[← Back to README](../README.md) · Next: [System Architecture →](02-system-architecture.md)

## 1.1 Problem statement

A library needs software to manage its **catalogue of books**, its **members**, the **lending of physical copies** (issue, return, renew, reserve), **fines for late returns**, and **administrative tasks** such as managing staff, setting lending policies and producing reports. Today this is done on paper, which leads to lost records, double-issued copies and fines that are never collected.

## 1.2 Actors

An **actor** is anyone or anything outside the system that interacts with it.

| Actor | Type | Description | Goals |
|---|---|---|---|
| **Member** | Primary, human | A registered borrower. Specialised into **Student** and **Faculty**, who have different borrowing limits. | Find books, borrow and return them, reserve copies that are out, see their loans and fines |
| **Librarian** | Primary, human | Front-desk staff who run day-to-day circulation | Issue and return copies, register members, maintain the catalogue, collect fines |
| **Administrator** | Primary, human | Senior staff who configure and oversee the system. Can also do everything a Librarian can. | Manage staff accounts, set loan policies (loan period, limits, fine rates), view reports |
| **Scheduler (System Clock)** | Secondary, system | A timed job that runs once a day | Detect overdue loans and expired reservations |
| **Notification Gateway** | Secondary, external system | Email/SMS provider | Deliver due-date reminders, overdue notices and "reservation ready" messages |

> **Actor generalisation:** `Student` and `Faculty` are kinds of `Member`, and `Administrator` inherits every use case of `Librarian`.

## 1.3 Key use cases

| ID | Use case | Primary actor | Short description |
|---|---|---|---|
| UC-01 | **Search Book** | Member, Librarian | Search the catalogue by title, author, ISBN or category and see how many copies are available |
| UC-02 | View Book Details | Member, Librarian | See full details of a title and the status of each copy |
| UC-03 | **Issue Book** | Librarian | Lend an available copy to an eligible member and record the due date |
| UC-04 | **Return Book** | Librarian | Record the return of a copy, calculate any fine, and pass the copy to the next reservation if there is one |
| UC-05 | Renew Loan | Member, Librarian | Extend the due date if renewal is allowed |
| UC-06 | **Reserve Book** | Member | Place a hold on a title whose copies are all issued |
| UC-07 | Cancel Reservation | Member | Withdraw a hold |
| UC-08 | View My Account | Member | See current loans, due dates, reservations and unpaid fines |
| UC-09 | Pay Fine | Member, Librarian | Settle an outstanding fine |
| UC-10 | Register Member | Librarian | Create a member account |
| UC-11 | Manage Members | Librarian | Update, block, unblock or deactivate a member |
| UC-12 | Manage Catalogue | Librarian | Add, edit or withdraw books and copies, and report a copy lost or damaged |
| UC-13 | Manage Staff | Administrator | Create, edit or disable librarian accounts |
| UC-14 | Configure Loan Policy | Administrator | Set loan period, max loans, max renewals and fine rate per member type |
| UC-15 | Generate Reports | Administrator | Most borrowed books, overdue list, fines collected, member activity |
| UC-16 | Log In / Log Out | All human actors | Authenticate and get role-based access |
| UC-17 | Send Overdue Notices | Scheduler | Find overdue loans daily and notify members |
| UC-18 | Expire Reservations | Scheduler | Cancel "ready" holds that weren't collected within 3 days |

Relationships between use cases (shown in the [use case diagram](04-use-case-diagrams.md)):

- **Issue Book** `«include»` **Verify Member Eligibility**, because issuing always checks the member.
- **Return Book** `«extend»` **Calculate Fine**, which only happens when the return is late.
- **Return Book** `«extend»` **Notify Next Reservation**, which only happens if someone is waiting.
- **Reserve Book** `«include»` **Search Book**.

## 1.4 Detailed use case descriptions

### UC-03 Issue Book

| Field | Value |
|---|---|
| **Primary actor** | Librarian |
| **Preconditions** | Librarian is logged in; the member and the copy exist |
| **Postconditions (success)** | A `Loan` is created; the copy's status is `ISSUED`; due date = today + loan period for the member's type |
| **Main flow** | 1. Librarian scans the member card. 2. System shows the member and checks eligibility (active, below loan limit, no unpaid fines over the limit). 3. Librarian scans the copy barcode. 4. System checks the copy is `AVAILABLE`, or `RESERVED` for this same member. 5. System creates the loan and calculates the due date. 6. System updates the copy to `ISSUED`. 7. System shows and prints a receipt with the due date. |
| **Alternative flows** | 2a. Member blocked, at the limit, or owing fines: system refuses and shows the reason. 4a. Copy is reserved for another member: refuse. 4b. Copy is lost or withdrawn: refuse and flag it for staff. |

### UC-04 Return Book

| Field | Value |
|---|---|
| **Primary actor** | Librarian |
| **Preconditions** | The copy is `ISSUED` (or `OVERDUE`) on an open loan |
| **Postconditions** | Loan closed with a return date; fine created if late; copy goes to `AVAILABLE`, or `RESERVED` for the next person in the queue, or `DAMAGED` |
| **Main flow** | 1. Librarian scans the copy. 2. System finds the open loan. 3. System records the return date. 4. If late, system calculates the fine (days late × rate) and adds it to the member's account. 5. Librarian inspects the copy's condition. 6. If a reservation is waiting, the copy is held for that member and they're notified; otherwise it goes back on the shelf as `AVAILABLE`. |
| **Alternative flows** | 2a. No open loan: show an error. 5a. Copy damaged: status becomes `DAMAGED` and a damage fine may be added. |

### UC-01 Search Book

| Field | Value |
|---|---|
| **Primary actor** | Member or Librarian |
| **Main flow** | 1. Actor enters a keyword and optional filters (author, category, year). 2. System searches the catalogue. 3. System lists matching titles with the number of available copies. 4. Actor opens a title to see copy details (UC-02). |
| **Alternative flows** | 3a. No results: show "no matches" and suggest similar titles. |

### UC-06 Reserve Book

| Field | Value |
|---|---|
| **Primary actor** | Member |
| **Preconditions** | Member is active; no copy of the title is available |
| **Postconditions** | A `Reservation` in state `WAITING` is added to the end of the title's queue |
| **Main flow** | 1. Member finds the title (UC-01). 2. Selects "Reserve". 3. System checks there's no available copy, the member doesn't already hold or reserve this title, and is under the reservation limit. 4. System creates the reservation and shows the queue position. |
| **Alternative flows** | 3a. A copy is available: system tells the member to borrow it instead. |

## 1.5 Functional requirements

| ID | Requirement | Use case |
|---|---|---|
| FR-1 | The system shall let users search books by title, author, ISBN and category. | UC-01 |
| FR-2 | The system shall track each physical copy separately, by barcode. | UC-02, UC-12 |
| FR-3 | The system shall issue a copy only to an active member under their loan limit. | UC-03 |
| FR-4 | The system shall calculate the due date from the member type's loan period. | UC-03 |
| FR-5 | The system shall calculate a fine of *days late × daily rate* on late returns. | UC-04 |
| FR-6 | The system shall keep a first-come-first-served reservation queue per title. | UC-06 |
| FR-7 | The system shall notify the first member in the queue when a reserved title is returned. | UC-04 |
| FR-8 | The system shall send overdue notices daily. | UC-17 |
| FR-9 | The system shall let administrators change loan policies without code changes. | UC-14 |
| FR-10 | The system shall restrict functions by role (Member, Librarian, Administrator). | UC-16 |
| FR-11 | The system shall produce reports on loans, overdues and fines. | UC-15 |

## 1.6 Non-functional requirements

| Category | Requirement |
|---|---|
| Performance | A catalogue search returns in under 2 seconds with 100,000 titles |
| Security | Passwords are stored hashed; role-based access control; every issue, return and fine is audit-logged |
| Reliability | No copy can be issued twice. Issuing runs in a single database transaction. |
| Usability | Issue and return need at most 2 barcode scans and 1 confirmation |
| Maintainability | Layered architecture; business rules live only in the business-logic layer |
| Availability | 99% during opening hours |
| Portability | Web UI works in current desktop and mobile browsers |

## 1.7 Business rules (default policy)

| Rule | Student | Faculty |
|---|---|---|
| Max copies on loan | 3 | 10 |
| Loan period | 14 days | 30 days |
| Max renewals per loan | 1 | 3 |
| Fine per day late | 10 units | 5 units |
| Borrowing blocked when unpaid fines exceed | 500 units | 500 units |

- A loan can't be renewed if the title has a waiting reservation.
- A copy held for a reservation is kept for **3 days**, then offered to the next member.
- Administrators can change every value in this table (UC-14).
