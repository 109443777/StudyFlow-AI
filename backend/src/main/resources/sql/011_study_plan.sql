CREATE TABLE IF NOT EXISTS study_plan (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    plan_type VARCHAR(32) NOT NULL,
    plan_name VARCHAR(128) NOT NULL,
    exam_date DATE,
    plan_content LONGTEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    create_time TIMESTAMP NOT NULL,
    update_time TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_study_plan_user_id ON study_plan (user_id);
CREATE INDEX IF NOT EXISTS idx_study_plan_material_id ON study_plan (material_id);
CREATE INDEX IF NOT EXISTS idx_study_plan_type ON study_plan (plan_type);
