CREATE TABLE call_history (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    called_at     TIMESTAMPTZ NOT NULL,
    method        VARCHAR(16) NOT NULL,
    path          TEXT        NOT NULL,
    query_string  TEXT,
    status        INTEGER     NOT NULL,
    response_body TEXT
);

CREATE INDEX idx_call_history_called_at ON call_history (called_at DESC);
