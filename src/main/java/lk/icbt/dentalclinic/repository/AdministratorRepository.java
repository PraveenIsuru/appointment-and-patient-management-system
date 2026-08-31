package lk.icbt.dentalclinic.repository;

import lk.icbt.dentalclinic.model.identity.Administrator;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** Data access for administrative staff. */
@Repository
public interface AdministratorRepository extends JpaRepository<Administrator, Long> {

    Optional<Administrator> findByStaffNo(String staffNo);

    Optional<Administrator> findByUsername(String username);
}
