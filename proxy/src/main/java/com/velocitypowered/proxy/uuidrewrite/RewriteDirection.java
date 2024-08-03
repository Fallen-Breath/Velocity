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
import java.util.UUID;
import java.util.function.Function;

/**
 * [fallen's fork] player uuid rewrite - implementation.
 */
public enum RewriteDirection {
  OFFLINE_TO_ONLINE(Player::getOfflineUuid),
  ONLINE_TO_OFFLINE(Player::getUniqueId);

  public static final RewriteDirection S2C = OFFLINE_TO_ONLINE;
  public static final RewriteDirection C2S = ONLINE_TO_OFFLINE;

  private final Function<Player, UUID> uuidExtractor;

  RewriteDirection(Function<Player, UUID> uuidExtractor) {
    this.uuidExtractor = uuidExtractor;
  }

  UUID getSourceUuid(Player player) {
    return this.uuidExtractor.apply(player);
  }
}
