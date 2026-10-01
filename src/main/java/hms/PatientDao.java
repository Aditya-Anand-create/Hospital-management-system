package hms;

import hms.Models.Patient;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** All patient SQL lives here. Every query uses PreparedStatement (no SQL injection). */
public class PatientDao {
    private final Database db;

    public PatientDao(Database db) { this.db = db; }

    /** Registers a new patient and returns the new id. */
    public int register(String firstName, String lastName, String dob, String gender, String phone, String address) {
        String fn = Validation.name(firstName, "First name");
        String ln = Validation.name(lastName, "Last name");
        LocalDate birth = Validation.birthDate(dob);
        String g = Validation.gender(gender);
        String ph = Validation.phone(phone);
        String addr = Validation.text(address, "Address", 200, false);

        String sql = "INSERT INTO patients (first_name, last_name, date_of_birth, gender, phone, address) VALUES (?,?,?,?,?,?)";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, fn);
            ps.setString(2, ln);
            ps.setDate(3, Date.valueOf(birth));
            ps.setString(4, g);
            ps.setString(5, ph);
            ps.setString(6, addr);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        } catch (SQLException e) {
            if (Database.isConstraintError(e)) throw new HmsException("A patient with this phone number is already registered.", e);
            throw new HmsException("Could not register patient: " + e.getMessage(), e);
        }
    }

    /** Finds patients whose first or last name starts with the text, or whose phone matches. */
    public List<Patient> search(String text) {
        String t = Validation.text(text, "Search text", 50, true);
        String sql = "SELECT id, first_name, last_name, date_of_birth, gender, phone, address FROM patients "
                   + "WHERE last_name LIKE ? OR first_name LIKE ? OR phone = ? ORDER BY last_name, first_name";
        List<Patient> result = new ArrayList<>();
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, t + "%");
            ps.setString(2, t + "%");
            ps.setString(3, t);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(map(rs));
            }
        } catch (SQLException e) {
            throw new HmsException("Search failed: " + e.getMessage(), e);
        }
        return result;
    }

    public Patient findById(int id) {
        String sql = "SELECT id, first_name, last_name, date_of_birth, gender, phone, address FROM patients WHERE id = ?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new HmsException("No patient with id " + id + ".");
                return map(rs);
            }
        } catch (SQLException e) {
            throw new HmsException("Could not load patient: " + e.getMessage(), e);
        }
    }

    private Patient map(ResultSet rs) throws SQLException {
        return new Patient(rs.getInt("id"), rs.getString("first_name"), rs.getString("last_name"),
                rs.getDate("date_of_birth").toLocalDate(), rs.getString("gender"),
                rs.getString("phone"), rs.getString("address"));
    }
}
