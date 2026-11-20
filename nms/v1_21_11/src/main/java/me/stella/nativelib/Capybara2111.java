package me.stella.nativelib;

import com.drtshock.playervaults.vaultmanagement.CardboardBoxSerialization;
import java.io.DataInputStream;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import me.stella.CapybaraNative;
import me.stella.CapybaraScan;
import me.stella.scan.model.ScanProgress;
import me.stella.utility.MutableOptional;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.inventory.ItemStack;

public class Capybara2111 implements CapybaraNative {

   private static final MutableOptional<HolderLookup.Provider> LOOKUP_PROVIDER = MutableOptional.empty();
   private static final Constructor<?> REGION_STORAGE_CTOR;
   private static final Method GET_REGION_FILE;

   static {
      try {
         REGION_STORAGE_CTOR = RegionFileStorage.class.getDeclaredConstructor(RegionStorageInfo.class, Path.class, boolean.class);
         REGION_STORAGE_CTOR.setAccessible(true);
         GET_REGION_FILE = RegionFileStorage.class.getDeclaredMethod("getRegionFile", ChunkPos.class, boolean.class);
         GET_REGION_FILE.setAccessible(true);
      } catch (Exception e) {
         throw new RuntimeException("Failed to initialize NMS reflection for Capybara2111", e);
      }
   }

   private static HolderLookup.Provider loadProvider() {
      return ((CraftServer) Bukkit.getServer()).getHandle().getServer().registryAccess();
   }

   private HolderLookup.Provider getProvider() {
      return LOOKUP_PROVIDER.provide(Capybara2111::loadProvider);
   }

   public static void cleanup() {
      LOOKUP_PROVIDER.set(null);
   }

   @Override
   public boolean isMatch(ItemStack model, ItemStack entry) {
      if (entry == null || entry.getType() == Material.AIR) {
         return false;
      }
      String modelPluginId = CapybaraScan.toPluginItemID(model);
      if (modelPluginId != null) {
         return Objects.equals(modelPluginId, CapybaraScan.toPluginItemID(entry));
      }
      if (isBasic(model) && model.getType() == entry.getType()) {
         return true;
      }
      if (entry.isSimilar(model)) {
         return true;
      }
      return deepNbtMatch(CraftItemStack.asNMSCopy(model), CraftItemStack.asNMSCopy(entry));
   }

