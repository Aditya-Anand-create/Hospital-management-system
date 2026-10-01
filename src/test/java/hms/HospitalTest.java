package hms;

import static org.junit.jupiter.api.Assertions.*;

import hms.Models.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Real JDBC code against H2 running in MySQL mode (no MySQL install needed). Each test gets a fresh database. */
class HospitalTest {
    Database db;
    PatientDao patients;
    DoctorDao doctors;
    AppointmentDao appointments;
    MedicalRecordDao records;

    // A time far in the future so the "not in the past" rule never fails
    static final LocalDateTime SLOT = LocalDateTime.of(2035, 5, 20, 10, 0);

    @BeforeEach
    void setUp() {
        db = new Database("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        db.createTables();
        patients = new PatientDao(db);
        doctors = new DoctorDao(db);
        appointments = new AppointmentDao(db);
        records = new MedicalRecordDao(db);
    }

    int newPatient(String phone) {
        return patients.register("Asha", "Verma", "1995-03-12", "female", phone, "Varanasi");
    }

    @Test
    void registersAndFindsPatientByNameAndPhone() {
        int id = newPatient("9876543210");
        assertEquals(1, patients.search("Ver").size());
        assertEquals(1, patients.search("asha".substring(0, 1).toUpperCase() + "sha").size());
        assertEquals(id, patients.search("9876543210").get(0).id());
        assertEquals("Female", patients.findById(id).gender());
        assertTrue(patients.search("Nobody").isEmpty());
    }

    @Test
    void duplicatePhoneIsRejected() {
        newPatient("9876543210");
        HmsException e = assertThrows(HmsException.class, () -> newPatient("9876543210"));
        assertTrue(e.getMessage().contains("already registered"));
    }

    @Test
    void badInputIsRejectedWithClearMessages() {
        assertThrows(HmsException.class, () -> patients.register("", "Verma", "1995-03-12", "Female", "9876543210", null));
        assertThrows(HmsException.class, () -> patients.register("Asha", "Verma", "12-03-1995", "Female", "9876543210", null));
        assertThrows(HmsException.class, () -> patients.register("Asha", "Verma", "2999-01-01", "Female", "9876543210", null));
        assertThrows(HmsException.class, () -> patients.register("Asha", "Verma", "1995-03-12", "Robot", "9876543210", null));
        assertThrows(HmsException.class, () -> patients.register("Asha", "Verma", "1995-03-12", "Female", "12345", null));
        assertThrows(HmsException.class, () -> patients.register("Asha<script>", "Verma", "1995-03-12", "Female", "9876543210", null));
        assertTrue(patients.search("Asha").isEmpty(), "nothing should be saved for invalid input");
    }

    @Test
    void sqlInjectionTextIsJustTextNotCode() {
        newPatient("9876543210");
        assertTrue(patients.search("x' OR '1'='1").isEmpty());
        assertEquals(1, patients.search("Asha").size()); // table still intact
    }

    @Test
    void bookingAndScheduleWork() {
        int p = newPatient("9876543210");
        int d = doctors.add("Dr. Rao", "Cardiology");
        int a1 = appointments.book(p, d, SLOT);
        appointments.book(p, d, SLOT.plusMinutes(30));
        List<Appointment> day = appointments.scheduleForDoctor(d, SLOT.toLocalDate());
        assertEquals(2, day.size());
        assertEquals(SLOT, day.get(0).time()); // sorted by time
        assertEquals("Asha Verma", day.get(0).patientName());
        assertTrue(appointments.scheduleForDoctor(d, SLOT.toLocalDate().plusDays(1)).isEmpty());
        assertTrue(appointments.cancel(a1));
        assertFalse(appointments.cancel(a1));
        assertEquals(1, appointments.scheduleForDoctor(d, SLOT.toLocalDate()).size());
    }

    @Test
    void doubleBookingIsRejectedAndCancelFreesTheSlot() {
        int p1 = newPatient("9876543210");
        int p2 = newPatient("9876543211");
        int d = doctors.add("Dr. Rao", "Cardiology");
        int first = appointments.book(p1, d, SLOT);
        assertThrows(HmsException.class, () -> appointments.book(p2, d, SLOT));
        appointments.cancel(first);
        assertDoesNotThrow(() -> appointments.book(p2, d, SLOT));
    }

    @Test
    void badSlotsAndUnknownIdsAreRejected() {
        int p = newPatient("9876543210");
        int d = doctors.add("Dr. Rao", "Cardiology");
        assertThrows(HmsException.class, () -> appointments.book(p, d, SLOT.plusMinutes(10)));          // not a 30-min slot
        assertThrows(HmsException.class, () -> appointments.book(p, d, LocalDateTime.of(2020, 1, 1, 10, 0))); // past
        assertThrows(HmsException.class, () -> appointments.book(999, d, SLOT));                         // no such patient
        assertThrows(HmsException.class, () -> appointments.book(p, 999, SLOT));                         // no such doctor
    }

    @Test
    void manyAdminsBookingTheSameSlotAtOnce_onlyOneWins() throws Exception {
        int d = doctors.add("Dr. Rao", "Cardiology");
        int threads = 12;
        int[] patientIds = new int[threads];
        for (int i = 0; i < threads; i++) patientIds[i] = newPatient("98765432" + (10 + i));

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        List<Future<?>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < threads; i++) {
            final int pid = patientIds[i];
            futures.add(pool.submit(() -> {
                try {
                    go.await();
                    appointments.book(pid, d, SLOT);
                    success.incrementAndGet();
                } catch (HmsException e) {
                    rejected.incrementAndGet();
                } catch (InterruptedException ignored) { }
            }));
        }
        go.countDown();
        for (Future<?> f : futures) f.get(30, TimeUnit.SECONDS);
        pool.shutdown();

        assertEquals(1, success.get(), "exactly one booking must succeed");
        assertEquals(threads - 1, rejected.get());
        assertEquals(1, appointments.scheduleForDoctor(d, SLOT.toLocalDate()).size());
    }

    @Test
    void medicalHistoryIsSavedAndShownNewestFirst() {
        int p = newPatient("9876543210");
        int d = doctors.add("Dr. Rao", "Cardiology");
        records.add(p, d, "2025-01-10", "Fever", "Paracetamol");
        records.add(p, d, "2025-06-02", "Back pain", null);
        List<MedicalRecord> history = records.historyOf(p);
        assertEquals(2, history.size());
        assertEquals("Back pain", history.get(0).diagnosis());
        assertEquals(LocalDate.of(2025, 1, 10), history.get(1).visitDate());
        assertEquals("Dr. Rao", history.get(0).doctorName());
        assertThrows(HmsException.class, () -> records.add(999, d, "2025-01-10", "Fever", null));
        assertThrows(HmsException.class, () -> records.add(p, d, "2999-01-10", "Fever", null));
        assertThrows(HmsException.class, () -> records.add(p, d, "2025-01-10", "  ", null));
        assertTrue(records.historyOf(12345).isEmpty());
    }
}
