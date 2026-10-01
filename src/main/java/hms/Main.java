package hms;

import hms.Models.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Scanner;

/** The console menu. It only reads input and prints; all real work is in the DAO classes. */
public class Main {
    public static void main(String[] args) {
        Database db = Database.fromEnvironment();
        try {
            db.createTables();   // safe to run every time (CREATE TABLE IF NOT EXISTS)
        } catch (HmsException e) {
            System.out.println("Cannot connect to the database: " + e.getMessage());
            System.out.println("Check DB_URL, DB_USER and DB_PASSWORD (see README).");
            return;
        }
        PatientDao patients = new PatientDao(db);
        DoctorDao doctors = new DoctorDao(db);
        AppointmentDao appointments = new AppointmentDao(db);
        MedicalRecordDao records = new MedicalRecordDao(db);

        Scanner in = new Scanner(System.in);
        while (true) {
            System.out.println("\n=== Hospital Management System ===");
            System.out.println("1. Register patient");
            System.out.println("2. Search patient");
            System.out.println("3. Add doctor");
            System.out.println("4. List doctors");
            System.out.println("5. Book appointment");
            System.out.println("6. Cancel appointment");
            System.out.println("7. Doctor's schedule for a day");
            System.out.println("8. Add medical record");
            System.out.println("9. View patient's medical history");
            System.out.println("0. Exit");
            String choice = ask(in, "Choose: ");
            if (choice.equals("0")) break;
            try {
                switch (choice) {
                    case "1" -> {
                        int id = patients.register(ask(in, "First name: "), ask(in, "Last name: "),
                                ask(in, "Date of birth (YYYY-MM-DD): "), ask(in, "Gender (Male/Female/Other): "),
                                ask(in, "Phone (10 digits): "), ask(in, "Address (optional): "));
                        System.out.println("Registered. Patient id = " + id);
                    }
                    case "2" -> {
                        List<Patient> found = patients.search(ask(in, "Name starts with, or full phone: "));
                        if (found.isEmpty()) System.out.println("No patient found.");
                        for (Patient p : found) {
                            System.out.printf("#%d  %s %s  | DOB %s | %s | %s%n", p.id(), p.firstName(), p.lastName(),
                                    p.dateOfBirth(), p.gender(), p.phone());
                        }
                    }
                    case "3" -> System.out.println("Added. Doctor id = "
                            + doctors.add(ask(in, "Doctor name: "), ask(in, "Specialization: ")));
                    case "4" -> {
                        List<Doctor> all = doctors.listAll();
                        if (all.isEmpty()) System.out.println("No doctors yet.");
                        for (Doctor d : all) System.out.printf("#%d  %s (%s)%n", d.id(), d.name(), d.specialization());
                    }
                    case "5" -> {
                        int id = appointments.book(askInt(in, "Patient id: "), askInt(in, "Doctor id: "),
                                Validation.appointmentTime(ask(in, "Time (YYYY-MM-DD HH:MM): ")));
                        System.out.println("Booked. Appointment id = " + id);
                    }
                    case "6" -> System.out.println(appointments.cancel(askInt(in, "Appointment id: "))
                            ? "Cancelled." : "No such appointment.");
                    case "7" -> {
                        List<Appointment> list = appointments.scheduleForDoctor(askInt(in, "Doctor id: "),
                                Validation.parseDate(ask(in, "Date (YYYY-MM-DD): ")));
                        if (list.isEmpty()) System.out.println("No appointments that day.");
                        for (Appointment a : list) {
                            System.out.printf("%s  %s (patient #%d)  [appointment #%d]%n",
                                    a.time().toLocalTime(), a.patientName(), a.patientId(), a.id());
                        }
                    }
                    case "8" -> System.out.println("Saved. Record id = " + records.add(
                            askInt(in, "Patient id: "), askInt(in, "Doctor id: "),
                            ask(in, "Visit date (YYYY-MM-DD, today is " + LocalDate.now() + "): "),
                            ask(in, "Diagnosis: "), ask(in, "Prescription (optional): ")));
                    case "9" -> {
                        List<MedicalRecord> list = records.historyOf(askInt(in, "Patient id: "));
                        if (list.isEmpty()) System.out.println("No records for this patient.");
                        for (MedicalRecord r : list) {
                            System.out.printf("%s | %s | %s | Rx: %s%n", r.visitDate(), r.doctorName(),
                                    r.diagnosis(), r.prescription() == null ? "-" : r.prescription());
                        }
                    }
                    default -> System.out.println("Please choose a number from the menu.");
                }
            } catch (HmsException e) {
                // Any problem is shown as a clear message and the program keeps running.
                System.out.println("Error: " + e.getMessage());
            }
        }
        System.out.println("Goodbye.");
    }

    private static String ask(Scanner in, String prompt) {
        System.out.print(prompt);
        return in.hasNextLine() ? in.nextLine() : "0";
    }

    private static int askInt(Scanner in, String prompt) {
        String s = ask(in, prompt).trim();
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new HmsException("Please type a number.");
        }
    }
}
