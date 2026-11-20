package me.stella;

import com.drtshock.playervaults.PlayerVaults;
import com.drtshock.playervaults.vaultmanagement.CardboardBoxSerialization;
import com.earth2me.essentials.Essentials;
import fr.maxlego08.zauctionhouse.ZAuctionPlugin;
import fr.maxlego08.zauctionhouse.api.AuctionItem;
import fr.maxlego08.zauctionhouse.api.enums.StorageType;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import me.stella.scan.engine.PlayerDataScanEngine;
import me.stella.scan.engine.ScanExecutors;
import me.stella.scan.engine.WorldScanEngine;
import me.stella.scan.model.ScanProgress;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.ShulkerBox;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Version-specific scanner contract implemented once per NMS module ({@code me.stella.nativelib.Capybara<key>}).
 *
 * <p>Heavy storage scanning (Anvil {@code .mca}/{@code .mcc} reading, streaming binary NBT parsing,
 * playerdata scanning, and isolated worker scheduling) lives in {@code core} so every supported MC
 * version shares the same zero-DOM hot path. NMS adapters only provide dimension-path resolution and
 * exact item deserialization fallback.</p>
 */
public interface CapybaraNative {

   boolean isMatch(ItemStack model, ItemStack entry);

   /** Resolves the on-disk dimension root containing {@code region/} and {@code entities/}. */
   Path dimensionPath(World world);

   /** Exact NMS fallback matcher for non-basic vanilla items whose raw NBT {@code id} matched the target. */
   boolean matchesRawItemNbt(byte[] namedRootCompound, ItemStack model);

   default Map<String, Integer> inventory(ItemStack target, ScanProgress progress) {
      return PlayerDataScanEngine.scan(this, target, false, progress);
   }

   default Map<String, Integer> ender(ItemStack target, ScanProgress progress) {
      return PlayerDataScanEngine.scan(this, target, true, progress);
   }

   default Map<String, Integer> world(ItemStack target, String world, ScanProgress progress) {
      return WorldScanEngine.scanWorld(this, target, world, progress);
   }

   default Map<String, Integer> world(ItemStack target, String world, long minTimestampSec, ScanProgress progress) {
      return WorldScanEngine.scanWorld(this, target, world, minTimestampSec, progress);
   }

   default Map<String, Long> pollInventory(ScanProgress progress) {
      return PlayerDataScanEngine.poll(false, progress);
   }

   default Map<String, Long> pollEnderChest(ScanProgress progress) {
      return PlayerDataScanEngine.poll(true, progress);
   }

   default Map<String, Long> pollWorld(String world, ScanProgress progress) {
      return WorldScanEngine.pollWorld(this, world, progress);
   }

   default Map<String, Long> pollWorld(String world, long minTimestampSec, ScanProgress progress) {
      return WorldScanEngine.pollWorld(this, world, minTimestampSec, progress);
   }

   static File[] playerDataFiles() {
      File folder = new File(Bukkit.getServer().getWorldContainer(), Bukkit.getWorlds().get(0).getName() + "/playerdata");
      File[] files = folder.listFiles((dir, name) -> name.endsWith(".dat"));
      return files == null ? new File[0] : Arrays.stream(files).filter(File::isFile).toArray(File[]::new);
   }

   default Map<String, Double> scanHouse(Location point, double radii, ScanProgress progress) {
      Map<String, Double> output = new ConcurrentHashMap<>();
      progress.trackAtomic(false);

      Essentials essentials;
      try {
         essentials = (Essentials) Bukkit.getPluginManager().getPlugin("Essentials");
      } catch (Exception notEssentials) {
         return output;
      }
      if (essentials == null) {
         return output;
      }

      File userdataDir = new File(essentials.getDataFolder(), "userdata");
      if (!userdataDir.isDirectory()) {
         return output;
      }
      File[] userFiles = userdataDir.listFiles((dir, name) -> name.endsWith(".yml") && !name.equals(".yml"));
      if (userFiles == null || userFiles.length == 0) {
         return output;
      }

      progress.total(userFiles.length);
      UUID pointWorldId = point.getWorld() != null ? point.getWorld().getUID() : null;
      double closestDistance = Double.MAX_VALUE;
      String closestKey = null;

      for (File userFile : userFiles) {
         if (progress.isCancelled()) {
            break;
         }
         try {
            UUID uuid;
            try {
               uuid = UUID.fromString(userFile.getName().replace(".yml", ""));
            } catch (IllegalArgumentException notAUserFile) {
               continue;
            }
            String name = Bukkit.getOfflinePlayer(uuid).getName();
            if (name == null) {
               continue;
            }
            FileConfiguration config = YamlConfiguration.loadConfiguration(userFile);
            ConfigurationSection homeSection = config.getConfigurationSection("homes");
            if (homeSection == null) {
               homeSection = config.getConfigurationSection("home");
            }
            if (homeSection != null) {
               for (String homeKey : homeSection.getKeys(false)) {
                  double distance = distanceTo(homeSection.getConfigurationSection(homeKey), point, pointWorldId);
                  if (distance <= radii && distance < closestDistance) {
                     closestDistance = distance;
                     closestKey = name + ";" + homeKey;
                  }
               }
            }
            for (String sectionKey : new String[]{"lastlocation", "logoutlocation"}) {
               double distance = distanceTo(config.getConfigurationSection(sectionKey), point, pointWorldId);
               if (distance <= radii && distance < closestDistance) {
                  closestDistance = distance;
                  closestKey = name + ";" + sectionKey;
               }
            }
         } catch (Exception err) {
            progress.recordError();
         } finally {
            progress.advance();
         }
      }
      if (closestKey != null) {
         output.put(closestKey, closestDistance);
         progress.recordMatch(1L, 0L);
      }
      return output;
   }

