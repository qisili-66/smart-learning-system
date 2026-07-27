-- Smart Learning System schema.
-- Creates the database and all tables.
-- Usage: mysql --default-character-set=utf8mb4 -u root -p < docs/sql/01_schema.sql

-- Complete initial schema for the Smart Learning System.
-- Safe to run on an empty database; it uses CREATE TABLE IF NOT EXISTS and does not drop existing data.
-- Usage:
--   mysql --default-character-set=utf8mb4 -uroot -proot < docs/sql/01_schema.sql

CREATE DATABASE IF NOT EXISTS smart_learning_system
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE smart_learning_system;
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sys_user (
    user_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'User id',
    username VARCHAR(50) NOT NULL COMMENT 'Unique username',
    password VARCHAR(255) NOT NULL COMMENT 'BCrypt password',
    real_name VARCHAR(50) NULL COMMENT 'Real name',
    role TINYINT NOT NULL DEFAULT 1 COMMENT '1=student, 2=admin',
    grade VARCHAR(20) NULL COMMENT 'Grade',
    subject VARCHAR(30) NULL COMMENT 'Main subject',
    phone VARCHAR(20) NULL COMMENT 'Phone',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1=normal, 0=disabled',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_sys_user_username (username),
    KEY idx_sys_user_role (role),
    KEY idx_sys_user_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='System users';

CREATE TABLE IF NOT EXISTS user_profile (
    profile_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Profile id',
    user_id BIGINT NOT NULL COMMENT 'Owner user id',
    ability_score DECIMAL(5,2) NOT NULL DEFAULT 0.00 COMMENT 'Ability score 0-100',
    knowledge_mastery DECIMAL(5,2) NOT NULL DEFAULT 0.00 COMMENT 'Knowledge mastery 0-100',
    study_habit TEXT NULL COMMENT 'Study habit summary',
    weak_points TEXT NULL COMMENT 'Weak knowledge points',
    preference TEXT NULL COMMENT 'Learning preferences',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_profile_user (user_id),
    CONSTRAINT fk_user_profile_user FOREIGN KEY (user_id) REFERENCES sys_user (user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Student learning profile';

CREATE TABLE IF NOT EXISTS learning_resource (
    resource_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Resource id',
    resource_name VARCHAR(200) NOT NULL COMMENT 'Resource name',
    resource_type TINYINT NOT NULL COMMENT '1=video, 2=courseware, 3=exercise, 4=mindmap, 5=handbook',
    subject VARCHAR(50) NULL COMMENT 'Subject',
    knowledge_point VARCHAR(255) NULL COMMENT 'Knowledge point',
    textbook_version VARCHAR(100) NULL COMMENT 'Textbook version',
    file_url VARCHAR(500) NULL COMMENT 'File or external URL',
    file_size BIGINT NULL COMMENT 'File size in bytes',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1=enabled, 0=disabled',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_resource_subject_point (subject, knowledge_point),
    KEY idx_resource_type_status (resource_type, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Learning resources';

CREATE TABLE IF NOT EXISTS study_plan (
    plan_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Study plan id',
    user_id BIGINT NOT NULL COMMENT 'Owner user id',
    plan_name VARCHAR(100) NOT NULL COMMENT 'Plan name',
    subject VARCHAR(50) NULL COMMENT 'Subject',
    target_desc VARCHAR(1000) NULL COMMENT 'Learning target',
    current_score DECIMAL(6,2) NULL COMMENT 'Current score before plan',
    target_score DECIMAL(6,2) NULL COMMENT 'Target score',
    daily_minutes INT NOT NULL DEFAULT 40 COMMENT 'Available learning minutes per day',
    ai_provider VARCHAR(32) NOT NULL DEFAULT 'auto' COMMENT 'AI provider used to generate path',
    ai_plan_summary VARCHAR(1000) NULL COMMENT 'AI generated path summary',
    start_date DATE NULL COMMENT 'Start date',
    end_date DATE NULL COMMENT 'End date',
    plan_status TINYINT NOT NULL DEFAULT 1 COMMENT '1=running, 2=finished, 3=terminated',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_study_plan_user_status (user_id, plan_status),
    CONSTRAINT fk_study_plan_user FOREIGN KEY (user_id) REFERENCES sys_user (user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Personalized study plans';

CREATE TABLE IF NOT EXISTS question_bank (
    question_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Question id',
    subject VARCHAR(50) NOT NULL COMMENT 'Subject',
    knowledge_point VARCHAR(255) NULL COMMENT 'Knowledge point',
    difficulty TINYINT NOT NULL DEFAULT 1 COMMENT '1=basic, 2=normal, 3=advanced',
    question_type TINYINT NOT NULL DEFAULT 1 COMMENT '1=single, 2=multi, 3=blank, 4=subjective',
    question_text TEXT NOT NULL COMMENT 'Question text',
    options TEXT NULL COMMENT 'Options separated by |',
    answer TEXT NULL COMMENT 'Reference answer',
    analysis TEXT NULL COMMENT 'Analysis',
    scoring_points TEXT NULL COMMENT 'Subjective question scoring points, one point per line or separated by ;',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_question_subject_point (subject, knowledge_point),
    KEY idx_question_type_difficulty (question_type, difficulty)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Question bank';

CREATE TABLE IF NOT EXISTS study_record (
    record_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Study record id',
    user_id BIGINT NOT NULL COMMENT 'Owner user id',
    resource_id BIGINT NULL COMMENT 'Resource id',
    study_type TINYINT NOT NULL COMMENT '1=resource, 2=practice, 3=assessment, 4=qa',
    study_duration INT NOT NULL DEFAULT 0 COMMENT 'Duration in minutes',
    finish_status TINYINT NOT NULL DEFAULT 0 COMMENT '0=unfinished, 1=finished',
    study_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Study time',
    KEY idx_study_record_user_time (user_id, study_time),
    KEY idx_study_record_resource (resource_id),
    CONSTRAINT fk_study_record_user FOREIGN KEY (user_id) REFERENCES sys_user (user_id) ON DELETE CASCADE,
    CONSTRAINT fk_study_record_resource FOREIGN KEY (resource_id) REFERENCES learning_resource (resource_id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Study records';

CREATE TABLE IF NOT EXISTS wrong_question (
    wrong_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Wrong question id',
    user_id BIGINT NOT NULL COMMENT 'Owner user id',
    question_id BIGINT NOT NULL COMMENT 'Question id',
    wrong_answer TEXT NULL COMMENT 'Wrong answer',
    wrong_reason TINYINT NULL COMMENT '1=calculation, 2=concept, 3=reading, 4=method',
    wrong_count INT NOT NULL DEFAULT 1 COMMENT 'Wrong count',
    is_mastered TINYINT NOT NULL DEFAULT 0 COMMENT '0=not mastered, 1=mastered',
    first_wrong_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_review_time DATETIME NULL,
    UNIQUE KEY uk_wrong_user_question (user_id, question_id),
    KEY idx_wrong_user_mastered (user_id, is_mastered),
    CONSTRAINT fk_wrong_question_user FOREIGN KEY (user_id) REFERENCES sys_user (user_id) ON DELETE CASCADE,
    CONSTRAINT fk_wrong_question_question FOREIGN KEY (question_id) REFERENCES question_bank (question_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Wrong question book';

CREATE TABLE IF NOT EXISTS assessment (
    assessment_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Assessment id',
    user_id BIGINT NOT NULL COMMENT 'Owner user id',
    assessment_type TINYINT NOT NULL DEFAULT 1 COMMENT '1=practice, 2=quiz, 3=exam',
    subject VARCHAR(50) NULL COMMENT 'Subject',
    knowledge_scope VARCHAR(500) NULL COMMENT 'Knowledge scope',
    difficulty TINYINT NULL COMMENT 'Target difficulty',
    total_score DECIMAL(6,2) NOT NULL DEFAULT 100.00 COMMENT 'Total score',
    user_score DECIMAL(6,2) NULL COMMENT 'User score',
    start_time DATETIME NULL,
    end_time DATETIME NULL,
    assessment_status TINYINT NOT NULL DEFAULT 1 COMMENT '1=in progress, 2=submitted',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_assessment_user_time (user_id, create_time),
    KEY idx_assessment_subject (subject),
    CONSTRAINT fk_assessment_user FOREIGN KEY (user_id) REFERENCES sys_user (user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Learning assessments';

CREATE TABLE IF NOT EXISTS study_task (
    task_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Daily task id',
    plan_id BIGINT NULL COMMENT 'Study plan id',
    user_id BIGINT NOT NULL COMMENT 'Owner user id',
    task_date DATE NOT NULL COMMENT 'Task date',
    task_type TINYINT NOT NULL COMMENT '1=learn, 2=practice, 3=review, 4=expand',
    step_type VARCHAR(32) NULL COMMENT 'diagnostic_test/practice/wrong_review/resource_study/stage_test',
    title VARCHAR(200) NOT NULL COMMENT 'Task title',
    description VARCHAR(1000) NULL COMMENT 'Task description',
    knowledge_point VARCHAR(255) NULL COMMENT 'Target knowledge point',
    resource_id BIGINT NULL COMMENT 'Matched resource id',
    difficulty TINYINT NOT NULL DEFAULT 1 COMMENT '1=basic, 2=normal, 3=advanced',
    estimated_minutes INT NOT NULL DEFAULT 10 COMMENT 'Estimated minutes',
    finish_status TINYINT NOT NULL DEFAULT 0 COMMENT '0=pending, 1=finished',
    correct_rate DECIMAL(5,2) NULL COMMENT 'Correct rate percent',
    target_correct_rate DECIMAL(5,2) NULL COMMENT 'Required correct rate to unlock next step',
    unlock_condition VARCHAR(500) NULL COMMENT 'Unlock rule description',
    action_path VARCHAR(255) NULL COMMENT 'Frontend execution route',
    ai_reason VARCHAR(1000) NULL COMMENT 'AI reason for this step',
    step_order INT NOT NULL DEFAULT 1 COMMENT 'Order in executable path',
    priority INT NOT NULL DEFAULT 1 COMMENT 'Smaller value means higher priority',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_study_task_user_date (user_id, task_date),
    KEY idx_study_task_plan_date (plan_id, task_date),
    KEY idx_study_task_resource (resource_id),
    CONSTRAINT fk_study_task_plan FOREIGN KEY (plan_id) REFERENCES study_plan (plan_id) ON DELETE CASCADE,
    CONSTRAINT fk_study_task_user FOREIGN KEY (user_id) REFERENCES sys_user (user_id) ON DELETE CASCADE,
    CONSTRAINT fk_study_task_resource FOREIGN KEY (resource_id) REFERENCES learning_resource (resource_id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Daily personalized study tasks';

CREATE TABLE IF NOT EXISTS user_profile_correction_log (
    log_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Correction log id',
    user_id BIGINT NOT NULL COMMENT 'Owner user id',
    field_name VARCHAR(64) NOT NULL COMMENT 'Corrected user_profile field',
    old_value TEXT NULL COMMENT 'Value before correction',
    new_value TEXT NULL COMMENT 'Value after correction',
    operator_type TINYINT NOT NULL DEFAULT 1 COMMENT '1=user manual correction',
    reason VARCHAR(500) NULL COMMENT 'Correction reason',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_profile_correction_user_time (user_id, create_time),
    KEY idx_profile_correction_user_field (user_id, field_name),
    CONSTRAINT fk_profile_correction_user FOREIGN KEY (user_id) REFERENCES sys_user (user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='User profile correction logs';

CREATE TABLE IF NOT EXISTS assessment_answer (
    answer_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Answer detail id',
    assessment_id BIGINT NOT NULL COMMENT 'Assessment id',
    user_id BIGINT NOT NULL COMMENT 'Owner user id',
    question_id BIGINT NOT NULL COMMENT 'Question id',
    user_answer TEXT NULL COMMENT 'Student answer',
    correct_answer TEXT NULL COMMENT 'Reference answer',
    is_correct TINYINT NOT NULL DEFAULT 0 COMMENT '0=wrong or pending, 1=correct',
    score DECIMAL(6,2) NOT NULL DEFAULT 0.00 COMMENT 'Question score',
    max_score DECIMAL(6,2) NOT NULL DEFAULT 0.00 COMMENT 'Question max score',
    score_status TINYINT NOT NULL DEFAULT 1 COMMENT '1=auto scored, 2=pending manual review, 3=reviewed',
    review_status TINYINT NOT NULL DEFAULT 0 COMMENT '0=not reviewed, 1=reviewed',
    review_comment VARCHAR(500) NULL COMMENT 'Manual review comment',
    scoring_detail VARCHAR(500) NULL COMMENT 'Scoring explanation',
    ai_score DECIMAL(6,2) NULL COMMENT 'AI semantic score snapshot',
    ai_confidence DECIMAL(5,2) NULL COMMENT 'AI semantic scoring confidence percent',
    scoring_points_snapshot TEXT NULL COMMENT 'Scoring points used when submitted',
    question_use_seconds INT NOT NULL DEFAULT 0 COMMENT 'Seconds spent on this question',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_assessment_answer_assessment (assessment_id),
    KEY idx_assessment_answer_user_assessment (user_id, assessment_id),
    KEY idx_assessment_answer_question (question_id),
    CONSTRAINT fk_assessment_answer_assessment FOREIGN KEY (assessment_id) REFERENCES assessment (assessment_id) ON DELETE CASCADE,
    CONSTRAINT fk_assessment_answer_user FOREIGN KEY (user_id) REFERENCES sys_user (user_id) ON DELETE CASCADE,
    CONSTRAINT fk_assessment_answer_question FOREIGN KEY (question_id) REFERENCES question_bank (question_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Assessment answer details and scoring records';

CREATE TABLE IF NOT EXISTS wrong_question_review_plan (
    plan_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Review plan id',
    wrong_id BIGINT NOT NULL COMMENT 'Wrong question id',
    user_id BIGINT NOT NULL COMMENT 'Owner user id',
    personal_note VARCHAR(1000) NULL COMMENT 'Student note',
    review_cycle_days INT NOT NULL DEFAULT 3 COMMENT 'Review cycle in days',
    next_review_time DATETIME NULL COMMENT 'Next review time',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_wrong_review_plan_wrong (wrong_id),
    KEY idx_wrong_review_plan_user_next (user_id, next_review_time),
    CONSTRAINT fk_wrong_review_plan_wrong FOREIGN KEY (wrong_id) REFERENCES wrong_question (wrong_id) ON DELETE CASCADE,
    CONSTRAINT fk_wrong_review_plan_user FOREIGN KEY (user_id) REFERENCES sys_user (user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Wrong question notes and review cycle';

CREATE TABLE IF NOT EXISTS qa_conversation (
    conversation_id VARCHAR(64) PRIMARY KEY COMMENT 'Conversation id shared by frontend/backend/AI',
    user_id BIGINT NOT NULL COMMENT 'Owner user id',
    title VARCHAR(120) NOT NULL DEFAULT '新的答疑会话' COMMENT 'Conversation title',
    subject VARCHAR(50) NULL COMMENT 'Subject',
    message_count INT NOT NULL DEFAULT 0 COMMENT 'Stored message count',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1=active',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_qa_conversation_user_time (user_id, update_time),
    CONSTRAINT fk_qa_conversation_user FOREIGN KEY (user_id) REFERENCES sys_user (user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Persistent AI QA conversations';

CREATE TABLE IF NOT EXISTS qa_message (
    message_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Message id',
    conversation_id VARCHAR(64) NOT NULL COMMENT 'Conversation id',
    user_id BIGINT NOT NULL COMMENT 'Owner user id',
    role VARCHAR(16) NOT NULL COMMENT 'user/assistant',
    content_type VARCHAR(16) NOT NULL DEFAULT 'text' COMMENT 'text/image/voice',
    content LONGTEXT NULL COMMENT 'Message content or recognized question text',
    audio_file_name VARCHAR(255) NOT NULL DEFAULT '' COMMENT 'Stored voice file name',
    recognized_text TEXT NULL COMMENT 'ASR raw text',
    corrected_text TEXT NULL COMMENT 'Student corrected ASR text',
    requires_confirmation TINYINT NOT NULL DEFAULT 0 COMMENT '1=homework/exam guardrail needs confirmation',
    confirmed TINYINT NOT NULL DEFAULT 0 COMMENT '1=student confirmed complete answer',
    latency_ms BIGINT NULL COMMENT 'AI first response latency in ms',
    qa_quality_status TINYINT NULL COMMENT '1=heuristic pass, 2=risk/fail',
    model VARCHAR(100) NULL COMMENT 'AI model name',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_qa_message_conversation_time (conversation_id, message_id),
    KEY idx_qa_message_user_time (user_id, create_time),
    KEY idx_qa_message_user_type (user_id, content_type),
    CONSTRAINT fk_qa_message_conversation FOREIGN KEY (conversation_id) REFERENCES qa_conversation (conversation_id) ON DELETE CASCADE,
    CONSTRAINT fk_qa_message_user FOREIGN KEY (user_id) REFERENCES sys_user (user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Persistent AI QA messages, voice metadata, and evaluation metrics';

CREATE TABLE IF NOT EXISTS personal_data_clear_log (
    log_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Clear log id',
    user_id BIGINT NOT NULL COMMENT 'Owner user id',
    clear_scope VARCHAR(1000) NOT NULL COMMENT 'Cleared data scopes',
    confirmation_text VARCHAR(100) NOT NULL COMMENT 'Second confirmation text',
    counts_json TEXT NULL COMMENT 'Deleted row/file counts as JSON',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_personal_clear_log_user_time (user_id, create_time),
    CONSTRAINT fk_personal_clear_log_user FOREIGN KEY (user_id) REFERENCES sys_user (user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Personal data clear audit logs';

CREATE TABLE IF NOT EXISTS personal_data_export_log (
    export_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Export log id',
    user_id BIGINT NOT NULL COMMENT 'Owner user id',
    file_name VARCHAR(255) NOT NULL COMMENT 'Generated ZIP file name',
    file_path VARCHAR(1000) NOT NULL COMMENT 'Server-side absolute file path',
    file_size BIGINT NOT NULL DEFAULT 0 COMMENT 'ZIP file size in bytes',
    token_hash VARCHAR(64) NOT NULL COMMENT 'SHA-256 hash of short-lived download token',
    expires_at DATETIME NOT NULL COMMENT 'Download URL expiration time',
    max_download_count INT NOT NULL DEFAULT 3 COMMENT 'Maximum allowed downloads',
    download_count INT NOT NULL DEFAULT 0 COMMENT 'Successful download count',
    last_download_time DATETIME NULL COMMENT 'Last successful download time',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1=active, 2=expired, 3=download limit reached, 4=deleted',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_personal_export_user_time (user_id, create_time),
    KEY idx_personal_export_user_file (user_id, file_name),
    KEY idx_personal_export_expires (status, expires_at),
    CONSTRAINT fk_personal_export_log_user FOREIGN KEY (user_id) REFERENCES sys_user (user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Personal data export audit logs';

-- Idempotent upgrade block for databases that already had older tables.
-- Keep this script as the single non-seed schema entry point; seed/demo data remain in separate *_seed.sql files.
DROP PROCEDURE IF EXISTS add_column_if_missing;
DELIMITER //
CREATE PROCEDURE add_column_if_missing(
    IN target_table VARCHAR(64),
    IN target_column VARCHAR(64),
    IN alter_sql TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = target_table
          AND COLUMN_NAME = target_column
    ) THEN
        SET @ddl = alter_sql;
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

CALL add_column_if_missing(
    'question_bank',
    'scoring_points',
    'ALTER TABLE question_bank ADD COLUMN scoring_points TEXT NULL COMMENT ''Subjective question scoring points, one point per line or separated by ;'' AFTER analysis'
);
CALL add_column_if_missing(
    'assessment_answer',
    'ai_score',
    'ALTER TABLE assessment_answer ADD COLUMN ai_score DECIMAL(6,2) NULL COMMENT ''AI semantic score snapshot'' AFTER scoring_detail'
);
CALL add_column_if_missing(
    'assessment_answer',
    'ai_confidence',
    'ALTER TABLE assessment_answer ADD COLUMN ai_confidence DECIMAL(5,2) NULL COMMENT ''AI semantic scoring confidence percent'' AFTER ai_score'
);
CALL add_column_if_missing(
    'assessment_answer',
    'scoring_points_snapshot',
    'ALTER TABLE assessment_answer ADD COLUMN scoring_points_snapshot TEXT NULL COMMENT ''Scoring points used when submitted'' AFTER ai_confidence'
);
CALL add_column_if_missing(
    'assessment_answer',
    'question_use_seconds',
    'ALTER TABLE assessment_answer ADD COLUMN question_use_seconds INT NOT NULL DEFAULT 0 COMMENT ''Seconds spent on this question'' AFTER scoring_points_snapshot'
);

CALL add_column_if_missing(
    'study_plan',
    'current_score',
    'ALTER TABLE study_plan ADD COLUMN current_score DECIMAL(6,2) NULL COMMENT ''Current score before plan'' AFTER target_desc'
);
CALL add_column_if_missing(
    'study_plan',
    'target_score',
    'ALTER TABLE study_plan ADD COLUMN target_score DECIMAL(6,2) NULL COMMENT ''Target score'' AFTER current_score'
);
CALL add_column_if_missing(
    'study_plan',
    'daily_minutes',
    'ALTER TABLE study_plan ADD COLUMN daily_minutes INT NOT NULL DEFAULT 40 COMMENT ''Available learning minutes per day'' AFTER target_score'
);
CALL add_column_if_missing(
    'study_plan',
    'ai_provider',
    'ALTER TABLE study_plan ADD COLUMN ai_provider VARCHAR(32) NOT NULL DEFAULT ''auto'' COMMENT ''AI provider used to generate path'' AFTER daily_minutes'
);
CALL add_column_if_missing(
    'study_plan',
    'ai_plan_summary',
    'ALTER TABLE study_plan ADD COLUMN ai_plan_summary VARCHAR(1000) NULL COMMENT ''AI generated path summary'' AFTER ai_provider'
);
CALL add_column_if_missing(
    'study_task',
    'step_type',
    'ALTER TABLE study_task ADD COLUMN step_type VARCHAR(32) NULL COMMENT ''diagnostic_test/practice/wrong_review/resource_study/stage_test'' AFTER task_type'
);
CALL add_column_if_missing(
    'study_task',
    'target_correct_rate',
    'ALTER TABLE study_task ADD COLUMN target_correct_rate DECIMAL(5,2) NULL COMMENT ''Required correct rate to unlock next step'' AFTER correct_rate'
);
CALL add_column_if_missing(
    'study_task',
    'unlock_condition',
    'ALTER TABLE study_task ADD COLUMN unlock_condition VARCHAR(500) NULL COMMENT ''Unlock rule description'' AFTER target_correct_rate'
);
CALL add_column_if_missing(
    'study_task',
    'action_path',
    'ALTER TABLE study_task ADD COLUMN action_path VARCHAR(255) NULL COMMENT ''Frontend execution route'' AFTER unlock_condition'
);
CALL add_column_if_missing(
    'study_task',
    'ai_reason',
    'ALTER TABLE study_task ADD COLUMN ai_reason VARCHAR(1000) NULL COMMENT ''AI reason for this step'' AFTER action_path'
);
CALL add_column_if_missing(
    'study_task',
    'step_order',
    'ALTER TABLE study_task ADD COLUMN step_order INT NOT NULL DEFAULT 1 COMMENT ''Order in executable path'' AFTER ai_reason'
);

DROP PROCEDURE IF EXISTS add_column_if_missing;
