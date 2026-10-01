package hms;

import hms.Models.Appointment;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Doctor scheduling. */
public class AppointmentDao {
    private final Database db;

    public AppointmentDao(Database db) { this.db = db; }

    /**
     * Books a slot. We do NOT check "is it free?" first and then insert (two admins could both pass the check).
     * We just insert: the UNIQUE(doctor_id, appointment_time) rule in the database lets exactly one win.
     */
    public int book(int patientId, int doctorId, LocalDateTime time) {
        LocalDateTime slot = Validation.checkSlot(time);
        String sql = "INSERT INTO appointments (patient_id, doctor_id, appointment_time) VALUES (?,?,?)";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, patientId);
            ps.setInt(2, doctorId);
            ps.setTimestamp(3, Timestamp.valueOf(slot));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        } catch (SQLException e) {
            if (Database.isConstraintError(e)) {
                throw new HmsException("Could not book: either that doctor is already booked at this time, "
                        + "or the patient/doctor id does not exist.", e);
            }
            throw new HmsException("Could not book appointment: " + e.getMessage(), e);
        }
    }

    /** Cancelling frees the slot (the row is deleted). Returns false if there was no such appointment. */
    public boolean cancel(int appointmentId) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM appointments WHERE id = ?")) {
            ps.setInt(1, appointmentId);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new HmsException("Could not cancel appointment: " + e.getMessage(), e);
        }
    }

    /** A doctor's schedule for one day, in time order. One JOIN query, no manual lookups. */
    public List<Appointment> scheduleForDoctor(int doctorId, LocalDate day) {
        String sql = "SELECT a.id, a.patient_id, CONCAT(p.first_name, ' ', p.last_name) AS patient_name, "
                   + "a.doctor_id, d.name AS doctor_name, a.appointment_time "
                   + "FROM appointments a JOIN patients p ON p.id = a.patient_id JOIN doctors d ON d.id = a.doctor_id "
                   + "WHERE a.doctor_id = ? AND a.appointment_time >= ? AND a.appointment_time < ? "
                   + "ORDER BY a.appointment_time";
        List<Appointment> result = new ArrayList<>();
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, doctorId);
            ps.setTimestamp(2, Timestamp.valueOf(day.atStartOfDay()));
            ps.setTimestamp(3, Timestamp.valueOf(day.plusDays(1).atStartOfDay()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new Appointment(rs.getInt("id"), rs.getInt("patient_id"), rs.getString("patient_name"),
                            rs.getInt("doctor_id"), rs.getString("doctor_name"),
                            rs.getTimestamp("appointment_time").toLocalDateTime()));
                }
            }
        } catch (SQLException e) {
            throw new HmsException("Could not load schedule: " + e.getMessage(), e);
        }
        return result;
    }
}
