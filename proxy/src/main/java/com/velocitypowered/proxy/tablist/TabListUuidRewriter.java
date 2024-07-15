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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * [fallen's fork] player uuid rewrite - tab list entry: rewrite logic.
 */
public class TabListUuidRewriter {

  // offline / server uuid -> online / client uuid
  // this cache is necessary, cuz during processing the RemovePlayerInfoPacket, the player might have already disconnected
  private static final Map<UUID, UUID> uuidMappingCache = new LinkedHashMap<>(16, 0.75f, true);
  private static final int uuidMappingCacheCapacity = 1024;

  private static boolean shouldRewrite(VelocityServer server) {
    var config = server.getConfiguration();
    return config.isOnlineMode() && config.getPlayerInfoForwardingMode() == PlayerInfoForwarding.NONE;
  }

  /**
   * Common use case: input uuid is an offline uuid, and we want to get its online uuid.
   */
  private static Optional<UUID> getClientsideUuid(VelocityServer server, UUID serverUuid) {
    synchronized (uuidMappingCache) {
      for (Player player : server.getAllPlayers()) {
        uuidMappingCache.put(player.getOfflineUuid(), player.getUniqueId());
      }
      while (uuidMappingCache.size() > uuidMappingCacheCapacity) {
        uuidMappingCache.remove(uuidMappingCache.keySet().iterator().next());
      }

      for (Player player : server.getAllPlayers()) {
        if (player.getOfflineUuid().equals(serverUuid)) {
          return Optional.of(player.getUniqueId());
        }
      }
      return Optional.ofNullable(uuidMappingCache.get(serverUuid));
    }
  }

  /**
   * Rewrite uuid for a LegacyPlayerListItem packet.
   */
  public static void rewrite(VelocityServer server, LegacyPlayerListItemPacket packet) {
    if (!shouldRewrite(server)) {
      return;
    }
    packet.getItems().replaceAll(item -> {
      var clientSideUuid = getClientsideUuid(server, item.getUuid());
      if (clientSideUuid.isPresent() && !clientSideUuid.get().equals(item.getUuid())) {
        var newItem = new LegacyPlayerListItemPacket.Item(clientSideUuid.get());

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
      var clientSideUuid = getClientsideUuid(server, entry.getProfileId());
      if (clientSideUuid.isPresent() && !clientSideUuid.get().equals(entry.getProfileId())) {
        var newEntry = new UpsertPlayerInfoPacket.Entry(clientSideUuid.get());

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
        .map(uuid -> getClientsideUuid(server, uuid).orElse(uuid))
        .collect(Collectors.toList());

    synchronized (uuidMappingCache) {
      packet.getProfilesToRemove().forEach(uuidMappingCache::remove);
    }

    packet.setProfilesToRemove(newProfiles);
  }
}
