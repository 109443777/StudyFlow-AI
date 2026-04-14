CREATE TABLE IF NOT EXISTS user (
    id BIGINT PRIMARY KEY,
    username VARCHAR(32) NOT NULL,
    password VARCHAR(255) NOT NULL,
    nickname VARCHAR(32) NOT NULL,
    avatar VARCHAR(255),
    status TINYINT NOT NULL DEFAULT 1,
    create_time TIMESTAMP NOT NULL,
    update_time TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX uk_user_username ON user (username);

CREATE TABLE IF NOT EXISTS material (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    file_asset_id BIGINT,
    reuse_source_material_id BIGINT,
    file_sha256 VARCHAR(64),
    file_name VARCHAR(255) NOT NULL,
    file_type VARCHAR(32) NOT NULL,
    file_size BIGINT NOT NULL,
    object_key VARCHAR(255) NOT NULL,
    material_type VARCHAR(32) NOT NULL,
    parse_status VARCHAR(32) NOT NULL,
    upload_status VARCHAR(32) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    create_time TIMESTAMP NOT NULL,
    update_time TIMESTAMP NOT NULL
);

CREATE INDEX idx_material_user_id ON material (user_id);
CREATE INDEX idx_material_file_asset_id ON material (file_asset_id);
CREATE INDEX idx_material_reuse_source_material_id ON material (reuse_source_material_id);
CREATE INDEX idx_material_file_sha256 ON material (file_sha256);
CREATE INDEX idx_material_parse_status ON material (parse_status);

CREATE TABLE IF NOT EXISTS file_asset (
    id BIGINT PRIMARY KEY,
    canonical_material_id BIGINT,
    file_sha256 VARCHAR(64) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_type VARCHAR(32) NOT NULL,
    file_size BIGINT NOT NULL,
    object_key VARCHAR(255) NOT NULL,
    material_type VARCHAR(32) NOT NULL,
    asset_status VARCHAR(32) NOT NULL,
    parse_status VARCHAR(32) NOT NULL,
    create_time TIMESTAMP NOT NULL,
    update_time TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX uk_file_asset_sha256_size ON file_asset (file_sha256, file_size);
CREATE INDEX idx_file_asset_canonical_material_id ON file_asset (canonical_material_id);
CREATE INDEX idx_file_asset_parse_status ON file_asset (parse_status);

CREATE TABLE IF NOT EXISTS upload_session (
    id BIGINT PRIMARY KEY,
    upload_id VARCHAR(64) NOT NULL,
    storage_upload_id VARCHAR(128) NOT NULL,
    material_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_type VARCHAR(32) NOT NULL,
    file_size BIGINT NOT NULL,
    file_md5 VARCHAR(32) NOT NULL,
    file_sha256 VARCHAR(64) NOT NULL,
    part_size BIGINT NOT NULL,
    total_parts INT NOT NULL,
    uploaded_parts INT NOT NULL DEFAULT 0,
    object_key VARCHAR(255) NOT NULL,
    material_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    fail_reason VARCHAR(512) NULL,
    expire_time TIMESTAMP NULL,
    create_time TIMESTAMP NOT NULL,
    update_time TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX uk_upload_session_upload_id ON upload_session (upload_id);
CREATE INDEX idx_upload_session_user_id ON upload_session (user_id);

CREATE TABLE IF NOT EXISTS parse_task (
    id BIGINT PRIMARY KEY,
    material_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    task_type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    fail_reason VARCHAR(1000),
    start_time TIMESTAMP NULL,
    end_time TIMESTAMP NULL,
    create_time TIMESTAMP NOT NULL,
    update_time TIMESTAMP NOT NULL
);

CREATE INDEX idx_parse_task_material_id ON parse_task (material_id);
CREATE INDEX idx_parse_task_status ON parse_task (status);
CREATE UNIQUE INDEX uk_parse_task_material_id_task_type ON parse_task (material_id, task_type);

CREATE TABLE IF NOT EXISTS material_content (
    id BIGINT PRIMARY KEY,
    material_id BIGINT NOT NULL,
    content_type VARCHAR(32) NOT NULL,
    raw_text LONGTEXT NOT NULL,
    cleaned_text LONGTEXT NOT NULL,
    chapter_info LONGTEXT NULL,
    create_time TIMESTAMP NOT NULL,
    update_time TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX uk_material_content_material_id ON material_content (material_id);

CREATE TABLE IF NOT EXISTS media_transcript (
    id BIGINT PRIMARY KEY,
    material_id BIGINT NOT NULL,
    media_type VARCHAR(16) NOT NULL,
    transcript_text LONGTEXT NOT NULL,
    transcript_segments LONGTEXT NULL,
    duration BIGINT NULL,
    transcript_status VARCHAR(16) NOT NULL,
    create_time TIMESTAMP NOT NULL,
    update_time TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX uk_media_transcript_material_id ON media_transcript (material_id);

CREATE TABLE IF NOT EXISTS material_summary (
    id BIGINT PRIMARY KEY,
    material_id BIGINT NOT NULL,
    summary_text LONGTEXT NOT NULL,
    keywords LONGTEXT NULL,
    key_points LONGTEXT NULL,
    chapter_highlights LONGTEXT NULL,
    review_outline LONGTEXT NULL,
    create_time TIMESTAMP NOT NULL,
    update_time TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX uk_material_summary_material_id ON material_summary (material_id);

CREATE TABLE IF NOT EXISTS material_chunk (
    id BIGINT PRIMARY KEY,
    material_id BIGINT NOT NULL,
    chunk_index INT NOT NULL,
    chunk_text LONGTEXT NOT NULL,
    token_count INT NOT NULL DEFAULT 0,
    embedding_vector LONGTEXT NULL,
    create_time TIMESTAMP NOT NULL,
    update_time TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX uk_material_chunk_material_index ON material_chunk (material_id, chunk_index);
CREATE INDEX idx_material_chunk_material_id ON material_chunk (material_id);

CREATE TABLE IF NOT EXISTS qa_session (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    session_name VARCHAR(128) NOT NULL,
    create_time TIMESTAMP NOT NULL,
    update_time TIMESTAMP NOT NULL
);

CREATE INDEX idx_qa_session_user_id ON qa_session (user_id);
CREATE INDEX idx_qa_session_material_id ON qa_session (material_id);

CREATE TABLE IF NOT EXISTS qa_message (
    id BIGINT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    role VARCHAR(32) NOT NULL,
    content LONGTEXT NOT NULL,
    reference_chunks LONGTEXT NULL,
    create_time TIMESTAMP NOT NULL,
    update_time TIMESTAMP NOT NULL
);

CREATE INDEX idx_qa_message_session_id ON qa_message (session_id);
CREATE INDEX idx_qa_message_session_create_time ON qa_message (session_id, create_time);

CREATE TABLE IF NOT EXISTS qa_session_material (
    id BIGINT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    create_time TIMESTAMP NOT NULL,
    update_time TIMESTAMP NOT NULL,
    UNIQUE KEY uk_qa_session_material (session_id, material_id),
    KEY idx_qa_session_material_session_id (session_id),
    KEY idx_qa_session_material_user_id (user_id),
    KEY idx_qa_session_material_material_id (material_id)
);

CREATE TABLE IF NOT EXISTS study_plan (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    plan_type VARCHAR(32) NOT NULL,
    plan_name VARCHAR(128) NOT NULL,
    exam_date DATE NULL,
    plan_content LONGTEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    create_time TIMESTAMP NOT NULL,
    update_time TIMESTAMP NOT NULL
);

CREATE INDEX idx_study_plan_user_id ON study_plan (user_id);
CREATE INDEX idx_study_plan_material_id ON study_plan (material_id);
CREATE INDEX idx_study_plan_type ON study_plan (plan_type);

CREATE TABLE IF NOT EXISTS task_failure_record (
    id BIGINT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    task_type VARCHAR(32) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    fail_reason VARCHAR(1000) NULL,
    record_status VARCHAR(32) NOT NULL,
    create_time TIMESTAMP NOT NULL,
    update_time TIMESTAMP NOT NULL
);

CREATE INDEX idx_task_failure_record_task_id ON task_failure_record (task_id);
CREATE INDEX idx_task_failure_record_user_id ON task_failure_record (user_id);