   private boolean deepNbtMatch(net.minecraft.world.item.ItemStack nmsModel, net.minecraft.world.item.ItemStack nmsEntry) {
      HolderLookup.Provider provider = getProvider();
      TagValueOutput nbtReader = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, provider);
      TagValueOutput entryReader = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, provider);
      nbtReader.store(net.minecraft.world.item.ItemStack.MAP_CODEC, nmsModel);
      entryReader.store(net.minecraft.world.item.ItemStack.MAP_CODEC, nmsEntry);

      CompoundTag modelNBT = nbtReader.buildResult().getCompoundOrEmpty("minecraft:custom_data").getCompoundOrEmpty("PublicBukkitValues");
      CompoundTag entryNBT = entryReader.buildResult().getCompoundOrEmpty("minecraft:custom_data").getCompoundOrEmpty("PublicBukkitValues");

      if (modelNBT.contains("excellentcrates:crate_key.id")) {
         return entryNBT.contains("excellentcrates:crate_key.id")
               && Objects.equals(entryNBT.getString("excellentcrates:crate_key.id"),
               modelNBT.getString("excellentcrates:crate_key.id"));
      }
      if (modelNBT.contains("crazyvouchers:crazyvouchers_voucher_item")) {
         return entryNBT.contains("crazyvouchers:crazyvouchers_voucher_item")
               && Objects.equals(entryNBT.getString("crazyvouchers:crazyvouchers_voucher_item"),
               modelNBT.getString("crazyvouchers:crazyvouchers_voucher_item"));
      }
      return false;
   }

   @Override
   public Map<String, Integer> inventory(ItemStack target, ScanProgress progress) {
      return scanPlayerData(target, progress, "Inventory");
   }

   @Override
   public Map<String, Integer> ender(ItemStack target, ScanProgress progress) {
      return scanPlayerData(target, progress, "EnderItems");
   }

   private Map<String, Integer> scanPlayerData(ItemStack target, ScanProgress progress, String listKey) {
      File[] files = CapybaraNative.playerDataFiles();
      if (files.length == 0) {
         return new HashMap<>();
      }
      Map<String, Integer> output = new ConcurrentHashMap<>();
      progress.watch(output).total(files.length);

      Arrays.stream(files).parallel().forEach(file -> {
         try {
            CompoundTag dataCompound = NbtIo.readCompressed(file.getAbsoluteFile().toPath(), NbtAccounter.unlimitedHeap());
            String name = dataCompound.getCompoundOrEmpty("bukkit").getString("lastKnownName").orElse("");
            ValueInput tagInput = TagValueInput.create(ProblemReporter.DISCARDING, getProvider(), dataCompound);
            ValueInput.TypedInputList<ItemStackWithSlot> listTag = tagInput.listOrEmpty(listKey, ItemStackWithSlot.CODEC);
            int count = countMatchesInList(listTag, target);
            if (count > 0) {
               output.put(name, count);
            }
         } catch (Throwable t) {
            t.printStackTrace();
         } finally {
            progress.advance();
         }
      });
      return output;
   }

   private int countMatchesInList(ValueInput.TypedInputList<ItemStackWithSlot> list, ItemStack target) {
      int total = 0;
      for (ItemStackWithSlot item : list) {
         net.minecraft.world.item.ItemStack nms = item.stack();
         if (nms.isEmpty()) {
            continue;
         }
         ItemStack bukkitStack = CraftItemStack.asBukkitCopy(nms);
         if (bukkitStack.getType() == Material.AIR) {
            continue;
         }
         if (isMatch(target, bukkitStack)) {
            total += bukkitStack.getAmount();
         } else {
            total += checkDepth(bukkitStack, target);
         }
      }
      return total;
   }

   @Override
   public Map<String, Integer> world(ItemStack itemStack, String world, ScanProgress progress) {
      ScanSession session = new ScanSession(itemStack);
      Map<String, Integer> output = new ConcurrentHashMap<>();
      runWorldScan(world, progress, output, (file, storage) ->
            processRegionFile(file, storage, chunkNBT -> processChunkNBT(chunkNBT, session, output)));
      return output;
   }

   @Override
   public Map<String, Integer> pollWorld(String worldId, ScanProgress progress) {
      Map<String, Integer> output = new ConcurrentHashMap<>();
      runWorldScan(worldId, progress, output, (file, storage) ->
            processRegionFile(file, storage, chunkNBT -> processChunkNBTForPoll(chunkNBT, output)));
      return output;
   }

   private void runWorldScan(String worldName, ScanProgress progress, Map<String, Integer> output,
         BiConsumer<File, RegionFileStorage> regionProcessor) {
      CraftWorld craftWorld = (CraftWorld) Bukkit.getWorld(worldName);
      if (craftWorld == null) {
         return;
      }
      ServerLevel level = craftWorld.getHandle();
      Path dimensionPath = level.levelStorageAccess.getDimensionPath(level.dimension());
      File root = new File(dimensionPath.toAbsolutePath().toFile(), "region");
      if (!root.isDirectory()) {
         return;
      }
      File[] files = root.listFiles((dir, name) -> name.endsWith(".mca"));
      if (files == null || files.length == 0) {
         return;
      }

      ConcurrentLinkedQueue<File> fileQueue = new ConcurrentLinkedQueue<>(Arrays.asList(files));
      progress.watch(output).total(files.length);
      RegionStorageInfo info = new RegionStorageInfo(level.levelStorageAccess.getLevelId(), level.dimension(), "chunk");
      Path rootPath = root.toPath();
      int workerCount = Math.min(6, Runtime.getRuntime().availableProcessors());
      List<CompletableFuture<Void>> futures = new ArrayList<>(workerCount);

      for (int i = 0; i < workerCount; i++) {
         futures.add(CapybaraScan.runAsync(() -> {
            Object storageInstance = null;
            try {
               storageInstance = REGION_STORAGE_CTOR.newInstance(info, rootPath, false);
               RegionFileStorage storage = (RegionFileStorage) storageInstance;
               File file;
               while ((file = fileQueue.poll()) != null) {
                  try {
                     regionProcessor.accept(file, storage);
                  } finally {
                     progress.advance();
                  }
               }
            } catch (Exception ignored) {
            } finally {
               if (storageInstance instanceof AutoCloseable closeable) {
                  try {
                     closeable.close();
                  } catch (Exception ignored) {
                  }
               }
            }
         }));
      }
      CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
   }

   private void processRegionFile(File file, RegionFileStorage storage, Consumer<CompoundTag> chunkConsumer) {
      try {
         ChunkPos regionCoord = RegionFileStorage.getRegionFileCoordinates(file.toPath());
         if (regionCoord == null) {
            return;
         }
         RegionFile regionFile = (RegionFile) GET_REGION_FILE.invoke(storage, regionCoord, true);
         if (regionFile == null) {
            return;
         }
         for (int x = 0; x < 32; x++) {
            for (int z = 0; z < 32; z++) {
               ChunkPos chunkCoord = new ChunkPos((regionCoord.x << 5) + x, (regionCoord.z << 5) + z);
               if (!regionFile.hasChunk(chunkCoord)) {
                  continue;
               }
               try (DataInputStream stream = regionFile.getChunkDataInputStream(chunkCoord)) {
                  if (stream == null) {
                     continue;
                  }
                  CompoundTag chunkNBT = NbtIo.read(stream);
                  if (!chunkNBT.isEmpty()) {
                     chunkConsumer.accept(chunkNBT);
                  }
               }
            }
         }
      } catch (Throwable ignored) {
      }
   }

   private void processChunkNBT(CompoundTag chunkNBT, ScanSession session, Map<String, Integer> output) {
      forEachChunkContainer(chunkNBT, (compound, isBlock) -> processEntityOrBlock(compound, session, output, isBlock));
   }

   private void processChunkNBTForPoll(CompoundTag chunkNBT, Map<String, Integer> output) {
      forEachChunkContainer(chunkNBT, (compound, isBlock) -> processEntityOrBlockForPoll(compound, output));
   }

   private void forEachChunkContainer(CompoundTag chunkNBT, BiConsumer<CompoundTag, Boolean> consumer) {
      if (chunkNBT.contains("block_entities")) {
         ListTag blockEntities = (ListTag) chunkNBT.get("block_entities");
         if (blockEntities != null) {
            for (Tag nbt : blockEntities) {
               consumer.accept((CompoundTag) nbt, true);
            }
         }
      }
      if (chunkNBT.contains("Entities")) {
         ListTag entities = (ListTag) chunkNBT.get("Entities");
         if (entities != null) {
            for (Tag entity : entities) {
               consumer.accept((CompoundTag) entity, false);
            }
         }
      }
   }

   private void processEntityOrBlock(CompoundTag compound, ScanSession session, Map<String, Integer> output, boolean isBlock) {
      if (!compound.contains("Items")) {
         return;
      }
      int localCount = 0;
      ValueInput input = TagValueInput.create(ProblemReporter.DISCARDING, getProvider(), compound);

      for (ItemStackWithSlot item : input.listOrEmpty("Items", ItemStackWithSlot.CODEC)) {
         net.minecraft.world.item.ItemStack nms = item.stack();
         if (nms.isEmpty()) {
            continue;
         }
         ItemStack bukkitStack = CraftItemStack.asBukkitCopy(nms);
         if (bukkitStack.getType() == Material.AIR) {
            continue;
         }
         if (isMatch(session.model, bukkitStack)) {
            localCount += bukkitStack.getAmount();
         } else {
            localCount += checkDepth(bukkitStack, session.model);
         }
      }

      if (localCount > 0) {
         output.put(formatContainerKey(compound, isBlock), localCount);
      }
   }

   private String formatContainerKey(CompoundTag compound, boolean isBlock) {
      String id = compound.getStringOr("id", "");
      StringBuilder key = new StringBuilder().append(id).append(';');
      if (isBlock) {
         return key.append(compound.getIntOr("x", 0)).append(';')
               .append(compound.getIntOr("y", 0)).append(';')
               .append(compound.getIntOr("z", 0)).toString();
      }
      ListTag pos = (ListTag) compound.get("Pos");
      if (pos != null) {
         return key.append(pos.getDouble(0).orElse(0.0)).append(';')
               .append(pos.getDouble(1).orElse(0.0)).append(';')
               .append(pos.getDouble(2).orElse(0.0)).toString();
      }
      return key.append("0.0;0.0;0.0").toString();
   }

   @Override
   public void pollPlayerData(File file, Map<String, Integer> output) {
      pollPlayerNbtList(file, "Inventory", output);
   }

   @Override
   public void pollPlayerEnderData(File file, Map<String, Integer> output) {
      pollPlayerNbtList(file, "EnderItems", output);
   }

   private void pollPlayerNbtList(File file, String listKey, Map<String, Integer> output) {
      try {
         CompoundTag dataCompound = NbtIo.readCompressed(file.getAbsoluteFile().toPath(), NbtAccounter.unlimitedHeap());
         ValueInput tagInput = TagValueInput.create(ProblemReporter.DISCARDING, getProvider(), dataCompound);
         for (ItemStackWithSlot item : tagInput.listOrEmpty(listKey, ItemStackWithSlot.CODEC)) {
            tallyNmsItem(item.stack(), output);
         }
      } catch (Throwable t) {
         t.printStackTrace();
      }
   }

   @Override
   public void pollVaultFile(File file, Map<String, Integer> output) {
      try {
         String name = Bukkit.getOfflinePlayer(UUID.fromString(file.getName().replace(".yml", ""))).getName();
         FileConfiguration config = YamlConfiguration.loadConfiguration(file);
         for (String key : config.getKeys(false)) {
            ItemStack[] contents = CardboardBoxSerialization.fromStorage(config.getString(key, ""), name);
            if (contents == null) {
               continue;
            }
            for (ItemStack item : contents) {
               if (item != null && item.getType() != Material.AIR) {
                  output.merge(CapybaraScan.toSearchItemID(item), item.getAmount(), Integer::sum);
                  accumulateDeepItems(item, output);
               }
            }
         }
      } catch (Exception err) {
         err.printStackTrace();
      }
   }

   private void processEntityOrBlockForPoll(CompoundTag compound, Map<String, Integer> output) {
      if (!compound.contains("Items")) {
         return;
      }
      ValueInput input = TagValueInput.create(ProblemReporter.DISCARDING, getProvider(), compound);
      for (ItemStackWithSlot item : input.listOrEmpty("Items", ItemStackWithSlot.CODEC)) {
         tallyNmsItem(item.stack(), output);
      }
   }

   private void tallyNmsItem(net.minecraft.world.item.ItemStack nms, Map<String, Integer> output) {
      if (nms.isEmpty()) {
         return;
      }
      ItemStack bukkitStack = CraftItemStack.asBukkitCopy(nms);
      if (bukkitStack.getType() == Material.AIR) {
         return;
      }
      output.merge(CapybaraScan.toSearchItemID(bukkitStack), bukkitStack.getAmount(), Integer::sum);
      accumulateDeepItems(bukkitStack, output);
   }

   private static class ScanSession {

      final ItemStack model;
      final net.minecraft.world.item.ItemStack nmsModel;
      final Item nmsItemType;
      final String modelMMOID;

      ScanSession(ItemStack model) {
         this.model = model;
         this.nmsModel = CraftItemStack.asNMSCopy(model);
         this.nmsItemType = nmsModel.getItem();
         this.modelMMOID = CapybaraScan.toMMOID(model);
      }
   }
}
