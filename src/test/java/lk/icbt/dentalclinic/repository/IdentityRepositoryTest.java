package lk.icbt.dentalclinic.repository;

import lk.icbt.dentalclinic.model.identity.Administrator;
import lk.icbt.dentalclinic.model.identity.Dentist;
import lk.icbt.dentalclinic.model.identity.Patient;
import lk.icbt.dentalclinic.model.identity.RoleType;
import lk.icbt.dentalclinic.model.identity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JOINED inheritance and the role model (assumptions A2 and A4).
 *
 * <p>The point under test is that a lookup on the abstract superclass returns the correct
 * concrete subclass, since Spring Security in M3 will load users without knowing which kind
 * of person it is about to authenticate.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class IdentityRepositoryTest {

    @Autowired
    private UserRepository users;

    @Autowired
    private PatientRepository patients;

    @Autowired
    private DentistRepository dentists;

    @Autowired
    private AdministratorRepository administrators;

    @Autowired
    private RoleRepository roles;

    @Test
    @DisplayName("all three authorities are seeded")
    void seedsEveryRole() {
        assertEquals(3, roles.count());
        for (RoleType type : RoleType.values()) {
            assertTrue(roles.findByCode(type).isPresent(), type + " must be seeded");
        }
    }

    @Test
    @DisplayName("RoleType carries the ROLE_ prefix Spring Security expects")
    void exposesSpringSecurityAuthority() {
        assertEquals("ROLE_ADMIN", roles.findByCode(RoleType.ADMIN).orElseThrow().getAuthority());
    }

    @Test
    @DisplayName("a lookup on the abstract superclass returns the concrete subclass")
    void resolvesSubclassThroughJoinedInheritance() {
        assertInstanceOf(Administrator.class, users.findByUsername("admin").orElseThrow());
        assertInstanceOf(Dentist.class, users.findByUsername("dr.perera").orElseThrow());
        assertInstanceOf(Patient.class, users.findByUsername("n.fernando").orElseThrow());
    }

    @Test
    @DisplayName("each seeded user holds exactly the authority for their kind")
    void assignsCorrectAuthorities() {
        assertTrue(users.findByUsername("admin").orElseThrow().hasRole(RoleType.ADMIN));
        assertTrue(users.findByUsername("dr.silva").orElseThrow().hasRole(RoleType.DENTIST));
        assertTrue(users.findByUsername("s.jayawardena").orElseThrow().hasRole(RoleType.PATIENT));

        assertFalse(users.findByUsername("n.fernando").orElseThrow().hasRole(RoleType.ADMIN),
                "a patient must not hold administrative authority");
    }

    @Test
    @DisplayName("role-specific columns survive the join")
    void loadsRoleSpecificColumns() {
        Dentist perera = dentists.findByDentistNo("DEN-001").orElseThrow();
        assertEquals("Restorative Dentistry", perera.getSpecialisation());
        assertEquals(0, new java.math.BigDecimal("2500.00")
                .compareTo(perera.getConsultationFee()));
        assertEquals(LocalTime.of(8, 0), perera.getSessionStart());

        Patient fernando = patients.findByPatientNo("PAT-000001").orElseThrow();
        assertEquals("42/3 Galle Road, Colombo 03", fernando.getAddress());
        assertEquals("Penicillin", fernando.getAllergies());

        Administrator admin = administrators.findByStaffNo("STF-001").orElseThrow();
        assertEquals("Clinic Manager", admin.getDesignation());
    }

    @Test
    @DisplayName("dentist session hours drive availability, with an exclusive end")
    void checksSessionHours() {
        Dentist perera = dentists.findByDentistNo("DEN-001").orElseThrow();

        assertTrue(perera.worksAt(LocalTime.of(8, 0)), "session start is inclusive");
        assertTrue(perera.worksAt(LocalTime.of(13, 30)));
        assertFalse(perera.worksAt(LocalTime.of(14, 0)),
                "session end is exclusive - a 14:00 slot runs past the session");
        assertFalse(perera.worksAt(LocalTime.of(7, 59)));
    }

    @Test
    @DisplayName("only active dentists are offered for booking")
    void listsBookableDentists() {
        assertEquals(2, dentists.findByActiveTrueOrderByFullNameAsc().size());
    }

    @Test
    @DisplayName("failed sign-in attempts are counted and can be cleared")
    void tracksFailedLogins() {
        User user = users.findByUsername("n.fernando").orElseThrow();

        user.recordFailedLogin();
        user.recordFailedLogin();
        assertEquals(2, users.saveAndFlush(user).getFailedLoginAttempts());

        user.resetFailedLogins();
        assertEquals(0, users.saveAndFlush(user).getFailedLoginAttempts());
    }

    @Test
    @DisplayName("username and email uniqueness is enforced")
    void enforcesUniqueness() {
        assertTrue(users.existsByUsername("admin"));
        assertTrue(users.existsByEmail("admin@sunrisedental.lk"));
        assertFalse(users.existsByUsername("nobody"));
    }
}
