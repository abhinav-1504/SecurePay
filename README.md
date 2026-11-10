# SecurePay — Concurrent Transaction Processing & Risk Engine

A **production-inspired Core Java** transaction processing engine designed to safely handle concurrent fund transfers under high load.

This project focuses on **correctness, concurrency safety, and reliability** rather than UI or simple CRUD. It demonstrates how real payment systems prevent race conditions, double-spending, deadlocks, and handle failures.

---

## Problem Statement

Imagine an account has **₹10,000**.

At the same moment, multiple transfer requests arrive:

```
Thread 1 → Transfer ₹7,000
Thread 2 → Transfer ₹6,000
Thread 3 → Transfer ₹5,000
```

If the system is not properly synchronized, all three threads can read the old balance (₹10,000) and all three transfers can succeed — resulting in **negative balance** and **double spending**.

**SecurePay guarantees:**

- No negative balance
- No double spending
- No deadlocks
- Thread-safe concurrent processing
- Safe retries via idempotency
- Background fraud detection and audit logging

---

## Key Features

| Feature | Implementation |
|---------|----------------|
| Concurrent Transaction Processing | `ExecutorService` + `BlockingQueue` (Producer-Consumer) |
| Race Condition Prevention | Fine-grained `ReentrantLock` per account + balance re-check under lock |
| Deadlock Prevention | Deterministic lock ordering (smaller `accountId` first) |
| Double-Spending Prevention | Atomic debit + credit under ordered locks |
| Secure Authentication | PBKDF2-HMAC-SHA256 + random salt + constant-time comparison |
| Session Management | Token-based sessions with TTL + failed-login lockout |
| Idempotency | Client-supplied key stored in `ConcurrentHashMap` |
| Rate Limiting | Sliding-window rate limiter |
| Asynchronous Fraud Detection | Background workers + pluggable rules |
| Audit Logging | Non-blocking queue → background writers |
| Failure Recovery | File-based Transaction Journal + Recovery Service |
| Transaction State Machine | Strict enum-based state transitions |
| Role-Based Access | `USER` and `ADMIN` roles |

---

## High-Level Architecture

```
                  CLIENT / USER REQUESTS
                           │
                           ▼
                 ┌──────────────────┐
                 │ Security Layer   │
                 │ Authentication   │
                 │ Authorization    │
                 └────────┬─────────┘
                          │
                          ▼
                 ┌──────────────────┐
                 │ Rate Limiter     │
                 │ Idempotency Check│
                 └────────┬─────────┘
                          │
                          ▼
                 ┌──────────────────┐
                 │ Transaction      │
                 │ Processing Queue │
                 └────────┬─────────┘
                          │
                    ┌─────┴─────┐
                    ▼           ▼
                 Worker 1    Worker 2 ...
                    │           │
                    └─────┬─────┘
                          ▼
                ┌────────────────────┐
                │ Transaction Engine │
                │ Lock Management    │
                └─────────┬──────────┘
                          │
              ┌───────────┼───────────┐
              ▼           ▼           ▼
         Wallet Update  Audit Log  Fraud Engine
                                          │
                                          ▼
                                    FLAG / BLOCK
```

---

## Project Structure

