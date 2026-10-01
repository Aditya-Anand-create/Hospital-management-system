# Hospital Management System (Java + MySQL + JDBC)

A simple console program that a hospital front desk can use to keep patient records on a computer instead of paper.

It does three main things:
1. **Patient registration** - save a patient once, search them later by name or phone.
2. **Doctor scheduling** - book and cancel appointment slots; see a doctor's day. A doctor can never be double-booked.
3. **Medical records** - save each visit (diagnosis, medicine) and see a patient's full history, newest first.

**Built with:** Java 17, MySQL, JDBC (the Java way to talk to a database), Maven, JUnit 5.

## How it works (in simple words)
```
You type in the menu  ->  Main.java  ->  DAO classes (run SQL with JDBC)  ->  MySQL database
```
- `Main.java` - the menu. It only reads what you type and prints results.
- `PatientDao`, `DoctorDao`, `AppointmentDao`, `MedicalRecordDao` - one class per table. All SQL is here.
- `Validation.java` - checks input first (10-digit phone, valid dates, no empty names...) so bad data never reaches the database.
- `Database.java` - opens connections. The password comes from an environment variable, never from the code.
- `schema.sql` - the 4 tables.

## The database (4 tables)
| Table | What it stores |
|---|---|
| `patients` | name, date of birth, gender, phone (unique), address |
| `doctors` | name, specialization |
| `appointments` | which patient, which doctor, which time |
| `medical_records` | which patient, which doctor, visit date, diagnosis, prescription |

Each fact is stored in one place only (that is what "normalized" means): an appointment stores the patient's **id**, not a copy of the patient's name. Tables are linked with foreign keys.

**Faster queries:** indexes on patient name and on (patient, visit date), so searching and loading history stays quick. Lists use one JOIN query instead of many small ones.

## Errors, security and many users at once
- **Errors:** every problem becomes a clear message ("Phone must be exactly 10 digits") and the program keeps running.
- **SQL injection:** all queries use `PreparedStatement`, so typed text is treated as data, never as SQL. There is a test for it.
- **Two admins at the same time:** the database has a rule `UNIQUE(doctor_id, appointment_time)`. If two people book the same doctor and time together, the database accepts one and rejects the other. A test starts 12 threads at once and checks exactly one wins.

## Run it
You need Java 17, Maven and MySQL.

1. Create the tables: `mysql -u root -p < schema.sql`
2. Set your database details (use your own password):
   ```
   export DB_URL="jdbc:mysql://localhost:3306/hospital_db"
   export DB_USER="root"
   export DB_PASSWORD="your-password"
   ```
3. Start: `mvn compile exec:java`

## Run the tests
```
mvn test
```
9 tests cover: registering and searching, duplicate phone, bad input, SQL injection text, booking, double booking, 12 simultaneous bookings, cancelling, and medical history. The tests use H2 (a small in-memory database in MySQL mode), so they need no MySQL install. The same tests also pass on a real MariaDB server (a MySQL-compatible database) using the same `schema.sql`.

## Limits (honest)
This is a small console project: no login for staff, no screen/GUI, no billing, no pharmacy or ward management. Cancelling an appointment deletes it.
