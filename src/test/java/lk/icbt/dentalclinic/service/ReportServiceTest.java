package lk.icbt.dentalclinic.service;

import lk.icbt.dentalclinic.model.billing.PaymentMethod;
import lk.icbt.dentalclinic.model.scheduling.Appointment;
import lk.icbt.dentalclinic.model.scheduling.TreatmentType;
import lk.icbt.dentalclinic.repository.AppointmentRepository;
import lk.icbt.dentalclinic.repository.BillRepository;
import lk.icbt.dentalclinic.repository.DentistRepository;
import lk.icbt.dentalclinic.repository.PatientRepository;
import lk.icbt.dentalclinic.repository.TreatmentRepository;
import lk.icbt.dentalclinic.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The management reports.
 *
 * <p>Each test builds a known day and checks the figure the clinic would act on. The
 * cancellation cases matter most: a report that counts work which never happened would make
 * every number optimistic, and the clinic would plan against it.
 */
@SpringBootTest
class ReportServiceTest {

    private static final LocalDate DAY = LocalDate.now().plusDays(30);

    @Autowired private ReportService reports;
    @Autowired private BillingService billing;
    @Autowired private AppointmentRepository appointments;
    @Autowired private BillRepository bills;
    @Autowired private PatientRepository patients;
    @Autowired private DentistRepository dentists;
    @Autowired private TreatmentRepository treatments;
    @Autowired private UserRepository users;

    @BeforeEach
    @AfterEach
    void clean() {
        appointments.deleteAll();
        bills.deleteAll();
    }

