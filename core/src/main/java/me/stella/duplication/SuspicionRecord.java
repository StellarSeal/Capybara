package me.stella.duplication;

import java.time.Instant;
import java.util.UUID;
import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;

public class SuspicionRecord {
   private final UUID playerUUID;
   private final String playerName;
   private final Instant flaggedAt;
   private final InventorySnapshot.ContainerType containerType;
   private final String itemId;
   private final int detectedAmount;
   private final int configuredCap;
   private final int recursionDepth;
   private final Location containerLocation;
   private final String entityIdentifier;

   public SuspicionRecord(UUID playerUUID, String playerName, Instant flaggedAt, InventorySnapshot.ContainerType containerType, String itemId, int detectedAmount, int configuredCap, int recursionDepth, @Nullable Location containerLocation, @Nullable String entityIdentifier) {
      this.playerUUID = playerUUID;
      this.playerName = playerName;
      this.flaggedAt = flaggedAt;
      this.containerType = containerType;
      this.itemId = itemId;
      this.detectedAmount = detectedAmount;
      this.configuredCap = configuredCap;
      this.recursionDepth = recursionDepth;
      this.containerLocation = containerLocation;
      this.entityIdentifier = entityIdentifier;
   }

   public UUID getPlayerUUID() {
      return this.playerUUID;
   }

   public String getPlayerName() {
      return this.playerName;
   }

   public Instant getFlaggedAt() {
      return this.flaggedAt;
   }

   public InventorySnapshot.ContainerType getContainerType() {
      return this.containerType;
   }

   public String getItemId() {
      return this.itemId;
   }

   public int getDetectedAmount() {
      return this.detectedAmount;
   }

   public int getConfiguredCap() {
      return this.configuredCap;
   }

   public int getRecursionDepth() {
      return this.recursionDepth;
   }

   public @Nullable Location getContainerLocation() {
      return this.containerLocation;
   }

   public @Nullable String getEntityIdentifier() {
      return this.entityIdentifier;
   }

   public String toString() {
      return String.format("[DUPLICATION_SUSPICION] Player: %s (%s) | Time: %s | Container: %s | Item: %s | Amount: %d | Cap: %d | Depth: %d | Location: %s | Entity: %s", this.playerName, this.playerUUID, this.flaggedAt, this.containerType, this.itemId, this.detectedAmount, this.configuredCap, this.recursionDepth, this.containerLocation != null ? String.format("W:%s X:%.0f Y:%.0f Z:%.0f", this.containerLocation.getWorld().getName(), this.containerLocation.getX(), this.containerLocation.getY(), this.containerLocation.getZ()) : "N/A", this.entityIdentifier != null ? this.entityIdentifier : "N/A");
   }
}
