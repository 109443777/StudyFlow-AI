CREATE TABLE IF NOT EXISTS material_chunk (
    id BIGINT PRIMARY KEY,
    material_id BIGINT NOT NULL,
    chunk_index INT NOT NULL,
    chunk_text LONGTEXT NOT NULL,
    token_count INT NOT NULL DEFAULT 0,
    embedding_vector LONGTEXT,
    create_time TIMESTAMP NOT NULL,
    update_time TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_material_chunk_material_index ON material_chunk (material_id, chunk_index);
CREATE INDEX IF NOT EXISTS idx_material_chunk_material_id ON material_chunk (material_id);
