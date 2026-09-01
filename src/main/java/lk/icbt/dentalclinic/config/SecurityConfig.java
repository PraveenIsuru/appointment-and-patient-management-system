package lk.icbt.dentalclinic.config;

import lk.icbt.dentalclinic.security.RoleAwareAuthenticationSuccessHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.SessionFixationProtectionStrategy;

import java.time.Duration;

/**
 * Authentication and authorisation for the whole application.
 *
 * <p>{@code @EnableMethodSecurity} turns on {@code @PreAuthorize}. URL rules below are the
 * coarse filter — they protect <em>pages</em>. Method-level rules protect <em>rows</em>, which
 * is what assumption A8 actually requires: a patient may reach an appointment only when it is
 * theirs. URL rules alone would let any patient read another patient's clinical history by
 * editing the appointment number in the address bar.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /** Days a remember-me cookie stays valid, per the login sequence diagram. */
    private static final int REMEMBER_ME_DAYS = 14;

    /**
     * Key signing the remember-me cookie. Fixed so that a restart does not invalidate every
     * outstanding cookie; in a real deployment this would come from the environment.
     */
    private static final String REMEMBER_ME_KEY = "sunrise-dental-clinic-remember-me";

    private final RoleAwareAuthenticationSuccessHandler successHandler;

    public SecurityConfig(RoleAwareAuthenticationSuccessHandler successHandler) {
        this.successHandler = successHandler;
    }

    /**
     * BCrypt at the default strength of 10.
     *
     * <p>BCrypt is deliberately slow and salts every hash individually, so two users with the
     * same password do not share a hash and an offline attack on a stolen table is expensive
     * rather than instant.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        // Public surface: landing page, sign-in, self-registration, assets.
                        .requestMatchers("/", "/login", "/register",
                                         "/css/**", "/js/**", "/images/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/h2-console/**").permitAll()
                        // Role-scoped areas.
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/dentist/**").hasAnyRole("DENTIST", "ADMIN")
                        .requestMatchers("/patient/**").hasAnyRole("PATIENT", "ADMIN")
                        .anyRequest().authenticated())

                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .usernameParameter("username")
                        .passwordParameter("password")
                        .successHandler(successHandler)
                        // One generic message for every failure, so the form cannot be used
                        // to work out which usernames exist.
                        .failureUrl("/login?error")
                        .permitAll())

                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID", "remember-me")
                        .permitAll())

                .rememberMe(remember -> remember
                        .key(REMEMBER_ME_KEY)
                        .rememberMeParameter("remember-me")
                        .rememberMeCookieName("remember-me")
                        .tokenValiditySeconds((int) Duration.ofDays(REMEMBER_ME_DAYS).toSeconds())
                        .useSecureCookie(false)) // development runs over plain HTTP

                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        // A new session id is issued on sign-in, so a session id captured
                        // beforehand is useless: this is the session-fixation defence noted
                        // in the login sequence diagram.
                        .sessionAuthenticationStrategy(new SessionFixationProtectionStrategy())
                        .invalidSessionUrl("/login?expired")
                        .maximumSessions(2)
                        .expiredUrl("/login?expired"))

                .exceptionHandling(ex -> ex.accessDeniedPage("/error/403"))

                // The H2 console renders in frames and posts without a CSRF token. Both
                // exemptions are development-only; the console is disabled on the mysql
                // profile and must be removed before deployment in M8.
                .csrf(csrf -> csrf.ignoringRequestMatchers("/h2-console/**"))
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .build();
    }
}
