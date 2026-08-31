package lk.icbt.dentalclinic.model.identity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Abstract superclass for every person who can sign in.
 *
 * <p><strong>Assumption A4.</strong> {@link InheritanceType#JOINED} is chosen over
 * {@code SINGLE_TABLE} because the role-specific columns — a dentist's licence number, a
 * patient's date of birth — would otherwise be nullable for two thirds of every row, which
 * stops {@code NOT NULL} from doing any work. The cost is a join on every user lookup;
 * at clinic scale that is irrelevant and the integrity gain is not.
 *
 * <p>Never instantiated directly: a user is always a {@link Patient}, a {@link Dentist} or
 * an {@link Administrator}.
 */
@Entity
@Table(name = "users")
@Inheritance(strategy = InheritanceType.JOINED)
public abstract class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "email", nullable = false, unique = true, length = 120)
    private String email;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(name = "contact_number", length = 20)
    private String contactNumber;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts = 0;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * A user may hold more than one authority — a dentist who also administers the system.
     * EAGER because authorities are needed on every authenticated request, and the set is
     * never larger than three.
     */
    @ManyToMany(fetch = FetchType.EAGER, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new LinkedHashSet<>();

    protected User() {
        // required by JPA
    }

    protected User(String username, String passwordHash, String email,
                   String fullName, String contactNumber) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.email = email;
        this.fullName = fullName;
        this.contactNumber = contactNumber;
    }

    // ---------------------------------------------------------------- behaviour ---

    /** Whether this user holds the given authority. */
    public boolean hasRole(RoleType code) {
        return roles.stream().anyMatch(role -> role.getCode() == code);
    }

    public void addRole(Role role) {
        roles.add(role);
    }

    /**
     * Records an unsuccessful sign-in attempt.
     *
     * <p>The counter is kept here rather than in the security layer so that lockout policy
     * has a single home; M3 decides what threshold acts on it.
     */
    public void recordFailedLogin() {
        this.failedLoginAttempts++;
    }

    /** Clears the failure counter after a successful sign-in. */
    public void resetFailedLogins() {
        this.failedLoginAttempts = 0;
    }

    public void deactivate() {
        this.active = false;
    }

    /** Landing page for this user's role, used after sign-in. */
    public abstract String getDashboardUrl();

    /** Human-readable role name for display in the UI. */
    public abstract String getDisplayRole();

    // ------------------------------------------------------------------ accessors ---

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    protected void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getContactNumber() {
        return contactNumber;
    }

    public void setContactNumber(String contactNumber) {
        this.contactNumber = contactNumber;
    }

    public boolean isActive() {
        return active;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public Set<Role> getRoles() {
        return Set.copyOf(roles);
    }

    // -------------------------------------------------------------------- identity ---

    /**
     * Identity is the persistent key only. Two unsaved users are never equal, which keeps
     * entities safe to put in a {@link Set} before they are flushed.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof User user)) {
            return false;
        }
        return id != null && id.equals(user.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getClass().getSimpleName());
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{id=" + id + ", username='" + username + "'}";
    }
}
