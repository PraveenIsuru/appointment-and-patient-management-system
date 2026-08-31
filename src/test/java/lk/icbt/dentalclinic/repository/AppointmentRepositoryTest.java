package lk.icbt.dentalclinic.repository;

import lk.icbt.dentalclinic.model.identity.Dentist;
import lk.icbt.dentalclinic.model.identity.Patient;
import lk.icbt.dentalclinic.model.scheduling.Appointment;
import lk.icbt.dentalclinic.model.scheduling.Treatment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Appointment persistence, run against the real Flyway-built schema.
 *
 * <p>{@code replace = NONE} keeps the H2 datasource declared in
 * {@code application.properties}, which runs in MySQL compatibility mode. Letting Boot
 * substitute its own embedded database would drop {@code MODE=MySQL} and the migrations
 * would not apply, so these tests would then be exercising a schema the application never
 * uses.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AppointmentRepositoryTest {

    private static final LocalDate DATE = LocalDate.of(2026, 9, 15);
    private static final LocalTime TIME = LocalTime.of(10, 0);

    @Autowired
    private AppointmentRepository appointments;

    @Autowired
    private PatientRepository patients;

    @Autowired
    private DentistRepository dentists;

    @Autowired
    private TreatmentRepository treatments;

    private Patient patient;
    private Dentist dentist;
    private Treatment treatment;

    @BeforeEach
    void loadSeedData() {
        patient = patients.findByPatientNo("PAT-000001").orElseThrow();
        dentist = dentists.findByDentistNo("DEN-001").orElseThrow();
        treatment = treatments.findByCode("FIL-01").orElseThrow();
    }

    private Appointment newAppointment(String no, LocalTime time) {
        return new Appointment(no, patient, dentist, treatment, DATE, time, "admin");
    }

    @Test
    @DisplayName("seed data from V2 is present")
    void seedDataLoaded() {
        assertNotNull(patient);
        assertNotNull(dentist);
        assertEquals(7, treatments.count(), "V2 seeds seven treatments");
        assertEquals("Dr. Anusha Perera", dentist.getFullName());
    }

    @Test
    @DisplayName("an appointment is found by its appointment number - brief requirement 3")
    void findsByAppointmentNo() {
        appointments.saveAndFlush(newAppointment("APT-2026-0001", TIME));

        Appointment found = appointments.findByAppointmentNo("APT-2026-0001").orElseThrow();

        assertEquals(patient.getId(), found.getPatient().getId());
        assertEquals("DEN-001", found.getDentist().getDentistNo());
        assertEquals(DATE, found.getAppointmentDate());
    }

    @Test
    @DisplayName("the unique slot index rejects a second booking for the same dentist and time")
    void preventsDoubleBooking() {
        appointments.saveAndFlush(newAppointment("APT-2026-0002", TIME));

        assertThrows(DataIntegrityViolationException.class,
                () -> appointments.saveAndFlush(newAppointment("APT-2026-0003", TIME)),
                "uq_dentist_slot must make the clash impossible, not merely unlikely");
    }

    @Test
    @DisplayName("a different time for the same dentist is accepted")
    void allowsDifferentSlot() {
        appointments.saveAndFlush(newAppointment("APT-2026-0004", TIME));
        appointments.saveAndFlush(newAppointment("APT-2026-0005", LocalTime.of(10, 30)));

        assertEquals(2, appointments.count());
    }

    @Test
    @DisplayName("cancelling releases the slot so it can be rebooked")
    void cancellationFreesTheSlot() {
        Appointment first = appointments.saveAndFlush(newAppointment("APT-2026-0006", TIME));
        assertEquals("A", first.getSlotLock(), "a live appointment holds its slot");

        first.cancel("Patient rescheduled by telephone");
        appointments.saveAndFlush(first);
        assertNull(first.getSlotLock(), "a cancelled appointment must release its slot");

        Appointment replacement =
                appointments.saveAndFlush(newAppointment("APT-2026-0007", TIME));

        assertNotNull(replacement.getId());
        assertEquals(2, appointments.count());
    }

    @Test
    @DisplayName("isSlotTaken reports a live booking but ignores a cancelled one")
    void reportsSlotAvailability() {
        assertFalse(appointments.isSlotTaken(dentist.getId(), DATE, TIME));

        Appointment booked = appointments.saveAndFlush(newAppointment("APT-2026-0008", TIME));
        assertTrue(appointments.isSlotTaken(dentist.getId(), DATE, TIME));

        booked.cancel("Clinic closed");
        appointments.saveAndFlush(booked);
        assertFalse(appointments.isSlotTaken(dentist.getId(), DATE, TIME),
                "a cancelled slot reads as free, matching the unique index");
    }

    @Test
    @DisplayName("appointment numbers are unique")
    void rejectsDuplicateAppointmentNo() {
        appointments.saveAndFlush(newAppointment("APT-2026-0009", TIME));

        assertThrows(DataIntegrityViolationException.class,
                () -> appointments.saveAndFlush(
                        newAppointment("APT-2026-0009", LocalTime.of(11, 0))));
    }

    @Test
    @DisplayName("a patient's appointments come back newest first")
    void listsPatientHistory() {
        appointments.saveAndFlush(newAppointment("APT-2026-0010", LocalTime.of(9, 0)));
        appointments.saveAndFlush(newAppointment("APT-2026-0011", LocalTime.of(11, 0)));

        var history = appointments
                .findByPatientIdOrderByAppointmentDateDescAppointmentTimeDesc(patient.getId());

        assertEquals(2, history.size());
        assertEquals(LocalTime.of(11, 0), history.get(0).getAppointmentTime());
    }

    @Test
    @DisplayName("year counter backs the APT-<year>-<0000> generator")
    void countsIssuedInYear() {
        appointments.saveAndFlush(newAppointment("APT-2026-0012", LocalTime.of(9, 30)));
        appointments.saveAndFlush(newAppointment("APT-2026-0013", LocalTime.of(12, 30)));

        assertEquals(2, appointments.countIssuedInYear("2026"));
        assertEquals(0, appointments.countIssuedInYear("2025"));
    }
}
