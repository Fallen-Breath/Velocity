/*
 * Copyright (C) 2018-2023 Velocity Contributors
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

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.config.PlayerInfoForwarding;
import com.velocitypowered.proxy.connection.backend.VelocityServerConnection;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.packet.LegacyPlayerListItemPacket;
import com.velocitypowered.proxy.protocol.packet.RemovePlayerInfoPacket;
import com.velocitypowered.proxy.protocol.packet.UpsertPlayerInfoPacket;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * [fallen's fork] player uuid rewrite - tab list entry: rewrite logic.
 */
public class TabListUuidRewriter {

  @SuppressWarnings("BooleanMethodIsAlwaysInverted")
  private static boolean shouldRewrite(VelocityServer server) {
    var config = server.getConfiguration();
    return config.isOnlineMode() && config.getPlayerInfoForwardingMode() == PlayerInfoForwarding.NONE;
  }

  // offline / server uuid -> online / client uuid
  private static Map<UUID, UUID> makeUuidMappingView(VelocityServer server) {
    Map<UUID, UUID> view = new HashMap<>();
    for (Player player : server.getAllPlayers()) {
      view.put(player.getOfflineUuid(), player.getUniqueId());
    }
    return view;
  }

  // [fallen's fork] player uuid rewrite
  // send the missing player tab-list removal packets to other players in the mc server
  // see bungeecord net.md_5.bungee.connection.UpstreamBridge#disconnected
  public static void onPlayerDisconnect(VelocityServer server, ConnectedPlayer player) {
    if (!shouldRewrite(server)) {
      return;
    }

    VelocityServerConnection connectedServer = player.getConnectedServer();
    if (connectedServer == null) {
      return;
    }

    var oldPacket = new LegacyPlayerListItemPacket(
            LegacyPlayerListItemPacket.REMOVE_PLAYER,
            Collections.singletonList(new LegacyPlayerListItemPacket.Item(player.getUniqueId()))
    );
    var newPacket = new RemovePlayerInfoPacket(
            Collections.singleton(player.getUniqueId())
    );

    for (Player otherPlayer : connectedServer.getServer().getPlayersConnected()) {
      if (otherPlayer != player && otherPlayer instanceof ConnectedPlayer) {
        var connection = ((ConnectedPlayer)otherPlayer).getConnection();
        MinecraftPacket packet;
        if (connection.getProtocolVersion().noLessThan(ProtocolVersion.MINECRAFT_1_19_3)) {
          packet = newPacket;
        } else {
          packet = oldPacket;
        }
        connection.write(packet);
      }
    }
  }

  /**
   * Rewrite uuid for a LegacyPlayerListItem packet.
   */
  public static void rewrite(VelocityServer server, LegacyPlayerListItemPacket packet) {
    if (!shouldRewrite(server)) {
      return;
    }

    var uuidMapping = makeUuidMappingView(server);
    packet.getItems().replaceAll(item -> {
      var clientSideUuid = uuidMapping.get(item.getUuid());
      if (clientSideUuid != null && !clientSideUuid.equals(item.getUuid())) {
        var newItem = new LegacyPlayerListItemPacket.Item(clientSideUuid);

        newItem.setName(item.getName());
        newItem.setProperties(item.getProperties());
        newItem.setGameMode(item.getGameMode());
        newItem.setLatency(item.getLatency());
        newItem.setDisplayName(item.getDisplayName());
        newItem.setPlayerKey(item.getPlayerKey());

        item = newItem;
      }

      return item;
    });
  }

  /**
   * Rewrite uuid for a UpsertPlayerInfo packet.
   */
  public static void rewrite(VelocityServer server, UpsertPlayerInfoPacket packet) {
    if (!shouldRewrite(server)) {
      return;
    }

    var uuidMapping = makeUuidMappingView(server);
    packet.getEntries().replaceAll(entry -> {
      var clientSideUuid = uuidMapping.get(entry.getProfileId());
      if (clientSideUuid != null && !clientSideUuid.equals(entry.getProfileId())) {
        var newEntry = new UpsertPlayerInfoPacket.Entry(clientSideUuid);

        newEntry.setProfile(entry.getProfile());
        newEntry.setListed(entry.isListed());
        newEntry.setLatency(entry.getLatency());
        newEntry.setGameMode(entry.getGameMode());
        newEntry.setDisplayName(entry.getDisplayName());
        newEntry.setChatSession(entry.getChatSession());

        entry = newEntry;
      }

      return entry;
    });
  }

  /**
   * Rewrite uuid for a RemovePlayerInfo packet.
   */
  public static void rewrite(VelocityServer server, RemovePlayerInfoPacket packet) {
    if (!shouldRewrite(server)) {
      return;
    }

    var uuidMapping = makeUuidMappingView(server);
    var newProfiles = packet.getProfilesToRemove().stream()
        .map(serverUuid -> Optional.ofNullable(uuidMapping.get(serverUuid)).orElse(serverUuid))
        .collect(Collectors.toList());

    packet.setProfilesToRemove(newProfiles);
  }
}
