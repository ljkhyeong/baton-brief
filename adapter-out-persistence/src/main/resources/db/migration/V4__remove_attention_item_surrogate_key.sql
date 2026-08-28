ALTER TABLE attention_item
    DROP CONSTRAINT attention_item_pkey,
    DROP CONSTRAINT attention_item_identity,
    DROP COLUMN item_id,
    ADD PRIMARY KEY (workspace_id, season_id, event_type, source_reference);
