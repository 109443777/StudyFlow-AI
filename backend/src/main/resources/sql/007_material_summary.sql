CREATE TABLE IF NOT EXISTS material_summary (
    id BIGINT PRIMARY KEY,
    material_id BIGINT NOT NULL,
    summary_text CLOB NOT NULL,
    keywords CLOB,
    key_points CLOB,
    chapter_highlights CLOB,
    review_outline CLOB,
    create_time TIMESTAMP NOT NULL,
    update_time TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_material_summary_material_id ON material_summary (material_id);
