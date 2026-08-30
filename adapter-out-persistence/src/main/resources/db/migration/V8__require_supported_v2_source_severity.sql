ALTER TABLE source_event_receipt
    DROP CONSTRAINT source_event_receipt_supported_contract,
    ADD CONSTRAINT source_event_receipt_supported_contract CHECK (
        processing_outcome = 'UNSUPPORTED'
        OR (
            event_version = 1
            AND event_type IN ('HANDOFF_BLOCKED', 'ROUTINE_MISSED', 'DECISION_FOLLOW_UP_OVERDUE')
            AND source_severity IS NULL
        )
        OR (
            event_version = 2
            AND event_type IN (
                'ROLE_UNASSIGNED',
                'ROLE_SUCCESSOR_MISSING',
                'ROLE_PREPARATION_INCOMPLETE',
                'ROUTINE_REPEATEDLY_OVERDUE',
                'HANDOFF_INCOMPLETE'
            )
            AND source_severity IS NOT NULL
            AND source_severity IN ('CRITICAL', 'WARNING')
        )
    );
