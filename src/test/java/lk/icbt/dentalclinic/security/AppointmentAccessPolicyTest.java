package lk.icbt.dentalclinic.security;

import lk.icbt.dentalclinic.model.identity.Administrator;
import lk.icbt.dentalclinic.model.identity.Dentist;
import lk.icbt.dentalclinic.model.identity.Patient;
import lk.icbt.dentalclinic.model.identity.Role;
import lk.icbt.dentalclinic.model.identity.RoleType;
import lk.icbt.dentalclinic.model.identity.User;
import lk.icbt.dentalclinic.model.scheduling.Appointment;
import lk.icbt.dentalclinic.model.scheduling.Treatment;
import lk.icbt.dentalclinic.model.scheduling.TreatmentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Row-level access rules — <strong>assumption A8</strong>.
 *
 * <p>This is the class that stops one patient reading another's clinical history by editing
 * the appointment number in the address bar, so every role is checked against both its own
 * appointment and someone else's. Being a plain object, it can be tested exhaustively
 * without a web request — which is precisely why the rule was written as a bean rather than
 * as a SpEL string inside an annotation.
 *
 * <p>Ids are set reflectively because they are database-generated and have no setter; the
 * policy compares them, so they must be present for the test to mean anything.
 */
class AppointmentAccessPolicyTest {

    private final AppointmentAccessPolicy policy = new AppointmentAccessPolicy();

    private Patient owner;
    private Patient otherPatient;
    private Dentist assignedDentist;
    private Dentist otherDentist;
    private Administrator admin;
    private Appointment appointment;

    private static <T extends User> T withId(T user, long id, RoleType role) {
        ReflectionTestUtils.setField(user, "id", id);
        user.addRole(new Role(role, role.name()));
        return user;
    }

    @BeforeEach
    void setUp() {
        owner = withId(new Patient("owner", "h", "owner@mail.lk", "Owner Patient",
                "0770000001", "PAT-000101", "1 Road", LocalDate.of(1990, 1, 1)), 1L, RoleType.PATIENT);
        otherPatient = withId(new Patient("other", "h", "other@mail.lk", "Other Patient",
                "0770000002", "PAT-000102", "2 Road", LocalDate.of(1991, 2, 2)), 2L, RoleType.PATIENT);
        assignedDentist = withId(new Dentist("dr.a", "h", "a@clinic.lk", "Dr Assigned",
                "0770000003", "DEN-901", "General", "SLMC-901", new BigDecimal("2000.00"),
                LocalTime.of(8, 0), LocalTime.of(14, 0)), 3L, RoleType.DENTIST);
        otherDentist = withId(new Dentist("dr.b", "h", "b@clinic.lk", "Dr Other",
                "0770000004", "DEN-902", "General", "SLMC-902", new BigDecimal("2000.00"),
                LocalTime.of(14, 0), LocalTime.of(20, 0)), 4L, RoleType.DENTIST);
        admin = withId(new Administrator("boss", "h", "boss@clinic.lk", "The Admin",
                "0770000005", "STF-901", "Manager"), 5L, RoleType.ADMIN);

        Treatment treatment = new Treatment("T-1", "Filling", TreatmentType.FILLING,
                new BigDecimal("4500.00"), 40, 1);
        appointment = new Appointment("APT-2026-9001", owner, assignedDentist, treatment,
                LocalDate.of(2026, 12, 1), LocalTime.of(10, 0), "admin");
    }

    // ---------------------------------------------------------------- canView ---

    @Test
    @DisplayName("the owning patient may view their own appointment")
    void ownerMayView() {
        assertTrue(policy.canView(owner, appointment));
    }

    @Test
    @DisplayName("another patient may NOT view it — the A8 rule that matters most")
    void otherPatientMayNotView() {
        assertFalse(policy.canView(otherPatient, appointment),
                "a patient guessing an appointment number must not reach another patient's record");
    }

    @Test
    @DisplayName("the assigned dentist may view it; another dentist may not")
    void onlyAssignedDentistMayView() {
        assertTrue(policy.canView(assignedDentist, appointment));
        assertFalse(policy.canView(otherDentist, appointment));
    }

    @Test
    @DisplayName("an administrator may view any appointment")
    void adminMayViewAnything() {
        assertTrue(policy.canView(admin, appointment));
    }

    // -------------------------------------------------------------- canModify ---

    @Test
    @DisplayName("the owning patient and an administrator may change an appointment")
    void ownerAndAdminMayModify() {
        assertTrue(policy.canModify(owner, appointment));
        assertTrue(policy.canModify(admin, appointment));
    }

    @Test
    @DisplayName("a dentist may read their appointment but not move or cancel it")
    void assignedDentistMayNotModify() {
        assertTrue(policy.canView(assignedDentist, appointment));
        assertFalse(policy.canModify(assignedDentist, appointment),
                "rearranging the diary is the clinic's job, not the dentist's");
    }

    @Test
    @DisplayName("another patient may not change it")
    void otherPatientMayNotModify() {
        assertFalse(policy.canModify(otherPatient, appointment));
    }

    // ------------------------------------------------------- canRecordTreatment ---

    @Test
    @DisplayName("only the assigned dentist or an administrator may record treatment")
    void treatmentRecordingIsRestricted() {
        assertTrue(policy.canRecordTreatment(assignedDentist, appointment));
        assertTrue(policy.canRecordTreatment(admin, appointment));
        assertFalse(policy.canRecordTreatment(otherDentist, appointment));
        assertFalse(policy.canRecordTreatment(owner, appointment),
                "a patient must not be able to declare their own treatment complete");
    }

    // -------------------------------------------------------------- null safety ---

    @Test
    @DisplayName("null arguments are refused rather than throwing — the default is deny")
    void deniesOnNulls() {
        assertFalse(policy.canView(null, appointment));
        assertFalse(policy.canView(owner, null));
        assertFalse(policy.canModify(null, null));
        assertFalse(policy.canRecordTreatment(null, appointment));
    }
}