```
SecurePay/
├── data/                              # Runtime logs
│   ├── audit.log
│   └── transaction_journal.log
│
└── src/main/java/com/securepay/
    ├── app/
    │   └── SecurePayApplication.java  # Main entry + full demo
    │
    ├── auth/
    │   ├── AuthenticationService.java # Register, Login, Lockout
    │   └── SessionManager.java        # Token-based sessions
    │
    ├── account/
    │   └── AccountService.java        # Wallet lifecycle
    │
    ├── transaction/
    │   ├── TransactionEngine.java     # Core transfer logic
    │   ├── TransactionProcessor.java  # Queue + workers
    │   └── IdempotencyStore.java
    │
    ├── concurrency/
    │   ├── LockManager.java           # Ordered locking
    │   ├── TransactionQueue.java
    │   └── ThreadPoolManager.java
    │
    ├── security/
    │   ├── PasswordHasher.java        # PBKDF2
    │   └── RateLimiter.java           # Sliding window
    │
    ├── fraud/
    │   ├── FraudDetector.java
    │   ├── FraudRule.java
    │   └── FraudContext.java
    │
    ├── audit/
    │   └── AuditLogger.java           # Async logging
    │
    ├── recovery/
    │   ├── TransactionJournal.java
    │   └── RecoveryService.java
    │
    ├── model/
    │   ├── User.java
    │   ├── Account.java
    │   ├── Transaction.java
    │   ├── AuditEvent.java
    │   ├── Role.java
    │   ├── AccountStatus.java
    │   └── TransactionStatus.java
    │
    └── exception/
        └── (Custom exceptions)
```

---

## Core Design Decisions

### 1. Fine-Grained Locking
Each `Account` owns its own fair `ReentrantLock`.  
Independent accounts can transfer in parallel. Only accounts involved in a transfer are locked.

### 2. Deadlock Prevention (Ordered Locking)
When transferring between Account A and Account B, locks are always acquired in a **global consistent order** (sorted by `accountId`).

```
Smaller Account ID  →  locked first
Larger Account ID   →  locked second
```

This eliminates circular wait and therefore deadlocks.

### 3. Balance Re-check Under Lock
Even after acquiring locks, balance is checked again before debiting.  
This prevents race conditions and double-spending.

### 4. Idempotency
Every transfer request carries an `idempotencyKey`.  
- First request → processed and stored  
- Retry with same key → previous result returned (no double transfer)

### 5. Asynchronous Side Effects
Fraud detection and audit logging never block the critical transfer path.  
Events are pushed into bounded `BlockingQueue`s and consumed by background workers.

### 6. Transaction State Machine
```
CREATED → VALIDATED → PROCESSING → SUCCESS
                                 → FAILED
                                 → FLAGGED
                                 → ROLLED_BACK
```
Invalid transitions are rejected.

### 7. Transaction Journal
Before/after every critical step the engine writes to a persistent journal.  
On restart, `RecoveryService` detects incomplete transactions.

---

## Technology & Concepts Used

| Category              | Concepts Used                                      |
|-----------------------|----------------------------------------------------|
| OOP                   | Encapsulation, Abstraction, Polymorphism           |
| Concurrency           | `ReentrantLock`, `ExecutorService`, `BlockingQueue`|
| Threading             | Thread pools, Named threads, Graceful shutdown     |
| Security              | PBKDF2, `SecureRandom`, Constant-time comparison   |
| Collections           | `ConcurrentHashMap`, `LinkedBlockingQueue`         |
| Design Patterns       | Producer-Consumer, State, Strategy (Fraud rules)   |
| Java 8+               | Lambdas, Streams, `CompletableFuture`              |
| Reliability           | Journaling, Recovery, Rate limiting                |

---

## How to Build & Run

### Requirements
- **JDK 17 or higher** (uses switch expressions)
- No external dependencies (pure Core Java)

### Windows (Command Prompt / PowerShell)

```bat
cd SecurePay
mkdir out
mkdir data

dir /s /b src\main\java\*.java > sources.txt
javac -encoding UTF-8 -d out @sources.txt

java -cp out com.securepay.app.SecurePayApplication
```

### Linux / macOS

```bash
cd SecurePay
mkdir -p out data

javac -encoding UTF-8 -d out $(find src/main/java -name "*.java")

java -cp out com.securepay.app.SecurePayApplication
```

---

## Demo Walkthrough (Detailed)

The demo uses names: **Rahul**, **Priya**, **Amit**, and **Admin**.

### 1. User Registration

