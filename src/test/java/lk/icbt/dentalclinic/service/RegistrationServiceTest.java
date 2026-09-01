package lk.icbt.dentalclinic.service;

import lk.icbt.dentalclinic.dto.PatientRegistrationForm;
import lk.icbt.dentalclinic.exception.RegistrationException;
import lk.icbt.dentalclinic.model.identity.Patient;
import lk.icbt.dentalclinic.model.identity.Role;
import lk.icbt.dentalclinic.model.identity.RoleType;
import lk.icbt.dentalclinic.repository.PatientRepository;
import lk.icbt.dentalclinic.repository.RoleRepository;
import lk.icbt.dentalclinic.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Registration rules, isolated from the database with mocks so each rule can be checked on
 * its own.
 *
 * <p>A real {@link BCryptPasswordEncoder} is used rather than a mock: the point of several of
 * these tests is that the stored value is genuinely a hash, and a mocked encoder could not
 * demonstrate that.
 */
@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    @Mock
    private UserRepository users;

    @Mock
    private PatientRepository patients;

    @Mock
    private RoleRepository roles;

    @Mock
    private PatientNumberGenerator patientNumbers;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private RegistrationService service;

    @BeforeEach
    void setUp() {
        service = new RegistrationService(users, patients, roles, passwordEncoder, patientNumbers);
    }

    private PatientRegistrationForm validForm() {
        PatientRegistrationForm form = new PatientRegistrationForm();
        form.setUsername("k.bandara");
        form.setPassword("Str0ngPass");
        form.setConfirmPassword("Str0ngPass");
        form.setFullName("Kamal Bandara");
        form.setEmail("kamal@mail.lk");
        form.setContactNumber("0771112223");
        form.setAddress("7 Lake Road, Colombo 05");
        form.setDateOfBirth(LocalDate.of(1990, 6, 15));
        return form;
    }

    private void stubHappyPath() {
        when(users.existsByUsername(any())).thenReturn(false);
        when(users.existsByEmail(any())).thenReturn(false);
        when(patientNumbers.next()).thenReturn("PAT-000003");
        when(roles.findByCode(RoleType.PATIENT))
                .thenReturn(Optional.of(new Role(RoleType.PATIENT, "Registered patient")));
        when(patients.save(any(Patient.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    @DisplayName("a valid form creates a patient with the next patient number")
    void createsPatient() {
        stubHappyPath();

        Patient created = service.register(validForm());

        assertEquals("k.bandara", created.getUsername());
        assertEquals("Kamal Bandara", created.getFullName());
        assertEquals("PAT-000003", created.getPatientNo());
        assertEquals("7 Lake Road, Colombo 05", created.getAddress());
    }

    @Test
    @DisplayName("the password is stored as a BCrypt hash, never in the clear")
    void hashesThePassword() {
        stubHappyPath();

        Patient created = service.register(validForm());
        String stored = created.getPasswordHash();

        assertFalse(stored.contains("Str0ngPass"), "the raw password must never be stored");
        assertTrue(stored.startsWith("$2a$"), "expected a BCrypt hash but was: " + stored);
        assertTrue(passwordEncoder.matches("Str0ngPass", stored),
                "the stored hash must verify against the original password");
    }

    @Test
    @DisplayName("a new account receives the PATIENT authority and nothing else")
    void grantsOnlyPatientAuthority() {
        stubHappyPath();

        Patient created = service.register(validForm());

        assertTrue(created.hasRole(RoleType.PATIENT));
        assertFalse(created.hasRole(RoleType.ADMIN), "self-registration must never grant ADMIN");
        assertFalse(created.hasRole(RoleType.DENTIST));
        assertEquals(1, created.getRoles().size());
    }

    @Test
    @DisplayName("mismatched passwords are rejected against the confirmation field")
    void rejectsMismatchedPasswords() {
        PatientRegistrationForm form = validForm();
        form.setConfirmPassword("SomethingElse1");

        RegistrationException thrown =
                assertThrows(RegistrationException.class, () -> service.register(form));

        assertEquals("confirmPassword", thrown.getField());
        verify(patients, never()).save(any());
    }

    @Test
    @DisplayName("a username already in use is rejected")
    void rejectsDuplicateUsername() {
        when(users.existsByUsername("k.bandara")).thenReturn(true);

        RegistrationException thrown =
                assertThrows(RegistrationException.class, () -> service.register(validForm()));

        assertEquals("username", thrown.getField());
        verify(patients, never()).save(any());
    }

    @Test
    @DisplayName("an email already in use is rejected")
    void rejectsDuplicateEmail() {
        when(users.existsByUsername(any())).thenReturn(false);
        when(users.existsByEmail("kamal@mail.lk")).thenReturn(true);

        RegistrationException thrown =
                assertThrows(RegistrationException.class, () -> service.register(validForm()));

        assertEquals("email", thrown.getField());
        verify(patients, never()).save(any());
    }

    @Test
    @DisplayName("optional allergies are carried onto the record")
    void carriesAllergies() {
        stubHappyPath();
        PatientRegistrationForm form = validForm();
        form.setAllergies("Latex");

        service.register(form);

        ArgumentCaptor<Patient> saved = ArgumentCaptor.forClass(Patient.class);
        verify(patients).save(saved.capture());
        assertEquals("Latex", saved.getValue().getAllergies());
    }

    @Test
    @DisplayName("a missing PATIENT role is a configuration fault, not a user error")
    void failsLoudlyWhenReferenceDataMissing() {
        when(users.existsByUsername(any())).thenReturn(false);
        when(users.existsByEmail(any())).thenReturn(false);
        when(patientNumbers.next()).thenReturn("PAT-000004");
        when(roles.findByCode(RoleType.PATIENT)).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> service.register(validForm()));
    }
}
