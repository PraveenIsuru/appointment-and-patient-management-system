package lk.icbt.dentalclinic.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Who may reach what.
 *
 * <p>These are the tests that would catch a broken authorisation rule, which is the failure
 * mode that matters most in a system holding health records: a bug here exposes data rather
 * than merely breaking a page. Every role is checked against every protected area, including
 * the combinations that must be refused.
 *
 * <p><strong>{@code @WithUserDetails}, not {@code @WithMockUser}.</strong> The mock annotation
 * fabricates a generic Spring {@code User} as the principal, so
 * {@code @AuthenticationPrincipal ClinicUserDetails} binds null and the pages never render —
 * the test would be exercising a principal the application never actually sees.
 * {@code @WithUserDetails} loads the seeded account through the real
 * {@code ClinicUserDetailsService}, giving the same principal a real sign-in produces.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AccessControlTest {

    private static final String UDS = "clinicUserDetailsService";

    @Autowired
    private MockMvc mockMvc;

    // ------------------------------------------------------------------ anonymous ---

    @ParameterizedTest
    @ValueSource(strings = {"/", "/login", "/register", "/actuator/health"})
    @WithAnonymousUser
    @DisplayName("public pages are reachable without signing in")
    void publicPagesAreOpen(String path) throws Exception {
        mockMvc.perform(get(path)).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/admin/dashboard", "/dentist/schedule", "/patient/dashboard", "/help"})
    @WithAnonymousUser
    @DisplayName("every protected page sends an anonymous visitor to sign in")
    void protectedPagesRedirectAnonymousUsers(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    // ---------------------------------------------------------------------- admin ---

    @Test
    @WithUserDetails(value = "admin", userDetailsServiceBeanName = UDS)
    @DisplayName("an administrator reaches every area")
    void administratorReachesEverything() throws Exception {
        mockMvc.perform(get("/admin/dashboard")).andExpect(status().isOk());
        mockMvc.perform(get("/dentist/schedule")).andExpect(status().isOk());
        mockMvc.perform(get("/patient/dashboard")).andExpect(status().isOk());
        mockMvc.perform(get("/help")).andExpect(status().isOk());
    }

    // -------------------------------------------------------------------- dentist ---

    @Test
    @WithUserDetails(value = "dr.perera", userDetailsServiceBeanName = UDS)
    @DisplayName("a dentist reaches their schedule")
    void dentistReachesOwnArea() throws Exception {
        mockMvc.perform(get("/dentist/schedule")).andExpect(status().isOk());
    }

    @Test
    @WithUserDetails(value = "dr.perera", userDetailsServiceBeanName = UDS)
    @DisplayName("a dentist is refused the administration and patient areas")
    void dentistIsRefusedOtherAreas() throws Exception {
        mockMvc.perform(get("/admin/dashboard")).andExpect(status().isForbidden());
        mockMvc.perform(get("/patient/dashboard")).andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------- patient ---

    @Test
    @WithUserDetails(value = "n.fernando", userDetailsServiceBeanName = UDS)
    @DisplayName("a patient reaches their own dashboard")
    void patientReachesOwnArea() throws Exception {
        mockMvc.perform(get("/patient/dashboard")).andExpect(status().isOk());
    }

    @Test
    @WithUserDetails(value = "n.fernando", userDetailsServiceBeanName = UDS)
    @DisplayName("a patient is refused the administration and dentist areas")
    void patientIsRefusedOtherAreas() throws Exception {
        mockMvc.perform(get("/admin/dashboard")).andExpect(status().isForbidden());
        mockMvc.perform(get("/dentist/schedule")).andExpect(status().isForbidden());
    }

    @Test
    @WithUserDetails(value = "n.fernando", userDetailsServiceBeanName = UDS)
    @DisplayName("the help page is available to any signed-in user")
    void helpIsAvailableToEveryone() throws Exception {
        mockMvc.perform(get("/help")).andExpect(status().isOk());
    }

    // ----------------------------------------------------------------- 403 handling ---

    @Test
    @WithUserDetails(value = "n.fernando", userDetailsServiceBeanName = UDS)
    @DisplayName("the access-denied page renders rather than showing a stack trace")
    void accessDeniedPageRenders() throws Exception {
        mockMvc.perform(get("/error/403")).andExpect(status().isOk());
    }
}
