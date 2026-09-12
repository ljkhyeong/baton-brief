CREATE INDEX source_event_receipt_scope_sequence_idx
    ON source_event_receipt (workspace_id, season_id, ingestion_sequence DESC);
