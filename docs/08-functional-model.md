# 8. Functional Model: what the system does

[← Data Model](07-data-model.md) · [README](../README.md) · Next: [Behavioral Model →](09-behavioral-model.md)

The functional model describes the **functions (processes)** of the system and the **data that flows** between them, without saying *how* or *when*. It uses **Data Flow Diagrams (DFDs)**:

| Symbol in the diagrams | DFD element |
|---|---|
| Rectangle | **External entity** (outside the system) |
| Circle | **Process** (transforms data) |
| Cylinder | **Data store** |
| Labelled arrow | **Data flow** |

## 8.1 Context diagram (DFD level 0)

The whole system is one process. This shows only what goes in and out.

```mermaid
flowchart LR
    MEM["Member"]
    LIB["Librarian"]
    ADM["Administrator"]
    NG["Notification Gateway"]

    SYS(("0<br/>Library<br/>Management<br/>System"))

    MEM -- "search query, reservation request,<br/>renewal request, fine payment" --> SYS
    SYS -- "search results, account summary,<br/>reservation confirmation" --> MEM

    LIB -- "member + copy barcodes,<br/>new books/copies, member details" --> SYS
    SYS -- "loan receipt, return summary,<br/>fine amount, refusal reasons" --> LIB

    ADM -- "loan policy, staff details,<br/>report request" --> SYS
    SYS -- "reports" --> ADM

    SYS -- "overdue notices,<br/>reservation-ready messages" --> NG
```

## 8.2 DFD level 1 (main processes)

Process 0 broken down into six processes, with the data stores from the [data model](07-data-model.md).

```mermaid
flowchart LR
    MEM["Member"]
    LIB["Librarian"]
    ADM["Administrator"]
    NG["Notification Gateway"]

    P1(("1.0<br/>Manage<br/>Catalogue"))
    P2(("2.0<br/>Manage<br/>Members"))
    P3(("3.0<br/>Circulation<br/>issue / return / renew"))
    P4(("4.0<br/>Manage<br/>Reservations"))
    P5(("5.0<br/>Manage<br/>Fines"))
    P6(("6.0<br/>Reports &<br/>Notifications"))

    D1[("D1 Books & Copies")]
    D2[("D2 Members")]
    D3[("D3 Loans")]
    D4[("D4 Reservations")]
    D5[("D5 Fines")]
    D6[("D6 Loan Policies")]

    LIB -- "book & copy details" --> P1
    MEM -- "search query" --> P1
    P1 -- "search results" --> MEM
    P1 <--> D1

    LIB -- "member details" --> P2
    P2 <--> D2

    LIB -- "member id + barcode" --> P3
    P3 -- "receipt / return summary" --> LIB
    D2 -- "member status, type" --> P3
    D6 -- "loan days, limits" --> P3
    P3 -- "copy status" --> D1
    P3 <--> D3
    P3 -- "late return data" --> P5
    P3 -- "returned copy isbn" --> P4

    MEM -- "reservation request" --> P4
    P4 -- "confirmation, queue position" --> MEM
    P4 <--> D4
    P4 -- "hold copy" --> D1

    MEM -- "payment" --> P5
    P5 <--> D5
    D6 -- "fine rate" --> P5

    ADM -- "policy changes" --> D6
    ADM -- "report request" --> P6
    P6 -- "reports" --> ADM
    D3 -- "loans" --> P6
    D5 -- "fines" --> P6
    P4 -- "ready notice" --> P6
    P6 -- "messages" --> NG
```

## 8.3 DFD level 2: process 3.0 Circulation

```mermaid
flowchart LR
    LIB["Librarian"]
    P31(("3.1<br/>Verify<br/>Member"))
    P32(("3.2<br/>Check Copy<br/>Status"))
    P33(("3.3<br/>Create Loan<br/>& Due Date"))
    P34(("3.4<br/>Record<br/>Return"))
    P35(("3.5<br/>Compute<br/>Fine"))
    P36(("3.6<br/>Renew<br/>Loan"))

    D1[("D1 Books & Copies")]
    D2[("D2 Members")]
    D3[("D3 Loans")]
    D5[("D5 Fines")]
    D6[("D6 Loan Policies")]

    LIB -- "member id" --> P31
    D2 -- "status, open loans" --> P31
    D6 -- "max loans" --> P31
    P31 -- "eligible member" --> P32
    LIB -- "barcode" --> P32
    D1 -- "copy status" --> P32
    P32 -- "available copy" --> P33
    D6 -- "loan days" --> P33
    P33 -- "new loan" --> D3
    P33 -- "status = ISSUED" --> D1
    P33 -- "receipt" --> LIB

    LIB -- "returned barcode" --> P34
    D3 -- "open loan" --> P34
    P34 -- "closed loan" --> D3
    P34 -- "days late" --> P35
    D6 -- "fine per day" --> P35
    P35 -- "fine" --> D5

    LIB -- "loan id" --> P36
    D3 -- "renew count" --> P36
    P36 -- "new due date" --> D3
```

## 8.4 Functional decomposition

```mermaid
flowchart TB
    LMS["Library Management System"]
    LMS --> F1["Catalogue"]
    LMS --> F2["Membership"]
    LMS --> F3["Circulation"]
    LMS --> F4["Reservations"]
    LMS --> F5["Fines"]
    LMS --> F6["Administration"]

    F1 --> F11["Search books"]
    F1 --> F12["View details"]
    F1 --> F13["Add / edit book"]
    F1 --> F14["Add / withdraw copy"]

    F2 --> F21["Register member"]
    F2 --> F22["Update member"]
    F2 --> F23["Block / unblock"]
    F2 --> F24["View account"]

    F3 --> F31["Issue book"]
    F3 --> F32["Return book"]
    F3 --> F33["Renew loan"]
    F3 --> F34["Flag overdue loans"]

    F4 --> F41["Reserve title"]
    F4 --> F42["Cancel reservation"]
    F4 --> F43["Hold copy for next member"]
    F4 --> F44["Expire uncollected holds"]

    F5 --> F51["Calculate late fine"]
    F5 --> F52["Record payment"]
    F5 --> F53["Check unpaid total"]

    F6 --> F61["Manage staff"]
    F6 --> F62["Configure loan policy"]
    F6 --> F63["Generate reports"]
```

## 8.5 Key functions: inputs, processing, outputs

| Function | Input | Processing | Output |
|---|---|---|---|
| Issue book | member id, barcode | verify member → check copy → due date = today + loanDays → create loan → set copy ISSUED | loan receipt / refusal reason |
| Return book | barcode | find open loan → set return date → fine = max(0, days late) × rate → hand copy to next reservation or shelf | return summary, fine |
| Renew loan | loan id | renewals left? no reservation waiting? → due date += loanDays | new due date / refusal |
| Search | keyword, filters | match title/author/ISBN/category → count available copies | result list |
| Reserve | member id, ISBN | no copy available? not already reserved? → append to queue | queue position |
| Flag overdue | today's date | loans where returnDate is null and dueDate < today → status OVERDUE → notify | overdue count, notices |
| Calculate fine | loan, return date, policy | `daysLate = returnDate − dueDate`; `fine = daysLate × finePerDay` | amount |
