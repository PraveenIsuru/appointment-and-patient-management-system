package lk.icbt.dentalclinic.service;

import lk.icbt.dentalclinic.dto.PatientRegistrationForm;
import lk.icbt.dentalclinic.exception.RegistrationException;
import lk.icbt.dentalclinic.model.identity.Patient;
import lk.icbt.dentalclinic.model.identity.Role;
import lk.icbt.dentalclinic.model.identity.RoleType;
import lk.icbt.dentalclinic.repository.PatientRepository;
import lk.icbt.dentalclinic.repository.RoleRepository;
import lk.icbt.dentalclinic.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Patient self-registration (assumption A2).
 *
 * <p>The brief restricts the system to authorised staff. Letting patients register themselves
 * is a documented extension, and it attacks two of the four problems the clinic reports —
 * long waiting times and lost records — by taking the receptionist off the critical path.
 *
 * <p>A new account always receives exactly the {@code PATIENT} authority. Nothing in the
 * request influences which role is granted: privilege comes from the code path taken, never
 * from submitted data, so no crafted form can create an administrator.
 */
@Service
public class RegistrationService {

    private final UserRepository users;
    private final PatientRepository patients;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final PatientNumberGenerator patientNumbers;

    public RegistrationService(UserRepository users,
                               PatientRepository patients,
                               RoleRepository roles,
                               PasswordEncoder passwordEncoder,
                               PatientNumberGenerator patientNumbers) {
        this.users = users;
        this.patients = patients;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.patientNumbers = patientNumbers;
    }

    /**
     * Creates a patient account.
     *
     * @throws RegistrationException when the username or email is already in use, or the two
     *                               password fields disagree
     */
    @Transactional
    public Patient register(PatientRegistrationForm form) {
        if (!form.passwordsMatch()) {
            throw new RegistrationException("confirmPassword", "The two passwords do not match");
        }
        if (users.existsByUsername(form.getUsername())) {
            throw new RegistrationException("username", "That username is already taken");
        }
        if (users.existsByEmail(form.getEmail())) {
            throw new RegistrationException("email", "An account already exists for that email address");
        }

        Patient patient = new Patient(
                form.getUsername(),
                // Hashed here and nowhere else: a raw password never reaches the entity, so
                // it cannot be persisted by accident.
                passwordEncoder.encode(form.getPassword()),
                form.getEmail(),
                form.getFullName(),
                form.getContactNumber(),
                patientNumbers.next(),
                form.getAddress(),
                form.getDateOfBirth());

        patient.setAllergies(form.getAllergies());
        patient.addRole(patientRole());

        return patients.save(patient);
    }

    private Role patientRole() {
        return roles.findByCode(RoleType.PATIENT).orElseThrow(() -> new IllegalStateException(
                "The PATIENT role is missing. Reference data from migration V2 has not been applied."));
    }
}