| User  | Role  | Action          |
|-------|-------|------------------|
| Rahul | USER  | Account created  |
| Priya | USER  | Account created  |
| Amit  | USER  | Account created  |
| Admin | ADMIN | Account created  |

Each user gets a separate wallet (Account) with an initial balance of ₹0.

### 2. Login

Rahul, Priya, and Amit log in successfully.  
Each successful login returns a **session token** with a 30-minute TTL.

### 3. Initial Deposits

| User  | Deposit Amount |
|-------|----------------|
| Rahul | ₹10,000        |
| Priya | ₹5,000         |
| Amit  | ₹3,000         |

Balances after deposit:

```
Rahul → ₹10,000
Priya → ₹5,000
Amit  → ₹3,000
```

### 4. Concurrent Transfers (Race Condition Test)

Rahul has ₹10,000 in his account.  
Three concurrent transfer requests are submitted at the same time:

| Request | From  | To    | Amount |
|---------|-------|-------|--------|
| 1       | Rahul | Priya | ₹7,000 |
| 2       | Rahul | Amit  | ₹6,000 |
| 3       | Rahul | Priya | ₹5,000 |

**Total demand = ₹18,000** (more than available balance)

**Expected Result:**
- Only transfers with sufficient available balance succeed
- Remaining transfers fail with reason `Insufficient balance`
- Rahul’s balance never goes negative
- No double-spending occurs

This demonstrates fine-grained locking and balance re-check under lock.

### 5. Idempotency Test

A transfer of ₹100 from Rahul to Priya is submitted twice with the **same idempotency key** (simulating a network retry).

**Expected Result:**
- First request → processed successfully, money is deducted
- Second request (same key) → returns the **same transaction ID**
- Balance is deducted **only once**

### 6. Failed Login Lockout

A user attempts login with the wrong password repeatedly:

```
Attempt 1 → Invalid username or password
Attempt 2 → Invalid username or password
Attempt 3 → Invalid username or password
Attempt 4 → Invalid username or password
Attempt 5 → Invalid username or password
Attempt 6 → Account is temporarily locked due to too many failed attempts
```

After 5 failed attempts, the account is locked for **15 minutes** (brute-force protection).

### 7. Large Transfer (Fraud Detection)

An additional deposit is made to Rahul’s account, followed by a transfer of **₹60,000**.

**Expected Result:**
- Transfer may succeed if balance is sufficient
- Background Fraud Engine triggers the `LARGE_AMOUNT` rule
- Audit log records a `FRAUD_FLAGGED` event
- In high-severity cases, the account can be auto-frozen

### 8. Final Balances + Logs

At the end of the demo:

- Consistent balances of all accounts are printed
- Full audit trail is saved in `data/audit.log`
- Transaction steps are recorded in `data/transaction_journal.log`

---

## Security Features

- Passwords are never stored in plain text
- PBKDF2-HMAC-SHA256 with 100,000 iterations
- Unique random salt per user
- Constant-time hash comparison (prevents timing attacks)
- Account lockout after 5 failed login attempts (15 minutes)
- Session tokens with 30-minute TTL
- Sliding-window rate limiting

---

## Fraud Rules (Extensible)

| Rule              | Description                              |
|-------------------|------------------------------------------|
| `HIGH_FREQUENCY`  | Too many transactions in 1 minute        |
| `LARGE_AMOUNT`    | Transfer above configured threshold      |
| `BLOCKED_ACCOUNT` | Transaction from frozen/blocked account  |

New rules can be added by implementing the `FraudRule` interface.

---

## Future Improvements

- Persistent storage (database instead of in-memory maps)
- Distributed locking (Redis / ZooKeeper) for multi-instance deployment
- More sophisticated fraud rules (ML-based scoring)
- REST API layer (Spring Boot)
- Metrics and monitoring (Micrometer / Prometheus)
- Compensating transactions for full automatic recovery

---

## Author

Built as a learning project demonstrating deep understanding of concurrent systems and payment safety.

---

**License:** MIT
