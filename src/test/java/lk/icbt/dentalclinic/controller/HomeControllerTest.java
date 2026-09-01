package lk.icbt.dentalclinic.controller;

import lk.icbt.dentalclinic.config.SecurityConfig;
import lk.icbt.dentalclinic.security.RoleAwareAuthenticationSuccessHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Verifies that the public landing page is reachable without authentication.
 *
 * <p>Also acts as the first regression guard on the security rules: if a future change
 * accidentally locks down the landing page, this test fails.
 */
@WebMvcTest(HomeController.class)
@Import({SecurityConfig.class, RoleAwareAuthenticationSuccessHandler.class})
class HomeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("landing page is public and renders the index view")
    void landingPageIsPubliclyAccessible() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attribute("clinicName", "Sunrise Dental Clinic"));
    }
}
