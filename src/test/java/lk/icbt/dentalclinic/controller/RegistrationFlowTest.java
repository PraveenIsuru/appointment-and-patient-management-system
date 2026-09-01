package lk.icbt.dentalclinic.controller;

import lk.icbt.dentalclinic.repository.PatientRepository;
import lk.icbt.dentalclinic.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Registration through the web layer, end to end.
 *
 * <p>The final test is the one that matters: registering and then immediately signing in with
 * the same credentials. Unit tests can confirm a hash was produced; only this proves the hash
 * is one Spring Security will later accept.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RegistrationFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private PatientRepository patients;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("a valid registration creates the account and redirects to sign in")
    void registersSuccessfully() throws Exception {
        mockMvc.perform(post("/register").with(csrf())
                        .param("username", "a.wickrama")
                        .param("password", "Str0ngPass")
                        .param("confirmPassword", "Str0ngPass")
                        .param("fullName", "Amali Wickramasinghe")
                        .param("email", "amali@mail.lk")
                        .param("contactNumber", "0759876543")
                        .param("address", "12 Hill Street, Kandy")
                        .param("dateOfBirth", "1992-03-08"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/login?registered=*"));

        var created = users.findByUsername("a.wickrama").orElseThrow();
        assertTrue(created.hasRole(lk.icbt.dentalclinic.model.identity.RoleType.PATIENT));
        assertFalse(created.getPasswordHash().contains("Str0ngPass"));
        assertTrue(passwordEncoder.matches("Str0ngPass", created.getPasswordHash()));
    }

    @Test
    @DisplayName("a newly registered patient can immediately sign in")
    void registeredPatientCanSignIn() throws Exception {
        mockMvc.perform(post("/register").with(csrf())
                        .param("username", "t.rathnayake")
                        .param("password", "Anoth3rPass")
                        .param("confirmPassword", "Anoth3rPass")
                        .param("fullName", "Tharindu Rathnayake")
                        .param("email", "tharindu@mail.lk")
                        .param("contactNumber", "0712223334")
                        .param("address", "5 Sea Street, Galle")
                        .param("dateOfBirth", "1985-12-01"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(formLogin("/login").user("t.rathnayake").password("Anoth3rPass"))
                .andExpect(authenticated().withRoles("PATIENT"));
    }

    @Test
    @DisplayName("invalid input redisplays the form with field errors rather than saving")
    void rejectsInvalidInput() throws Exception {
        long before = patients.count();

        mockMvc.perform(post("/register").with(csrf())
                        .param("username", "x")                   // too short
                        .param("password", "weak")                // fails complexity
                        .param("confirmPassword", "weak")
                        .param("fullName", "")                    // blank
                        .param("email", "not-an-email")
                        .param("contactNumber", "12345")          // wrong format
                        .param("address", "")
                        .param("dateOfBirth", "2099-01-01"))      // not in the past
                .andExpect(status().isOk())
                .andExpect(view().name("auth/register"))
                .andExpect(model().attributeHasFieldErrors("form",
                        "username", "password", "fullName", "email",
                        "contactNumber", "address", "dateOfBirth"));

        assertTrue(patients.count() == before, "nothing may be saved when validation fails");
    }

    @Test
    @DisplayName("a duplicate username is reported against the username field")
    void rejectsDuplicateUsername() throws Exception {
        mockMvc.perform(post("/register").with(csrf())
                        .param("username", "admin")               // already seeded
                        .param("password", "Str0ngPass")
                        .param("confirmPassword", "Str0ngPass")
                        .param("fullName", "Impostor")
                        .param("email", "impostor@mail.lk")
                        .param("contactNumber", "0771234567")
                        .param("address", "Somewhere")
                        .param("dateOfBirth", "1990-01-01"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/register"))
                .andExpect(model().attributeHasFieldErrors("form", "username"));
    }

    @Test
    @DisplayName("mismatched passwords are reported against the confirmation field")
    void rejectsMismatchedPasswords() throws Exception {
        mockMvc.perform(post("/register").with(csrf())
                        .param("username", "m.mismatch")
                        .param("password", "Str0ngPass")
                        .param("confirmPassword", "Different1")
                        .param("fullName", "Mia Mismatch")
                        .param("email", "mia@mail.lk")
                        .param("contactNumber", "0771234599")
                        .param("address", "Somewhere")
                        .param("dateOfBirth", "1990-01-01"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "confirmPassword"));
    }

    @Test
    @DisplayName("registration without a CSRF token is refused")
    void requiresCsrfToken() throws Exception {
        // The response is a redirect to /login?expired rather than a bare 403. The CSRF
        // token is held in the session, so a request arriving without one is indistinguishable
        // from a request whose session has gone: Spring Security routes MissingCsrfTokenException
        // through the invalid-session handler. What matters is that the request never reached
        // the service -- no account is created, so a forged cross-site POST cannot register
        // anyone.
        mockMvc.perform(post("/register")
                        .param("username", "c.forged")
                        .param("password", "Str0ngPass")
                        .param("confirmPassword", "Str0ngPass")
                        .param("fullName", "Carl Forged")
                        .param("email", "carl@mail.lk")
                        .param("contactNumber", "0771234500")
                        .param("address", "Elsewhere")
                        .param("dateOfBirth", "1990-01-01"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?expired"));

        assertTrue(users.findByUsername("c.forged").isEmpty(),
                "a request without a CSRF token must not create an account");
    }
}
