CREATE TABLE IF NOT EXISTS qa_session (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    session_name VARCHAR(128) NOT NULL,
    create_time TIMESTAMP NOT NULL,
    update_time TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_qa_session_user_id ON qa_session (user_id);
CREATE INDEX IF NOT EXISTS idx_qa_session_material_id ON qa_session (material_id);
