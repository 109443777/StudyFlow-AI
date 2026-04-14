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

CREATE UNIQUE INDEX IF NOT EXISTS uk_file_asset_sha256_size ON file_asset (file_sha256, file_size);
CREATE INDEX IF NOT EXISTS idx_file_asset_canonical_material_id ON file_asset (canonical_material_id);
CREATE INDEX IF NOT EXISTS idx_file_asset_parse_status ON file_asset (parse_status);
