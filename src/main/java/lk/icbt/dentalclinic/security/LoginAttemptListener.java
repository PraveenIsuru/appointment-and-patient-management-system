package lk.icbt.dentalclinic.security;

import lk.icbt.dentalclinic.model.identity.User;
import lk.icbt.dentalclinic.repository.UserRepository;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Maintains the failed sign-in counter on {@code User}.
 *
 * <p><strong>Deviation from the login sequence diagram, and why.</strong> The diagram shows
 * {@code DaoAuthenticationProvider} calling {@code recordFailedLogin()} directly. Spring
 * Security's provider is final and does no such thing, and subclassing the framework to make
 * it match the picture would be the wrong trade. Listening for the authentication events it
 * publishes achieves exactly the same effect at the same moment, without modifying the
 * framework. The behaviour the diagram specifies is preserved; only the mechanism differs.
 *
 * <p>A failure for an unknown username is ignored rather than logged, since there is no row
 * to update and recording it elsewhere would leak which usernames exist.
 */
@Component
public class LoginAttemptListener {

    private final UserRepository users;

    public LoginAttemptListener(UserRepository users) {
        this.users = users;
    }

    @EventListener
    @Transactional
    public void onFailure(AuthenticationFailureBadCredentialsEvent event) {
        String username = String.valueOf(event.getAuthentication().getName());
        users.findByUsername(username).ifPresent(user -> {
            user.recordFailedLogin();
            users.save(user);
        });
    }

    @EventListener
    @Transactional
    public void onSuccess(AuthenticationSuccessEvent event) {
        Object principal = event.getAuthentication().getPrincipal();
        if (principal instanceof ClinicUserDetails details) {
            users.findByUsername(details.getUsername()).ifPresent(this::clearCounter);
        }
    }

    private void clearCounter(User user) {
        if (user.getFailedLoginAttempts() > 0) {
            user.resetFailedLogins();
            users.save(user);
        }
    }
}
