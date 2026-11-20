package me.stella.duplication.snapshot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import me.stella.CapybaraScan;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Camel;
import org.bukkit.entity.ChestedHorse;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Llama;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;

public final class InventorySnapshotFactory {

   private static final int MAX_CAPTURE_DEPTH = 8;

   private InventorySnapshotFactory() {
   }

   public static InventorySnapshot capturePlayerInventory(Player player) {
      return new InventorySnapshot(player.getUniqueId(), player.getName(), System.currentTimeMillis(),
            InventorySnapshot.ContainerType.PLAYER_INVENTORY, cloneInventoryItems(player.getInventory()), null, null);
   }

   public static InventorySnapshot captureEnderChest(Player player) {
      return new InventorySnapshot(player.getUniqueId(), player.getName(), System.currentTimeMillis(),
            InventorySnapshot.ContainerType.PLAYER_ENDER_CHEST, cloneInventoryItems(player.getEnderChest()), null, null);
   }

   public static InventorySnapshot captureContainer(Player accessingPlayer, BlockState container) {
      Location loc = container.getLocation();
      InventorySnapshot.ContainerType type = container instanceof ShulkerBox
            ? InventorySnapshot.ContainerType.SHULKER_BOX
            : InventorySnapshot.ContainerType.CONTAINER;
      Inventory inv = ((InventoryHolder) container).getInventory();
      return new InventorySnapshot(accessingPlayer.getUniqueId(), accessingPlayer.getName(), System.currentTimeMillis(),
            type, cloneInventoryItems(inv), loc != null ? loc.clone() : null, container.getClass().getSimpleName());
   }

   public static @Nullable InventorySnapshot captureEntityInventory(Player accessingPlayer, Entity entity) {
      Inventory inv = resolveEntityInventory(entity);
      if (inv == null) {
         return null;
      }
      return new InventorySnapshot(accessingPlayer.getUniqueId(), accessingPlayer.getName(), System.currentTimeMillis(),
            InventorySnapshot.ContainerType.ENTITY_INVENTORY, cloneInventoryItems(inv), entity.getLocation().clone(),
            entity.getType().toString());
   }

   public static InventorySnapshot captureCustomInventory(Player player, Inventory inventory, @Nullable String openingCommand) {
      InventoryHolder holder = inventory.getHolder();
      String holderName = holder == null ? "none" : holder.getClass().getName();
      String source = "Command: " + (openingCommand == null ? "unknown" : openingCommand) + " | Holder: " + holderName;
      return new InventorySnapshot(player.getUniqueId(), player.getName(), System.currentTimeMillis(),
            InventorySnapshot.ContainerType.CUSTOM_INVENTORY, cloneInventoryItems(inventory), null, source);
   }

   public static InventorySnapshot captureItemTransfer(Player player, ItemStack transferredItem, int transferredAmount,
         InventorySnapshot.ContainerType type, @Nullable Location location) {
      if (type != InventorySnapshot.ContainerType.ITEM_PICKUP && type != InventorySnapshot.ContainerType.ITEM_DROP) {
         throw new IllegalArgumentException("Item transfer snapshot requires a pickup or drop type");
      }
      List<InventorySnapshot.ItemEntry> items = cloneInventoryItems(player.getInventory());
      if (transferredItem != null && transferredItem.getType() != Material.AIR && transferredAmount > 0) {
         ItemStack transfer = transferredItem.clone();
         transfer.setAmount(transferredAmount);
         String itemId = getItemId(transfer);
         if (itemId != null) {
            items.add(new InventorySnapshot.ItemEntry(itemId, transferredAmount, -1));
            scanNestedStorage(transfer, items, 1);
         }
      }
      String source = type == InventorySnapshot.ContainerType.ITEM_PICKUP ? "ITEM_PICKUP" : "ITEM_DROP";
      return new InventorySnapshot(player.getUniqueId(), player.getName(), System.currentTimeMillis(),
            type, items, location == null ? null : location.clone(), source);
   }

   private static @Nullable Inventory resolveEntityInventory(Entity entity) {
      if (entity instanceof ChestedHorse horse) {
         return horse.getInventory();
      }
      if (entity instanceof Llama llama) {
         return llama.getInventory();
      }
      if (entity instanceof Camel camel) {
         return camel.getInventory();
      }
      return null;
   }

   private static List<InventorySnapshot.ItemEntry> cloneInventoryItems(@Nullable Inventory inventory) {
      List<InventorySnapshot.ItemEntry> items = new ArrayList<>();
      if (inventory == null) {
         return items;
      }
      ItemStack[] contents = inventory.getContents();
      for (int slot = 0; slot < contents.length; slot++) {
         ItemStack stack = contents[slot];
         String itemId = getItemId(stack);
         if (itemId != null && stack.getAmount() > 0) {
            items.add(new InventorySnapshot.ItemEntry(itemId, stack.getAmount(), slot));
            scanNestedStorage(stack, items, 1);
         }
      }
      return items;
   }

   private static void scanNestedStorage(ItemStack item, List<InventorySnapshot.ItemEntry> items, int depth) {
      if (depth >= MAX_CAPTURE_DEPTH || item == null) {
         return;
      }
      ItemMeta meta = item.getItemMeta();
      if (meta == null) {
         return;
      }
      for (ItemStack nested : extractNestedItems(meta)) {
         String itemId = getItemId(nested);
         if (itemId != null) {
            items.add(new InventorySnapshot.ItemEntry(itemId, nested.getAmount(), -1));
            scanNestedStorage(nested, items, depth + 1);
         }
      }
   }

   private static Iterable<ItemStack> extractNestedItems(ItemMeta meta) {
      if (meta instanceof BundleMeta bundle) {
         return bundle.getItems();
      }
      if (meta instanceof BlockStateMeta blockMeta && blockMeta.getBlockState() instanceof ShulkerBox shulker) {
         return Arrays.asList(shulker.getInventory().getContents());
      }
      return List.of();
   }

   private static @Nullable String getItemId(@Nullable ItemStack item) {
      return item != null && item.getType() != Material.AIR ? CapybaraScan.toSearchItemID(item) : null;
   }
}
