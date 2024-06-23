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

package com.velocitypowered.proxy.tablist;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.config.PlayerInfoForwarding;
import com.velocitypowered.proxy.protocol.packet.LegacyPlayerListItemPacket;
import com.velocitypowered.proxy.protocol.packet.RemovePlayerInfoPacket;
import com.velocitypowered.proxy.protocol.packet.UpsertPlayerInfoPacket;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * [fallen's fork] player uuid rewrite - tab list entry: rewrite logic.
 */
public class TabListUuidRewriter {

  private static boolean shouldRewrite(VelocityServer server) {
    var config = server.getConfiguration();
    return config.isOnlineMode() && config.getPlayerInfoForwardingMode() == PlayerInfoForwarding.NONE;
  }

  /**
   * Common use case: input uuid is an offline uuid, and we want to get its online uuid.
   */
  private static Optional<UUID> getRealUuid(VelocityServer server, UUID playerUuid) {
    for (Player player : server.getAllPlayers()) {
      if (player.getOfflineUuid().equals(playerUuid)) {
        return Optional.of(player.getUniqueId());
      }
    }
    return Optional.empty();
  }

  /**
   * Rewrite uuid for a LegacyPlayerListItem packet.
   */
  public static void rewrite(VelocityServer server, LegacyPlayerListItemPacket packet) {
    if (!shouldRewrite(server)) {
      return;
    }
    packet.getItems().replaceAll(item -> {
      var realUuid = getRealUuid(server, item.getUuid());
      if (realUuid.isPresent() && !realUuid.get().equals(item.getUuid())) {
        var newItem = new LegacyPlayerListItemPacket.Item(realUuid.get());

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
    packet.getEntries().replaceAll(entry -> {
      var realUuid = getRealUuid(server, entry.getProfileId());
      if (realUuid.isPresent() && !realUuid.get().equals(entry.getProfileId())) {
        var newEntry = new UpsertPlayerInfoPacket.Entry(realUuid.get());

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
    var newProfiles = packet.getProfilesToRemove().stream()
        .map(uuid -> getRealUuid(server, uuid).orElse(uuid))
        .collect(Collectors.toList());
    packet.setProfilesToRemove(newProfiles);
  }
}
