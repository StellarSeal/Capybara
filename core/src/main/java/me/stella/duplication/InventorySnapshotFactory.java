package me.stella.duplication;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import me.stella.CapybaraScan;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Camel;
import org.bukkit.entity.ChestedHorse;
import org.bukkit.entity.Donkey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Llama;
import org.bukkit.entity.Mule;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;

public class InventorySnapshotFactory {
   private static final Map<Material, Boolean> SHULKER_CACHE = new ConcurrentHashMap<>();
   private static final Set<Class<?>> ENTITY_TYPES = ConcurrentHashMap.newKeySet();

   public static InventorySnapshot capturePlayerInventory(Player player) {
      List<InventorySnapshot.ItemEntry> items = cloneInventoryItems(player.getInventory());
      return new InventorySnapshot(player.getUniqueId(), player.getName(), System.currentTimeMillis(), InventorySnapshot.ContainerType.PLAYER_INVENTORY, items, (Location)null, (String)null);
   }

   public static InventorySnapshot captureEnderChest(Player player) {
      List<InventorySnapshot.ItemEntry> items = cloneInventoryItems(player.getEnderChest());
      return new InventorySnapshot(player.getUniqueId(), player.getName(), System.currentTimeMillis(), InventorySnapshot.ContainerType.PLAYER_ENDER_CHEST, items, (Location)null, (String)null);
   }

   public static InventorySnapshot captureContainer(Player accessingPlayer, BlockState container) {
      Location loc = container.getLocation();
      String containerTypeStr = container.getClass().getSimpleName();
      InventorySnapshot.ContainerType type = InventorySnapshot.ContainerType.CONTAINER;
      if (container instanceof ShulkerBox) {
         type = InventorySnapshot.ContainerType.SHULKER_BOX;
      }

      Inventory inv = ((InventoryHolder)container).getInventory();
      List<InventorySnapshot.ItemEntry> items = cloneInventoryItems(inv);
      return new InventorySnapshot(accessingPlayer.getUniqueId(), accessingPlayer.getName(), System.currentTimeMillis(), type, items, loc != null ? loc.clone() : null, containerTypeStr);
   }

   public static InventorySnapshot captureEntityInventory(Player accessingPlayer, Entity entity) {
      Location loc = entity.getLocation().clone();
      String entityId = entity.getType().toString();
      Inventory inv = null;
      if (entity instanceof ChestedHorse horse) {
         inv = horse.getInventory();
      } else if (entity instanceof Llama llama) {
         inv = llama.getInventory();
      } else if (entity instanceof Camel camel) {
         inv = camel.getInventory();
      }

      if (inv == null) {
         return null;
      } else {
         List<InventorySnapshot.ItemEntry> items = cloneInventoryItems(inv);
         return new InventorySnapshot(accessingPlayer.getUniqueId(), accessingPlayer.getName(), System.currentTimeMillis(), InventorySnapshot.ContainerType.ENTITY_INVENTORY, items, loc, entityId);
      }
   }

   public static InventorySnapshot captureCustomInventory(Player player, Inventory inventory, @Nullable String openingCommand) {
      InventoryHolder holder = inventory.getHolder();
      String holderName = holder == null ? "none" : holder.getClass().getName();
      String source = "Command: " + (openingCommand == null ? "unknown" : openingCommand) + " | Holder: " + holderName;
      return new InventorySnapshot(player.getUniqueId(), player.getName(), System.currentTimeMillis(), InventorySnapshot.ContainerType.CUSTOM_INVENTORY, cloneInventoryItems(inventory), (Location)null, source);
   }

