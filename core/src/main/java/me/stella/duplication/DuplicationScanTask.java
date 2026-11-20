package me.stella.duplication;

import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import me.stella.CapybaraScan;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;

public class DuplicationScanTask implements Runnable {
   private final InventorySnapshot snapshot;
   private final DuplicationDetectionConfig config;
   private final DuplicationDetectionModule module;

   public DuplicationScanTask(InventorySnapshot snapshot, DuplicationDetectionConfig config, DuplicationDetectionModule module) {
      this.snapshot = snapshot;
      this.config = config;
      this.module = module;
   }

   public void run() {
      try {
         Map<String, AggregatedItemData> aggregated = this.aggregateItems(this.snapshot.getItems(), 0);
         this.evaluateAndFlag(aggregated);
      } catch (Exception e) {
         System.err.println("[DuplicationDetection] Error scanning inventory for " + this.snapshot.getPlayerName());
         e.printStackTrace();
      }

   }

   private String getItemIdentifier(@Nullable ItemStack itemStack) {
      return itemStack != null && itemStack.getType() != Material.AIR ? CapybaraScan.toSearchItemID(itemStack) : null;
   }

   private Map<String, AggregatedItemData> aggregateItems(List<InventorySnapshot.ItemEntry> items, int currentDepth) {
      Map<String, AggregatedItemData> result = new ConcurrentHashMap<>();
      if (currentDepth > this.config.maxRecursionDepth()) {
         return result;
      } else {
         for(InventorySnapshot.ItemEntry entry : items) {
            String itemId = entry.itemStack != null ? this.getItemIdentifier(entry.itemStack) : entry.itemId;
            if (itemId != null && entry.amount > 0) {
               AggregatedItemData data = (AggregatedItemData)result.computeIfAbsent(itemId, (k) -> new AggregatedItemData());
               data.totalAmount += entry.amount;
               data.maxDepth = Math.max(data.maxDepth, currentDepth);
               if (entry.itemStack != null && this.isDeep(entry.itemStack)) {
                  Map<String, Integer> nestedItems = this.scanNestedContainer(entry.itemStack, currentDepth + 1);

                  for(Map.Entry<String, Integer> nestedEntry : nestedItems.entrySet()) {
                     AggregatedItemData nestedData = (AggregatedItemData)result.computeIfAbsent((String)nestedEntry.getKey(), (k) -> new AggregatedItemData());
                     nestedData.totalAmount += (Integer)nestedEntry.getValue();
                     nestedData.maxDepth = Math.max(nestedData.maxDepth, currentDepth + 1);
                  }
               }
            }
         }

         return result;
      }
   }

   private boolean isDeep(ItemStack itemStack) {
      if (itemStack == null) {
         return false;
      } else {
         ItemMeta meta = itemStack.getItemMeta();
         boolean var10000;
         if (!(meta instanceof BundleMeta)) {
            label32: {
               if (meta instanceof BlockStateMeta) {
                  BlockStateMeta blockStateMeta = (BlockStateMeta)meta;
                  if (blockStateMeta.getBlockState() instanceof ShulkerBox) {
                     break label32;
                  }
               }

               var10000 = false;
               return var10000;
            }
         }

         var10000 = true;
         return var10000;
      }
   }

   private Map<String, Integer> scanNestedContainer(ItemStack container, int depth) {
      Map<String, Integer> result = new HashMap<>();
      if (container != null && container.getType() != Material.AIR) {
         if (depth > this.config.maxRecursionDepth()) {
            return result;
         } else {
            ItemMeta meta = container.getItemMeta();
            if (meta == null) {
               return result;
            } else {
               if (meta instanceof BlockStateMeta) {
                  BlockStateMeta blockStateMeta = (BlockStateMeta)meta;
                  BlockState block = blockStateMeta.getBlockState();
                  if (block instanceof ShulkerBox) {
                     ShulkerBox shulkerBox = (ShulkerBox)block;
                     Inventory inventory = shulkerBox.getInventory();

                     for(ItemStack item : inventory.getContents()) {
                        if (item != null && item.getType() != Material.AIR) {
                           String itemId = this.getItemIdentifier(item);
                           if (itemId != null) {
                              result.put(itemId, (Integer)result.getOrDefault(itemId, 0) + item.getAmount());
                           }

                           if (this.isDeep(item)) {
                              Map<String, Integer> nested = this.scanNestedContainer(item, depth + 1);

                              for(Map.Entry<String, Integer> nestedEntry : nested.entrySet()) {
                                 result.put((String)nestedEntry.getKey(), (Integer)result.getOrDefault(nestedEntry.getKey(), 0) + (Integer)nestedEntry.getValue());
                              }
                           }
                        }
                     }
                  }
               } else if (meta instanceof BundleMeta) {
                  BundleMeta bundleMeta = (BundleMeta)meta;

                  for(ItemStack item : bundleMeta.getItems()) {
                     if (item != null && item.getType() != Material.AIR) {
                        String itemId = this.getItemIdentifier(item);
                        if (itemId != null) {
                           result.put(itemId, (Integer)result.getOrDefault(itemId, 0) + item.getAmount());
                        }

                        if (this.isDeep(item)) {
                           Map<String, Integer> nested = this.scanNestedContainer(item, depth + 1);

                           for(Map.Entry<String, Integer> nestedEntry : nested.entrySet()) {
                              result.put((String)nestedEntry.getKey(), (Integer)result.getOrDefault(nestedEntry.getKey(), 0) + (Integer)nestedEntry.getValue());
                           }
                        }
                     }
                  }
               }

               return result;
            }
         }
      } else {
         return result;
      }
   }

   private void evaluateAndFlag(Map<String, AggregatedItemData> aggregated) {
      Instant now = Instant.now().atZone(ZoneId.of("GMT+07")).toInstant();

      for(Map.Entry<String, AggregatedItemData> entry : aggregated.entrySet()) {
         String itemId = (String)entry.getKey();
         Integer cap = this.config.getCapForItem(itemId);
         if (cap != null && cap != 2147483647) {
            int detectedAmount = ((AggregatedItemData)entry.getValue()).totalAmount;
            int depth = ((AggregatedItemData)entry.getValue()).maxDepth;
            if (detectedAmount > cap) {
               SuspicionRecord record = new SuspicionRecord(this.snapshot.getPlayerUUID(), this.snapshot.getPlayerName(), now, this.snapshot.getContainerType(), itemId, detectedAmount, cap, depth, this.snapshot.getContainerLocation(), this.snapshot.getEntityIdentifier());
               this.module.logSuspicion(record);
            }
         }
      }

   }

   private static class AggregatedItemData {
      int totalAmount = 0;
      int maxDepth = 0;
   }
}
