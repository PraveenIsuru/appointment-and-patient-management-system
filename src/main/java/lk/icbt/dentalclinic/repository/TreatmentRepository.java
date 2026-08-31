package lk.icbt.dentalclinic.repository;

import lk.icbt.dentalclinic.model.scheduling.Treatment;
import lk.icbt.dentalclinic.model.scheduling.TreatmentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Data access for the treatment catalogue. */
@Repository
public interface TreatmentRepository extends JpaRepository<Treatment, Long> {

    Optional<Treatment> findByCode(String code);

    /** Withdrawn treatments stay in the table for historical bills, but are not bookable. */
    List<Treatment> findByActiveTrueOrderByNameAsc();

    List<Treatment> findByType(TreatmentType type);
}
