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

  private static boolean shouldRewrite(VelocityServer server) {
    var config = server.getConfiguration();
    return config.isOnlineMode() && config.getPlayerInfoForwardingMode() == PlayerInfoForwarding.NONE;
  }

  private static Map<UUID, UUID> makeUuidMappingView(VelocityServer server) {
    synchronized (uuidMappingCache) {
      for (Player player : server.getAllPlayers()) {
        uuidMappingCache.put(player.getOfflineUuid(), player.getUniqueId());
      }

      // allow at most 1024 players to disconnect at the same time
      while (uuidMappingCache.size() > server.getAllPlayers().size() + 1024) {
        uuidMappingCache.remove(uuidMappingCache.keySet().iterator().next());
      }
      return Map.copyOf(uuidMappingCache);
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

    synchronized (uuidMappingCache) {
      packet.getProfilesToRemove().forEach(uuidMappingCache::remove);
    }

    packet.setProfilesToRemove(newProfiles);
  }
}
