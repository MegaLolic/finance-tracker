CREATE TABLE user_lifecycle (
    user_id BIGINT PRIMARY KEY,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);
