-- 여러 테이블에 쓰는 열거형 값은 도메인으로 한 번만 정의한다. NOT NULL은 열에 둔다.
CREATE DOMAIN source_event_type AS TEXT
    CONSTRAINT source_event_type_known CHECK (
        VALUE IN (
            'ROLE_UNASSIGNED',
            'ROLE_SUCCESSOR_MISSING',
            'ROLE_PREPARATION_INCOMPLETE',
            'ROUTINE_REPEATEDLY_OVERDUE',
            'HANDOFF_INCOMPLETE'
        )
    );

CREATE DOMAIN source_event_state AS TEXT
    CONSTRAINT source_event_state_known CHECK (VALUE IN ('ACTIVE', 'RESOLVED'));

CREATE DOMAIN brief_severity AS TEXT
    CONSTRAINT brief_severity_known CHECK (VALUE IN ('MEDIUM', 'HIGH'));

CREATE TABLE source_event_receipt (
    event_id UUID PRIMARY KEY,
    ingestion_sequence BIGINT GENERATED ALWAYS AS IDENTITY UNIQUE,
    event_type source_event_type NOT NULL,
    event_version INTEGER NOT NULL,
    source_severity VARCHAR(16),
    workspace_id UUID NOT NULL,
    season_id UUID NOT NULL,
    source_reference VARCHAR(128) NOT NULL,
    aggregate_revision BIGINT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    event_state source_event_state NOT NULL,
    processing_outcome VARCHAR(32) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT source_event_receipt_version_known CHECK (event_version >= 2),
    CONSTRAINT source_event_receipt_source_severity_known CHECK (source_severity IN ('CRITICAL', 'WARNING')),
    -- 지원 버전은 심각도가 필요하고, 이후 버전은 미지원 기록으로만 보존한다.
    CONSTRAINT source_event_receipt_supported_contract CHECK (
        processing_outcome = 'UNSUPPORTED' OR (event_version = 2 AND source_severity IS NOT NULL)
    ),
    CONSTRAINT source_event_receipt_revision_positive CHECK (aggregate_revision > 0),
    CONSTRAINT source_event_receipt_outcome_known CHECK (
        processing_outcome IN ('APPLIED', 'APPLIED_WITH_GAP', 'STALE', 'UNSUPPORTED')
    )
);

CREATE INDEX source_event_receipt_scope_sequence_idx
    ON source_event_receipt (workspace_id, season_id, ingestion_sequence DESC);

CREATE TABLE source_event_conflict (
    event_id UUID PRIMARY KEY REFERENCES source_event_receipt (event_id),
    detected_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE attention_item (
    workspace_id UUID NOT NULL,
    season_id UUID NOT NULL,
    event_type source_event_type NOT NULL,
    source_reference VARCHAR(128) NOT NULL,
    severity brief_severity NOT NULL,
    item_status source_event_state NOT NULL,
    observed_at TIMESTAMPTZ NOT NULL,
    rule_version INTEGER NOT NULL,
    last_revision BIGINT NOT NULL,
    revision_gap BOOLEAN NOT NULL,
    PRIMARY KEY (workspace_id, season_id, event_type, source_reference),
    CONSTRAINT attention_item_rule_version_positive CHECK (rule_version > 0),
    CONSTRAINT attention_item_revision_positive CHECK (last_revision > 0)
);

CREATE INDEX attention_item_edition_selection_idx
    ON attention_item (workspace_id, season_id, item_status, observed_at);

CREATE TABLE brief_edition (
    edition_id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    season_id UUID NOT NULL,
    generation BIGINT NOT NULL,
    week_start DATE NOT NULL,
    zone_id VARCHAR(64) NOT NULL,
    window_start TIMESTAMPTZ NOT NULL,
    window_end TIMESTAMPTZ NOT NULL,
    rule_version INTEGER NOT NULL,
    source_cursor BIGINT NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT brief_edition_generation UNIQUE (workspace_id, season_id, generation),
    CONSTRAINT brief_edition_generation_positive CHECK (generation > 0),
    CONSTRAINT brief_edition_source_cursor_non_negative CHECK (source_cursor >= 0),
    CONSTRAINT brief_edition_window_ordered CHECK (window_start < window_end),
    CONSTRAINT brief_edition_week_starts_monday CHECK (EXTRACT(ISODOW FROM week_start) = 1),
    CONSTRAINT brief_edition_rule_version_positive CHECK (rule_version > 0)
);

CREATE INDEX brief_edition_request_latest_idx
    ON brief_edition (workspace_id, season_id, week_start, zone_id, rule_version, generation DESC);

CREATE TABLE brief_edition_item (
    edition_id UUID NOT NULL REFERENCES brief_edition (edition_id),
    position INTEGER NOT NULL,
    source_reference VARCHAR(128) NOT NULL,
    reason_code source_event_type NOT NULL,
    severity brief_severity NOT NULL,
    item_status source_event_state NOT NULL,
    observed_at TIMESTAMPTZ NOT NULL,
    rule_version INTEGER NOT NULL,
    aggregate_revision BIGINT NOT NULL,
    revision_gap BOOLEAN NOT NULL,
    section VARCHAR(16) NOT NULL,
    PRIMARY KEY (edition_id, position),
    CONSTRAINT brief_edition_item_position_non_negative CHECK (position >= 0),
    CONSTRAINT brief_edition_item_rule_version_positive CHECK (rule_version > 0),
    CONSTRAINT brief_edition_item_revision_positive CHECK (aggregate_revision > 0),
    CONSTRAINT brief_edition_item_section_known CHECK (section IN ('CURRENT_WEEK', 'CARRY_OVER'))
);