   private static double distanceTo(ConfigurationSection location, Location point, UUID pointWorldId) {
      if (location == null) {
         return Double.POSITIVE_INFINITY;
      }
      String world = location.getString("world", "");
      String worldName = location.getString("world-name", "");
      UUID worldId = null;
      if (!world.isEmpty()) {
         try {
            worldId = UUID.fromString(world);
         } catch (IllegalArgumentException ignored) {
         }
      }
      if (worldId != null) {
         if (pointWorldId != null && !pointWorldId.equals(worldId)) {
            return Double.POSITIVE_INFINITY;
         }
      } else if (worldName.isEmpty() || point.getWorld() != null && !point.getWorld().getName().equals(worldName)) {
         return Double.POSITIVE_INFINITY;
      }
      double dx = location.getDouble("x", 0.0) - point.getX();
      double dy = location.getDouble("y", 0.0) - point.getY();
      double dz = location.getDouble("z", 0.0) - point.getZ();
      return Math.sqrt(dx * dx + dy * dy + dz * dz);
   }

   default Map<String, Double> scan(Location point, double radii, ScanProgress progress) {
      return scanHouse(point, radii, progress);
   }

   default boolean isDeep(ItemStack itemStack) {
      if (itemStack == null || !itemStack.hasItemMeta()) {
         return false;
      }
      ItemMeta meta = itemStack.getItemMeta();
      return meta instanceof BundleMeta
            || meta instanceof BlockStateMeta blockStateMeta && blockStateMeta.getBlockState() instanceof ShulkerBox;
   }

   default boolean isBasic(ItemStack itemStack) {
      if (itemStack == null || !itemStack.hasItemMeta()) {
         return true;
      }
      ItemMeta meta = itemStack.getItemMeta();
      if (meta == null) {
         return true;
      }
      return !meta.hasLore()
            && !meta.hasDisplayName()
            && !meta.hasEnchants()
            && !meta.hasCustomModelData()
            && meta.getPersistentDataContainer().getKeys().isEmpty();
   }

   default int checkDepth(ItemStack itemStack, ItemStack target) {
      if (itemStack == null || itemStack.getType() == Material.AIR || !isDeep(itemStack)) {
         return 0;
      }
      ItemMeta meta = itemStack.getItemMeta();
      int output = 0;
      for (ItemStack item : nestedItems(meta)) {
         if (item != null && item.getType() != Material.AIR) {
            output += isMatch(target, item) ? item.getAmount() : checkDepth(item, target);
         }
      }
      return output;
   }

   private static Iterable<ItemStack> nestedItems(ItemMeta meta) {
      if (meta instanceof BlockStateMeta blockStateMeta) {
         BlockState block = blockStateMeta.getBlockState();
         if (block instanceof ShulkerBox shulkerBox) {
            Inventory inventory = shulkerBox.getInventory();
            return Arrays.asList(inventory.getContents());
         }
         return List.of();
      }
      if (meta instanceof BundleMeta bundleMeta) {
         return bundleMeta.getItems();
      }
      return List.of();
   }

