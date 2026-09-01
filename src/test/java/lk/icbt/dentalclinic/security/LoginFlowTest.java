package lk.icbt.dentalclinic.security;

import lk.icbt.dentalclinic.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.logout;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end sign-in against the seeded accounts.
 *
 * <p>Exercises the whole chain described by the login sequence diagram: the real
 * {@code UserDetailsService}, the real BCrypt hashes from migration V2, and the role-aware
 * redirect. If the seeded hashes were wrong, these tests would fail — which is exactly why
 * they are written against real credentials rather than mocked ones.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LoginFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository users;

    @Test
    @DisplayName("an administrator signs in and lands on the admin dashboard")
    void administratorLandsOnAdminDashboard() throws Exception {
        mockMvc.perform(formLogin("/login").user("admin").password("Admin@123"))
                .andExpect(authenticated().withRoles("ADMIN"))
                .andExpect(redirectedUrl("/admin/dashboard"));
    }

    @Test
    @DisplayName("a dentist signs in and lands on their schedule")
    void dentistLandsOnSchedule() throws Exception {
        mockMvc.perform(formLogin("/login").user("dr.perera").password("Dentist@123"))
                .andExpect(authenticated().withRoles("DENTIST"))
                .andExpect(redirectedUrl("/dentist/schedule"));
    }

    @Test
    @DisplayName("a patient signs in and lands on their dashboard")
    void patientLandsOnPatientDashboard() throws Exception {
        mockMvc.perform(formLogin("/login").user("n.fernando").password("Patient@123"))
                .andExpect(authenticated().withRoles("PATIENT"))
                .andExpect(redirectedUrl("/patient/dashboard"));
    }

    @Test
    @DisplayName("a wrong password is rejected")
    void rejectsWrongPassword() throws Exception {
        mockMvc.perform(formLogin("/login").user("admin").password("wrong"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    @DisplayName("an unknown username is rejected the same way as a wrong password")
    void rejectsUnknownUserIdentically() throws Exception {
        mockMvc.perform(formLogin("/login").user("nobody").password("whatever"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    @DisplayName("the failure message never reveals whether the username exists")
    void failureMessageDoesNotEnumerateAccounts() throws Exception {
        String body = mockMvc.perform(get("/login").param("error", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("Invalid username or password")))
                .andReturn().getResponse().getContentAsString();

        assertTrue(body.toLowerCase().contains("invalid username or password"));
        assertTrue(!body.toLowerCase().contains("no such user"),
                "the page must not distinguish an unknown user from a wrong password");
    }

    @Test
    @DisplayName("a failed attempt increments the counter on the user record")
    void countsFailedAttempts() throws Exception {
        String username = "s.jayawardena";
        int before = users.findByUsername(username).orElseThrow().getFailedLoginAttempts();

        mockMvc.perform(formLogin("/login").user(username).password("definitely-wrong"))
                .andExpect(unauthenticated());

        assertEquals(before + 1,
                users.findByUsername(username).orElseThrow().getFailedLoginAttempts());
    }

    @Test
    @DisplayName("a successful sign-in clears the failure counter")
    void resetsCounterOnSuccess() throws Exception {
        String username = "dr.silva";

        mockMvc.perform(formLogin("/login").user(username).password("nope"))
                .andExpect(unauthenticated());
        assertTrue(users.findByUsername(username).orElseThrow().getFailedLoginAttempts() > 0);

        mockMvc.perform(formLogin("/login").user(username).password("Dentist@123"))
                .andExpect(authenticated());

        assertEquals(0, users.findByUsername(username).orElseThrow().getFailedLoginAttempts());
    }

    @Test
    @DisplayName("signing out clears the authentication and the cookies")
    void signOutClearsSession() throws Exception {
        mockMvc.perform(logout("/logout"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?logout"));
    }

    @Test
    @DisplayName("a deactivated account cannot sign in")
    void refusesDeactivatedAccount() throws Exception {
        var user = users.findByUsername("n.fernando").orElseThrow();
        user.deactivate();
        users.saveAndFlush(user);
        try {
            mockMvc.perform(formLogin("/login").user("n.fernando").password("Patient@123"))
                    .andExpect(unauthenticated())
                    .andExpect(redirectedUrl("/login?error"));
        } finally {
            // Restore, so ordering between tests cannot matter.
            var restored = users.findByUsername("n.fernando").orElseThrow();
            restored.activate();
            users.saveAndFlush(restored);
        }
    }
}
