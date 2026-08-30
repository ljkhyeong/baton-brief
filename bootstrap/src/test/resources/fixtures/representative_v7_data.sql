INSERT INTO source_event_receipt (
    event_id, event_type, event_version, source_severity, workspace_id, season_id,
    source_reference, aggregate_revision, occurred_at, event_state,
    payload_fingerprint, processing_outcome, received_at
) VALUES (
    '83000000-0000-0000-0000-000000000002',
    'ROLE_UNASSIGNED', 2, 'CRITICAL',
    '81000000-0000-0000-0000-000000000002',
    '81000000-0000-0000-0000-000000000003',
    'role:v2-supported', 1, TIMESTAMPTZ '2026-08-12T09:00:00Z', 'ACTIVE',
    'cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc',
    'APPLIED', TIMESTAMPTZ '2026-08-12T09:00:01Z'
), (
    '83000000-0000-0000-0000-000000000003',
    'HANDOFF_BLOCKED', 1, NULL,
    '81000000-0000-0000-0000-000000000002',
    '81000000-0000-0000-0000-000000000003',
    'handoff:v1-supported', 1, TIMESTAMPTZ '2026-08-12T09:00:00Z', 'ACTIVE',
    'dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd',
    'APPLIED', TIMESTAMPTZ '2026-08-12T09:00:01Z'
);
