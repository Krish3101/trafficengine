DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM violations
             WHERE speed_kph::text::numeric       <> round(speed_kph::text::numeric, 2)
                OR speed_limit_kph::text::numeric <> round(speed_limit_kph::text::numeric, 2)
                OR speed_kph > 999.99) THEN
    RAISE EXCEPTION 'violations has speeds with more than 2 decimals (or > 999.99); fix them before V2';
  END IF;
END $$;

ALTER TABLE violations RENAME TO citations;
ALTER SEQUENCE violations_id_seq RENAME TO citations_id_seq;
ALTER INDEX violations_pkey RENAME TO citations_pkey;
DROP INDEX idx_violations_zone;

ALTER TABLE citations
    ALTER COLUMN speed_kph       TYPE numeric(5,2)  USING speed_kph::numeric,
    ALTER COLUMN speed_limit_kph TYPE numeric(5,2)  USING speed_limit_kph::numeric,
    ALTER COLUMN fine_amount     TYPE numeric(12,2) USING fine_amount::numeric,
    ADD COLUMN currency varchar(3) NOT NULL DEFAULT 'INR';
ALTER TABLE citations ALTER COLUMN currency DROP DEFAULT;

ALTER TABLE citations
    ADD CONSTRAINT citations_speed_nonneg_ck  CHECK (speed_kph >= 0),
    ADD CONSTRAINT citations_limit_pos_ck     CHECK (speed_limit_kph > 0),
    ADD CONSTRAINT citations_over_limit_ck    CHECK (speed_kph > speed_limit_kph),
    ADD CONSTRAINT citations_fine_nonneg_ck   CHECK (fine_amount >= 0),
    ADD CONSTRAINT citations_currency_ck      CHECK (currency ~ '^[A-Z]{3}$');

CREATE INDEX citations_zone_id_idx ON citations (zone, id DESC);
