-- =============================================================================
--  V1  Baseline schema
--  Sunrise Dental Clinic - Appointment & Patient Management System
--
--  Mirrors the Task A domain class diagram
--  (my-docs/task-a/diagrams/class-diagram.puml) -- one table per class.
--
--  Written in portable SQL so the identical file runs on MySQL 8 and on H2 in
--  MySQL compatibility mode. Vendor-specific objects (trigger, function and
--  stored procedure) live in db/migration/{vendor}/V3.
-- =============================================================================

-- --------------------------------------------------------------- identity ---

CREATE TABLE roles (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    code        VARCHAR(20)  NOT NULL,
    description VARCHAR(100) NOT NULL
);
CREATE UNIQUE INDEX uq_roles_code ON roles (code);

-- Abstract superclass User -> JOINED inheritance (assumption A4).
-- Shared identity columns live here exactly once.
CREATE TABLE users (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    username              VARCHAR(50)  NOT NULL,
    password_hash         VARCHAR(100) NOT NULL,
    email                 VARCHAR(120) NOT NULL,
    full_name             VARCHAR(120) NOT NULL,
    contact_number        VARCHAR(20),
    active                BOOLEAN      NOT NULL DEFAULT TRUE,
    failed_login_attempts INT          NOT NULL DEFAULT 0,
    created_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uq_users_username ON users (username);
CREATE UNIQUE INDEX uq_users_email    ON users (email);

-- User "0..*" --> "1..*" Role : holds
CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id)
);

