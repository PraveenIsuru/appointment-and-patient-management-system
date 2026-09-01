package lk.icbt.dentalclinic.service;

import lk.icbt.dentalclinic.dto.AppointmentBookingForm;
import lk.icbt.dentalclinic.exception.AppointmentNotFoundException;
import lk.icbt.dentalclinic.exception.InvalidAppointmentException;
import lk.icbt.dentalclinic.exception.SlotUnavailableException;
import lk.icbt.dentalclinic.model.identity.Dentist;
import lk.icbt.dentalclinic.model.identity.Patient;
import lk.icbt.dentalclinic.model.identity.RoleType;
import lk.icbt.dentalclinic.model.identity.User;
import lk.icbt.dentalclinic.model.scheduling.Appointment;
import lk.icbt.dentalclinic.model.scheduling.AppointmentStatus;
import lk.icbt.dentalclinic.model.scheduling.Treatment;
import lk.icbt.dentalclinic.repository.AppointmentRepository;
import lk.icbt.dentalclinic.repository.DentistRepository;
import lk.icbt.dentalclinic.repository.PatientRepository;
import lk.icbt.dentalclinic.repository.TreatmentRepository;
import lk.icbt.dentalclinic.security.AppointmentAccessPolicy;
import lk.icbt.dentalclinic.service.notification.AppointmentBookedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Appointment booking, search and lifecycle — brief requirements 2 and 3.
 *
 * <p>Realises use case UC10 and the sequence diagram
 * {@code sequence-book-appointment.puml}. The business logic tier: it orchestrates the
 * repositories, owns the transaction boundaries, and is the only place that decides whether a
 * booking may go ahead.
 */
@Service
public class AppointmentService {

    private static final int MAX_ALTERNATIVES = 3;

    private final AppointmentRepository appointments;
    private final PatientRepository patients;
    private final DentistRepository dentists;
    private final TreatmentRepository treatments;
    private final AppointmentValidator validator;
    private final AppointmentNumberGenerator numberGenerator;
    private final AppointmentAccessPolicy accessPolicy;
    private final ApplicationEventPublisher events;

    public AppointmentService(AppointmentRepository appointments,
                              PatientRepository patients,
                              DentistRepository dentists,
                              TreatmentRepository treatments,
                              AppointmentValidator validator,
                              AppointmentNumberGenerator numberGenerator,
                              AppointmentAccessPolicy accessPolicy,
                              ApplicationEventPublisher events) {
        this.appointments = appointments;
        this.patients = patients;
        this.dentists = dentists;
        this.treatments = treatments;
        this.validator = validator;
        this.numberGenerator = numberGenerator;
        this.accessPolicy = accessPolicy;
        this.events = events;
    }

    // ------------------------------------------------------------------- booking ---

    /**
     * Books an appointment (UC10).
     *
     * @param form         the submitted booking
     * @param currentUser  who is booking; a patient may book only for themselves
     * @throws InvalidAppointmentException when a clinic rule is broken
     * @throws SlotUnavailableException    when the dentist is already booked at that time
     */
    @Transactional
    public Appointment book(AppointmentBookingForm form, User currentUser) {
        Patient patient = resolvePatient(form, currentUser);
        Dentist dentist = dentists.findById(form.getDentistId())
                .orElseThrow(() -> new InvalidAppointmentException(
                        "dentistId", "That dentist is not on the clinic's list"));
        Treatment treatment = treatments.findById(form.getTreatmentId())
                .orElseThrow(() -> new InvalidAppointmentException(
                        "treatmentId", "That treatment is not offered"));
        if (!treatment.isActive()) {
            throw new InvalidAppointmentException(
                    "treatmentId", treatment.getName() + " is no longer offered");
        }

        // UC13 — clinic rules, then the dentist's own session hours.
        validator.validateForDentist(form.getAppointmentDate(), form.getAppointmentTime(), dentist);

        // UC11 — availability. This produces the helpful message; the unique index below is
        // what actually makes the clash impossible.
        assertSlotFree(dentist, form.getAppointmentDate(), form.getAppointmentTime());

        // A6 — address and contact number belong to the patient, not to the appointment.
        // Saved explicitly rather than relying on dirty checking, so the intent is visible
        // and the write does not depend on the entity happening to be managed.
        patient.setAddress(form.getAddress());
        patient.setContactNumber(form.getContactNumber());
        patients.save(patient);

        Appointment appointment = new Appointment(
                numberGenerator.next(),          // UC12
                patient,
                dentist,
                treatment,
                form.getAppointmentDate(),
                form.getAppointmentTime(),
                currentUser.getUsername());
        appointment.setReasonForVisit(form.getReasonForVisit());

        Appointment saved = persist(appointment, dentist, form.getAppointmentTime(),
                form.getAppointmentDate());

        // UC14 <<extend>> — published, not called. The listener runs after commit and off
        // this thread, so a failed message cannot undo a confirmed appointment.
        events.publishEvent(new AppointmentBookedEvent(
                saved.getAppointmentNo(),
                patient.getFullName(),
                patient.getContactNumber(),
                dentist.getFullName(),
                saved.getAppointmentDate(),
                saved.getAppointmentTime()));

        return saved;
    }

