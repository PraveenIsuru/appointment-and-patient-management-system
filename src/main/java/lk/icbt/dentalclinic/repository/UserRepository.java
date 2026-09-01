package lk.icbt.dentalclinic.repository;

import lk.icbt.dentalclinic.model.identity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Data access across every kind of user.
 *
 * <p>Typed to the abstract superclass, so a lookup by username finds a patient, a dentist or
 * an administrator without the caller knowing which. Spring Security depends on exactly this
 * in M3.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    /**
     * Authentication lookup, named after the call in the login sequence diagram.
     *
     * <p>Fetch-joins the authorities so signing in costs one query rather than two, and so the
     * roles are already loaded when the persistence context closes.
     */
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.roles WHERE u.username = :username")
    Optional<User> findByUsernameWithRoles(@Param("username") String username);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}
