CREATE TABLE IF NOT EXISTS media_transcript (
    id BIGINT PRIMARY KEY,
    material_id BIGINT NOT NULL,
    media_type VARCHAR(16) NOT NULL,
    transcript_text CLOB NOT NULL,
    transcript_segments CLOB,
    duration BIGINT,
    transcript_status VARCHAR(16) NOT NULL,
    create_time TIMESTAMP NOT NULL,
    update_time TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_media_transcript_material_id ON media_transcript (material_id);
