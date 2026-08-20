ALTER TABLE brief_edition_item
    ADD COLUMN aggregate_revision BIGINT,
    ADD COLUMN revision_gap BOOLEAN,
    ADD CONSTRAINT brief_edition_item_revision_positive
        CHECK (aggregate_revision IS NULL OR aggregate_revision > 0),
    ADD CONSTRAINT brief_edition_item_revision_evidence_paired
        CHECK ((aggregate_revision IS NULL) = (revision_gap IS NULL));
