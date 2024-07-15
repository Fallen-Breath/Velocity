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

package com.velocitypowered.proxy.protocol.packet.uuidrewrite;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.config.PlayerInfoForwarding;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.UUID;
import java.util.function.Function;

public class EntityPacketUuidRewriter {

  private static final boolean DEBUG = false;
  private static final Logger logger = LogManager.getLogger(EntityPacketUuidRewriter.class);

  public static void rewriteS2C(VelocityServer server, Player connectionPlayer, PacketToRewriteEntityUuid packet) {
    rewrite(server, connectionPlayer, packet, Player::getOfflineUuid, Player::getUniqueId);
  }

  public static void rewriteC2S(VelocityServer server, Player connectionPlayer, PacketToRewriteEntityUuid packet) {
    rewrite(server, connectionPlayer, packet, Player::getUniqueId, Player::getOfflineUuid);
  }

  private static void rewrite(VelocityServer server, Player connectionPlayer, PacketToRewriteEntityUuid packet,
                              Function<Player, UUID> uuidFrom, Function<Player, UUID> uuidTo) {
    if (DEBUG) {
      logger.info("EPUR for {} start, packet {} ({} {})", connectionPlayer.getUsername(), packet.getClass().getSimpleName(), packet.isPlayer(), packet.getEntityUuid());
    }

    var config = server.getConfiguration();
    if (!(config.isOnlineMode() && config.getPlayerInfoForwardingMode() == PlayerInfoForwarding.NONE)) {
      return;  // early return for performance optimization
    }
    if (!packet.isPlayer()) {
      return;
    }
    UUID uuid = packet.getEntityUuid();
    if (uuid == null) {
      return;
    }

    if (DEBUG) {
      logger.info("EPUR for {} check pass, uuid to rewrite: {}", connectionPlayer.getUsername(), uuid);
    }

    // FIXME: inefficient implementation using for loop. Replace it with map lookup?
    for (Player player : server.getAllPlayers()) {
      UUID serverUuid = uuidFrom.apply(player);
      if (DEBUG) {
        logger.info("EPUR for {} checking {} {}", connectionPlayer.getUsername(), player.getUsername(), serverUuid);
      }
      if (serverUuid.equals(uuid)) {
        if (DEBUG) {
          logger.info("EPUR for {} match {}", connectionPlayer.getUsername(), player.getUsername());
        }

        UUID newUuid = uuidTo.apply(player);
        if (!newUuid.equals(uuid)) {
          packet.setEntityUuid(newUuid);
        }
        break;
      }
    }

    if (DEBUG) {
      logger.info("EPUR for {} check end", connectionPlayer.getUsername());
    }
  }
}
