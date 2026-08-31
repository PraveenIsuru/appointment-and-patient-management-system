package lk.icbt.dentalclinic.repository;

import lk.icbt.dentalclinic.model.identity.Dentist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Data access for dentists. */
@Repository
public interface DentistRepository extends JpaRepository<Dentist, Long> {

    Optional<Dentist> findByDentistNo(String dentistNo);

    Optional<Dentist> findByUsername(String username);

    /** Only active dentists are offered when booking. */
    List<Dentist> findByActiveTrueOrderByFullNameAsc();
}