    /**
     * Saves, translating a lost race on the unique index into the same message the advisory
     * check produces.
     *
     * <p>Two bookings can both pass {@code assertSlotFree} and then both try to commit. The
     * database rejects the second; without this the patient would see a stack trace instead
     * of "that slot has just been taken".
     */
    private Appointment persist(Appointment appointment, Dentist dentist,
                                LocalTime time, LocalDate date) {
        try {
            return appointments.saveAndFlush(appointment);
        } catch (DataIntegrityViolationException e) {
            throw new SlotUnavailableException(String.format(
                    "%s was booked at %s a moment ago. Please choose another time.",
                    dentist.getFullName(), time),
                    freeSlotsNear(dentist, date, time));
        }
    }

    /**
     * A patient books only for themselves; staff book for whoever the form names.
     *
     * <p>The patient's own identity comes from the session, never from the submitted
     * {@code patientId}, so a crafted form cannot book onto another patient's record.
     */
    private Patient resolvePatient(AppointmentBookingForm form, User currentUser) {
        if (currentUser instanceof Patient self && !currentUser.hasRole(RoleType.ADMIN)) {
            // Re-read rather than using the principal directly. The signed-in user was loaded
            // when the session was created and is detached from this transaction's
            // persistence context, so changes to it are silently discarded — the address and
            // contact number below would never reach the database.
            return patients.findById(self.getId())
                    .orElseThrow(() -> new InvalidAppointmentException(
                            "patientId", "Your patient record could not be found"));
        }
        if (form.getPatientId() == null) {
            throw new InvalidAppointmentException("patientId", "Please choose a patient");
        }
        return patients.findById(form.getPatientId())
                .orElseThrow(() -> new InvalidAppointmentException(
                        "patientId", "That patient is not registered"));
    }

    private void assertSlotFree(Dentist dentist, LocalDate date, LocalTime time) {
        if (appointments.isSlotTaken(dentist.getId(), date, time)) {
            throw new SlotUnavailableException(String.format(
                    "%s is already booked at %s on %s.", dentist.getFullName(), time, date),
                    freeSlotsNear(dentist, date, time));
        }
    }

    /**
     * The free slots closest to the one requested, for the "nearest free slots" message.
     *
     * <p>Only slots the dentist actually consults in are offered, so the suggestion is one
     * the user can accept without being refused again.
     */
    public List<LocalTime> freeSlotsNear(Dentist dentist, LocalDate date, LocalTime around) {
        Set<LocalTime> taken = appointments
                .findByDentistIdAndAppointmentDateOrderByAppointmentTimeAsc(dentist.getId(), date)
                .stream()
                .filter(a -> a.getStatus() != AppointmentStatus.CANCELLED)
                .map(Appointment::getAppointmentTime)
                .collect(Collectors.toSet());

        return validator.allSlots().stream()
                .filter(dentist::worksAt)
                .filter(slot -> !taken.contains(slot))
                .sorted(Comparator.comparingLong(
                        slot -> Math.abs(slot.toSecondOfDay() - around.toSecondOfDay())))
                .limit(MAX_ALTERNATIVES)
                .sorted()
                .toList();
    }

    // -------------------------------------------------------------------- search ---

