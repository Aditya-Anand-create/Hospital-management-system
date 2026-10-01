package hms;

import hms.Models.Doctor;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DoctorDao {
    private final Database db;

    public DoctorDao(Database db) { this.db = db; }

    public int add(String name, String specialization) {
        String n = Validation.name(name, "Doctor name");
        String s = Validation.text(specialization, "Specialization", 80, true);
        String sql = "INSERT INTO doctors (name, specialization) VALUES (?,?)";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, n);
            ps.setString(2, s);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        } catch (SQLException e) {
            throw new HmsException("Could not add doctor: " + e.getMessage(), e);
        }
    }

    public List<Doctor> listAll() {
        List<Doctor> result = new ArrayList<>();
        try (Connection c = db.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, name, specialization FROM doctors ORDER BY name")) {
            while (rs.next()) result.add(new Doctor(rs.getInt(1), rs.getString(2), rs.getString(3)));
        } catch (SQLException e) {
            throw new HmsException("Could not list doctors: " + e.getMessage(), e);
        }
        return result;
    }
}