    private static void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                () -> "expected " + expected + " but was " + actual);
    }

    private Appointment appointment(String no, String dentistNo, String treatmentCode,
                                    LocalTime time) {
        var patient = patients.findByPatientNo("PAT-000001").orElseThrow();
        var dentist = dentists.findByDentistNo(dentistNo).orElseThrow();
        var treatment = treatments.findByCode(treatmentCode).orElseThrow();
        return appointments.saveAndFlush(new Appointment(no, patient, dentist, treatment,
                DAY, time, "admin"));
    }

    private Appointment completed(String no, String dentistNo, String code, LocalTime time) {
        Appointment a = appointment(no, dentistNo, code, time);
        a.complete("Done");
        return appointments.saveAndFlush(a);
    }

    private void bill(String appointmentNo) {
        billing.generateBill(appointmentNo, BigDecimal.ZERO,
                users.findByUsername("admin").orElseThrow());
    }

    // ------------------------------------------------------------- daily summary ---

    @Test
    @DisplayName("the daily summary counts each status separately")
    void countsStatuses() {
        completed("APT-2026-6001", "DEN-001", "EXT-02", LocalTime.of(9, 0));
        appointment("APT-2026-6002", "DEN-001", "FIL-01", LocalTime.of(9, 30));
        var cancelled = appointment("APT-2026-6003", "DEN-001", "FIL-01", LocalTime.of(10, 0));
        cancelled.cancel("Patient telephoned");
        appointments.saveAndFlush(cancelled);

        var summary = reports.dailySummary(DAY);

        assertEquals(3, summary.booked());
        assertEquals(1, summary.completed());
        assertEquals(1, summary.cancelled());
        assertEquals(0, summary.noShows());
    }

    @Test
    @DisplayName("chair time lost is the share of bookings cancelled or not attended")
    void reportsLostChairTime() {
        completed("APT-2026-6010", "DEN-001", "EXT-02", LocalTime.of(9, 0));
        var cancelled = appointment("APT-2026-6011", "DEN-001", "FIL-01", LocalTime.of(9, 30));
        cancelled.cancel("Called off");
        appointments.saveAndFlush(cancelled);

        // One of two bookings lost = 50%
        assertEquals(0, new BigDecimal("50.0")
                .compareTo(reports.dailySummary(DAY).lostSlotPercentage()));
    }

    @Test
    @DisplayName("billed and collected are tracked separately, so outstanding money is visible")
    void separatesBilledFromCollected() {
        completed("APT-2026-6020", "DEN-001", "EXT-02", LocalTime.of(9, 0));
        completed("APT-2026-6021", "DEN-001", "FIL-01", LocalTime.of(9, 30));
        bill("APT-2026-6020");   // 2500 + 13800 = 16300
        bill("APT-2026-6021");   // 2500 + 5040  =  7540

        var beforePayment = reports.dailySummary(DAY);
        assertAmount("23840.00", beforePayment.billed());
        assertAmount("0.00", beforePayment.collected());
        assertAmount("23840.00", beforePayment.outstanding());

        String billNo = appointments.findByAppointmentNo("APT-2026-6020")
                .orElseThrow().getBill().getBillNo();
        billing.recordPayment(billNo, PaymentMethod.CASH);

        var afterPayment = reports.dailySummary(DAY);
        assertAmount("16300.00", afterPayment.collected());
        assertAmount("7540.00", afterPayment.outstanding());
    }

    // ------------------------------------------------------ revenue by treatment ---

    @Test
    @DisplayName("revenue is grouped by treatment type, highest first")
    void groupsRevenueByTreatment() {
        completed("APT-2026-6030", "DEN-001", "EXT-02", LocalTime.of(9, 0));   // 16300
        completed("APT-2026-6031", "DEN-001", "FIL-01", LocalTime.of(9, 30));  //  7540
        bill("APT-2026-6030");
        bill("APT-2026-6031");

        var rows = reports.revenueByTreatment(DAY, DAY);

        assertEquals(2, rows.size());
        assertEquals(TreatmentType.EXTRACTION, rows.get(0).treatmentType());
        assertAmount("16300.00", rows.get(0).revenue());
        assertEquals(TreatmentType.FILLING, rows.get(1).treatmentType());
        assertAmount("7540.00", rows.get(1).revenue());
    }

    @Test
    @DisplayName("cancelled visits are excluded from revenue entirely")
    void excludesCancelledFromRevenue() {
        var cancelled = appointment("APT-2026-6040", "DEN-001", "EXT-02", LocalTime.of(9, 0));
        cancelled.cancel("Called off");
        appointments.saveAndFlush(cancelled);

        assertTrue(reports.revenueByTreatment(DAY, DAY).isEmpty(),
                "work that never happened must not appear as revenue");
    }

    @Test
    @DisplayName("an unbilled visit counts as a visit but contributes no revenue")
    void countsUnbilledVisitsAtZero() {
        completed("APT-2026-6050", "DEN-001", "EXT-02", LocalTime.of(9, 0));

        var rows = reports.revenueByTreatment(DAY, DAY);
        assertEquals(1, rows.get(0).visits());
        assertAmount("0.00", rows.get(0).revenue());
    }

    // ------------------------------------------------------------------ workload ---

    @Test
    @DisplayName("workload reports bookings, completions and the completion rate per dentist")
    void reportsDentistWorkload() {
        completed("APT-2026-6060", "DEN-001", "EXT-02", LocalTime.of(9, 0));
        appointment("APT-2026-6061", "DEN-001", "FIL-01", LocalTime.of(9, 30));
        bill("APT-2026-6060");

        var perera = reports.dentistWorkload(DAY, DAY).stream()
                .filter(r -> r.dentistNo().equals("DEN-001"))
                .findFirst().orElseThrow();

        assertEquals(2, perera.booked());
        assertEquals(1, perera.completed());
        assertEquals(0, new BigDecimal("50.0").compareTo(perera.completionRate()));
        assertAmount("16300.00", perera.revenue());
    }

    @Test
    @DisplayName("a dentist with no bookings still appears — idle chair time must be visible")
    void listsIdleDentists() {
        completed("APT-2026-6070", "DEN-001", "EXT-02", LocalTime.of(9, 0));

        var rows = reports.dentistWorkload(DAY, DAY);

        assertEquals(2, rows.size(), "both active dentists should be listed");
        var silva = rows.stream().filter(r -> r.dentistNo().equals("DEN-002"))
                .findFirst().orElseThrow();
        assertEquals(0, silva.booked());
        assertEquals(0, new BigDecimal("0").compareTo(silva.completionRate()));
    }

    // ------------------------------------------------------------------- history ---

    @Test
    @DisplayName("patient history returns that patient's visits, newest first")
    void reportsPatientHistory() {
        completed("APT-2026-6080", "DEN-001", "EXT-02", LocalTime.of(9, 0));
        completed("APT-2026-6081", "DEN-001", "FIL-01", LocalTime.of(11, 0));

        var patientId = patients.findByPatientNo("PAT-000001").orElseThrow().getId();
        var history = reports.patientHistory(patientId);

        assertEquals(2, history.size());
        assertEquals(LocalTime.of(11, 0), history.get(0).getAppointmentTime());
    }
}