   default Map<String, Map<String, Integer>> vault(ItemStack target, ScanProgress progress) {
      File vaultData = JavaPlugin.getPlugin(PlayerVaults.class).getVaultData();
      File[] rawFiles = vaultData.listFiles((dir, name) -> name.endsWith(".yml"));
      if (rawFiles == null || rawFiles.length == 0) {
         return new ConcurrentHashMap<>();
      }
      List<File> files = Arrays.stream(rawFiles).filter(File::isFile).toList();
      Map<String, Map<String, Integer>> output = new ConcurrentHashMap<>();
      progress.trackAtomic(true).total(files.size());

      AtomicInteger nextIndex = new AtomicInteger();
      int workers = Math.min(ScanExecutors.workerCount(), files.size());
      List<CompletableFuture<Void>> futures = new ArrayList<>(workers);

      for (int i = 0; i < workers; i++) {
         futures.add(ScanExecutors.runWorker(() -> {
            int idx;
            while (!progress.isCancelled() && (idx = nextIndex.getAndIncrement()) < files.size()) {
               File file = files.get(idx);
               try {
                  String name = Bukkit.getOfflinePlayer(UUID.fromString(file.getName().replace(".yml", ""))).getName();
                  if (name == null) {
                     continue;
                  }
                  FileConfiguration config = YamlConfiguration.loadConfiguration(file);
                  Map<String, Integer> pool = new HashMap<>();
                  int totalAccount = 0;
                  for (String key : config.getKeys(false)) {
                     ItemStack[] contents = CardboardBoxSerialization.fromStorage(config.getString(key, ""), name);
                     if (contents == null) {
                        continue;
                     }
                     for (ItemStack item : contents) {
                        if (item == null || item.getType() == Material.AIR) {
                           continue;
                        }
                        int found = isMatch(target, item) ? item.getAmount() : checkDepth(item, target);
                        if (found > 0) {
                           pool.merge(name + ";" + key, found, Integer::sum);
                           totalAccount += found;
                        }
                     }
                  }
                  if (totalAccount > 0) {
                     output.put(name, pool);
                     progress.recordMatch(1L, totalAccount);
                  }
               } catch (Exception err) {
                  progress.recordError();
               } finally {
                  progress.advance();
               }
            }
         }));
      }

      CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
      return output;
   }

   default Map<String, Integer> auctionHouse(ItemStack target, ScanProgress progress) {
      Map<String, Integer> output = new ConcurrentHashMap<>();
      progress.trackAtomic(true);
      ZAuctionPlugin auctionPlugin = readyAuctionPlugin();
      if (auctionPlugin != null) {
         scanAuctionBucket(target, auctionPlugin, StorageType.STORAGE, "selling", false, output, progress);
         scanAuctionBucket(target, auctionPlugin, StorageType.EXPIRE, "outdated", false, output, progress);
         scanAuctionBucket(target, auctionPlugin, StorageType.BUY, "purchased", true, output, progress);
      }
      return output;
   }

   private static ZAuctionPlugin readyAuctionPlugin() {
      if (!CapybaraScan.supportZAuctionHouse()) {
         return null;
      }
      Plugin installed = Bukkit.getPluginManager().getPlugin("zAuctionHouseV3");
      return installed instanceof ZAuctionPlugin auctionPlugin && auctionPlugin.isReady() ? auctionPlugin : null;
   }

   private static List<AuctionItem> auctionBucket(ZAuctionPlugin plugin, StorageType storageType, ScanProgress progress) {
      if (plugin.getStorage() == null) {
         return List.of();
      }
      List<AuctionItem> stored = plugin.getStorage().getItems(plugin, storageType);
      if (stored == null || stored.isEmpty()) {
         return List.of();
      }
      List<AuctionItem> snapshot = new ArrayList<>(stored);
      progress.expand(snapshot.size());
      return snapshot;
   }

   private static List<ItemStack> auctionContents(AuctionItem auctionItem) {
      List<ItemStack> contents = auctionItem.getItemStacks();
      if (contents == null || contents.isEmpty()) {
         ItemStack itemStack = auctionItem.getItemStack();
         contents = itemStack == null ? Collections.emptyList() : Collections.singletonList(itemStack);
      }
      return contents;
   }

   private void scanAuctionBucket(ItemStack target, ZAuctionPlugin plugin, StorageType storageType, String state,
         boolean linkBuyer, Map<String, Integer> output, ScanProgress progress) {
      for (AuctionItem auctionItem : auctionBucket(plugin, storageType, progress)) {
         if (progress.isCancelled()) {
            return;
         }
         try {
            if (auctionItem == null) {
               continue;
            }
            int count = countAuctionMatches(target, auctionItem);
            if (count < 1) {
               continue;
            }
            UUID ownerId = linkBuyer ? auctionItem.getBuyerUniqueId() : auctionItem.getSellerUniqueId();
            if (ownerId != null) {
               String ownerName = linkBuyer ? Bukkit.getOfflinePlayer(ownerId).getName() : auctionItem.getSellerName();
               String owner = ownerName == null ? ownerId.toString() : ownerName;
               String key = owner + ";" + state;
               Integer merged = output.merge(key, count, Integer::sum);
               progress.recordMatch(merged == count ? 1L : 0L, count);
            }
         } finally {
            progress.advance();
         }
      }
   }

