package hms;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Plain data holders (Java records). They only carry data. */
public class Models {
    public record Patient(int id, String firstName, String lastName, LocalDate dateOfBirth,
                          String gender, String phone, String address) {}

    public record Doctor(int id, String name, String specialization) {}

    public record Appointment(int id, int patientId, String patientName, int doctorId,
                              String doctorName, LocalDateTime time) {}

    public record MedicalRecord(int id, int patientId, int doctorId, String doctorName,
                                LocalDate visitDate, String diagnosis, String prescription) {}
}
