-- =============================================================================
--  V3  (MySQL)  Advanced database objects
--
--  Three server-side objects, each solving a problem that is genuinely better
--  solved in the database than in Java:
--
--    trg_appointment_audit_*    accountability that cannot be bypassed
--    fn_calculate_bill_total    one definition of the money arithmetic
--    sp_daily_revenue_report    set-based reporting without shipping rows
--
--  Flyway is configured with locations=classpath:db/migration/common,
--  classpath:db/migration/{vendor}, so this file runs only on MySQL. The H2
--  counterpart documents what is deliberately absent there.
-- =============================================================================

-- -----------------------------------------------------------------------------
--  TRIGGERS - assumption A9
--
--  Auditing is a database responsibility. Writing it here rather than in a
--  service method means it also captures changes made outside the application
--  -- a direct SQL correction by an administrator, for instance -- which is
--  exactly the circumstance in which an audit trail earns its keep.
--
--  MySQL permits one trigger per timing/event pair, so INSERT and UPDATE are
--  separate triggers rather than one combined body.
-- -----------------------------------------------------------------------------

CREATE TRIGGER trg_appointment_audit_insert
AFTER INSERT ON appointments
FOR EACH ROW
    INSERT INTO audit_log (entity_name, entity_id, action, performed_by, performed_at, old_value, new_value)
    VALUES ('Appointment',
            NEW.id,
            'CREATE',
            NEW.created_by,
            CURRENT_TIMESTAMP,
            NULL,
            CONCAT('no=', NEW.appointment_no,
                   '; dentist=', NEW.dentist_id,
                   '; date=', NEW.appointment_date,
                   '; time=', NEW.appointment_time,
                   '; status=', NEW.status));

CREATE TRIGGER trg_appointment_audit_update
AFTER UPDATE ON appointments
FOR EACH ROW
    INSERT INTO audit_log (entity_name, entity_id, action, performed_by, performed_at, old_value, new_value)
    VALUES ('Appointment',
            NEW.id,
            'UPDATE',
            NEW.created_by,
            CURRENT_TIMESTAMP,
            CONCAT('date=', OLD.appointment_date,
                   '; time=', OLD.appointment_time,
                   '; status=', OLD.status),
            CONCAT('date=', NEW.appointment_date,
                   '; time=', NEW.appointment_time,
                   '; status=', NEW.status));

-- -----------------------------------------------------------------------------
--  FUNCTION
--
--  The brief requires the bill total to be computed from "treatment type and
--  consultation fee". Defining that arithmetic once, in the database, means a
--  report written in SQL and a receipt rendered by the application cannot
--  disagree about what a bill is worth.
--
--  DETERMINISTIC: the same arguments always produce the same result, which
--  lets MySQL use the function in generated columns and indexed expressions.
-- -----------------------------------------------------------------------------

CREATE FUNCTION fn_calculate_bill_total(
    p_consultation_fee DECIMAL(10,2),
    p_treatment_cost   DECIMAL(10,2),
    p_discount_amount  DECIMAL(10,2),
    p_tax_amount       DECIMAL(10,2)
) RETURNS DECIMAL(10,2)
DETERMINISTIC
NO SQL
RETURN ROUND(
    COALESCE(p_consultation_fee, 0)
  + COALESCE(p_treatment_cost,   0)
  - COALESCE(p_discount_amount,  0)
  + COALESCE(p_tax_amount,       0), 2);

-- -----------------------------------------------------------------------------
--  STORED PROCEDURE
--
--  Backs the daily revenue report. Aggregating in the database returns one row
--  per treatment type instead of shipping every bill to the application to be
--  summed in a loop.
-- -----------------------------------------------------------------------------

CREATE PROCEDURE sp_daily_revenue_report(IN p_report_date DATE)
    SELECT t.type                                            AS treatment_type,
           COUNT(DISTINCT a.id)                              AS appointment_count,
           SUM(CASE WHEN b.status = 'PAID'   THEN 1 ELSE 0 END) AS bills_paid,
           SUM(CASE WHEN b.status = 'UNPAID' THEN 1 ELSE 0 END) AS bills_unpaid,
           COALESCE(SUM(b.consultation_fee), 0)              AS consultation_revenue,
           COALESCE(SUM(b.treatment_cost),   0)              AS treatment_revenue,
           COALESCE(SUM(b.discount_amount),  0)              AS total_discount,
           COALESCE(SUM(b.total_amount),     0)              AS gross_revenue
    FROM appointments a
    JOIN treatments t ON t.id = a.treatment_id
    LEFT JOIN bills b ON b.id = a.bill_id
    WHERE a.appointment_date = p_report_date
      AND a.status <> 'CANCELLED'
    GROUP BY t.type
    ORDER BY gross_revenue DESC;
