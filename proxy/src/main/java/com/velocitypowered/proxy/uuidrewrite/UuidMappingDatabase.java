/*
 * Copyright (C) 2024 Velocity Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.velocitypowered.proxy.uuidrewrite;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

/**
 * [fallen's fork] player uuid rewrite - uuid database.
 */
@SuppressWarnings({"MissingJavadocMethod", "MissingJavadocType"})
public class UuidMappingDatabase {

  private static final Logger logger = LogManager.getLogger(UuidMappingDatabase.class);
  private static final UuidMappingDatabase INSTANCE = new UuidMappingDatabase();
  private final SQLiteDataSource dataSource;
  private Connection connection;
  private boolean enabled = false;

  private UuidMappingDatabase() {
    SQLiteConfig config = new SQLiteConfig();
    config.enforceForeignKeys(true);
    config.setBusyTimeout(1000);

    this.dataSource = new SQLiteDataSource(config);
  }

  public static UuidMappingDatabase getInstance() {
    return INSTANCE;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  private Connection getConnection() throws SQLException {
    if (this.connection == null || this.connection.isClosed()) {
      this.connection = this.dataSource.getConnection();
      this.connection.setAutoCommit(false);
    }
    return this.connection;
  }

  public void init(String dbPath) throws SQLException {
    String url = "jdbc:sqlite:" + dbPath;
    this.dataSource.setUrl(url);

    try (var stmt = this.getConnection().createStatement()) {
      stmt.executeUpdate(
          "CREATE TABLE IF NOT EXISTS uuid_mapping ("
          + "online_uuid TEXT PRIMARY KEY, "
          + "offline_uuid TEXT, "
          + "player_name TEXT, "
          + "updated_at INTEGER)"
      );
      stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_online_uuid ON uuid_mapping (online_uuid)");
      stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_offline_uuid ON uuid_mapping (offline_uuid)");
      stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_last_used ON uuid_mapping (updated_at)");
      stmt.getConnection().commit();
    }
  }

  public void close() {
    try {
      if (this.connection != null && !this.connection.isClosed()) {
        this.connection.close();
      }
    } catch (SQLException sqlException) {
      logger.error("close failed", sqlException);
    }
  }

  @Nullable
  public UUID queryOnlineUuid(UUID offlineUuid) {
    if (!this.enabled) {
      return null;
    }

    String query = "SELECT online_uuid FROM uuid_mapping WHERE offline_uuid = ? ORDER BY updated_at DESC LIMIT 1";
    try (var stmt = this.getConnection().prepareStatement(query)) {
      stmt.setString(1, offlineUuid.toString());
      ResultSet resultSet = stmt.executeQuery();
      if (resultSet.next()) {
        return UUID.fromString(resultSet.getString("online_uuid"));
      }
    } catch (SQLException sqlException) {
      logger.error("queryOnlineUuid failed", sqlException);
    }
    return null;
  }

  @Nullable
  public UUID queryOfflineUuid(UUID onlineUuid) {
    if (!this.enabled) {
      return null;
    }

    String sql = "SELECT offline_uuid FROM uuid_mapping WHERE online_uuid = ? ORDER BY updated_at DESC LIMIT 1";
    try (var stmt = this.getConnection().prepareStatement(sql)) {
      stmt.setString(1, onlineUuid.toString());
      ResultSet resultSet = stmt.executeQuery();
      if (resultSet.next()) {
        return UUID.fromString(resultSet.getString("offline_uuid"));
      }
    } catch (SQLException sqlException) {
      logger.error("queryOfflineUuid failed", sqlException);
    }
    return null;
  }

  private long lastVacuumMilli = 0;
  private final Object vacuumLock = new Object();

  public void createNewEntry(UUID onlineUuid, UUID offlineUuid, String playerName) {
    if (!this.enabled) {
      return;
    }

    long now = System.currentTimeMillis();
    String sqlQuery = "SELECT * FROM uuid_mapping WHERE online_uuid = ?";
    try (var stmt = this.getConnection().prepareStatement(sqlQuery)) {
      stmt.setString(1, onlineUuid.toString());
      ResultSet resultSet = stmt.executeQuery();
      if (
          resultSet.next()
          && Objects.equals(resultSet.getString("player_name"), playerName)
          && Objects.equals(resultSet.getString("offline_uuid"), offlineUuid.toString())
          && Objects.equals(resultSet.getString("online_uuid"), onlineUuid.toString())
      ) {
        // no changes to this player
        if (now / 1000 - resultSet.getBigDecimal("updated_at").longValue() < 60 * 60) {  // 1h cooldown
          // has been updated recently
          // skip the update
          return;
        }
      }
    } catch (SQLException sqlException) {
      logger.error("createNewEntry existence check failed", sqlException);
    }

    synchronized (this.vacuumLock) {
      if (now - this.lastVacuumMilli >= 24 * 60 * 60) {  // 1day
        this.vacuumSqlite();
        this.lastVacuumMilli = now;
      }
    }

    logger.debug("Create or update uuid mapping entry {} {} {}", onlineUuid, offlineUuid, playerName);
    try {
      String sqlDelete = "DELETE FROM uuid_mapping WHERE offline_uuid = ?";
      String sqlInsert =
          "INSERT OR REPLACE INTO uuid_mapping (online_uuid, offline_uuid, player_name, updated_at) "
          + "VALUES (?, ?, ?, strftime('%s','now'))";

      var conn = this.getConnection();
      try (var stmt = conn.prepareStatement(sqlDelete)) {
        stmt.setString(1, offlineUuid.toString());
        int cnt = stmt.executeUpdate();
        logger.debug("Deleted {} existed entries with offline_uuid = {}", cnt, offlineUuid);
      }
      try (var stmt = conn.prepareStatement(sqlInsert)) {
        stmt.setString(1, onlineUuid.toString());
        stmt.setString(2, offlineUuid.toString());
        stmt.setString(3, playerName);
        stmt.executeUpdate();
      }
      conn.commit();
    } catch (SQLException sqlException) {
      logger.error("createRow update failed", sqlException);
    }
  }

  private void vacuumSqlite() {
    try (var conn = this.dataSource.getConnection(); var stmt = conn.prepareStatement("VACUUM")) {
      stmt.executeUpdate();
    } catch (SQLException sqlException) {
      logger.error("vacuumSqlite failed", sqlException);
    }
  }
}
