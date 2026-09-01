package lk.icbt.dentalclinic.controller;

import lk.icbt.dentalclinic.model.scheduling.AppointmentStatus;
import lk.icbt.dentalclinic.repository.AppointmentRepository;
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

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Booking, searching and the lifecycle, through the web layer — brief requirements 2 and 3.
 *
 * <p>Realises the Book Appointment sequence diagram end to end, including the parts that only
 * appear under a real request: CSRF, binding, validation messages and the redirect.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AppointmentFlowTest {

    private static final String UDS = "clinicUserDetailsService";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppointmentRepository appointments;

    @Autowired
    private PatientRepository patients;

    @Autowired
    private DentistRepository dentists;

    @Autowired
    private TreatmentRepository treatments;

    private Long dentistId;
    private Long treatmentId;
    private Long patientId;
    private String futureDate;

    /**
     * Removes appointments before and after each test.
     *
     * <p>{@code @SpringBootTest} with a real MockMvc request is deliberately <em>not</em>
     * transactional — a rolled-back transaction would not exercise the commit path, and the
     * post-commit notification listener would never fire. The cost is that rows written here
     * are real and survive the test, so this class cleans up after itself. Without the
     * {@code @AfterEach}, leftover appointments changed the counts other test classes assert
     * on, which is exactly the sort of order-dependent failure that makes a suite untrusted.
     */
    @BeforeEach
    @AfterEach
    void clearAppointments() {
        appointments.deleteAll();
    }

    @BeforeEach
    void setUp() {
        dentistId = dentists.findByDentistNo("DEN-001").orElseThrow().getId();
        treatmentId = treatments.findByCode("FIL-01").orElseThrow().getId();
        patientId = patients.findByPatientNo("PAT-000001").orElseThrow().getId();
        futureDate = LocalDate.now().plusDays(7).toString();
    }

    /** Books as the signed-in user and returns the generated appointment number. */
    private String book(String time) throws Exception {
        MvcResult result = mockMvc.perform(post("/appointments").with(csrf())
                        .param("dentistId", String.valueOf(dentistId))
                        .param("treatmentId", String.valueOf(treatmentId))
                        .param("patientId", String.valueOf(patientId))
                        .param("appointmentDate", futureDate)
                        .param("appointmentTime", time)
                        .param("contactNumber", "0763456789")
                        .param("address", "42/3 Galle Road, Colombo 03")
                        .param("reasonForVisit", "Routine check"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/appointments/APT-*"))
                .andReturn();
        String location = result.getResponse().getRedirectedUrl();
        return location.substring(location.lastIndexOf('/') + 1);
    }

    // ------------------------------------------------------------------- booking ---

    @Test
    @WithUserDetails(value = "n.fernando", userDetailsServiceBeanName = UDS)
    @DisplayName("a patient books an appointment and gets a generated APT number")
    void patientBooksAppointment() throws Exception {
        String no = book("10:00");

        assertTrue(no.matches("APT-\\d{4}-\\d{4}"), "unexpected number format: " + no);
        var saved = appointments.findByAppointmentNo(no).orElseThrow();
        assertEquals(AppointmentStatus.SCHEDULED, saved.getStatus());
        assertEquals(LocalTime.of(10, 0), saved.getAppointmentTime());
        assertEquals("Nimal Fernando", saved.getPatient().getFullName());
    }

    @Test
    @WithUserDetails(value = "n.fernando", userDetailsServiceBeanName = UDS)
    @DisplayName("numbers increment across bookings in the same year")
    void numbersIncrement() throws Exception {
        String first = book("10:00");
        String second = book("10:30");

        assertEquals(1, Integer.parseInt(first.substring(first.lastIndexOf('-') + 1)));
        assertEquals(2, Integer.parseInt(second.substring(second.lastIndexOf('-') + 1)));
    }

    @Test
    @WithUserDetails(value = "n.fernando", userDetailsServiceBeanName = UDS)
    @DisplayName("booking updates the patient record rather than copying the address (A6)")
    void bookingUpdatesPatientRecord() throws Exception {
        mockMvc.perform(post("/appointments").with(csrf())
                        .param("dentistId", String.valueOf(dentistId))
                        .param("treatmentId", String.valueOf(treatmentId))
                        .param("appointmentDate", futureDate)
                        .param("appointmentTime", "11:00")
                        .param("contactNumber", "0759999999")
                        .param("address", "New Address, Dehiwala"))
                .andExpect(status().is3xxRedirection());

        var patient = patients.findByPatientNo("PAT-000001").orElseThrow();
        assertEquals("New Address, Dehiwala", patient.getAddress());
        assertEquals("0759999999", patient.getContactNumber());
    }

    // ---------------------------------------------------------------- validation ---

    @Test
    @WithUserDetails(value = "n.fernando", userDetailsServiceBeanName = UDS)
    @DisplayName("a date in the past is refused with a message on the date field")
    void refusesPastDate() throws Exception {
        mockMvc.perform(post("/appointments").with(csrf())
                        .param("dentistId", String.valueOf(dentistId))
                        .param("treatmentId", String.valueOf(treatmentId))
                        .param("appointmentDate", LocalDate.now().minusDays(1).toString())
                        .param("appointmentTime", "10:00")
                        .param("contactNumber", "0763456789")
                        .param("address", "42/3 Galle Road"))
                .andExpect(status().isOk())
                .andExpect(view().name("appointments/book"))
                .andExpect(model().attributeHasFieldErrors("form", "appointmentDate"));

        assertEquals(0, appointments.count());
    }

    @Test
    @WithUserDetails(value = "n.fernando", userDetailsServiceBeanName = UDS)
    @DisplayName("a time outside the dentist's session is refused")
    void refusesTimeOutsideDentistSession() throws Exception {
        // DEN-001 consults 08:00-14:00, so 16:00 is inside clinic hours but not their session.
        mockMvc.perform(post("/appointments").with(csrf())
                        .param("dentistId", String.valueOf(dentistId))
                        .param("treatmentId", String.valueOf(treatmentId))
                        .param("appointmentDate", futureDate)
                        .param("appointmentTime", "16:00")
                        .param("contactNumber", "0763456789")
                        .param("address", "42/3 Galle Road"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "appointmentTime"));

        assertEquals(0, appointments.count());
    }

    @Test
    @WithUserDetails(value = "n.fernando", userDetailsServiceBeanName = UDS)
    @DisplayName("a malformed contact number is refused by Bean Validation")
    void refusesMalformedContactNumber() throws Exception {
        mockMvc.perform(post("/appointments").with(csrf())
                        .param("dentistId", String.valueOf(dentistId))
                        .param("treatmentId", String.valueOf(treatmentId))
                        .param("appointmentDate", futureDate)
                        .param("appointmentTime", "10:00")
                        .param("contactNumber", "12345")
                        .param("address", "42/3 Galle Road"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "contactNumber"));
    }

    // ------------------------------------------------------------ double booking ---

    @Test
    @WithUserDetails(value = "admin", userDetailsServiceBeanName = UDS)
    @DisplayName("the clinic's original problem: the same slot cannot be booked twice")
    void refusesDoubleBooking() throws Exception {
        book("09:00");

        MvcResult clash = mockMvc.perform(post("/appointments").with(csrf())
                        .param("dentistId", String.valueOf(dentistId))
                        .param("treatmentId", String.valueOf(treatmentId))
                        .param("patientId", String.valueOf(
                                patients.findByPatientNo("PAT-000002").orElseThrow().getId()))
                        .param("appointmentDate", futureDate)
                        .param("appointmentTime", "09:00")
                        .param("contactNumber", "0714567890")
                        .param("address", "18 Temple Lane, Nugegoda"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "appointmentTime"))
                .andReturn();

        assertEquals(1, appointments.count(), "the clashing booking must not be saved");
        String body = clash.getResponse().getContentAsString();
        assertTrue(body.contains("Nearest free slots"),
                "the refusal should suggest alternatives, not merely refuse");
    }

    @Test
    @WithUserDetails(value = "admin", userDetailsServiceBeanName = UDS)
    @DisplayName("cancelling frees the slot, so it can be rebooked")
    void cancellationFreesTheSlot() throws Exception {
        String first = book("09:30");

        mockMvc.perform(post("/appointments/" + first + "/cancel").with(csrf())
                        .param("reason", "Patient telephoned"))
                .andExpect(status().is3xxRedirection());

        assertEquals(AppointmentStatus.CANCELLED,
                appointments.findByAppointmentNo(first).orElseThrow().getStatus());

        String replacement = book("09:30");
        assertNotNull(replacement);
        assertEquals(2, appointments.count());
    }

    // -------------------------------------------------------------------- search ---

    @Test
    @WithUserDetails(value = "n.fernando", userDetailsServiceBeanName = UDS)
    @DisplayName("search by appointment number shows the full details — requirement 3")
    void searchShowsFullDetails() throws Exception {
        String no = book("12:00");

        mockMvc.perform(get("/appointments/" + no))
                .andExpect(status().isOk())
                .andExpect(view().name("appointments/detail"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(no)))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("Dr. Anusha Perera")))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("Composite Filling")));
    }

    @Test
    @WithUserDetails(value = "n.fernando", userDetailsServiceBeanName = UDS)
    @DisplayName("searching is not fussy about case")
    void searchIsCaseInsensitive() throws Exception {
        String no = book("12:30");

        mockMvc.perform(get("/appointments/" + no.toLowerCase()))
                .andExpect(status().isOk())
                .andExpect(view().name("appointments/detail"));
    }

    @Test
    @WithUserDetails(value = "n.fernando", userDetailsServiceBeanName = UDS)
    @DisplayName("an unknown number gives a readable message, not a stack trace")
    void unknownNumberIsHandled() throws Exception {
        mockMvc.perform(get("/appointments/APT-2026-9999"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/message"));
    }

    // ----------------------------------------------------------------- A8 scoping ---

    @Test
    @WithUserDetails(value = "s.jayawardena", userDetailsServiceBeanName = UDS)
    @DisplayName("a patient cannot open another patient's appointment (A8)")
    void patientCannotReadAnotherPatientsAppointment() throws Exception {
        // Booked for PAT-000001; the signed-in user is PAT-000002.
        String no = bookAsAdminForFernando("13:00");

        mockMvc.perform(get("/appointments/" + no))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/message"))
                // "Not found", not "forbidden": confirming the record exists would let a
                // patient discover valid appointment numbers by the difference.
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("No appointment exists")));
    }

    @Test
    @WithUserDetails(value = "s.jayawardena", userDetailsServiceBeanName = UDS)
    @DisplayName("a patient's list contains only their own appointments")
    void patientListIsScoped() throws Exception {
        bookAsAdminForFernando("14:30");

        mockMvc.perform(get("/appointments"))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString("Nimal Fernando"))));
    }

    /**
     * Creates an appointment owned by PAT-000001 without going through the web layer, so a
     * test signed in as a different patient can try to reach it.
     */
    private String bookAsAdminForFernando(String time) {
        var patient = patients.findByPatientNo("PAT-000001").orElseThrow();
        var dentist = dentists.findByDentistNo("DEN-001").orElseThrow();
        var treatment = treatments.findByCode("FIL-01").orElseThrow();
        var appointment = new lk.icbt.dentalclinic.model.scheduling.Appointment(
                "APT-2026-8001", patient, dentist, treatment,
                LocalDate.parse(futureDate), LocalTime.parse(time), "admin");
        return appointments.saveAndFlush(appointment).getAppointmentNo();
    }
}
