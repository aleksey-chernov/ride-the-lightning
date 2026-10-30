-- one row per deduplicated strike; time as epoch microseconds; rows older than history.keep are deleted hourly
CREATE TABLE strikes (
  time_us       INTEGER NOT NULL,
  lat           REAL    NOT NULL,
  lon           REAL    NOT NULL,
  region        INTEGER NOT NULL,
  stations      INTEGER NOT NULL,
  delay_seconds REAL    NOT NULL,
  mds           INTEGER NOT NULL
);

CREATE INDEX strikes_time_us ON strikes (time_us);
