# Secure Banking Application

## Description
A console-based banking application written in Java that demonstrates Object-Oriented Programming
(entity / repository / service layers) together with secure coding practices such as salted password
hashing, strict input validation and role-based access control. All data is persisted to text files.

## Student Details
- **Name:** [Faatimah Leher]
- **Registration Number:** [H250430P]

## Features
- **User authentication** - login with salted PBKDF2-HMAC-SHA256 password hashes (never stored in plain text)
- **Brute-force protection** - account locked for 60 seconds after 3 failed logins; generic error messages
- **Account management** - admin creates customer accounts (optional opening balance); view account balance
- **Transactions** - deposit, withdraw and fund transfer between accounts
- **Transaction history** - every deposit, withdrawal and transfer is recorded with date/time and before/after balances
- **Data persistence** - users, accounts, transaction history and checkbook requests are saved to text files
  in `data/` and loaded on start-up (atomic writes protect against half-written files)
- **Role-based access** - customers can only touch their own account; admin-only operations are enforced in the service layer
- **Input validation** - whitelist validation of usernames, passwords (strength policy), phone numbers and amounts
  (positive, max 2 decimal places, upper limit); non-numeric menu input cannot crash the app
- **Money handled with `BigDecimal`** - no floating-point rounding errors
- **Checkbook requests** - customers request, admin approves
- **Change password**

## Project Structure
```
src/
  entity/      User, Transaction, Role           (data model)
  repository/  UserRepository                    (file persistence)
  service/     UserService                       (business rules & security checks)
  util/        PasswordUtil, Validator, BankException
  main/        Main                              (console UI)
data/          users.txt, transactions.txt, checkbook.txt
```

## How to Run
Requires JDK 11 or later. Run from the project root (the folder containing `src/` and `data/`):
```bash
mkdir out
javac -d out $(find src -name "*.java")      # Windows PowerShell: javac -d out (Get-ChildItem -Recurse src -Filter *.java).FullName
java -cp out main.Main
```

## Default Accounts (sample data)
| Username | Password     | Role     |
|----------|--------------|----------|
| admin    | `Admin@1234` | Admin    |
| alice    | `Alice@1234` | Customer |
| bob      | `Bob@12345`  | Customer |

Change these passwords after first login (menu option *Change my password*).

## Data File Formats
- `users.txt` - `username|salt|passwordHash|contact|role|balance`
- `transactions.txt` - `timestamp|account|amount|type|balanceBefore|balanceAfter|performedBy`
- `checkbook.txt` - `username|PENDING` or `username|APPROVED`
