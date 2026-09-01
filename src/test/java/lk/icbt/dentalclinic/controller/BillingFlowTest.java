package lk.icbt.dentalclinic.controller;

import lk.icbt.dentalclinic.model.billing.LineCategory;
import lk.icbt.dentalclinic.model.billing.PaymentStatus;
import lk.icbt.dentalclinic.model.scheduling.Appointment;
import lk.icbt.dentalclinic.repository.AppointmentRepository;
import lk.icbt.dentalclinic.repository.BillRepository;
import lk.icbt.dentalclinic.repository.DentistRepository;
import lk.icbt.dentalclinic.repository.PatientRepository;
import lk.icbt.dentalclinic.repository.TreatmentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Billing and receipts through the web layer — brief requirement 4.
 *
 * <p>Realises the Generate Bill sequence diagram, including the two refusals it specifies:
 * a bill may be raised only for a completed treatment, and only once.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BillingFlowTest {

    private static final String UDS = "clinicUserDetailsService";

    @Autowired private MockMvc mockMvc;
    @Autowired private AppointmentRepository appointments;
    @Autowired private BillRepository bills;
    @Autowired private PatientRepository patients;
    @Autowired private DentistRepository dentists;
    @Autowired private TreatmentRepository treatments;

    /** Non-transactional, so rows are real; cleaned up so other classes are unaffected. */
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

    /** A completed extraction for Nimal Fernando with Dr Perera. */
    private String completedAppointment(String treatmentCode, LocalTime time) {
        var patient = patients.findByPatientNo("PAT-000001").orElseThrow();
        var dentist = dentists.findByDentistNo("DEN-001").orElseThrow();
        var treatment = treatments.findByCode(treatmentCode).orElseThrow();
        var appointment = new Appointment("APT-2026-7001", patient, dentist, treatment,
                LocalDate.now().plusDays(3), time, "admin");
        appointment.complete("Treatment carried out without complication");
        return appointments.saveAndFlush(appointment).getAppointmentNo();
    }

    private String raiseBill(String appointmentNo, String discountPercent) throws Exception {
        MvcResult result = mockMvc.perform(post("/billing").with(csrf())
                        .param("appointmentNo", appointmentNo)
                        .param("discountPercent", discountPercent))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/billing/BILL-*"))
                .andReturn();
        String location = result.getResponse().getRedirectedUrl();
        return location.substring(location.lastIndexOf('/') + 1);
    }

    // ------------------------------------------------------------------- billing ---

    @Test
    @WithUserDetails(value = "admin", userDetailsServiceBeanName = UDS)
    @DisplayName("a bill is consultation fee plus treatment cost, itemised")
    void raisesItemisedBill() throws Exception {
        // EXT-02 lists at 12,000; surgical loading of 15% gives 13,800.
        // Dr Perera's consultation fee is 2,500. Total 16,300.
        String billNo = raiseBill(completedAppointment("EXT-02", LocalTime.of(9, 0)), "0");

        var bill = bills.findByBillNo(billNo).orElseThrow();
        assertAmount("2500.00", bill.getConsultationFee());
        assertAmount("13800.00", bill.getTreatmentCost());
        assertAmount("16300.00", bill.getTotalAmount());
        assertEquals(PaymentStatus.UNPAID, bill.getStatus());

        assertEquals(2, bill.getLineItems().size(), "a receipt needs itemised lines");
        assertTrue(bill.getLineItems().stream()
                .anyMatch(l -> l.getCategory() == LineCategory.CONSULTATION));
        assertTrue(bill.getLineItems().stream()
                .anyMatch(l -> l.getCategory() == LineCategory.TREATMENT));
    }

    @Test
    @WithUserDetails(value = "admin", userDetailsServiceBeanName = UDS)
    @DisplayName("a discount reduces the total and is recorded on the bill")
    void appliesDiscount() throws Exception {
        String billNo = raiseBill(completedAppointment("EXT-02", LocalTime.of(9, 30)), "10");

        var bill = bills.findByBillNo(billNo).orElseThrow();
        // 10% of 16,300 = 1,630
        assertAmount("1630.00", bill.getDiscountAmount());
        assertAmount("14670.00", bill.getTotalAmount());
    }

    @Test
    @WithUserDetails(value = "admin", userDetailsServiceBeanName = UDS)
    @DisplayName("a discount above the 25% cap is refused")
    void refusesExcessiveDiscount() throws Exception {
        String appointmentNo = completedAppointment("EXT-02", LocalTime.of(10, 0));

        mockMvc.perform(post("/billing").with(csrf())
                        .param("appointmentNo", appointmentNo)
                        .param("discountPercent", "50"))
                .andExpect(status().isConflict())
                .andExpect(view().name("error/message"));

        assertEquals(0, bills.count(), "no bill may be written when the discount is refused");
    }

    @Test
    @WithUserDetails(value = "admin", userDetailsServiceBeanName = UDS)
    @DisplayName("a bill cannot be raised for an incomplete treatment")
    void refusesUnbillableAppointment() throws Exception {
        var patient = patients.findByPatientNo("PAT-000001").orElseThrow();
        var dentist = dentists.findByDentistNo("DEN-001").orElseThrow();
        var treatment = treatments.findByCode("FIL-01").orElseThrow();
        var scheduled = appointments.saveAndFlush(new Appointment("APT-2026-7002",
                patient, dentist, treatment, LocalDate.now().plusDays(3),
                LocalTime.of(11, 0), "admin"));

        mockMvc.perform(post("/billing").with(csrf())
                        .param("appointmentNo", scheduled.getAppointmentNo())
                        .param("discountPercent", "0"))
                .andExpect(status().isConflict())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("completed treatment")));

        assertEquals(0, bills.count());
    }

    @Test
    @WithUserDetails(value = "admin", userDetailsServiceBeanName = UDS)
    @DisplayName("a treatment cannot be billed twice")
    void refusesSecondBill() throws Exception {
        String appointmentNo = completedAppointment("EXT-02", LocalTime.of(12, 0));
        raiseBill(appointmentNo, "0");

        mockMvc.perform(post("/billing").with(csrf())
                        .param("appointmentNo", appointmentNo)
                        .param("discountPercent", "0"))
                .andExpect(status().isConflict())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("already been billed")));

        assertEquals(1, bills.count());
    }

    // ------------------------------------------------------------------ receipts ---

    @Test
    @WithUserDetails(value = "admin", userDetailsServiceBeanName = UDS)
    @DisplayName("the receipt shows the patient, the lines and the total")
    void rendersReceipt() throws Exception {
        String billNo = raiseBill(completedAppointment("EXT-02", LocalTime.of(13, 0)), "0");

        mockMvc.perform(get("/billing/" + billNo))
                .andExpect(status().isOk())
                .andExpect(view().name("billing/receipt"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(billNo)))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("Nimal Fernando")))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("16,300.00")));
    }

    @Test
    @WithUserDetails(value = "admin", userDetailsServiceBeanName = UDS)
    @DisplayName("the print view drops the navigation and carries the clinic's details")
    void rendersPrintableReceipt() throws Exception {
        String billNo = raiseBill(completedAppointment("EXT-02", LocalTime.of(13, 30)), "0");

        String body = mockMvc.perform(get("/billing/" + billNo + "/print"))
                .andExpect(status().isOk())
                .andExpect(view().name("billing/print"))
                .andReturn().getResponse().getContentAsString();

        assertTrue(body.contains("Sunrise Dental Clinic"));
        assertTrue(body.contains("Total payable"));
        assertTrue(!body.contains("Sign out"),
                "a printed receipt must not carry the application's navigation");
    }

    @Test
    @WithUserDetails(value = "admin", userDetailsServiceBeanName = UDS)
    @DisplayName("recording payment marks the bill paid")
    void recordsPayment() throws Exception {
        String billNo = raiseBill(completedAppointment("EXT-02", LocalTime.of(14, 0)), "0");

        mockMvc.perform(post("/billing/" + billNo + "/pay").with(csrf())
                        .param("method", "CARD"))
                .andExpect(status().is3xxRedirection());

        assertEquals(PaymentStatus.PAID, bills.findByBillNo(billNo).orElseThrow().getStatus());
    }

    // ------------------------------------------------------------------ A8 on bills ---

    @Test
    @WithUserDetails(value = "s.jayawardena", userDetailsServiceBeanName = UDS)
    @DisplayName("a patient cannot open another patient's bill")
    void billsAreRowScoped() throws Exception {
        // Raised against PAT-000001; the signed-in user is PAT-000002.
        var patient = patients.findByPatientNo("PAT-000001").orElseThrow();
        var dentist = dentists.findByDentistNo("DEN-001").orElseThrow();
        var treatment = treatments.findByCode("EXT-02").orElseThrow();
        var appointment = new Appointment("APT-2026-7003", patient, dentist, treatment,
                LocalDate.now().plusDays(3), LocalTime.of(15, 0), "admin");
        appointment.complete("Done");
        appointments.saveAndFlush(appointment);

        // Patients may not raise bills at all.
        mockMvc.perform(post("/billing").with(csrf())
                        .param("appointmentNo", "APT-2026-7003")
                        .param("discountPercent", "0"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithUserDetails(value = "n.fernando", userDetailsServiceBeanName = UDS)
    @DisplayName("a patient cannot raise their own bill")
    void patientsCannotBillThemselves() throws Exception {
        completedAppointment("EXT-02", LocalTime.of(16, 0));

        mockMvc.perform(post("/billing").with(csrf())
                        .param("appointmentNo", "APT-2026-7001")
                        .param("discountPercent", "25"))
                .andExpect(status().isForbidden());

        assertEquals(0, bills.count());
    }
}
