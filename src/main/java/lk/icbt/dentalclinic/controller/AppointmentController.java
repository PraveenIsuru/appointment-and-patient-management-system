package lk.icbt.dentalclinic.controller;

import jakarta.validation.Valid;
import lk.icbt.dentalclinic.dto.AppointmentBookingForm;
import lk.icbt.dentalclinic.exception.InvalidAppointmentException;
import lk.icbt.dentalclinic.exception.SlotUnavailableException;
import lk.icbt.dentalclinic.model.identity.Patient;
import lk.icbt.dentalclinic.model.identity.RoleType;
import lk.icbt.dentalclinic.model.scheduling.Appointment;
import lk.icbt.dentalclinic.repository.DentistRepository;
import lk.icbt.dentalclinic.repository.PatientRepository;
import lk.icbt.dentalclinic.repository.TreatmentRepository;
import lk.icbt.dentalclinic.security.ClinicUserDetails;
import lk.icbt.dentalclinic.service.AppointmentService;
import lk.icbt.dentalclinic.service.AppointmentValidator;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.stream.Collectors;

/**
 * Appointment booking, search and lifecycle — brief requirements 2 and 3.
 *
 * <p>Presentation tier only: it binds and validates the request, hands off to
 * {@code AppointmentService}, and chooses a view. Every business rule lives in the service,
 * so the same rules apply unchanged to the REST API added in M6.
 */
@Controller
@RequestMapping("/appointments")
public class AppointmentController {

    private final AppointmentService appointmentService;
    private final AppointmentValidator validator;
    private final DentistRepository dentists;
    private final TreatmentRepository treatments;
    private final PatientRepository patients;

    public AppointmentController(AppointmentService appointmentService,
                                 AppointmentValidator validator,
                                 DentistRepository dentists,
                                 TreatmentRepository treatments,
                                 PatientRepository patients) {
        this.appointmentService = appointmentService;
        this.validator = validator;
        this.dentists = dentists;
        this.treatments = treatments;
        this.patients = patients;
    }

    // ---------------------------------------------------------------------- list ---

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public String list(@AuthenticationPrincipal ClinicUserDetails principal, Model model) {
        model.addAttribute("appointments", appointmentService.findVisibleTo(principal.getUser()));
        model.addAttribute("principal", principal);
        return "appointments/list";
    }

    // ------------------------------------------------------------------- booking ---

