-- Hospital Management System: MySQL schema (4 normalized tables).
CREATE DATABASE IF NOT EXISTS hospital_db;
USE hospital_db;

-- Each patient is stored once. Phone is unique so the same patient cannot be registered twice.
CREATE TABLE IF NOT EXISTS patients (
  id            INT AUTO_INCREMENT PRIMARY KEY,
  first_name    VARCHAR(50)  NOT NULL,
  last_name     VARCHAR(50)  NOT NULL,
  date_of_birth DATE         NOT NULL,
  gender        VARCHAR(10)  NOT NULL,
  phone         VARCHAR(15)  NOT NULL UNIQUE,
  address       VARCHAR(200),
  registered_on TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_patient_name (last_name, first_name)   -- fast search by name
);

CREATE TABLE IF NOT EXISTS doctors (
  id             INT AUTO_INCREMENT PRIMARY KEY,
  name           VARCHAR(100) NOT NULL,
  specialization VARCHAR(80)  NOT NULL
);

-- One row = one booked slot. UNIQUE(doctor_id, appointment_time) means the DATABASE itself
-- refuses a double booking, even if two admins try at the same moment.
CREATE TABLE IF NOT EXISTS appointments (
  id               INT AUTO_INCREMENT PRIMARY KEY,
  patient_id       INT      NOT NULL,
  doctor_id        INT      NOT NULL,
  appointment_time DATETIME NOT NULL,
  FOREIGN KEY (patient_id) REFERENCES patients(id),
  FOREIGN KEY (doctor_id)  REFERENCES doctors(id),
  UNIQUE KEY uq_doctor_slot (doctor_id, appointment_time)
);

CREATE TABLE IF NOT EXISTS medical_records (
  id           INT AUTO_INCREMENT PRIMARY KEY,
  patient_id   INT          NOT NULL,
  doctor_id    INT          NOT NULL,
  visit_date   DATE         NOT NULL,
  diagnosis    VARCHAR(200) NOT NULL,
  prescription VARCHAR(300),
  FOREIGN KEY (patient_id) REFERENCES patients(id),
  FOREIGN KEY (doctor_id)  REFERENCES doctors(id),
  INDEX idx_record_patient (patient_id, visit_date)   -- fast "show this patient's history"
);