   private int countAuctionMatches(ItemStack target, AuctionItem auctionItem) {
      int total = 0;
      for (ItemStack itemStack : auctionContents(auctionItem)) {
         if (itemStack != null && itemStack.getType() != Material.AIR) {
            total += isMatch(target, itemStack) ? itemStack.getAmount() : checkDepth(itemStack, target);
         }
      }
      return total;
   }

   default Map<String, Long> pollVault(ScanProgress progress) {
      Map<String, Long> output = new ConcurrentHashMap<>();
      progress.trackAtomic(true);
      if (!CapybaraScan.supportPlayerVaults()) {
         return output;
      }
      File vaultData = JavaPlugin.getPlugin(PlayerVaults.class).getVaultData();
      File[] rawFiles = vaultData.listFiles((dir, name) -> name.endsWith(".yml"));
      if (rawFiles == null || rawFiles.length == 0) {
         return output;
      }
      List<File> files = Arrays.stream(rawFiles).filter(File::isFile).toList();
      progress.total(files.size());

      AtomicInteger nextIndex = new AtomicInteger();
      int workers = Math.min(ScanExecutors.workerCount(), files.size());
      List<CompletableFuture<Void>> futures = new ArrayList<>(workers);

      for (int i = 0; i < workers; i++) {
         futures.add(ScanExecutors.runWorker(() -> {
            int idx;
            while (!progress.isCancelled() && (idx = nextIndex.getAndIncrement()) < files.size()) {
               File file = files.get(idx);
               try {
                  pollVaultFile(file, output, progress);
               } catch (Exception err) {
                  progress.recordError();
               } finally {
                  progress.advance();
               }
            }
         }));
      }

      CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
      return output;
   }

   private void pollVaultFile(File file, Map<String, Long> output, ScanProgress progress) {
      String name = Bukkit.getOfflinePlayer(UUID.fromString(file.getName().replace(".yml", ""))).getName();
      if (name == null) {
         return;
      }
      FileConfiguration config = YamlConfiguration.loadConfiguration(file);
      for (String key : config.getKeys(false)) {
         ItemStack[] contents = CardboardBoxSerialization.fromStorage(config.getString(key, ""), name);
         if (contents == null) {
            continue;
         }
         for (ItemStack item : contents) {
            if (item != null && item.getType() != Material.AIR) {
               recordTalliedItem(item, output, progress);
               accumulateDeepItems(item, output, progress);
            }
         }
      }
   }

   default Map<String, Long> pollAuctionHouse(ScanProgress progress) {
      Map<String, Long> output = new ConcurrentHashMap<>();
      progress.trackAtomic(true);
      ZAuctionPlugin auctionPlugin = readyAuctionPlugin();
      if (auctionPlugin != null) {
         pollAuctionBucket(auctionPlugin, StorageType.STORAGE, output, progress);
         pollAuctionBucket(auctionPlugin, StorageType.EXPIRE, output, progress);
         pollAuctionBucket(auctionPlugin, StorageType.BUY, output, progress);
      }
      return output;
   }

   private void pollAuctionBucket(ZAuctionPlugin plugin, StorageType storageType, Map<String, Long> output,
         ScanProgress progress) {
      for (AuctionItem auctionItem : auctionBucket(plugin, storageType, progress)) {
         if (progress.isCancelled()) {
            return;
         }
         try {
            if (auctionItem == null) {
               continue;
            }
            for (ItemStack itemStack : auctionContents(auctionItem)) {
               if (itemStack != null && itemStack.getType() != Material.AIR) {
                  recordTalliedItem(itemStack, output, progress);
                  accumulateDeepItems(itemStack, output, progress);
               }
            }
         } finally {
            progress.advance();
         }
      }
   }

   private void recordTalliedItem(ItemStack item, Map<String, Long> output, ScanProgress progress) {
      String searchId = CapybaraScan.toSearchItemID(item);
      long amount = item.getAmount();
      Long merged = output.merge(searchId, amount, Long::sum);
      progress.recordMatch(merged == amount ? 1L : 0L, amount);
   }

   private void accumulateDeepItems(ItemStack itemStack, Map<String, Long> output, ScanProgress progress) {
      if (itemStack == null || itemStack.getType() == Material.AIR || !isDeep(itemStack)) {
         return;
      }
      ItemMeta meta = itemStack.getItemMeta();
      if (meta == null) {
         return;
      }
      for (ItemStack item : nestedItems(meta)) {
         if (item != null && item.getType() != Material.AIR) {
            recordTalliedItem(item, output, progress);
            accumulateDeepItems(item, output, progress);
         }
      }
   }
}
