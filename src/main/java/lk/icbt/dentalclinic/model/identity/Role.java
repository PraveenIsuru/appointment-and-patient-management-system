package lk.icbt.dentalclinic.model.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;

/**
 * A grantable authority.
 *
 * <p>Modelled as an entity rather than as an enum field on {@link User} so that a user can
 * hold several authorities without a schema change, and so the set maps directly onto
 * Spring Security's {@code GrantedAuthority}.
 */
@Entity
@Table(name = "roles")
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Stored as a string, not an ordinal: reordering the enum must not rewrite the data. */
    @Enumerated(EnumType.STRING)
    @Column(name = "code", nullable = false, unique = true, length = 20)
    private RoleType code;

    @Column(name = "description", nullable = false, length = 100)
    private String description;

    protected Role() {
        // required by JPA
    }

    public Role(RoleType code, String description) {
        this.code = code;
        this.description = description;
    }

    /** The authority string Spring Security expects, for example {@code ROLE_ADMIN}. */
    public String getAuthority() {
        return code.getAuthority();
    }

    public Long getId() {
        return id;
    }

    public RoleType getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Role role)) {
            return false;
        }
        return code == role.code;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(code);
    }

    @Override
    public String toString() {
        return "Role{" + code + "}";
    }
}
