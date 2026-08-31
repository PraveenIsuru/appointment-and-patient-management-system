package lk.icbt.dentalclinic.repository;

import lk.icbt.dentalclinic.model.identity.User;
import org.springframework.data.jpa.repository.JpaRepository;
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

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}
