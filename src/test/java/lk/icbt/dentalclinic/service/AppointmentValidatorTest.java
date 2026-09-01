package lk.icbt.dentalclinic.service;

import lk.icbt.dentalclinic.exception.InvalidAppointmentException;
import lk.icbt.dentalclinic.model.identity.Dentist;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Clinic booking rules (UC13, assumption A10).
 *
 * <p>The clock is fixed at 2026-09-01 10:00 Asia/Colombo. Freezing time is what makes
 * "cannot be in the past" testable at all: with a live clock, a test asserting that 09:00
 * today is rejected would pass in the afternoon and fail in the morning.
 */
class AppointmentValidatorTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Colombo");
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 1);
    private static final LocalDate TOMORROW = TODAY.plusDays(1);

    private final Clock fixedClock = Clock.fixed(
            LocalDate.of(2026, 9, 1).atTime(10, 0).atZone(ZONE).toInstant(), ZONE);

    private final AppointmentValidator validator = new AppointmentValidator(fixedClock);

    private Dentist dentist(LocalTime start, LocalTime end) {
        return new Dentist("dr.test", "hash", "t@clinic.lk", "Dr Test", "0770000000",
                "DEN-900", "General", "SLMC-D-90000", new BigDecimal("2000.00"), start, end);
    }

    // ------------------------------------------------------------- clinic hours ---

    @ParameterizedTest
    @ValueSource(strings = {"08:00", "12:30", "19:00", "19:30"})
    @DisplayName("times inside consulting hours are accepted")
    void acceptsTimesWithinClinicHours(String time) {
        assertDoesNotThrow(() -> validator.validate(TOMORROW, LocalTime.parse(time)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"07:30", "06:00", "20:00", "20:30", "23:30"})
    @DisplayName("times outside consulting hours are rejected")
    void rejectsTimesOutsideClinicHours(String time) {
        InvalidAppointmentException thrown = assertThrows(InvalidAppointmentException.class,
                () -> validator.validate(TOMORROW, LocalTime.parse(time)));
        assertEquals("appointmentTime", thrown.getField());
    }

    @Test
    @DisplayName("20:00 is refused because the slot would run past closing")
    void closingTimeIsExclusive() {
        assertThrows(InvalidAppointmentException.class,
                () -> validator.validate(TOMORROW, LocalTime.of(20, 0)));
        assertDoesNotThrow(() -> validator.validate(TOMORROW, LocalTime.of(19, 30)));
    }

    // ----------------------------------------------------------- slot boundaries ---

    @ParameterizedTest
    @ValueSource(strings = {"09:15", "10:01", "11:45", "14:20"})
    @DisplayName("times off the half-hour boundary are rejected")
    void rejectsTimesOffSlotBoundary(String time) {
        InvalidAppointmentException thrown = assertThrows(InvalidAppointmentException.class,
                () -> validator.validate(TOMORROW, LocalTime.parse(time)));
        assertTrue(thrown.getMessage().contains("hour or the half hour"));
    }

    @Test
    @DisplayName("the clinic offers 24 slots a day")
    void enumeratesEverySlot() {
        var slots = validator.allSlots();
        assertEquals(24, slots.size());
        assertEquals(LocalTime.of(8, 0), slots.get(0));
        assertEquals(LocalTime.of(19, 30), slots.get(slots.size() - 1));
    }

    // -------------------------------------------------------------------- past ---

    @Test
    @DisplayName("a date in the past is rejected")
    void rejectsPastDate() {
        InvalidAppointmentException thrown = assertThrows(InvalidAppointmentException.class,
                () -> validator.validate(TODAY.minusDays(1), LocalTime.of(10, 0)));
        assertEquals("appointmentDate", thrown.getField());
        assertTrue(thrown.getMessage().contains("cannot be made in the past"));
    }

    @Test
    @DisplayName("earlier today is rejected, later today is accepted")
    void discriminatesWithinToday() {
        assertThrows(InvalidAppointmentException.class,
                () -> validator.validate(TODAY, LocalTime.of(9, 0)));
        assertDoesNotThrow(() -> validator.validate(TODAY, LocalTime.of(14, 0)));
    }

    // ------------------------------------------------------------ missing values ---

    @Test
    @DisplayName("a missing date or time is reported against its own field")
    void rejectsMissingValues() {
        assertEquals("appointmentDate",
                assertThrows(InvalidAppointmentException.class,
                        () -> validator.validate(null, LocalTime.of(10, 0))).getField());
        assertEquals("appointmentTime",
                assertThrows(InvalidAppointmentException.class,
                        () -> validator.validate(TOMORROW, null)).getField());
    }

    // ---------------------------------------------------------- dentist sessions ---

    @ParameterizedTest
    @CsvSource({"08:00,true", "13:30,true", "14:00,false", "16:00,false", "07:30,false"})
    @DisplayName("a time must fall inside the chosen dentist's own session")
    void checksDentistSession(String time, boolean acceptable) {
        Dentist morning = dentist(LocalTime.of(8, 0), LocalTime.of(14, 0));
        LocalTime at = LocalTime.parse(time);

        if (acceptable) {
            assertDoesNotThrow(() -> validator.validateForDentist(TOMORROW, at, morning));
        } else {
            assertThrows(InvalidAppointmentException.class,
                    () -> validator.validateForDentist(TOMORROW, at, morning));
        }
    }

    @Test
    @DisplayName("a dentist who no longer takes appointments is rejected")
    void rejectsInactiveDentist() {
        Dentist retired = dentist(LocalTime.of(8, 0), LocalTime.of(14, 0));
        retired.deactivate();

        InvalidAppointmentException thrown = assertThrows(InvalidAppointmentException.class,
                () -> validator.validateForDentist(TOMORROW, LocalTime.of(10, 0), retired));
        assertEquals("dentistId", thrown.getField());
    }

    @Test
    @DisplayName("no dentist chosen is reported against the dentist field")
    void rejectsMissingDentist() {
        assertEquals("dentistId",
                assertThrows(InvalidAppointmentException.class,
                        () -> validator.validateForDentist(TOMORROW, LocalTime.of(10, 0), null))
                        .getField());
    }

    // ------------------------------------------------------------- isBookableTime ---

    @Test
    @DisplayName("isBookableTime answers without throwing")
    void reportsBookabilityQuietly() {
        assertTrue(validator.isBookableTime(LocalTime.of(9, 30)));
        assertTrue(!validator.isBookableTime(LocalTime.of(9, 15)));
        assertTrue(!validator.isBookableTime(LocalTime.of(21, 0)));
        assertTrue(!validator.isBookableTime(null));
    }

    @Test
    @DisplayName("the fixed clock really is fixed, so these tests cannot drift")
    void clockIsFixed() {
        assertEquals(Instant.parse("2026-09-01T04:30:00Z"), fixedClock.instant());
    }
}
