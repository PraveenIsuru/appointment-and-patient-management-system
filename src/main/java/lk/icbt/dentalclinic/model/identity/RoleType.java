package lk.icbt.dentalclinic.model.identity;

/**
 * The three authorities recognised by the system (assumption A2).
 *
 * <p>This enumeration constrains the permitted <em>values</em>; the {@link Role} entity
 * carries the <em>relationship</em> to users. Keeping them separate lets one user hold
 * several authorities without a schema change.
 */
public enum RoleType {

    ADMIN,
    DENTIST,
    PATIENT;

    /** Spring Security expects authorities to be prefixed with {@code ROLE_}. */
    public String getAuthority() {
        return "ROLE_" + name();
    }
}
