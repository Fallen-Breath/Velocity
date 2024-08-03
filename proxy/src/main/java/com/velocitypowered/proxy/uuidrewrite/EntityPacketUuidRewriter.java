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

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.protocol.packet.uuidrewrite.PacketToRewriteEntityUuid;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * [fallen's fork] player uuid rewrite - entity packets: rewrite logic.
 */
public class EntityPacketUuidRewriter {

  private static final boolean DEBUG = false;
  private static final Logger logger = LogManager.getLogger(EntityPacketUuidRewriter.class);

  public static void rewriteS2C(VelocityServer server, Player connectionPlayer, PacketToRewriteEntityUuid packet) {
    rewrite(server, connectionPlayer, packet, RewriteDirection.S2C);
  }

  public static void rewriteC2S(VelocityServer server, Player connectionPlayer, PacketToRewriteEntityUuid packet) {
    rewrite(server, connectionPlayer, packet, RewriteDirection.C2S);
  }

  private static void rewrite(VelocityServer server, Player connectionPlayer, PacketToRewriteEntityUuid packet,
                              RewriteDirection direction) {
    if (DEBUG) {
      logger.info("EPUR for {} start, packet {} ({} {})", connectionPlayer.getUsername(), packet.getClass().getSimpleName(),
              packet.isPlayer(), packet.getEntityUuid());
    }

    if (!UuidRewriteUtils.isUuidRewriteEnabled(server.getConfiguration())) {
      return;
    }
    if (!packet.isPlayer()) {
      return;
    }

    var oldUuid = packet.getEntityUuid();
    if (oldUuid == null) {
      return;
    }

    var rewriter = UuidRewriter.create(server);
    var newUuid = rewriter.rewrite(oldUuid, direction);
    if (newUuid != null && !newUuid.equals(oldUuid)) {
      packet.setEntityUuid(newUuid);
    }
  }
}
