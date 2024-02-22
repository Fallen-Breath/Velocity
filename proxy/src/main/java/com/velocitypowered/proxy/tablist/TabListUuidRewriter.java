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

import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.protocol.packet.LegacyPlayerListItemPacket;
import com.velocitypowered.proxy.protocol.packet.RemovePlayerInfoPacket;
import com.velocitypowered.proxy.protocol.packet.UpsertPlayerInfoPacket;
import java.util.stream.Collectors;

/**
 * [fallen's fork] tab list entry uuid rewrite: rewrite logic.
 */
public class TabListUuidRewriter {
  /**
   * Rewrite uuid for a LegacyPlayerListItem packet.
   */
  public static void rewrite(ConnectedPlayer player, LegacyPlayerListItemPacket packet) {
    packet.getItems().replaceAll(item -> {
      if (player.getOfflineUuid().equals(item.getUuid())) {
        var newItem = new LegacyPlayerListItemPacket.Item(player.getUniqueId());

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
  public static void rewrite(ConnectedPlayer player, UpsertPlayerInfoPacket packet) {
    packet.getEntries().replaceAll(entry -> {
      if (player.getOfflineUuid().equals(entry.getProfileId())) {
        var newEntry = new UpsertPlayerInfoPacket.Entry(player.getUniqueId());

        newEntry.setProfile(player.getGameProfile());
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
  public static void rewrite(ConnectedPlayer player, RemovePlayerInfoPacket packet) {
    var newProfiles = packet.getProfilesToRemove().stream()
        .map(uuid -> {
          if (player.getOfflineUuid().equals(uuid)) {
            uuid = player.getUniqueId();
          }
          return uuid;
        })
        .collect(Collectors.toList());
    packet.setProfilesToRemove(newProfiles);
  }
}
