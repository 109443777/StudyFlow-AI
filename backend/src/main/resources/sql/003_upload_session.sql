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

CREATE UNIQUE INDEX IF NOT EXISTS uk_upload_session_upload_id ON upload_session (upload_id);
CREATE INDEX IF NOT EXISTS idx_upload_session_user_id ON upload_session (user_id);
