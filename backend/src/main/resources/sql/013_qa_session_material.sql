CREATE TABLE IF NOT EXISTS qa_session_material (
    id BIGINT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    create_time TIMESTAMP NOT NULL,
    update_time TIMESTAMP NOT NULL,
    UNIQUE KEY uk_qa_session_material (session_id, material_id),
    KEY idx_qa_session_material_session_id (session_id),
    KEY idx_qa_session_material_user_id (user_id),
    KEY idx_qa_session_material_material_id (material_id)
);