    /**
     * Search by appointment number — brief requirement 3.
     *
     * <p>Applies assumption A8 before returning anything: a patient guessing another
     * patient's appointment number is refused rather than shown the record.
     */
    @Transactional(readOnly = true)
    public Appointment findForUser(String appointmentNo, User currentUser) {
        Appointment appointment = appointments.findByAppointmentNo(normalise(appointmentNo))
                .orElseThrow(() -> new AppointmentNotFoundException(appointmentNo));
        if (!accessPolicy.canView(currentUser, appointment)) {
            // Deliberately "not found", not "forbidden" (A8). Confirming that a record
            // exists but belongs to someone else would let a patient discover valid
            // appointment numbers by watching which response they get.
            throw new AppointmentNotFoundException(appointmentNo);
        }
        return appointment;
    }

    /** Appointment numbers are stored upper case; searching should not be fussy about it. */
    private String normalise(String appointmentNo) {
        return appointmentNo == null ? "" : appointmentNo.trim().toUpperCase();
    }

    /** Everything the signed-in user is entitled to see, newest first. */
    @Transactional(readOnly = true)
    public List<Appointment> findVisibleTo(User currentUser) {
        if (currentUser.hasRole(RoleType.ADMIN)) {
            return appointments.findAllWithDetails().stream()
                    .sorted(Comparator.comparing(Appointment::getScheduledAt).reversed())
                    .toList();
        }
        if (currentUser.hasRole(RoleType.DENTIST)) {
            return appointments.findAllWithDetails().stream()
                    .filter(a -> a.getDentist() != null
                            && a.getDentist().getId().equals(currentUser.getId()))
                    .sorted(Comparator.comparing(Appointment::getScheduledAt).reversed())
                    .toList();
        }
        return appointments
                .findByPatientIdOrderByAppointmentDateDescAppointmentTimeDesc(currentUser.getId());
    }

    /** One dentist's day, for the schedule view. */
    @Transactional(readOnly = true)
    public List<Appointment> scheduleFor(Long dentistId, LocalDate date) {
        return appointments
                .findByDentistIdAndAppointmentDateOrderByAppointmentTimeAsc(dentistId, date);
    }

    /** The whole clinic's day, for the administrator's list. */
    @Transactional(readOnly = true)
    public List<Appointment> scheduleForDate(LocalDate date) {
        return appointments.findByAppointmentDateOrderByAppointmentTimeAsc(date);
    }

    // ----------------------------------------------------------------- lifecycle ---

    /** Moves an appointment, re-running every rule that applied to the original booking. */
    @Transactional
    public Appointment reschedule(String appointmentNo, LocalDate date, LocalTime time,
                                  User currentUser) {
        Appointment appointment = loadModifiable(appointmentNo, currentUser);
        Dentist dentist = appointment.getDentist();

        validator.validateForDentist(date, time, dentist);

        boolean unchanged = date.equals(appointment.getAppointmentDate())
                && time.equals(appointment.getAppointmentTime());
        if (!unchanged) {
            assertSlotFree(dentist, date, time);
        }

        appointment.reschedule(date, time);
        return persist(appointment, dentist, time, date);
    }

    /** Cancels an appointment, which also releases its slot for rebooking. */
    @Transactional
    public Appointment cancel(String appointmentNo, String reason, User currentUser) {
        Appointment appointment = loadModifiable(appointmentNo, currentUser);
        appointment.cancel(reason);
        return appointments.saveAndFlush(appointment);
    }

    /** Confirms a booking with the patient. */
    @Transactional
    public Appointment confirm(String appointmentNo, User currentUser) {
        Appointment appointment = loadModifiable(appointmentNo, currentUser);
        appointment.confirm();
        return appointments.saveAndFlush(appointment);
    }

    /** Records treatment as carried out. Only the assigned dentist or an administrator may. */
    @Transactional
    public Appointment complete(String appointmentNo, String clinicalNotes, User currentUser) {
        Appointment appointment = appointments.findByAppointmentNo(normalise(appointmentNo))
                .orElseThrow(() -> new AppointmentNotFoundException(appointmentNo));
        if (!accessPolicy.canRecordTreatment(currentUser, appointment)) {
            throw new AppointmentNotFoundException(appointmentNo);
        }
        appointment.complete(clinicalNotes);
        return appointments.saveAndFlush(appointment);
    }

    private Appointment loadModifiable(String appointmentNo, User currentUser) {
        Appointment appointment = appointments.findByAppointmentNo(normalise(appointmentNo))
                .orElseThrow(() -> new AppointmentNotFoundException(appointmentNo));
        if (!accessPolicy.canModify(currentUser, appointment)) {
            throw new AppointmentNotFoundException(appointmentNo);
        }
        return appointment;
    }
}
