package me.stella.duplication.engine;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import me.stella.CapybaraScan;
import me.stella.duplication.alert.SuspicionRecord;
import me.stella.duplication.config.DuplicationDetectionConfig;
import me.stella.duplication.snapshot.InventorySnapshot;
import org.bukkit.Material;
import org.bukkit.block.ShulkerBox;
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

   @Override
   public void run() {
      try {
         Map<String, AggregatedItemData> aggregated = aggregateItems(snapshot.getItems(), 0);
         evaluateAndFlag(aggregated);
      } catch (Exception e) {
         System.err.println("[DuplicationDetection] Error scanning inventory for " + snapshot.getPlayerName());
         e.printStackTrace();
      }
   }

   private @Nullable String getItemIdentifier(@Nullable ItemStack itemStack) {
      return itemStack != null && itemStack.getType() != Material.AIR ? CapybaraScan.toSearchItemID(itemStack) : null;
   }

   private Map<String, AggregatedItemData> aggregateItems(List<InventorySnapshot.ItemEntry> items, int currentDepth) {
      Map<String, AggregatedItemData> result = new HashMap<>();
      if (currentDepth > config.maxRecursionDepth()) {
         return result;
      }

      for (InventorySnapshot.ItemEntry entry : items) {
         String itemId = entry.itemStack != null ? getItemIdentifier(entry.itemStack) : entry.itemId;
         if (itemId == null || entry.amount <= 0) {
            continue;
         }

         AggregatedItemData data = result.computeIfAbsent(itemId, k -> new AggregatedItemData());
         data.totalAmount += entry.amount;
         data.maxDepth = Math.max(data.maxDepth, currentDepth);

         if (entry.itemStack != null && isDeep(entry.itemStack)) {
            Map<String, Integer> nestedItems = scanNestedContainer(entry.itemStack, currentDepth + 1);
            for (Map.Entry<String, Integer> nestedEntry : nestedItems.entrySet()) {
               AggregatedItemData nestedData = result.computeIfAbsent(nestedEntry.getKey(), k -> new AggregatedItemData());
               nestedData.totalAmount += nestedEntry.getValue();
               nestedData.maxDepth = Math.max(nestedData.maxDepth, currentDepth + 1);
            }
         }
      }
      return result;
   }

   private boolean isDeep(@Nullable ItemStack itemStack) {
      if (itemStack == null) {
         return false;
      }
      ItemMeta meta = itemStack.getItemMeta();
      return meta instanceof BundleMeta
            || meta instanceof BlockStateMeta blockStateMeta && blockStateMeta.getBlockState() instanceof ShulkerBox;
   }

   private Map<String, Integer> scanNestedContainer(@Nullable ItemStack container, int depth) {
      Map<String, Integer> result = new HashMap<>();
      if (container == null || container.getType() == Material.AIR || depth > config.maxRecursionDepth()) {
         return result;
      }
      ItemMeta meta = container.getItemMeta();
      if (meta == null) {
         return result;
      }

      for (ItemStack item : extractNestedItems(meta)) {
         if (item == null || item.getType() == Material.AIR) {
            continue;
         }
         String itemId = getItemIdentifier(item);
         if (itemId != null) {
            result.merge(itemId, item.getAmount(), Integer::sum);
         }
         if (isDeep(item)) {
            scanNestedContainer(item, depth + 1)
                  .forEach((nestedId, nestedAmount) -> result.merge(nestedId, nestedAmount, Integer::sum));
         }
      }
      return result;
   }

   private Iterable<ItemStack> extractNestedItems(ItemMeta meta) {
      if (meta instanceof BlockStateMeta blockStateMeta && blockStateMeta.getBlockState() instanceof ShulkerBox shulker) {
         return Arrays.asList(shulker.getInventory().getContents());
      }
      if (meta instanceof BundleMeta bundle) {
         return bundle.getItems();
      }
      return List.of();
   }

   private void evaluateAndFlag(Map<String, AggregatedItemData> aggregated) {
      Instant now = Instant.now().atZone(ZoneId.of("GMT+07")).toInstant();
      for (Map.Entry<String, AggregatedItemData> entry : aggregated.entrySet()) {
         String itemId = entry.getKey();
         Integer cap = config.getCapForItem(itemId);
         if (cap == null || cap == Integer.MAX_VALUE) {
            continue;
         }
         AggregatedItemData data = entry.getValue();
         if (data.totalAmount > cap) {
            module.logSuspicion(new SuspicionRecord(
                  snapshot.getPlayerUUID(), snapshot.getPlayerName(), now,
                  snapshot.getContainerType(), itemId, data.totalAmount, cap, data.maxDepth,
                  snapshot.getContainerLocation(), snapshot.getEntityIdentifier()));
         }
      }
   }

   private static class AggregatedItemData {

      int totalAmount = 0;
      int maxDepth = 0;
   }
}
