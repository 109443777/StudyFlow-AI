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

CREATE INDEX IF NOT EXISTS idx_material_user_id ON material (user_id);
CREATE INDEX IF NOT EXISTS idx_material_file_asset_id ON material (file_asset_id);
CREATE INDEX IF NOT EXISTS idx_material_reuse_source_material_id ON material (reuse_source_material_id);
CREATE INDEX IF NOT EXISTS idx_material_file_sha256 ON material (file_sha256);
CREATE INDEX IF NOT EXISTS idx_material_parse_status ON material (parse_status);
