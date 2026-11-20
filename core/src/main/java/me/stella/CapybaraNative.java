package me.stella;

import com.drtshock.playervaults.PlayerVaults;
import com.drtshock.playervaults.vaultmanagement.CardboardBoxSerialization;
import com.earth2me.essentials.Essentials;
import fr.maxlego08.zauctionhouse.ZAuctionPlugin;
import fr.maxlego08.zauctionhouse.api.AuctionItem;
import fr.maxlego08.zauctionhouse.api.enums.StorageType;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import me.stella.scan.ScanProgress;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
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
 * Version-specific scanner contract, implemented once per NMS module ({@code me.stella.nativelib.Capybara<key>}).
 *
 * <p>Every scan reports into a {@link ScanProgress}: it declares a total of work units, advances once per unit and
 * exposes its live output so the {@link me.stella.scan.ScanMonitor} can render it.</p>
 */
public interface CapybaraNative {

   boolean isMatch(ItemStack model, ItemStack entry);

   /** Player inventories; unit = player data file. */
   Map<String, Integer> inventory(ItemStack target, ScanProgress progress);

   /** Player ender chests; unit = player data file. */
   Map<String, Integer> ender(ItemStack target, ScanProgress progress);

   /** Containers and entities of a world; unit = region file. */
   Map<String, Integer> world(ItemStack target, String world, ScanProgress progress);

   /** Tally of every item in a world; unit = region file. */
   Map<String, Integer> pollWorld(String world, ScanProgress progress);

   void pollPlayerData(File file, Map<String, Integer> output);

   void pollPlayerEnderData(File file, Map<String, Integer> output);

   void pollVaultFile(File file, Map<String, Integer> output);

   /** {@code <main world>/playerdata/*.dat}; empty if the folder is missing. */
   static File[] playerDataFiles() {
      File folder = new File(Bukkit.getServer().getWorldContainer(), Bukkit.getWorlds().get(0).getName() + "/playerdata");
      File[] files = folder.listFiles((dir, name) -> name.endsWith(".dat"));
      return files == null ? new File[0] : Arrays.stream(files).filter(File::isFile).toArray(File[]::new);
   }

