package org.krish.traffic.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

// Runs the real migrations against rows shaped like the old Hibernate-created table (V1).
// Each test uses its own schema so they don't see each other's rows.
@Testcontainers
class MigrationIT {

  @Container
  static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

  private static Flyway flyway(String schema, String target) {
    return Flyway.configure()
        .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
        .schemas(schema)
        .target(target)
        .load();
  }

  private static Connection connect(String schema) throws SQLException {
    Connection c =
        DriverManager.getConnection(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    try (Statement st = c.createStatement()) {
      st.execute("SET search_path TO " + schema);
    }
    return c;
  }

  private static void insertLegacy(Connection c, String vehicle, double speed, double limit, int fine)
      throws SQLException {
    try (var ps =
        c.prepareStatement(
            "INSERT INTO violations (fine_amount, recorded_at, speed_kph, speed_limit_kph, vehicle_id, zone)"
                + " VALUES (?, now(), ?, ?, ?, 'SCHOOL-ZONE')")) {
      ps.setInt(1, fine);
      ps.setDouble(2, speed);
      ps.setDouble(3, limit);
      ps.setString(4, vehicle);
      ps.executeUpdate();
    }
  }

  @Test
  void legacyRowsAreConvertedAndBackfilled() throws Exception {
    flyway("legacy_ok", "1").migrate();
    try (Connection c = connect("legacy_ok")) {
      insertLegacy(c, "KA 01 AB 1234", 55, 30, 2000);
      insertLegacy(c, "V2", 120.1, 100, 2000);
      insertLegacy(c, "V3", 140.1, 100, 5000);
      insertLegacy(c, "V4", 50, 30, 1000);
      insertLegacy(c, "V5", 55, 30, 1500); // fine doesn't match the tier table
    }

    flyway("legacy_ok", "latest").migrate();

    try (Connection c = connect("legacy_ok");
        Statement st = c.createStatement()) {
      ResultSet types =
          st.executeQuery(
              "SELECT column_name, data_type, numeric_precision, numeric_scale"
                  + " FROM information_schema.columns"
                  + " WHERE table_schema = 'legacy_ok' AND table_name = 'citations'"
                  + " AND column_name IN ('speed_kph', 'speed_limit_kph', 'excess_kph', 'fine_amount')"
                  + " ORDER BY column_name");
      List<String> found = new ArrayList<>();
      while (types.next()) {
        found.add(
            types.getString(1) + " " + types.getString(2) + "(" + types.getInt(3) + "," + types.getInt(4) + ")");
      }
      assertThat(found)
          .containsExactly(
              "excess_kph numeric(5,2)",
              "fine_amount numeric(12,2)",
              "speed_kph numeric(5,2)",
              "speed_limit_kph numeric(5,2)");

      ResultSet rows =
          st.executeQuery(
              "SELECT vehicle_id, speed_kph, excess_kph, tier_over_by_kph, rule_set_version, currency"
                  + " FROM citations ORDER BY id");
      List<String> got = new ArrayList<>();
      while (rows.next()) {
        got.add(
            String.join(
                " | ",
                rows.getString(1),
                rows.getBigDecimal(2).toPlainString(),
                rows.getBigDecimal(3).toPlainString(),
                String.valueOf(rows.getBigDecimal(4)),
                rows.getString(5),
                rows.getString(6)));
      }
      assertThat(got)
          .containsExactly(
              "KA 01 AB 1234 | 55.00 | 25.00 | 20.00 | 2026-09 | INR",
              "V2 | 120.10 | 20.10 | 20.00 | 2026-09 | INR",
              "V3 | 140.10 | 40.10 | 40.00 | 2026-09 | INR",
              "V4 | 50.00 | 20.00 | 0.00 | 2026-09 | INR",
              "V5 | 55.00 | 25.00 | null | legacy-unverified | INR");

      // NOT VALID keeps the old 'KA 01 AB 1234' row but rejects new non-canonical ids
      assertThatThrownBy(
              () ->
                  st.executeUpdate(
                      "INSERT INTO citations (vehicle_id, zone, speed_kph, speed_limit_kph, excess_kph,"
                          + " tier_over_by_kph, fine_amount, currency, rule_set_version, observed_at, recorded_at)"
                          + " VALUES ('KA 9', 'SCHOOL-ZONE', 55, 30, 25, 20, 2000, 'INR', '2026-09', now(), now())"))
          .isInstanceOf(SQLException.class)
          .hasMessageContaining("citations_vehicle_id_ck");
    }
  }

  @Test
  void v2AbortsAndRollsBackOnSpeedsWithMoreThanTwoDecimals() throws Exception {
    flyway("legacy_bad", "1").migrate();
    try (Connection c = connect("legacy_bad")) {
      insertLegacy(c, "V1", 50.00000000000001, 30, 1000);
    }

    assertThatThrownBy(() -> flyway("legacy_bad", "latest").migrate())
        .isInstanceOf(FlywayException.class)
        .hasMessageContaining("fix them before V2");

    try (Connection c = connect("legacy_bad");
        Statement st = c.createStatement()) {
      ResultSet table =
          st.executeQuery(
              "SELECT data_type FROM information_schema.columns"
                  + " WHERE table_schema = 'legacy_bad' AND table_name = 'violations'"
                  + " AND column_name = 'speed_kph'");
      assertThat(table.next()).isTrue();
      assertThat(table.getString(1)).isEqualTo("double precision");

      ResultSet version =
          st.executeQuery(
              "SELECT max(version) FROM flyway_schema_history WHERE success");
      version.next();
      assertThat(version.getString(1)).isEqualTo("1");
    }
  }
}
