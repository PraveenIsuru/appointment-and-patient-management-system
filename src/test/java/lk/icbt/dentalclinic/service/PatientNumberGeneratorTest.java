package lk.icbt.dentalclinic.service;

import lk.icbt.dentalclinic.repository.PatientRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/** Patient number sequencing and its behaviour on unexpected data. */
@ExtendWith(MockitoExtension.class)
class PatientNumberGeneratorTest {

    @Mock
    private PatientRepository patients;

    private PatientNumberGenerator generator() {
        return new PatientNumberGenerator(patients);
    }

    @Test
    @DisplayName("the first patient is PAT-000001")
    void startsAtOne() {
        when(patients.findHighestPatientNo()).thenReturn(Optional.empty());

        assertEquals("PAT-000001", generator().next());
    }

    @Test
    @DisplayName("the next number follows the highest already issued")
    void continuesFromHighest() {
        when(patients.findHighestPatientNo()).thenReturn(Optional.of("PAT-000002"));

        assertEquals("PAT-000003", generator().next());
    }

    @Test
    @DisplayName("padding holds as the sequence grows")
    void padsConsistently() {
        when(patients.findHighestPatientNo()).thenReturn(Optional.of("PAT-000099"));
        assertEquals("PAT-000100", generator().next());
    }

    @Test
    @DisplayName("the sequence keeps working past six digits rather than truncating")
    void survivesOverflowOfThePadding() {
        when(patients.findHighestPatientNo()).thenReturn(Optional.of("PAT-999999"));

        assertEquals("PAT-1000000", generator().next());
    }

    @Test
    @DisplayName("a hand-entered number that does not fit the pattern does not stop registration")
    void toleratesMalformedExistingNumber() {
        when(patients.findHighestPatientNo()).thenReturn(Optional.of("LEGACY-PATIENT"));

        assertEquals("PAT-000001", generator().next());
    }
}
