ALTER TABLE citations
    ADD COLUMN observed_at      timestamptz,
    ADD COLUMN rule_set_version varchar(32),
    ADD COLUMN excess_kph       numeric(5,2),
    ADD COLUMN tier_over_by_kph numeric(5,2);

UPDATE citations SET observed_at = recorded_at, excess_kph = speed_kph - speed_limit_kph;

UPDATE citations
SET tier_over_by_kph = t.over_by,
    rule_set_version = CASE WHEN t.expected_fine = fine_amount THEN '2026-09' ELSE 'legacy-unverified' END
FROM (SELECT id,
             CASE WHEN excess_kph > 40 THEN 40 WHEN excess_kph > 20 THEN 20 ELSE 0 END AS over_by,
             CASE WHEN excess_kph > 40 THEN 5000 WHEN excess_kph > 20 THEN 2000 ELSE 1000 END AS expected_fine
      FROM citations) t
WHERE citations.id = t.id;
UPDATE citations SET tier_over_by_kph = NULL WHERE rule_set_version = 'legacy-unverified';

ALTER TABLE citations
    ALTER COLUMN observed_at      SET NOT NULL,
    ALTER COLUMN rule_set_version SET NOT NULL,
    ALTER COLUMN excess_kph       SET NOT NULL,
    ADD CONSTRAINT citations_excess_ck  CHECK (excess_kph = speed_kph - speed_limit_kph),
    ADD CONSTRAINT citations_tier_ck    CHECK (tier_over_by_kph >= 0 AND excess_kph > tier_over_by_kph),
    ADD CONSTRAINT citations_tier_known_ck
        CHECK (tier_over_by_kph IS NOT NULL OR rule_set_version = 'legacy-unverified'),
    ADD CONSTRAINT citations_observed_ck CHECK (observed_at <= recorded_at + interval '5 minutes');

ALTER TABLE citations
    ADD CONSTRAINT citations_vehicle_id_ck CHECK (vehicle_id ~ '^[A-Z0-9]{2,20}$') NOT VALID;
