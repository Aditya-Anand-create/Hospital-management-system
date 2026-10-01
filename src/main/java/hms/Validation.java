package hms;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Checks input BEFORE it reaches the database. Throws HmsException with a clear message. */
public class Validation {

    public static String name(String value, String field) {
        if (value == null || value.isBlank()) throw new HmsException(field + " cannot be empty.");
        String v = value.trim();
        if (v.length() > 50) throw new HmsException(field + " is too long (max 50 letters).");
        if (!v.matches("[\\p{L} .'-]+")) throw new HmsException(field + " can only have letters, spaces, . ' and -");
        return v;
    }

    public static String phone(String value) {
        if (value == null || !value.trim().matches("\\d{10}"))
            throw new HmsException("Phone must be exactly 10 digits.");
        return value.trim();
    }

    public static String gender(String value) {
        if (value == null) throw new HmsException("Gender must be Male, Female or Other.");
        String v = value.trim().toLowerCase();
        return switch (v) {
            case "male" -> "Male";
            case "female" -> "Female";
            case "other" -> "Other";
            default -> throw new HmsException("Gender must be Male, Female or Other.");
        };
    }

    public static LocalDate birthDate(String value) {
        LocalDate d = parseDate(value);
        if (d.isAfter(LocalDate.now())) throw new HmsException("Date of birth cannot be in the future.");
        return d;
    }

    public static LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value.trim());
        } catch (Exception e) {
            throw new HmsException("Date must look like 2025-08-31 (YYYY-MM-DD).");
        }
    }

    public static LocalDateTime appointmentTime(String value) {
        LocalDateTime t;
        try {
            t = LocalDateTime.parse(value.trim().replace(' ', 'T'));
        } catch (Exception e) {
            throw new HmsException("Time must look like 2025-08-31 10:30 (YYYY-MM-DD HH:MM).");
        }
        return checkSlot(t);
    }

    /** Appointments must be in the future and on a 30-minute slot (10:00, 10:30, ...). */
    public static LocalDateTime checkSlot(LocalDateTime t) {
        if (t.isBefore(LocalDateTime.now())) throw new HmsException("Appointment time is in the past.");
        if (t.getMinute() % 30 != 0) throw new HmsException("Appointments are in 30-minute slots (e.g. 10:00 or 10:30).");
        return t.withSecond(0).withNano(0);
    }

    public static String text(String value, String field, int max, boolean required) {
        if (value == null || value.isBlank()) {
            if (required) throw new HmsException(field + " cannot be empty.");
            return null;
        }
        if (value.trim().length() > max) throw new HmsException(field + " is too long (max " + max + " characters).");
        return value.trim();
    }
}
