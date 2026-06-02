ALTER TABLE users
    ADD COLUMN expo_push_token VARCHAR(256),
    ADD COLUMN push_provider   VARCHAR(20) NOT NULL DEFAULT 'EXPO';

ALTER TABLE users
    ADD CONSTRAINT chk_users_push_provider CHECK (push_provider IN ('EXPO', 'FCM'));

CREATE INDEX idx_users_push_token ON users (expo_push_token) WHERE expo_push_token IS NOT NULL;
