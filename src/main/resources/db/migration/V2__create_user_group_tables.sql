CREATE TABLE users (
    id          BIGSERIAL PRIMARY KEY,
    external_id VARCHAR(100) UNIQUE,
    provider    VARCHAR(20),
    name        VARCHAR(100) NOT NULL,
    email       VARCHAR(200),
    profile_url VARCHAR(500),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE care_groups (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE memberships (
    id            BIGSERIAL PRIMARY KEY,
    care_group_id BIGINT NOT NULL REFERENCES care_groups(id),
    user_id       BIGINT NOT NULL REFERENCES users(id),
    role          VARCHAR(20) NOT NULL CHECK (role IN ('ADMIN','GUARDIAN','PATIENT')),
    invited_by    BIGINT,
    joined_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (care_group_id, user_id)
);
CREATE INDEX idx_memberships_user  ON memberships (user_id);
CREATE INDEX idx_memberships_group ON memberships (care_group_id);

CREATE TABLE invite_codes (
    id            BIGSERIAL PRIMARY KEY,
    care_group_id BIGINT NOT NULL REFERENCES care_groups(id),
    code          VARCHAR(6) UNIQUE NOT NULL,
    created_by    BIGINT NOT NULL,
    expires_at    TIMESTAMPTZ NOT NULL,
    used_at       TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_invite_codes_code ON invite_codes (code) WHERE used_at IS NULL;
