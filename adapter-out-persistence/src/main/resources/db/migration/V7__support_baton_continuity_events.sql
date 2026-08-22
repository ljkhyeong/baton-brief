ALTER TABLE source_event_receipt
    ADD COLUMN source_severity VARCHAR(16),
    ADD CONSTRAINT source_event_receipt_type_known CHECK (
        event_type IN (
            'HANDOFF_BLOCKED',
            'ROUTINE_MISSED',
            'DECISION_FOLLOW_UP_OVERDUE',
            'ROLE_UNASSIGNED',
            'ROLE_SUCCESSOR_MISSING',
            'ROLE_PREPARATION_INCOMPLETE',
            'ROUTINE_REPEATEDLY_OVERDUE',
            'HANDOFF_INCOMPLETE'
        )
    ),
    ADD CONSTRAINT source_event_receipt_source_severity_known CHECK (
        source_severity IS NULL OR source_severity IN ('CRITICAL', 'WARNING')
    ),
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
            AND source_severity IN ('CRITICAL', 'WARNING')
        )
    );

ALTER TABLE attention_item
    ADD CONSTRAINT attention_item_event_type_known CHECK (
        event_type IN (
            'HANDOFF_BLOCKED',
            'ROUTINE_MISSED',
            'DECISION_FOLLOW_UP_OVERDUE',
            'ROLE_UNASSIGNED',
            'ROLE_SUCCESSOR_MISSING',
            'ROLE_PREPARATION_INCOMPLETE',
            'ROUTINE_REPEATEDLY_OVERDUE',
            'HANDOFF_INCOMPLETE'
        )
    );

ALTER TABLE brief_edition_item
    ADD CONSTRAINT brief_edition_item_reason_code_known CHECK (
        reason_code IN (
            'HANDOFF_BLOCKED',
            'ROUTINE_MISSED',
            'DECISION_FOLLOW_UP_OVERDUE',
            'ROLE_UNASSIGNED',
            'ROLE_SUCCESSOR_MISSING',
            'ROLE_PREPARATION_INCOMPLETE',
            'ROUTINE_REPEATEDLY_OVERDUE',
            'HANDOFF_INCOMPLETE'
        )
    );
