package lk.icbt.dentalclinic.security;

import lk.icbt.dentalclinic.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads a user for authentication, as {@code uds:AppUserDetailsService} in the login
 * sequence diagram.
 *
 * <p>The exception message is deliberately uninformative. Spring Security converts a
 * {@link UsernameNotFoundException} into the same {@code BadCredentialsException} that a
 * wrong password produces, so the login form cannot be used to discover which usernames
 * exist. Distinguishing "no such user" from "wrong password" would turn the form into an
 * account enumeration oracle.
 */
@Service
public class ClinicUserDetailsService implements UserDetailsService {

    private final UserRepository users;

    public ClinicUserDetailsService(UserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        return users.findByUsernameWithRoles(username)
                .map(ClinicUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("Bad credentials"));
    }
}
