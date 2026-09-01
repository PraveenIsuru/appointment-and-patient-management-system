package lk.icbt.dentalclinic.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Sends each user to the landing page for their role after signing in.
 *
 * <p>The destination comes from {@code User.getDashboardUrl()} — an abstract method
 * implemented by each subclass — so this handler contains no role switch at all. Adding a
 * fourth kind of user would not change this class.
 */
@Component
public class RoleAwareAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private static final String FALLBACK_URL = "/";

    private final RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        String target = FALLBACK_URL;
        if (authentication.getPrincipal() instanceof ClinicUserDetails principal) {
            target = principal.getDashboardUrl();
        }
        redirectStrategy.sendRedirect(request, response, target);
    }
}