   /** Closest Essentials home / last / logout location within {@code radii}; unit = userdata file. */
   default Map<String, Double> scanHouse(Location point, double radii, ScanProgress progress) {
      Map<String, Double> output = new ConcurrentHashMap<>();
      progress.watch(output::size, null);
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
            err.printStackTrace();
         } finally {
            progress.advance();
         }
      }
      if (closestKey != null) {
         output.put(closestKey, closestDistance);
      }
      return output;
   }

   /** Distance from {@code point} to a saved Essentials location, or {@code +Infinity} if absent / other world. */
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

   /** Bundles and shulker boxes can hold further items. */
   default boolean isDeep(ItemStack itemStack) {
      ItemMeta meta = itemStack.getItemMeta();
      return meta instanceof BundleMeta
              || meta instanceof BlockStateMeta blockStateMeta && blockStateMeta.getBlockState() instanceof ShulkerBox;
   }

   default boolean isBasic(ItemStack itemStack) {
      return !Objects.requireNonNull(itemStack.getItemMeta()).hasLore();
   }

   /** Number of {@code target} items nested (recursively) inside {@code itemStack}. */
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

   /** PlayerVaults vaults; unit = vault file. Result: player -> ("player;vault" -> count). */
   default Map<String, Map<String, Integer>> vault(ItemStack target, ScanProgress progress) {
      File vaultData = JavaPlugin.getPlugin(PlayerVaults.class).getVaultData();
      List<File> dataStream = Arrays.stream(Objects.requireNonNull(vaultData.listFiles()))
              .filter(f -> f.isFile() && f.getName().endsWith(".yml")).toList();
      Map<String, Map<String, Integer>> output = new ConcurrentHashMap<>();
      progress.total(dataStream.size()).watch(
              () -> output.values().stream().filter(pool -> !pool.isEmpty()).count(),
              () -> output.values().stream().flatMap(pool -> pool.values().stream()).mapToLong(Integer::longValue).sum());
      dataStream.forEach(file -> {
         try {
            String name = Bukkit.getOfflinePlayer(UUID.fromString(file.getName().replace(".yml", ""))).getName();
            FileConfiguration config = YamlConfiguration.loadConfiguration(file);
            AtomicInteger totalAccount = new AtomicInteger(0);
            Map<String, Integer> pool = output.computeIfAbsent(name, n -> new ConcurrentHashMap<>());
            config.getKeys(false).forEach(key -> {
               ItemStack[] contents = CardboardBoxSerialization.fromStorage(config.getString(key, ""), name);
               if (contents != null) {
                  for (ItemStack item : contents) {
                     if (item.getType() != Material.AIR && isMatch(target, item)) {
                        pool.merge(name + ";" + key, item.getAmount(), Integer::sum);
                        totalAccount.getAndAdd(item.getAmount());
                     }
                  }
               }
            });
            if (totalAccount.get() > 0) {
               output.put(name, pool);
            }
         } catch (Exception err) {
            err.printStackTrace();
         } finally {
            progress.advance();
         }
      });
      return output;
   }

   /** zAuctionHouse listings, expired and purchased items; unit = auction entry. */
   default Map<String, Integer> auctionHouse(ItemStack target, ScanProgress progress) {
      Map<String, Integer> output = new ConcurrentHashMap<>();
      progress.watch(output);
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
               output.merge(owner + ";" + state, count, Integer::sum);
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

   /** Tally of every inventory item; unit = player data file. */
   default Map<String, Integer> pollInventory(ScanProgress progress) {
      return pollPlayerFiles(progress, this::pollPlayerData);
   }

   /** Tally of every ender chest item; unit = player data file. */
   default Map<String, Integer> pollEnderChest(ScanProgress progress) {
      return pollPlayerFiles(progress, this::pollPlayerEnderData);
   }

   private Map<String, Integer> pollPlayerFiles(ScanProgress progress, FilePoller poller) {
      Map<String, Integer> output = new ConcurrentHashMap<>();
      File[] files = playerDataFiles();
      progress.total(files.length).watch(output);
      for (File file : files) {
         try {
            poller.poll(file, output);
         } catch (Exception err) {
            err.printStackTrace();
         } finally {
            progress.advance();
         }
      }
      return output;
   }

   /** Tally of every vault item; unit = vault file. */
   default Map<String, Integer> pollVault(ScanProgress progress) {
      Map<String, Integer> output = new ConcurrentHashMap<>();
      progress.watch(output);
      if (!CapybaraScan.supportPlayerVaults()) {
         return output;
      }
      File vaultData = JavaPlugin.getPlugin(PlayerVaults.class).getVaultData();
      List<File> dataStream = Arrays.stream(Objects.requireNonNull(vaultData.listFiles()))
              .filter(f -> f.isFile() && f.getName().endsWith(".yml")).toList();
      progress.total(dataStream.size());
      dataStream.forEach(file -> {
         try {
            pollVaultFile(file, output);
         } catch (Exception err) {
            err.printStackTrace();
         } finally {
            progress.advance();
         }
      });
      return output;
   }

   /** Tally of every auction item; unit = auction entry. */
   default Map<String, Integer> pollAuctionHouse(ScanProgress progress) {
      Map<String, Integer> output = new ConcurrentHashMap<>();
      progress.watch(output);
      ZAuctionPlugin auctionPlugin = readyAuctionPlugin();
      if (auctionPlugin != null) {
         pollAuctionBucket(auctionPlugin, StorageType.STORAGE, output, progress);
         pollAuctionBucket(auctionPlugin, StorageType.EXPIRE, output, progress);
         pollAuctionBucket(auctionPlugin, StorageType.BUY, output, progress);
      }
      return output;
   }

   default void pollAuctionBucket(ZAuctionPlugin plugin, StorageType storageType, Map<String, Integer> output,
                                  ScanProgress progress) {
      for (AuctionItem auctionItem : auctionBucket(plugin, storageType, progress)) {
         try {
            if (auctionItem == null) {
               continue;
            }
            for (ItemStack itemStack : auctionContents(auctionItem)) {
               if (itemStack != null && itemStack.getType() != Material.AIR) {
                  output.merge(CapybaraScan.toSearchItemID(itemStack), itemStack.getAmount(), Integer::sum);
                  accumulateDeepItems(itemStack, output);
               }
            }
         } finally {
            progress.advance();
         }
      }
   }

   /** Adds every item nested (recursively) inside {@code itemStack} to the tally. */
   default void accumulateDeepItems(ItemStack itemStack, Map<String, Integer> output) {
      if (itemStack == null || itemStack.getType() == Material.AIR || !isDeep(itemStack)) {
         return;
      }
      ItemMeta meta = itemStack.getItemMeta();
      if (meta == null) {
         return;
      }
      for (ItemStack item : nestedItems(meta)) {
         if (item != null && item.getType() != Material.AIR) {
            output.merge(CapybaraScan.toSearchItemID(item), item.getAmount(), Integer::sum);
            accumulateDeepItems(item, output);
         }
      }
   }

   @FunctionalInterface
   interface FilePoller {
      void poll(File file, Map<String, Integer> output);
   }
}
