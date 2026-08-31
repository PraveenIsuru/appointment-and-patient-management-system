-- =============================================================================
--  V2  Reference data and development seed
--
--  Reference data (roles, treatments) is part of the schema contract: the
--  application cannot function without it, so it belongs in a migration rather
--  than in an ad-hoc script.
--
--  Rows are inserted with INSERT ... SELECT rather than with literal primary
--  keys. Hard-coding ids would work on MySQL but leaves H2's identity counter
--  behind the highest inserted value, so the next generated key collides.
--  Selecting the parent id keeps the file portable and keeps both engines'
--  counters correct.
-- =============================================================================

-- ------------------------------------------------------------------ roles ---

INSERT INTO roles (code, description) VALUES
    ('ADMIN',   'Clinic administrator - full access to appointments, billing and reports'),
    ('DENTIST', 'Practising dentist - own schedule and assigned patient histories'),
    ('PATIENT', 'Registered patient - own appointments and bills only');

-- ------------------------------------------------------------- treatments ---
-- Costs are in LKR. base_cost excludes the dentist consultation fee, which is
-- held on the dentist (see the Generate Bill sequence diagram).

INSERT INTO treatments (code, name, type, base_cost, estimated_minutes, sessions_required, active) VALUES
    ('CONS-01', 'General Consultation',      'CONSULTATION',           0.00, 20, 1, TRUE),
    ('SCP-01',  'Scaling and Polishing',     'SCALING_AND_POLISHING', 6500.00, 45, 1, TRUE),
    ('FIL-01',  'Composite Filling',         'FILLING',               4500.00, 40, 1, TRUE),
    ('EXT-01',  'Simple Extraction',         'EXTRACTION',            5000.00, 30, 1, TRUE),
    ('EXT-02',  'Surgical Extraction',       'EXTRACTION',           12000.00, 60, 1, TRUE),
    ('RCT-01',  'Root Canal Treatment',      'ROOT_CANAL',           28000.00, 90, 3, TRUE),
    ('ORT-01',  'Orthodontic Assessment',    'ORTHODONTIC',           9500.00, 60, 1, TRUE);

-- =============================================================================
--  Development users
--
--  Passwords are BCrypt hashes generated with Spring Security's
--  BCryptPasswordEncoder (strength 10) and verified with matches() before
--  being written here. Plaintext, for development sign-in only:
--
--      admin           Admin@123
--      dr.perera       Dentist@123
--      dr.silva        Dentist@123
--      n.fernando      Patient@123
--      s.jayawardena   Patient@123
--
--  These are seed credentials for a coursework demonstration. They must be
--  removed or rotated before any real deployment -- see M8.
-- =============================================================================

INSERT INTO users (username, password_hash, email, full_name, contact_number, active, failed_login_attempts) VALUES
    ('admin',         '$2a$10$.LBv2..aUY1Q8.6YrTza2eKyjMhJkns6PgM7Cum.L7QymJ3lnKFKq', 'admin@sunrisedental.lk',      'Ruwan Alwis',        '0112345678', TRUE, 0),
    ('dr.perera',     '$2a$10$ECdyrQjzBZ6X9/aY43H6T.a6MyoPF1E5sdvGOxasFeWazEI86na.O', 'perera@sunrisedental.lk',     'Dr. Anusha Perera',  '0771234567', TRUE, 0),
    ('dr.silva',      '$2a$10$dXTGWESHFaQPYOkeMeFhAuWNDANaLLzjJV9b/uq.QwU4zgCD0zIQm', 'silva@sunrisedental.lk',      'Dr. Kasun Silva',    '0772345678', TRUE, 0),
    ('n.fernando',    '$2a$10$PirOHvw1rZW3q5qFgrlcQ.Y8AdaMkRtiNpy7RdyET2mgTU9/nx7vG', 'nimal.fernando@mail.lk',      'Nimal Fernando',     '0763456789', TRUE, 0),
    ('s.jayawardena', '$2a$10$MhTehJ3Xj4fajl.zZGQUhu0az1EEECP2avzzVfWfDZyAhigtrIo0O', 'sanduni.j@mail.lk',           'Sanduni Jayawardena','0714567890', TRUE, 0);

-- --------------------------------------------------------- role-specific ----

INSERT INTO administrators (id, staff_no, designation)
    SELECT id, 'STF-001', 'Clinic Manager' FROM users WHERE username = 'admin';

INSERT INTO dentists (id, dentist_no, specialisation, licence_number, consultation_fee, session_start, session_end)
    SELECT id, 'DEN-001', 'Restorative Dentistry', 'SLMC-D-10432', 2500.00, '08:00:00', '14:00:00'
    FROM users WHERE username = 'dr.perera';

INSERT INTO dentists (id, dentist_no, specialisation, licence_number, consultation_fee, session_start, session_end)
    SELECT id, 'DEN-002', 'Oral Surgery', 'SLMC-D-11876', 3000.00, '14:00:00', '20:00:00'
    FROM users WHERE username = 'dr.silva';

INSERT INTO patients (id, patient_no, address, date_of_birth, registered_on, allergies)
    SELECT id, 'PAT-000001', '42/3 Galle Road, Colombo 03', '1988-04-17', '2026-01-12', 'Penicillin'
    FROM users WHERE username = 'n.fernando';

INSERT INTO patients (id, patient_no, address, date_of_birth, registered_on, allergies)
    SELECT id, 'PAT-000002', '18 Temple Lane, Nugegoda', '1995-11-02', '2026-02-28', NULL
    FROM users WHERE username = 's.jayawardena';

-- -------------------------------------------------------------- authority ---

INSERT INTO user_roles (user_id, role_id)
    SELECT u.id, r.id FROM users u, roles r
    WHERE (u.username = 'admin'         AND r.code = 'ADMIN')
       OR (u.username = 'dr.perera'     AND r.code = 'DENTIST')
       OR (u.username = 'dr.silva'      AND r.code = 'DENTIST')
       OR (u.username = 'n.fernando'    AND r.code = 'PATIENT')
       OR (u.username = 's.jayawardena' AND r.code = 'PATIENT');
