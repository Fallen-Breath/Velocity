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

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import io.netty.buffer.ByteBuf;

import java.util.UUID;

/**
 * [fallen's fork] player uuid rewrite - entity packet
 * used in mc >= 1.20.2
 */
public class UrSpawnEntityS2CPacket implements MinecraftPacket, PacketToRewriteEntityUuid {

  private int entityId;
  private UUID entityUuid;
  private int entityType;
  private byte[] remainingBuf;

  private boolean isPlayer;

  private static int getPlayerEntityType(ProtocolVersion version) {
    // https://wiki.vg/Entity_metadata#Mobs
    // https://github.com/Fallen-Breath/mc-registry-dump
    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_21_4)) {
      return 147;
    } else if (version.noLessThan(ProtocolVersion.MINECRAFT_1_21_2)) {
      return 148;
    } else if (version.noLessThan(ProtocolVersion.MINECRAFT_1_20_5)) {
      return 128;
    } else if (version.noLessThan(ProtocolVersion.MINECRAFT_1_20_3)) {
      return 124;
    } else if (version.noLessThan(ProtocolVersion.MINECRAFT_1_20_2)) {
      return 122;
    } else {
      throw new IllegalArgumentException("Unsupported protocol version: " + version);
    }
  }

  @Override
  public void decode(ByteBuf buf, ProtocolUtils.Direction direction, ProtocolVersion protocolVersion) {
    this.entityId = ProtocolUtils.readVarInt(buf);
    this.entityUuid = ProtocolUtils.readUuid(buf);
    this.entityType = ProtocolUtils.readVarInt(buf);
    this.remainingBuf = new byte[buf.readableBytes()];
    buf.readBytes(this.remainingBuf);

    this.isPlayer = this.entityType == getPlayerEntityType(protocolVersion);
  }

  @Override
  public void encode(ByteBuf buf, ProtocolUtils.Direction direction, ProtocolVersion protocolVersion) {
    ProtocolUtils.writeVarInt(buf, this.entityId);
    ProtocolUtils.writeUuid(buf, this.entityUuid);
    ProtocolUtils.writeVarInt(buf, this.entityType);
    buf.writeBytes(this.remainingBuf);
  }

  @Override
  public boolean handle(MinecraftSessionHandler handler) {
    return handler.handle(this);
  }

  @Override
  public boolean isPlayer() {
    return this.isPlayer;
  }

  @Override
  public UUID getEntityUuid() {
    return this.entityUuid;
  }

  @Override
  public void setEntityUuid(UUID entityUuid) {
    this.entityUuid = entityUuid;
  }
}
