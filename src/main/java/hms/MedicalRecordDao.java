package hms;

import hms.Models.MedicalRecord;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Medical records tracking: add a visit note, read a patient's full history. */
public class MedicalRecordDao {
    private final Database db;

    public MedicalRecordDao(Database db) { this.db = db; }

    public int add(int patientId, int doctorId, String visitDate, String diagnosis, String prescription) {
        LocalDate date = Validation.parseDate(visitDate);
        if (date.isAfter(LocalDate.now())) throw new HmsException("Visit date cannot be in the future.");
        String diag = Validation.text(diagnosis, "Diagnosis", 200, true);
        String rx = Validation.text(prescription, "Prescription", 300, false);

        String sql = "INSERT INTO medical_records (patient_id, doctor_id, visit_date, diagnosis, prescription) VALUES (?,?,?,?,?)";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, patientId);
            ps.setInt(2, doctorId);
            ps.setDate(3, Date.valueOf(date));
            ps.setString(4, diag);
            ps.setString(5, rx);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        } catch (SQLException e) {
            if (Database.isConstraintError(e)) throw new HmsException("Patient id or doctor id does not exist.", e);
            throw new HmsException("Could not save record: " + e.getMessage(), e);
        }
    }

    /** Newest visit first. Uses the (patient_id, visit_date) index. */
    public List<MedicalRecord> historyOf(int patientId) {
        String sql = "SELECT r.id, r.patient_id, r.doctor_id, d.name AS doctor_name, r.visit_date, r.diagnosis, r.prescription "
                   + "FROM medical_records r JOIN doctors d ON d.id = r.doctor_id "
                   + "WHERE r.patient_id = ? ORDER BY r.visit_date DESC, r.id DESC";
        List<MedicalRecord> result = new ArrayList<>();
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, patientId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new MedicalRecord(rs.getInt("id"), rs.getInt("patient_id"), rs.getInt("doctor_id"),
                            rs.getString("doctor_name"), rs.getDate("visit_date").toLocalDate(),
                            rs.getString("diagnosis"), rs.getString("prescription")));
                }
            }
        } catch (SQLException e) {
            throw new HmsException("Could not load history: " + e.getMessage(), e);
        }
        return result;
    }
}
