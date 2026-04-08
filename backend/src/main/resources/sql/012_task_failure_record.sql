CREATE TABLE IF NOT EXISTS task_failure_record (
    id BIGINT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    task_type VARCHAR(32) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    fail_reason VARCHAR(1000),
    record_status VARCHAR(32) NOT NULL,
    create_time TIMESTAMP NOT NULL,
    update_time TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_task_failure_record_task_id ON task_failure_record (task_id);
CREATE INDEX IF NOT EXISTS idx_task_failure_record_user_id ON task_failure_record (user_id);
