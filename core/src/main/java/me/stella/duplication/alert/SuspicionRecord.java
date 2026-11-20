package me.stella.duplication.alert;

import java.time.Instant;
import java.util.UUID;
import me.stella.duplication.snapshot.InventorySnapshot;
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
   private final @Nullable Location containerLocation;
   private final @Nullable String entityIdentifier;

   public SuspicionRecord(UUID playerUUID, String playerName, Instant flaggedAt,
         InventorySnapshot.ContainerType containerType, String itemId, int detectedAmount,
         int configuredCap, int recursionDepth, @Nullable Location containerLocation,
         @Nullable String entityIdentifier) {
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
      return playerUUID;
   }

   public String getPlayerName() {
      return playerName;
   }

   public Instant getFlaggedAt() {
      return flaggedAt;
   }

   public InventorySnapshot.ContainerType getContainerType() {
      return containerType;
   }

   public String getItemId() {
      return itemId;
   }

   public int getDetectedAmount() {
      return detectedAmount;
   }

   public int getConfiguredCap() {
      return configuredCap;
   }

   public int getRecursionDepth() {
      return recursionDepth;
   }

   public @Nullable Location getContainerLocation() {
      return containerLocation;
   }

   public @Nullable String getEntityIdentifier() {
      return entityIdentifier;
   }

   @Override
   public String toString() {
      String locationText = "N/A";
      if (containerLocation != null) {
         String worldName = containerLocation.getWorld() != null ? containerLocation.getWorld().getName() : "Unknown";
         locationText = String.format("W:%s X:%.0f Y:%.0f Z:%.0f",
               worldName, containerLocation.getX(), containerLocation.getY(), containerLocation.getZ());
      }
      return String.format(
            "[DUPLICATION_SUSPICION] Player: %s (%s) | Time: %s | Container: %s | Item: %s | Amount: %d | Cap: %d | Depth: %d | Location: %s | Entity: %s",
            playerName, playerUUID, flaggedAt, containerType, itemId, detectedAmount,
            configuredCap, recursionDepth, locationText, entityIdentifier != null ? entityIdentifier : "N/A");
   }
}
