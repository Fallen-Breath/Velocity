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

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.proxy.VelocityServer;
import java.util.UUID;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * offline / server UUID  <->  online / client UUID.
 */
@SuppressWarnings({"MissingJavadocMethod", "MissingJavadocType"})
public interface UuidRewriter {

  // -------------------- Interfaces --------------------

  @Nullable UUID toOnline(UUID offlineUuid);

  @Nullable UUID toOffline(UUID onlineUuid);

  default @Nullable UUID toClient(UUID serverUuid) {
    return this.toOnline(serverUuid);
  }

  default @Nullable UUID toServer(UUID clientUuid) {
    return this.toOffline(clientUuid);
  }

  default @Nullable UUID rewrite(UUID uuid, RewriteDirection direction) {
    return switch (direction) {
      case ONLINE_TO_OFFLINE -> this.toOffline(uuid);
      case OFFLINE_TO_ONLINE -> this.toOnline(uuid);
    };
  }

  default @Nullable UUID rewrite(Player player, RewriteDirection direction) {
    return this.rewrite(direction.getSourceUuid(player), direction);
  }

  // -------------------- Utilities --------------------

  static UuidRewriter create(VelocityServer server) {
    return new ChainedRewriter(new MapRewriter(server), new DatabaseRewriter());
  }

  // -------------------- Implementations --------------------

  class MapRewriter implements UuidRewriter {
    private final BiMap<UUID, UUID> offlineToOnline = HashBiMap.create();

    private MapRewriter(VelocityServer server) {
      for (Player player : server.getAllPlayers()) {
        this.offlineToOnline.put(player.getOfflineUuid(), player.getUniqueId());
      }
    }

    @Override
    public @Nullable UUID toOnline(UUID offlineUuid) {
      return this.offlineToOnline.get(offlineUuid);
    }

    @Override
    public @Nullable UUID toOffline(UUID onlineUuid) {
      return this.offlineToOnline.inverse().get(onlineUuid);
    }
  }

  class DatabaseRewriter implements UuidRewriter {
    private DatabaseRewriter() {}

    @Override
    public @Nullable UUID toOnline(UUID offlineUuid) {
      var db = UuidMappingDatabase.getInstance();
      return db.queryOnlineUuid(offlineUuid);
    }

    @Override
    public @Nullable UUID toOffline(UUID onlineUuid) {
      var db = UuidMappingDatabase.getInstance();
      return db.queryOfflineUuid(onlineUuid);
    }
  }

  class ChainedRewriter implements UuidRewriter {
    private final UuidRewriter[] rewriters;

    private ChainedRewriter(UuidRewriter... rewriters) {
      this.rewriters = rewriters;
    }

    @Override
    public @Nullable UUID toOnline(UUID offlineUuid) {
      for (UuidRewriter rewriter : this.rewriters) {
        var result = rewriter.toOnline(offlineUuid);
        if (result != null) {
          return result;
        }
      }
      return null;
    }

    @Override
    public @Nullable UUID toOffline(UUID onlineUuid) {
      for (UuidRewriter rewriter : this.rewriters) {
        var result = rewriter.toOffline(onlineUuid);
        if (result != null) {
          return result;
        }
      }
      return null;
    }
  }
}
