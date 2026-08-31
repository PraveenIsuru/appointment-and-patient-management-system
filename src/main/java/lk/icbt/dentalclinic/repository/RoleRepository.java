package lk.icbt.dentalclinic.repository;

import lk.icbt.dentalclinic.model.identity.Role;
import lk.icbt.dentalclinic.model.identity.RoleType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** Data access for grantable authorities. */
@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByCode(RoleType code);
}
