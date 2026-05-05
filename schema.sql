-- =====================================================================
-- Smart Daily Task Timetable Generator - MySQL Schema
-- Run once before starting the application:
--   mysql -u root -p < schema.sql
-- =====================================================================

CREATE DATABASE IF NOT EXISTS smart_scheduler
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE smart_scheduler;

-- ---------------------------------------------------------------------
-- Users
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    user_id    INT AUTO_INCREMENT PRIMARY KEY,
    username   VARCHAR(50)  NOT NULL UNIQUE,
    password   VARCHAR(255) NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- Tasks
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tasks (
    task_id     INT AUTO_INCREMENT PRIMARY KEY,
    user_id     INT          NOT NULL,
    title       VARCHAR(255) NOT NULL,
    description TEXT,
    deadline    DATETIME     NOT NULL,
    priority    ENUM('HIGH','MEDIUM','LOW')               NOT NULL DEFAULT 'MEDIUM',
    duration    INT                                       NOT NULL,
    task_type   ENUM('STUDY','WORK','PERSONAL')           NOT NULL DEFAULT 'PERSONAL',
    status      ENUM('PENDING','IN_PROGRESS','DELAYED','COMPLETED') NOT NULL DEFAULT 'PENDING',
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_tasks_user FOREIGN KEY (user_id)
        REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB;

ALTER TABLE tasks
    MODIFY status ENUM('PENDING','IN_PROGRESS','DELAYED','COMPLETED')
    NOT NULL DEFAULT 'PENDING';

CREATE INDEX idx_tasks_user_status   ON tasks(user_id, status);
CREATE INDEX idx_tasks_user_deadline ON tasks(user_id, deadline);

-- ---------------------------------------------------------------------
-- Timetable (generated schedule entries)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS timetable (
    schedule_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id     INT      NOT NULL,
    task_id     INT      NOT NULL,
    start_time  DATETIME NOT NULL,
    end_time    DATETIME NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_tt_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_tt_task FOREIGN KEY (task_id) REFERENCES tasks(task_id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE INDEX idx_tt_user_start ON timetable(user_id, start_time);
