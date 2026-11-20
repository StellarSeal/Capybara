package me.stella.duplication.snapshot;

import java.util.List;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

public class InventorySnapshot {

   private final UUID playerUUID;
   private final String playerName;
   private final long timestamp;
   private final ContainerType containerType;
   private final List<ItemEntry> items;
   private final @Nullable Location containerLocation;
   private final @Nullable String entityIdentifier;

   public InventorySnapshot(UUID playerUUID, String playerName, long timestamp, ContainerType containerType,
         List<ItemEntry> items, @Nullable Location containerLocation, @Nullable String entityIdentifier) {
      this.playerUUID = playerUUID;
      this.playerName = playerName;
      this.timestamp = timestamp;
      this.containerType = containerType;
      this.items = List.copyOf(items);
      this.containerLocation = containerLocation;
      this.entityIdentifier = entityIdentifier;
   }

   public UUID getPlayerUUID() {
      return playerUUID;
   }

   public String getPlayerName() {
      return playerName;
   }

   public long getTimestamp() {
      return timestamp;
   }

   public ContainerType getContainerType() {
      return containerType;
   }

   public List<ItemEntry> getItems() {
      return items;
   }

   public @Nullable Location getContainerLocation() {
      return containerLocation;
   }

   public @Nullable String getEntityIdentifier() {
      return entityIdentifier;
   }

   public enum ContainerType {
      PLAYER_INVENTORY,
      PLAYER_ENDER_CHEST,
      CONTAINER,
      SHULKER_BOX,
      BUNDLE,
      ENTITY_INVENTORY,
      CUSTOM_INVENTORY,
      ITEM_PICKUP,
      ITEM_DROP
   }

   public static class ItemEntry {

      public final String itemId;
      public final int amount;
      public final int slot;
      public final @Nullable ItemStack itemStack;

      public ItemEntry(String itemId, int amount, int slot, @Nullable ItemStack itemStack) {
         this.itemId = itemId;
         this.amount = amount;
         this.slot = slot;
         this.itemStack = itemStack;
      }

      public ItemEntry(String itemId, int amount, int slot) {
         this(itemId, amount, slot, null);
      }
   }
}
