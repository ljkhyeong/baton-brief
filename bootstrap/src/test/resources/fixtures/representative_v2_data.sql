INSERT INTO attention_item (
    item_id, workspace_id, season_id, event_type, source_reference, reason_code,
    severity, item_status, observed_at, projected_at, rule_version,
    last_revision, revision_gap
) VALUES (
    '81000000-0000-0000-0000-000000000001',
    '81000000-0000-0000-0000-000000000002',
    '81000000-0000-0000-0000-000000000003',
    'HANDOFF_BLOCKED', 'handoff:legacy', 'HANDOFF_BLOCKED',
    'HIGH', 'ACTIVE', TIMESTAMPTZ '2026-08-12T09:00:00Z',
    TIMESTAMPTZ '2026-08-12T09:00:01Z', 1, 1, FALSE
);

INSERT INTO brief_edition (
    edition_id, workspace_id, season_id, generation, week_start, zone_id,
    window_start, window_end, rule_version, source_cursor, state_fingerprint,
    generated_at
) VALUES (
    '82000000-0000-0000-0000-000000000001',
    '81000000-0000-0000-0000-000000000002',
    '81000000-0000-0000-0000-000000000003',
    1, DATE '2026-08-10', 'Asia/Seoul',
    TIMESTAMPTZ '2026-08-09T15:00:00Z', TIMESTAMPTZ '2026-08-16T15:00:00Z',
    1, 0, 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
    TIMESTAMPTZ '2026-08-12T09:00:02Z'
);

INSERT INTO brief_edition_item (
    edition_id, position, source_reference, reason_code, severity,
    item_status, observed_at, rule_version
) VALUES (
    '82000000-0000-0000-0000-000000000001', 0, 'handoff:legacy',
    'HANDOFF_BLOCKED', 'HIGH', 'ACTIVE',
    TIMESTAMPTZ '2026-08-12T09:00:00Z', 1
);

INSERT INTO source_event_receipt (
    event_id, event_type, event_version, workspace_id, season_id, source_reference,
    aggregate_revision, occurred_at, event_state, payload_fingerprint,
    processing_outcome, received_at
) VALUES (
    '83000000-0000-0000-0000-000000000001',
    'HANDOFF_BLOCKED', 2,
    '81000000-0000-0000-0000-000000000002',
    '81000000-0000-0000-0000-000000000003',
    'handoff:legacy-unsupported', 1,
    TIMESTAMPTZ '2026-08-12T09:00:00Z', 'ACTIVE',
    'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',
    'UNSUPPORTED', TIMESTAMPTZ '2026-08-12T09:00:01Z'
);