   public static InventorySnapshot captureItemTransfer(Player player, ItemStack transferredItem, int transferredAmount, InventorySnapshot.ContainerType type, @Nullable Location location) {
      if (type != InventorySnapshot.ContainerType.ITEM_PICKUP && type != InventorySnapshot.ContainerType.ITEM_DROP) {
         throw new IllegalArgumentException("Item transfer snapshot requires a pickup or drop type");
      } else {
         List<InventorySnapshot.ItemEntry> items = cloneInventoryItems(player.getInventory());
         if (transferredItem != null && transferredItem.getType() != Material.AIR && transferredAmount > 0) {
            ItemStack transfer = transferredItem.clone();
            transfer.setAmount(transferredAmount);
            String itemId = getItemId(transfer);
            if (itemId != null) {
               items.add(new InventorySnapshot.ItemEntry(itemId, transferredAmount, -1));
               scanNestedStorage(transfer, items, 1, 8);
            }
         }

         return new InventorySnapshot(player.getUniqueId(), player.getName(), System.currentTimeMillis(), type, items, location == null ? null : location.clone(), type == InventorySnapshot.ContainerType.ITEM_PICKUP ? "ITEM_PICKUP" : "ITEM_DROP");
      }
   }

   private static List<InventorySnapshot.ItemEntry> cloneInventoryItems(Inventory inventory) {
      List<InventorySnapshot.ItemEntry> items = new ArrayList<>();
      if (inventory == null) {
         return items;
      } else {
         ItemStack[] contents = inventory.getContents();

         for(int slot = 0; slot < contents.length; ++slot) {
            ItemStack stack = contents[slot];
            if (stack != null && stack.getType() != Material.AIR) {
               String itemId = getItemId(stack);
               if (itemId != null && stack.getAmount() > 0) {
                  items.add(new InventorySnapshot.ItemEntry(itemId, stack.getAmount(), slot));
                  scanNestedStorage(stack, items, 1, 8);
               }
            }
         }

         return items;
      }
   }

   private static void scanNestedStorage(ItemStack item, List<InventorySnapshot.ItemEntry> items, int depth, int maxDepth) {
      if (depth < maxDepth && item != null) {
         ItemMeta meta = item.getItemMeta();
         if (meta != null) {
            if (meta instanceof BundleMeta) {
               BundleMeta bundle = (BundleMeta)meta;

               for(ItemStack bundledItem : bundle.getItems()) {
                  if (bundledItem != null && bundledItem.getType() != Material.AIR) {
                     String itemId = getItemId(bundledItem);
                     if (itemId != null) {
                        items.add(new InventorySnapshot.ItemEntry(itemId, bundledItem.getAmount(), -1));
                        scanNestedStorage(bundledItem, items, depth + 1, maxDepth);
                     }
                  }
               }
            } else if (meta instanceof BlockStateMeta) {
               BlockStateMeta blockStateMeta = (BlockStateMeta)meta;
               BlockState state = blockStateMeta.getBlockState();
               if (state instanceof ShulkerBox) {
                  ShulkerBox shulker = (ShulkerBox)state;
                  Inventory shulkerInv = shulker.getInventory();
                  ItemStack[] contents = shulkerInv.getContents();

                  for(ItemStack nestedStack : contents) {
                     if (nestedStack != null && nestedStack.getType() != Material.AIR) {
                        String itemId = getItemId(nestedStack);
                        if (itemId != null) {
                           items.add(new InventorySnapshot.ItemEntry(itemId, nestedStack.getAmount(), -1));
                           scanNestedStorage(nestedStack, items, depth + 1, maxDepth);
                        }
                     }
                  }
               }
            }

         }
      }
   }

   private static @Nullable String getItemId(ItemStack item) {
      return item != null && item.getType() != Material.AIR ? CapybaraScan.toSearchItemID(item) : null;
   }

   private static boolean isShulkerBox(Material material) {
      return (Boolean)SHULKER_CACHE.computeIfAbsent(material, (m) -> {
         String name = m.name();
         return name.endsWith("_SHULKER_BOX");
      });
   }

   static {
      ENTITY_TYPES.add(Donkey.class);
      ENTITY_TYPES.add(Llama.class);
      ENTITY_TYPES.add(Camel.class);
      ENTITY_TYPES.add(Horse.class);
      ENTITY_TYPES.add(Mule.class);
   }
}