    @GetMapping("/new")
    @PreAuthorize("isAuthenticated()")
    public String showBookingForm(@AuthenticationPrincipal ClinicUserDetails principal,
                                  Model model) {
        AppointmentBookingForm form = new AppointmentBookingForm();
        // Pre-fill a patient's own details, so the common case is a two-field form.
        if (principal.getUser() instanceof Patient patient) {
            form.setAddress(patient.getAddress());
            form.setContactNumber(patient.getContactNumber());
        }
        model.addAttribute("form", form);
        addFormOptions(model, principal);
        return "appointments/book";
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public String book(@Valid @ModelAttribute("form") AppointmentBookingForm form,
                       BindingResult binding,
                       @AuthenticationPrincipal ClinicUserDetails principal,
                       Model model,
                       RedirectAttributes flash) {
        if (binding.hasErrors()) {
            addFormOptions(model, principal);
            return "appointments/book";
        }
        try {
            Appointment booked = appointmentService.book(form, principal.getUser());
            flash.addFlashAttribute("successMessage",
                    "Appointment " + booked.getAppointmentNo() + " confirmed.");
            return "redirect:/appointments/" + booked.getAppointmentNo();

        } catch (InvalidAppointmentException e) {
            binding.rejectValue(e.getField(), "appointment.invalid", e.getMessage());

        } catch (SlotUnavailableException e) {
            // Offer a way forward rather than only refusing.
            String message = e.getMessage();
            if (e.hasAlternatives()) {
                message += " Nearest free slots: " + e.getAlternatives().stream()
                        .map(LocalTime::toString)
                        .collect(Collectors.joining(", ")) + ".";
            }
            binding.rejectValue("appointmentTime", "appointment.slotTaken", message);
        }
        addFormOptions(model, principal);
        return "appointments/book";
    }

    // -------------------------------------------------------------------- detail ---

    /**
     * Search by appointment number — brief requirement 3.
     *
     * <p>The row-level check lives in the service, so a patient guessing another patient's
     * number is refused there rather than relying on this method remembering to ask.
     */
    @GetMapping("/{appointmentNo}")
    @PreAuthorize("isAuthenticated()")
    public String detail(@PathVariable String appointmentNo,
                         @AuthenticationPrincipal ClinicUserDetails principal,
                         Model model) {
        Appointment appointment =
                appointmentService.findForUser(appointmentNo, principal.getUser());
        model.addAttribute("appointment", appointment);
        model.addAttribute("principal", principal);
        model.addAttribute("canModify", principal.getUser().hasRole(RoleType.ADMIN)
                || principal.getUser().hasRole(RoleType.PATIENT));
        return "appointments/detail";
    }

    @GetMapping("/search")
    @PreAuthorize("isAuthenticated()")
    public String search(@RequestParam(required = false) String appointmentNo,
                         @AuthenticationPrincipal ClinicUserDetails principal,
                         Model model) {
        model.addAttribute("principal", principal);
        if (appointmentNo == null || appointmentNo.isBlank()) {
            return "appointments/search";
        }
        return "redirect:/appointments/" + appointmentNo.trim().toUpperCase();
    }

    // ----------------------------------------------------------------- lifecycle ---

    @PostMapping("/{appointmentNo}/cancel")
    @PreAuthorize("isAuthenticated()")
    public String cancel(@PathVariable String appointmentNo,
                         @RequestParam(required = false) String reason,
                         @AuthenticationPrincipal ClinicUserDetails principal,
                         RedirectAttributes flash) {
        appointmentService.cancel(appointmentNo, reason, principal.getUser());
        flash.addFlashAttribute("successMessage",
                "Appointment " + appointmentNo + " has been cancelled.");
        return "redirect:/appointments/" + appointmentNo;
    }

    @PostMapping("/{appointmentNo}/confirm")
    @PreAuthorize("hasRole('ADMIN')")
    public String confirm(@PathVariable String appointmentNo,
                          @AuthenticationPrincipal ClinicUserDetails principal,
                          RedirectAttributes flash) {
        appointmentService.confirm(appointmentNo, principal.getUser());
        flash.addFlashAttribute("successMessage", "Appointment confirmed with the patient.");
        return "redirect:/appointments/" + appointmentNo;
    }

    @PostMapping("/{appointmentNo}/reschedule")
    @PreAuthorize("isAuthenticated()")
    public String reschedule(@PathVariable String appointmentNo,
                             @RequestParam LocalDate appointmentDate,
                             @RequestParam LocalTime appointmentTime,
                             @AuthenticationPrincipal ClinicUserDetails principal,
                             RedirectAttributes flash) {
        try {
            appointmentService.reschedule(appointmentNo, appointmentDate, appointmentTime,
                    principal.getUser());
            flash.addFlashAttribute("successMessage", "Appointment moved.");
        } catch (InvalidAppointmentException | SlotUnavailableException e) {
            flash.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/appointments/" + appointmentNo;
    }

    @PostMapping("/{appointmentNo}/complete")
    @PreAuthorize("hasAnyRole('DENTIST', 'ADMIN')")
    public String complete(@PathVariable String appointmentNo,
                           @RequestParam(required = false) String clinicalNotes,
                           @AuthenticationPrincipal ClinicUserDetails principal,
                           RedirectAttributes flash) {
        appointmentService.complete(appointmentNo, clinicalNotes, principal.getUser());
        flash.addFlashAttribute("successMessage", "Treatment recorded.");
        return "redirect:/appointments/" + appointmentNo;
    }

    // ------------------------------------------------------------------- helpers ---

    private void addFormOptions(Model model, ClinicUserDetails principal) {
        model.addAttribute("dentists", dentists.findByActiveTrueOrderByFullNameAsc());
        model.addAttribute("treatments", treatments.findByActiveTrueOrderByNameAsc());
        model.addAttribute("slots", validator.allSlots());
        model.addAttribute("principal", principal);
        model.addAttribute("today", LocalDate.now());
        // Staff choose a patient; a patient never sees the list, and could not use it anyway.
        model.addAttribute("bookingForOthers", principal.getUser().hasRole(RoleType.ADMIN));
        if (principal.getUser().hasRole(RoleType.ADMIN)) {
            model.addAttribute("patients", patients.findAll());
        }
    }
}
