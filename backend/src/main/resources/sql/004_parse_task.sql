CREATE TABLE IF NOT EXISTS parse_task (
    id BIGINT PRIMARY KEY,
    material_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    task_type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    fail_reason VARCHAR(1000),
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    create_time TIMESTAMP NOT NULL,
    update_time TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_parse_task_material_id ON parse_task (material_id);
CREATE INDEX IF NOT EXISTS idx_parse_task_status ON parse_task (status);
CREATE UNIQUE INDEX IF NOT EXISTS uk_parse_task_material_id_task_type ON parse_task (material_id, task_type);
