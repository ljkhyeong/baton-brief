ALTER TABLE brief_edition
    DROP CONSTRAINT brief_edition_request;

CREATE INDEX brief_edition_request_latest_idx
    ON brief_edition (
        workspace_id,
        season_id,
        week_start,
        zone_id,
        rule_version,
        generation DESC
    );
