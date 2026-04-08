CREATE TABLE IF NOT EXISTS qa_message (
    id BIGINT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    role VARCHAR(32) NOT NULL,
    content LONGTEXT NOT NULL,
    reference_chunks LONGTEXT,
    create_time TIMESTAMP NOT NULL,
    update_time TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_qa_message_session_id ON qa_message (session_id);
CREATE INDEX IF NOT EXISTS idx_qa_message_session_create_time ON qa_message (session_id, create_time);
