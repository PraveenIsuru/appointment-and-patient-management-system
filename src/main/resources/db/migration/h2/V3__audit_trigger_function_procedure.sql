-- =============================================================================
--  V3  (H2)  Counterpart to the MySQL advanced database objects
--
--  The MySQL migration at db/migration/mysql/V3 creates a trigger pair, a
--  function and a stored procedure. H2 cannot express any of them in SQL: its
--  triggers and aliases are implemented as Java classes, and it has no
--  procedural language.
--
--  Rather than hand-write Java stand-ins that would test something other than
--  the code actually shipped, this file creates only the object H2 can express
--  faithfully, and records what is deliberately missing.
--
--  KNOWN LIMITATION, carried into the M7 test plan:
--    - trg_appointment_audit_insert / _update  are NOT exercised by the H2
--      test suite. They are verified by running the application against MySQL.
--    - sp_daily_revenue_report                  likewise; the equivalent query
--      is covered by a repository test in the meantime.
--
--  This divergence is why MySQL is the authoritative target and H2 is a
--  convenience for fast, dependency-free tests -- not the other way round.
-- =============================================================================

-- H2 accepts an inline scalar function body through CREATE ALIAS with a Java
-- expression. This keeps the money arithmetic verifiable on both engines, so
-- the one object that the application logic depends on is not untested here.
CREATE ALIAS fn_calculate_bill_total AS '
java.math.BigDecimal calculate(java.math.BigDecimal consultationFee,
                               java.math.BigDecimal treatmentCost,
                               java.math.BigDecimal discountAmount,
                               java.math.BigDecimal taxAmount) {
    java.math.BigDecimal zero = java.math.BigDecimal.ZERO;
    java.math.BigDecimal fee      = consultationFee == null ? zero : consultationFee;
    java.math.BigDecimal cost     = treatmentCost   == null ? zero : treatmentCost;
    java.math.BigDecimal discount = discountAmount  == null ? zero : discountAmount;
    java.math.BigDecimal tax      = taxAmount       == null ? zero : taxAmount;
    return fee.add(cost).subtract(discount).add(tax)
              .setScale(2, java.math.RoundingMode.HALF_UP);
}
';
