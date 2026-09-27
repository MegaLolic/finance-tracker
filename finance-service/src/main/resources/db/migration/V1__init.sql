-- Таблица счетов (Accounts)
CREATE TABLE accounts (
                          id BIGSERIAL PRIMARY KEY,
                          user_id BIGINT NOT NULL,
                          name VARCHAR(255) NOT NULL,
                          balance DECIMAL(19, 1) NOT NULL DEFAULT 0.0,
                          currency VARCHAR(20) NOT NULL DEFAULT 'BYN'
);

CREATE INDEX idx_accounts_user_id ON accounts(user_id);

-- Таблица категорий (Categories) с привязкой к пользователю
CREATE TABLE categories (
                            id BIGSERIAL PRIMARY KEY,
                            user_id BIGINT NOT NULL,
                            name VARCHAR(255) NOT NULL,
                            type VARCHAR(20) NOT NULL DEFAULT 'EXPENSE'
);

CREATE INDEX idx_categories_user_id ON categories(user_id);
CREATE UNIQUE INDEX idx_categories_user_name ON categories(user_id, name);

CREATE TABLE transactions (
                              id BIGSERIAL PRIMARY KEY,
                              user_id BIGINT NOT NULL,
                              account_id BIGINT NOT NULL,
                              category_id BIGINT NOT NULL,
                              amount DECIMAL(19, 2) NOT NULL,
                              description TEXT,
                              created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              CONSTRAINT fk_transactions_account
                                  FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE CASCADE,
                              CONSTRAINT fk_transactions_category
                                  FOREIGN KEY (category_id) REFERENCES categories(id)
);

CREATE INDEX idx_transactions_user_id ON transactions(user_id);
CREATE INDEX idx_transactions_created_at ON transactions(created_at);