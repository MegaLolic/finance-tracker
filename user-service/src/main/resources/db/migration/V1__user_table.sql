-- V1__init.sql
CREATE TABLE users (
                       id BIGSERIAL PRIMARY KEY,
                       name VARCHAR(100) NOT NULL,
                       surname VARCHAR(100),
                       password VARCHAR(255) NOT NULL,
                       email VARCHAR(255) NOT NULL,
                       CONSTRAINT uk_user_email UNIQUE (email)
);