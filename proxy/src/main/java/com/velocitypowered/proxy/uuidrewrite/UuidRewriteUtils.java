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

import com.velocitypowered.proxy.config.PlayerInfoForwarding;
import com.velocitypowered.proxy.config.VelocityConfiguration;

/**
 * [fallen's fork] player uuid rewrite - implementation.
 */
@SuppressWarnings("MissingJavadocMethod")
public class UuidRewriteUtils {

  public static boolean isUuidRewriteEnabled(VelocityConfiguration config) {
    if (config.isUuidRewriteEnabled()) {
      return config.isOnlineMode() && config.getPlayerInfoForwardingMode() == PlayerInfoForwarding.NONE;
    }
    return false;
  }

}
