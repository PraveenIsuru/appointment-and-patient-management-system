package lk.icbt.dentalclinic.controller;

import lk.icbt.dentalclinic.service.ReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Management reports.
 *
 * <p>Administrators only. Every report aggregates across patients, so none of them can be
 * scoped to a single person the way an appointment can — which is precisely why they are not
 * offered to patients or dentists at all.
 */
@Controller
@RequestMapping("/reports")
@PreAuthorize("hasRole('ADMIN')")
public class ReportController {

    private final ReportService reports;
    private final Clock clock;

    public ReportController(ReportService reports, Clock clock) {
        this.reports = reports;
        this.clock = clock;
    }

    @GetMapping
    public String index(Model model) {
        model.addAttribute("today", LocalDate.now(clock));
        return "reports/index";
    }

    @GetMapping("/daily")
    public String daily(@RequestParam(required = false)
                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                        Model model) {
        LocalDate on = date != null ? date : LocalDate.now(clock);
        model.addAttribute("date", on);
        model.addAttribute("summary", reports.dailySummary(on));
        model.addAttribute("schedule", reports.dailySchedule(on));
        return "reports/daily";
    }

    @GetMapping("/revenue")
    public String revenue(@RequestParam(required = false)
                          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                          @RequestParam(required = false)
                          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                          Model model) {
        LocalDate start = from != null ? from : LocalDate.now(clock).withDayOfMonth(1);
        LocalDate end = to != null ? to : LocalDate.now(clock);
        model.addAttribute("from", start);
        model.addAttribute("to", end);
        model.addAttribute("rows", reports.revenueByTreatment(start, end));
        return "reports/revenue";
    }

    @GetMapping("/workload")
    public String workload(@RequestParam(required = false)
                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                           @RequestParam(required = false)
                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                           Model model) {
        LocalDate start = from != null ? from : LocalDate.now(clock).withDayOfMonth(1);
        LocalDate end = to != null ? to : LocalDate.now(clock);
        model.addAttribute("from", start);
        model.addAttribute("to", end);
        model.addAttribute("rows", reports.dentistWorkload(start, end));
        return "reports/workload";
    }
}
