CREATE TABLE IF NOT EXISTS material_content (
    id BIGINT PRIMARY KEY,
    material_id BIGINT NOT NULL,
    content_type VARCHAR(32) NOT NULL,
    raw_text CLOB NOT NULL,
    cleaned_text CLOB NOT NULL,
    chapter_info CLOB,
    create_time TIMESTAMP NOT NULL,
    update_time TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_material_content_material_id ON material_content (material_id);
