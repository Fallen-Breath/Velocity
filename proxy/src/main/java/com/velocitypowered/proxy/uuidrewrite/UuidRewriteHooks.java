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

import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import java.sql.SQLException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * [fallen's fork] player uuid rewrite - lifecycle hooks.
 */
@SuppressWarnings("MissingJavadocMethod")
public class UuidRewriteHooks {

  private static final Logger logger = LogManager.getLogger(UuidRewriteHooks.class);
  private static final UuidMappingDatabase db = UuidMappingDatabase.getInstance();

  public static void onServerStart(VelocityServer server) {
    var config = server.getConfiguration();
    if (config.isUuidRewriteDatabaseEnabled()) {
      var dbPath = config.getUuidRewriteDatabasePath();
      try {
        db.init(dbPath);
        logger.info("UUID-Rewrite mapping database connect ok, path '{}'", dbPath);
      } catch (SQLException e) {
        logger.error("UUID-Rewrite mapping database initialization failed, disabling database, path '{}'", dbPath, e);
        config.setUuidRewriteDatabaseEnabled(false);
      }
    }
    db.setEnabled(config.isUuidRewriteDatabaseEnabled());
  }

  public static void onServerStop(VelocityServer server) {
    var config = server.getConfiguration();
    if (config.isUuidRewriteDatabaseEnabled()) {
      db.close();
    }
  }

  public static void onPlayerConnect(VelocityServer server, ConnectedPlayer player) {
    var config = server.getConfiguration();
    if (UuidRewriteUtils.isUuidRewriteEnabled(config)) {
      if (config.isUuidRewriteDatabaseEnabled()) {
        db.createNewEntry(player.getUniqueId(), player.getOfflineUuid(), player.getUsername());
      }
    }
  }

  public static void onPlayerDisconnect(VelocityServer server, ConnectedPlayer player) {
    TabListUuidRewriter.sendRewrittenTabListRemovalPackets(server, player);
  }

}
