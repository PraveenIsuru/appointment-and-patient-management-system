package lk.icbt.dentalclinic.security;

import lk.icbt.dentalclinic.model.identity.Role;
import lk.icbt.dentalclinic.model.identity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Adapts a domain {@link User} to Spring Security's {@link UserDetails}.
 *
 * <p>An Adapter, and a deliberate boundary: the domain model does not implement a framework
 * interface, so Spring Security can be replaced without touching the entities. It also keeps
 * the authenticated principal useful — controllers can ask for the domain user rather than
 * re-loading it by username on every request.
 */
public class ClinicUserDetails implements UserDetails {

    private final transient User user;

    public ClinicUserDetails(User user) {
        this.user = user;
    }

    /** The domain object behind the principal. */
    public User getUser() {
        return user;
    }

    public Long getId() {
        return user.getId();
    }

    public String getFullName() {
        return user.getFullName();
    }

    public String getDisplayRole() {
        return user.getDisplayRole();
    }

    /** Where this user lands after signing in — polymorphic, per {@link User}. */
    public String getDashboardUrl() {
        return user.getDashboardUrl();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return user.getRoles().stream()
                .map(Role::getAuthority)
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }

    /**
     * A deactivated account cannot sign in.
     *
     * <p>The other three {@code UserDetails} flags default to true: this system expires
     * neither accounts nor credentials, so returning anything else would be pretending to
     * enforce a policy that does not exist.
     */
    @Override
    public boolean isEnabled() {
        return user.isActive();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    /** Authorities as plain strings, for logging and tests. */
    public List<String> authorityNames() {
        return getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }
}
