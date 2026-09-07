ALTER TABLE brief_edition_item
    ADD COLUMN section VARCHAR(16),
    ADD CONSTRAINT brief_edition_item_section_known
        CHECK (section IN ('CURRENT_WEEK', 'CARRY_OVER'));