-- Role-specific columns sit in their own tables, so NOT NULL still means
-- something. Under SINGLE_TABLE they would be nullable for two thirds of rows.
CREATE TABLE patients (
    id            BIGINT PRIMARY KEY,
    patient_no    VARCHAR(20)  NOT NULL,
    address       VARCHAR(255) NOT NULL,
    date_of_birth DATE         NOT NULL,
    registered_on DATE         NOT NULL,
    allergies     VARCHAR(255),
    CONSTRAINT fk_patients_user FOREIGN KEY (id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE UNIQUE INDEX uq_patients_patient_no ON patients (patient_no);

CREATE TABLE dentists (
    id               BIGINT PRIMARY KEY,
    dentist_no       VARCHAR(20)   NOT NULL,
    specialisation   VARCHAR(100)  NOT NULL,
    licence_number   VARCHAR(50)   NOT NULL,
    consultation_fee DECIMAL(10,2) NOT NULL,
    session_start    TIME          NOT NULL,
    session_end      TIME          NOT NULL,
    CONSTRAINT fk_dentists_user FOREIGN KEY (id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE UNIQUE INDEX uq_dentists_dentist_no ON dentists (dentist_no);
CREATE UNIQUE INDEX uq_dentists_licence    ON dentists (licence_number);

CREATE TABLE administrators (
    id          BIGINT PRIMARY KEY,
    staff_no    VARCHAR(20)  NOT NULL,
    designation VARCHAR(100) NOT NULL,
    CONSTRAINT fk_administrators_user FOREIGN KEY (id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE UNIQUE INDEX uq_administrators_staff_no ON administrators (staff_no);

-- ------------------------------------------------------------- scheduling ---

CREATE TABLE treatments (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    code              VARCHAR(20)   NOT NULL,
    name              VARCHAR(100)  NOT NULL,
    type              VARCHAR(30)   NOT NULL,
    base_cost         DECIMAL(10,2) NOT NULL,
    estimated_minutes INT           NOT NULL,
    sessions_required INT           NOT NULL DEFAULT 1,
    active            BOOLEAN       NOT NULL DEFAULT TRUE
);
CREATE UNIQUE INDEX uq_treatments_code ON treatments (code);

-- ---------------------------------------------------------------- billing ---

CREATE TABLE bills (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    bill_no          VARCHAR(20)   NOT NULL,
    issued_at        TIMESTAMP     NOT NULL,
    consultation_fee DECIMAL(10,2) NOT NULL,
    treatment_cost   DECIMAL(10,2) NOT NULL,
    discount_amount  DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    tax_amount       DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    total_amount     DECIMAL(10,2) NOT NULL,
    status           VARCHAR(20)   NOT NULL,
    payment_method   VARCHAR(20),
    issued_by        VARCHAR(50)   NOT NULL
);
CREATE UNIQUE INDEX uq_bills_bill_no ON bills (bill_no);

-- Bill "1" *-- "1..*" BillLineItem : composition.
-- ON DELETE CASCADE is the physical expression of "the part cannot outlive
-- the whole".
CREATE TABLE bill_line_items (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    bill_id     BIGINT        NOT NULL,
    description VARCHAR(150)  NOT NULL,
    category    VARCHAR(20)   NOT NULL,
    unit_price  DECIMAL(10,2) NOT NULL,
    quantity    INT           NOT NULL DEFAULT 1,
    CONSTRAINT fk_line_items_bill FOREIGN KEY (bill_id) REFERENCES bills (id) ON DELETE CASCADE
);
CREATE INDEX ix_line_items_bill ON bill_line_items (bill_id);

-- ----------------------------------------------------------- appointments ---

CREATE TABLE appointments (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    appointment_no      VARCHAR(20)   NOT NULL,
    patient_id          BIGINT        NOT NULL,
    dentist_id          BIGINT        NOT NULL,
    treatment_id        BIGINT        NOT NULL,
    bill_id             BIGINT,
    appointment_date    DATE          NOT NULL,
    appointment_time    TIME          NOT NULL,
    status              VARCHAR(20)   NOT NULL,
    reason_for_visit    VARCHAR(255),
    clinical_notes      VARCHAR(1000),
    cancellation_reason VARCHAR(255),
    created_by          VARCHAR(50)   NOT NULL,
    created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    slot_lock           VARCHAR(1),
    CONSTRAINT fk_appointments_patient   FOREIGN KEY (patient_id)   REFERENCES patients (id),
    CONSTRAINT fk_appointments_dentist   FOREIGN KEY (dentist_id)   REFERENCES dentists (id),
    CONSTRAINT fk_appointments_treatment FOREIGN KEY (treatment_id) REFERENCES treatments (id),
    CONSTRAINT fk_appointments_bill      FOREIGN KEY (bill_id)      REFERENCES bills (id)
);
CREATE UNIQUE INDEX uq_appointments_no ON appointments (appointment_no);

-- =============================================================================
--  ASSUMPTION A11 - the constraint that structurally removes double booking.
--
--  The service layer also checks availability, but only in order to produce a
--  helpful message. This index is what makes the clash *impossible*: two
--  concurrent bookings for the same dentist and slot cannot both commit,
--  however the race is interleaved.
--
--  REFINEMENT to the Task A design (see design-decisions.md A11).
--  A11 specifies UNIQUE (dentist_id, appointment_date, appointment_time). Taken
--  literally that is too strong: a CANCELLED appointment would go on occupying
--  its slot forever, so a cancelled 10:00 slot could never be rebooked. It also
--  contradicts Appointment.clashesWith(), which correctly treats a cancelled
--  slot as free.
--
--  slot_lock reconciles the two. It holds 'A' while the appointment occupies its
--  slot and NULL once cancelled, and is maintained by a JPA lifecycle callback
--  so it cannot drift from status. Because both engines treat NULLs in a unique
--  index as distinct, cancelled rows stop competing for the slot the moment they
--  are cancelled, while two live bookings still collide.
-- =============================================================================
CREATE UNIQUE INDEX uq_dentist_slot
    ON appointments (dentist_id, appointment_date, appointment_time, slot_lock);

-- Appointment "1" --> "0..1" Bill. Multiple NULLs are permitted by a UNIQUE
-- index on both engines, so this enforces "at most one bill per appointment"
-- without forcing every appointment to have one.
CREATE UNIQUE INDEX uq_appointments_bill ON appointments (bill_id);

-- ------------------------------------------------------------------ audit ---

-- ASSUMPTION A9: deliberately no foreign keys. An audit row must survive the
-- deletion of the record it describes, so the actor is stored as a username
-- string rather than as a reference to users.
CREATE TABLE audit_log (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    entity_name  VARCHAR(50)   NOT NULL,
    entity_id    BIGINT        NOT NULL,
    action       VARCHAR(20)   NOT NULL,
    performed_by VARCHAR(50)   NOT NULL,
    performed_at TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    old_value    VARCHAR(1000),
    new_value    VARCHAR(1000)
);
CREATE INDEX ix_audit_entity ON audit_log (entity_name, entity_id);
