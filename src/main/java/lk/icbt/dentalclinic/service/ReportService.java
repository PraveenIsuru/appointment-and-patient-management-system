package lk.icbt.dentalclinic.service;

import lk.icbt.dentalclinic.dto.report.DailySummary;
import lk.icbt.dentalclinic.dto.report.DentistWorkloadRow;
import lk.icbt.dentalclinic.dto.report.RevenueByTreatmentRow;
import lk.icbt.dentalclinic.model.billing.Bill;
import lk.icbt.dentalclinic.model.billing.PaymentStatus;
import lk.icbt.dentalclinic.model.scheduling.Appointment;
import lk.icbt.dentalclinic.model.scheduling.AppointmentStatus;
import lk.icbt.dentalclinic.model.scheduling.TreatmentType;
import lk.icbt.dentalclinic.repository.AppointmentRepository;
import lk.icbt.dentalclinic.repository.DentistRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Management reports.
 *
 * <p>The brief asks for "a suitable set of reports which you think add more value to your
 * system". These four are chosen because each answers a question the clinic's paper system
 * could not: what is happening today, which work earns its chair time, which dentist is
 * losing slots, and what a given patient has had done.
 *
 * <p>Cancelled appointments are excluded from revenue and workload throughout — counting work
 * that never happened would make every figure optimistic.
 */
@Service
public class ReportService {

    private final AppointmentRepository appointments;
    private final DentistRepository dentists;

    public ReportService(AppointmentRepository appointments, DentistRepository dentists) {
        this.appointments = appointments;
        this.dentists = dentists;
    }

    /** Report 1 — the day's schedule, in time order. */
    @Transactional(readOnly = true)
    public List<Appointment> dailySchedule(LocalDate date) {
        return appointments.findByAppointmentDateOrderByAppointmentTimeAsc(date);
    }

    /** Report 2 — headline figures for a day. */
    @Transactional(readOnly = true)
    public DailySummary dailySummary(LocalDate date) {
        List<Appointment> ofDay = appointments.findByAppointmentDateOrderByAppointmentTimeAsc(date);

        BigDecimal billed = sumBills(ofDay, bill -> true);
        BigDecimal collected = sumBills(ofDay, bill -> bill.getStatus() == PaymentStatus.PAID);

        return new DailySummary(
                date,
                ofDay.size(),
                count(ofDay, AppointmentStatus.COMPLETED),
                count(ofDay, AppointmentStatus.CANCELLED),
                count(ofDay, AppointmentStatus.NO_SHOW),
                billed,
                collected);
    }

    /**
     * Report 3 — revenue by treatment type over a date range.
     *
     * <p>The equivalent of the {@code sp_daily_revenue_report} stored procedure, computed in
     * Java so it also works on H2 and can span more than one day. Both exist deliberately;
     * the M7 test plan cross-checks them against each other on MySQL.
     */
    @Transactional(readOnly = true)
    public List<RevenueByTreatmentRow> revenueByTreatment(LocalDate from, LocalDate to) {
        Map<TreatmentType, List<Appointment>> byType = inRange(from, to).stream()
                .filter(a -> a.getStatus() != AppointmentStatus.CANCELLED)
                .collect(Collectors.groupingBy(a -> a.getTreatment().getType()));

        List<RevenueByTreatmentRow> rows = new ArrayList<>();
        byType.forEach((type, visits) ->
                rows.add(new RevenueByTreatmentRow(type, visits.size(), sumBills(visits, b -> true))));

        rows.sort(Comparator.comparing(RevenueByTreatmentRow::revenue).reversed());
        return rows;
    }

    /**
     * Report 4 — dentist workload over a date range.
     *
     * <p>Every active dentist appears, including those with no bookings: a dentist sitting
     * idle is exactly what a workload report should make visible, and omitting them would
     * hide it.
     */
    @Transactional(readOnly = true)
    public List<DentistWorkloadRow> dentistWorkload(LocalDate from, LocalDate to) {
        List<Appointment> range = inRange(from, to).stream()
                .filter(a -> a.getStatus() != AppointmentStatus.CANCELLED)
                .toList();

        return dentists.findByActiveTrueOrderByFullNameAsc().stream()
                .map(dentist -> {
                    List<Appointment> theirs = range.stream()
                            .filter(a -> a.getDentist() != null
                                    && a.getDentist().getId().equals(dentist.getId()))
                            .toList();
                    long completed = count(theirs, AppointmentStatus.COMPLETED);
                    return new DentistWorkloadRow(dentist.getDentistNo(), dentist.getFullName(),
                            theirs.size(), completed, sumBills(theirs, b -> true));
                })
                .sorted(Comparator.comparing(DentistWorkloadRow::revenue).reversed())
                .toList();
    }

    /** Report 5 — one patient's complete visit history, newest first. */
    @Transactional(readOnly = true)
    public List<Appointment> patientHistory(Long patientId) {
        return appointments
                .findByPatientIdOrderByAppointmentDateDescAppointmentTimeDesc(patientId);
    }

    // ------------------------------------------------------------------- helpers ---

    private List<Appointment> inRange(LocalDate from, LocalDate to) {
        return appointments.findAllWithDetails().stream()
                .filter(a -> !a.getAppointmentDate().isBefore(from)
                        && !a.getAppointmentDate().isAfter(to))
                .toList();
    }

    private long count(List<Appointment> list, AppointmentStatus status) {
        return list.stream().filter(a -> a.getStatus() == status).count();
    }

    private BigDecimal sumBills(List<Appointment> list, Predicate<Bill> include) {
        return list.stream()
                .map(Appointment::getBill)
                .filter(java.util.Objects::nonNull)
                .filter(include)
                .map(Bill::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
