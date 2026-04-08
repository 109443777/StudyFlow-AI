CREATE TABLE IF NOT EXISTS material (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
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

CREATE INDEX IF NOT EXISTS idx_material_user_id ON material (user_id);
CREATE INDEX IF NOT EXISTS idx_material_parse_status ON material (parse_status);
