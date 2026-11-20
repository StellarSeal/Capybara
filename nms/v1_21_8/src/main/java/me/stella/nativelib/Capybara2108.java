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
import java.util.stream.Stream;
import me.stella.CapybaraNative;
import me.stella.CapybaraScan;
import me.stella.scan.ScanProgress;
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
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.inventory.ItemStack;

public class Capybara2108 implements CapybaraNative {
   private static final MutableOptional<HolderLookup.Provider> lookupProvider = MutableOptional.<HolderLookup.Provider>empty();
   private static final Constructor<?> REGION_STORAGE_CTOR;
   private static final Method GET_REGION_FILE;

   private static HolderLookup.Provider loadProvider() {
      return ((CraftServer)Bukkit.getServer()).getHandle().getServer().registryAccess();
   }

   private HolderLookup.Provider getProvider() {
      return lookupProvider.provide(Capybara2108::loadProvider);
   }

   public static void cleanup() {
      lookupProvider.set(null);
   }

   public boolean isMatch(ItemStack model, ItemStack entry) {
      if (entry != null && entry.getType() != Material.AIR) {
         String modelPluginId = CapybaraScan.toPluginItemID(model);
         if (modelPluginId != null) {
            return Objects.equals(modelPluginId, CapybaraScan.toPluginItemID(entry));
         } else if (this.isBasic(model) && model.getType() == entry.getType()) {
            return true;
         } else if (entry.isSimilar(model)) {
            return true;
         } else {
            net.minecraft.world.item.ItemStack nmsModel = CraftItemStack.asNMSCopy(model);
            net.minecraft.world.item.ItemStack nmsEntry = CraftItemStack.asNMSCopy(entry);
            return this.deepNbtMatch(nmsModel, nmsEntry);
         }
      } else {
         return false;
      }
   }

   private boolean deepNbtMatch(net.minecraft.world.item.ItemStack nmsModel, net.minecraft.world.item.ItemStack nmsEntry) {
      HolderLookup.Provider provider = this.getProvider();
      TagValueOutput nbtReader = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, provider);
      TagValueOutput entryReader = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, provider);
      nbtReader.store(net.minecraft.world.item.ItemStack.MAP_CODEC, nmsModel);
      entryReader.store(net.minecraft.world.item.ItemStack.MAP_CODEC, nmsEntry);
      CompoundTag modelNBT = nbtReader.buildResult().getCompoundOrEmpty("minecraft:custom_data").getCompoundOrEmpty("PublicBukkitValues");
      CompoundTag modelEntry = entryReader.buildResult().getCompoundOrEmpty("minecraft:custom_data").getCompoundOrEmpty("PublicBukkitValues");
      if (modelNBT.contains("excellentcrates:crate_key.id")) {
         return modelEntry.contains("excellentcrates:crate_key.id") && Objects.equals(modelEntry.getString("excellentcrates:crate_key.id"), modelNBT.getString("excellentcrates:crate_key.id"));
      } else if (!modelNBT.contains("crazyvouchers:crazyvouchers_voucher_item")) {
         return false;
      } else {
         return modelEntry.contains("crazyvouchers:crazyvouchers_voucher_item") && Objects.equals(modelEntry.getString("crazyvouchers:crazyvouchers_voucher_item"), modelNBT.getString("crazyvouchers:crazyvouchers_voucher_item"));
      }
   }

   public Map<String, Integer> inventory(ItemStack target, ScanProgress progress) {
      File dir = Bukkit.getServer().getWorldContainer();
      List var10003 = Bukkit.getWorlds();
      File main = new File(dir, ((World)var10003.get(0)).getName() + "/playerdata");
      if (!main.exists()) {
         return new HashMap<>();
      } else {
         File[] files = main.listFiles();
         if (files == null) {
            return new HashMap<>();
         } else {
            Map<String, Integer> output = new ConcurrentHashMap<>();
            progress.watch(output).total(Arrays.stream(files).filter((f) -> f.isFile() && f.getName().endsWith(".dat")).count());
            Arrays.stream(files).parallel().filter((f) -> f.isFile() && f.getName().endsWith(".dat")).forEach((file) -> {
               try {
                  CompoundTag dataCompound = NbtIo.readCompressed(file.getAbsoluteFile().toPath(), NbtAccounter.unlimitedHeap());
                  String name = (String)dataCompound.getCompoundOrEmpty("bukkit").getString("lastKnownName").orElse("");
                  ValueInput tagInput = TagValueInput.create(ProblemReporter.DISCARDING, this.getProvider(), dataCompound);
                  ValueInput.TypedInputList<ItemStackWithSlot> listTag = tagInput.listOrEmpty("Inventory", ItemStackWithSlot.CODEC);
                  int count = this.countMatchesInList(listTag, target);
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
      }
   }

   public Map<String, Integer> ender(ItemStack target, ScanProgress progress) {
      File dir = Bukkit.getServer().getWorldContainer();
      List var10003 = Bukkit.getWorlds();
      File main = new File(dir, ((World)var10003.get(0)).getName() + "/playerdata");
      if (!main.exists()) {
         return new HashMap<>();
      } else {
         File[] files = main.listFiles();
         if (files == null) {
            return new HashMap<>();
         } else {
            Map<String, Integer> output = new ConcurrentHashMap<>();
            progress.watch(output).total(Arrays.stream(files).filter((f) -> f.isFile() && f.getName().endsWith(".dat")).count());
            Arrays.stream(files).parallel().filter((f) -> f.isFile() && f.getName().endsWith(".dat")).forEach((file) -> {
               try {
                  CompoundTag dataCompound = NbtIo.readCompressed(file.getAbsoluteFile().toPath(), NbtAccounter.unlimitedHeap());
                  String name = (String)((CompoundTag)dataCompound.getCompound("bukkit").orElse(new CompoundTag())).getString("lastKnownName").orElse("");
                  ValueInput tagInput = TagValueInput.create(ProblemReporter.DISCARDING, this.getProvider(), dataCompound);
                  ValueInput.TypedInputList<ItemStackWithSlot> listTag = tagInput.listOrEmpty("EnderItems", ItemStackWithSlot.CODEC);
                  int count = this.countMatchesInList(listTag, target);
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
      }
   }

   private int countMatchesInList(ValueInput.TypedInputList<ItemStackWithSlot> list, ItemStack target) {
      int total = 0;

      for(ItemStackWithSlot item : list) {
         net.minecraft.world.item.ItemStack nms = item.stack();
         if (!nms.isEmpty()) {
            ItemStack bukkitStack = CraftItemStack.asBukkitCopy(nms);
            if (bukkitStack.getType() != Material.AIR) {
               if (this.isMatch(target, bukkitStack)) {
                  total += bukkitStack.getAmount();
               } else {
                  int depth = this.checkDepth(bukkitStack, target);
                  if (depth > 0) {
                     total += depth;
                  }
               }
            }
         }
      }

      return total;
   }

   public Map<String, Integer> world(ItemStack itemStack, String world, ScanProgress progress) {
      File dir = Bukkit.getServer().getWorldContainer();
      CraftWorld craftWorld = (CraftWorld)Bukkit.getWorld(world);
      if (craftWorld == null) {
         return new ConcurrentHashMap<>();
      } else {
         new File(dir, craftWorld.getName());
         ServerLevel level = craftWorld.getHandle();
         Path dimensionPath = level.levelStorageAccess.getDimensionPath(level.dimension());
         File root = new File(dimensionPath.toAbsolutePath().toFile(), "region");
         if (root.exists() && root.isDirectory()) {
            File[] files = root.listFiles((d, n) -> n.endsWith(".mca"));
            if (files != null && files.length != 0) {
               ScanSession session = new ScanSession(itemStack);
               Map<String, Integer> output = new ConcurrentHashMap<>();
               ConcurrentLinkedQueue<File> fileQueue = new ConcurrentLinkedQueue<>(Arrays.asList(files));
               progress.watch(output).total(files.length);
               RegionStorageInfo info = new RegionStorageInfo(level.levelStorageAccess.getLevelId(), level.dimension(), "chunk");
               Path rootPath = root.toPath();
               int workerCount = Math.min(6, Runtime.getRuntime().availableProcessors());
               List<CompletableFuture<Void>> futures = new ArrayList<>(workerCount);

               for(int i = 0; i < workerCount; ++i) {
                  futures.add(CapybaraScan.runAsync(() -> {
                     Object storageInstance = null;

                     try {
                        storageInstance = REGION_STORAGE_CTOR.newInstance(info, rootPath, false);
                        RegionFileStorage storage = (RegionFileStorage)storageInstance;

                        File file;
                        while((file = (File)fileQueue.poll()) != null) {
                           this.processRegionFile(file, storage, session, output);
                           progress.advance();
                        }
                     } catch (Exception var17) {
                     } finally {
                        if (storageInstance instanceof AutoCloseable) {
                           try {
                              ((AutoCloseable)storageInstance).close();
                           } catch (Exception var16) {
                           }
                        }

                     }

                  }));
               }

               CompletableFuture.allOf((CompletableFuture[])futures.toArray(new CompletableFuture[0])).join();
               return output;
            } else {
               return new ConcurrentHashMap<>();
            }
         } else {
            return new ConcurrentHashMap<>();
         }
      }
   }

   private void processRegionFile(File file, RegionFileStorage storage, ScanSession session, Map<String, Integer> output) {
      try {
         ChunkPos regionCoord = RegionFileStorage.getRegionFileCoordinates(file.toPath());
         if (regionCoord == null) {
            return;
         }

         RegionFile regionFile = (RegionFile)GET_REGION_FILE.invoke(storage, regionCoord, true);

         for(int x = 0; x < 32; ++x) {
            for(int z = 0; z < 32; ++z) {
               ChunkPos chunkCoord = new ChunkPos((regionCoord.x << 5) + x, (regionCoord.z << 5) + z);
               if (regionFile.hasChunk(chunkCoord)) {
                  DataInputStream stream = regionFile.getChunkDataInputStream(chunkCoord);

                  label64: {
                     label63: {
                        try {
                           if (stream != null) {
                              CompoundTag chunkNBT = NbtIo.read(stream);
                              if (chunkNBT.isEmpty()) {
                                 break label63;
                              }

                              this.processChunkNBT(chunkNBT, session, output);
                              break label64;
                           }
                        } catch (Throwable var14) {
                           if (stream != null) {
                              try {
                                 stream.close();
                              } catch (Throwable var13) {
                                 var14.addSuppressed(var13);
                              }
                           }

                           throw var14;
                        }

                        if (stream != null) {
                           stream.close();
                        }
                        continue;
                     }

                     if (stream != null) {
                        stream.close();
                     }
                     continue;
                  }

                  if (stream != null) {
                     stream.close();
                  }
               }
            }
         }
      } catch (Throwable var15) {
      }

   }

   private void processChunkNBT(CompoundTag chunkNBT, ScanSession session, Map<String, Integer> output) {
      if (chunkNBT.contains("block_entities")) {
         ListTag blockEntities = (ListTag)chunkNBT.get("block_entities");
         if (blockEntities != null) {
            for(Tag nbt : blockEntities) {
               this.processEntityOrBlock((CompoundTag)nbt, session, output, true);
            }
         }
      }

      if (chunkNBT.contains("Entities")) {
         ListTag entities = (ListTag)chunkNBT.get("Entities");
         if (entities != null) {
            for(Tag entity : entities) {
               this.processEntityOrBlock((CompoundTag)entity, session, output, false);
            }
         }
      }

   }

   private void processEntityOrBlock(CompoundTag compound, ScanSession session, Map<String, Integer> output, boolean isBlock) {
      if (compound.contains("Items")) {
         int localCount = 0;
         ValueInput input = TagValueInput.create(ProblemReporter.DISCARDING, this.getProvider(), compound);

         for(ItemStackWithSlot item : input.listOrEmpty("Items", ItemStackWithSlot.CODEC)) {
            net.minecraft.world.item.ItemStack nms = item.stack();
            if (!nms.isEmpty()) {
               ItemStack bukkitStack = CraftItemStack.asBukkitCopy(nms);
               if (bukkitStack.getType() != Material.AIR) {
                  if (this.isMatch(session.model, bukkitStack)) {
                     localCount += bukkitStack.getAmount();
                  } else {
                     int depth = this.checkDepth(bukkitStack, session.model);
                     if (depth > 0) {
                        localCount += depth;
                     }
                  }
               }
            }
         }

         if (localCount > 0) {
            String id = compound.getStringOr("id", "");
            StringBuilder key = new StringBuilder();
            key.append(id).append(';');
            if (isBlock) {
               key.append(compound.getIntOr("x", 0)).append(';').append(compound.getIntOr("y", 0)).append(';').append(compound.getIntOr("z", 0));
            } else {
               ListTag pos = (ListTag)compound.get("Pos");
               if (pos != null) {
                  key.append(pos.getDouble(0).orElse(0.0)).append(';').append(pos.getDouble(1).orElse(0.0)).append(';').append(pos.getDouble(2).orElse(0.0));
               } else {
                  key.append("0.0;0.0;0.0");
               }
            }

            output.put(key.toString(), localCount);
         }

      }
   }

   public void pollPlayerData(File file, Map<String, Integer> output) {
      try {
         CompoundTag dataCompound = NbtIo.readCompressed(file.getAbsoluteFile().toPath(), NbtAccounter.unlimitedHeap());
         ValueInput tagInput = TagValueInput.create(ProblemReporter.DISCARDING, this.getProvider(), dataCompound);

         for(ItemStackWithSlot item : tagInput.listOrEmpty("Inventory", ItemStackWithSlot.CODEC)) {
            net.minecraft.world.item.ItemStack nms = item.stack();
            if (!nms.isEmpty()) {
               ItemStack bukkitStack = CraftItemStack.asBukkitCopy(nms);
               if (bukkitStack.getType() != Material.AIR) {
                  String searchId = CapybaraScan.toSearchItemID(bukkitStack);
                  output.merge(searchId, bukkitStack.getAmount(), Integer::sum);
                  this.accumulateDeepItems(bukkitStack, output);
               }
            }
         }
      } catch (Throwable t) {
         t.printStackTrace();
      }

   }

   public void pollPlayerEnderData(File file, Map<String, Integer> output) {
      try {
         CompoundTag dataCompound = NbtIo.readCompressed(file.getAbsoluteFile().toPath(), NbtAccounter.unlimitedHeap());
         ValueInput tagInput = TagValueInput.create(ProblemReporter.DISCARDING, this.getProvider(), dataCompound);

         for(ItemStackWithSlot item : tagInput.listOrEmpty("EnderItems", ItemStackWithSlot.CODEC)) {
            net.minecraft.world.item.ItemStack nms = item.stack();
            if (!nms.isEmpty()) {
               ItemStack bukkitStack = CraftItemStack.asBukkitCopy(nms);
               if (bukkitStack.getType() != Material.AIR) {
                  String searchId = CapybaraScan.toSearchItemID(bukkitStack);
                  output.merge(searchId, bukkitStack.getAmount(), Integer::sum);
                  this.accumulateDeepItems(bukkitStack, output);
               }
            }
         }
      } catch (Throwable t) {
         t.printStackTrace();
      }

   }

   public void pollVaultFile(File file, Map<String, Integer> output) {
      try {
         String name = Bukkit.getOfflinePlayer(UUID.fromString(file.getName().replace(".yml", ""))).getName();
         FileConfiguration config = YamlConfiguration.loadConfiguration(file);
         config.getKeys(false).forEach((key) -> {
            ItemStack[] contents = CardboardBoxSerialization.fromStorage(config.getString(key, ""), name);
            if (contents != null && contents.length >= 1) {
               for(ItemStack item : contents) {
                  if (item != null && item.getType() != Material.AIR) {
                     String searchId = CapybaraScan.toSearchItemID(item);
                     output.merge(searchId, item.getAmount(), Integer::sum);
                     this.accumulateDeepItems(item, output);
                  }
               }

            }
         });
      } catch (Exception err) {
         err.printStackTrace();
      }

   }

   public Map<String, Integer> pollWorld(String worldId, ScanProgress progress) {
      Map<String, Integer> output = new ConcurrentHashMap<>();
      File dir = Bukkit.getServer().getWorldContainer();
      CraftWorld craftWorld = (CraftWorld)Bukkit.getWorld(worldId);
      if (craftWorld == null) {
         return output;
      } else {
         ServerLevel level = craftWorld.getHandle();
         Path dimensionPath = level.levelStorageAccess.getDimensionPath(level.dimension());
         File root = new File(dimensionPath.toAbsolutePath().toFile(), "region");
         if (root.exists() && root.isDirectory()) {
            File[] files = root.listFiles((d, n) -> n.endsWith(".mca"));
            if (files != null && files.length != 0) {
               ConcurrentLinkedQueue<File> fileQueue = new ConcurrentLinkedQueue<>(Arrays.asList(files));
               progress.watch(output).total(files.length);
               RegionStorageInfo info = new RegionStorageInfo(level.levelStorageAccess.getLevelId(), level.dimension(), "chunk");
               Path rootPath = root.toPath();
               int workerCount = Math.min(6, Runtime.getRuntime().availableProcessors());
               List<CompletableFuture<Void>> futures = new ArrayList<>(workerCount);

               for(int i = 0; i < workerCount; ++i) {
                  futures.add(CapybaraScan.runAsync(() -> {
                     Object storageInstance = null;

                     try {
                        storageInstance = REGION_STORAGE_CTOR.newInstance(info, rootPath, false);
                        RegionFileStorage storage = (RegionFileStorage)storageInstance;

                        File file;
                        while((file = (File)fileQueue.poll()) != null) {
                           this.processRegionFileForPoll(file, storage, output);
                           progress.advance();
                        }
                     } catch (Exception var16) {
                     } finally {
                        if (storageInstance instanceof AutoCloseable) {
                           try {
                              ((AutoCloseable)storageInstance).close();
                           } catch (Exception var15) {
                           }
                        }

                     }

                  }));
               }

               CompletableFuture.allOf((CompletableFuture[])futures.toArray(new CompletableFuture[0])).join();
               return output;
            } else {
               return output;
            }
         } else {
            return output;
         }
      }
   }

   private void processRegionFileForPoll(File file, RegionFileStorage storage, Map<String, Integer> output) {
      try {
         ChunkPos regionCoord = RegionFileStorage.getRegionFileCoordinates(file.toPath());
         if (regionCoord == null) {
            return;
         }

         RegionFile regionFile = (RegionFile)GET_REGION_FILE.invoke(storage, regionCoord, true);

         for(int x = 0; x < 32; ++x) {
            for(int z = 0; z < 32; ++z) {
               ChunkPos chunkCoord = new ChunkPos((regionCoord.x << 5) + x, (regionCoord.z << 5) + z);
               if (regionFile.hasChunk(chunkCoord)) {
                  DataInputStream stream = regionFile.getChunkDataInputStream(chunkCoord);

                  label64: {
                     label63: {
                        try {
                           if (stream != null) {
                              CompoundTag chunkNBT = NbtIo.read(stream);
                              if (chunkNBT.isEmpty()) {
                                 break label63;
                              }

                              this.processChunkNBTForPoll(chunkNBT, output);
                              break label64;
                           }
                        } catch (Throwable var13) {
                           if (stream != null) {
                              try {
                                 stream.close();
                              } catch (Throwable var12) {
                                 var13.addSuppressed(var12);
                              }
                           }

                           throw var13;
                        }

                        if (stream != null) {
                           stream.close();
                        }
                        continue;
                     }

                     if (stream != null) {
                        stream.close();
                     }
                     continue;
                  }

                  if (stream != null) {
                     stream.close();
                  }
               }
            }
         }
      } catch (Throwable var14) {
      }

   }

   private void processChunkNBTForPoll(CompoundTag chunkNBT, Map<String, Integer> output) {
      if (chunkNBT.contains("block_entities")) {
         ListTag blockEntities = (ListTag)chunkNBT.get("block_entities");
         if (blockEntities != null) {
            for(Tag nbt : blockEntities) {
               this.processEntityOrBlockForPoll((CompoundTag)nbt, output);
            }
         }
      }

      if (chunkNBT.contains("Entities")) {
         ListTag entities = (ListTag)chunkNBT.get("Entities");
         if (entities != null) {
            for(Tag entity : entities) {
               this.processEntityOrBlockForPoll((CompoundTag)entity, output);
            }
         }
      }

   }

   private void processEntityOrBlockForPoll(CompoundTag compound, Map<String, Integer> output) {
      if (compound.contains("Items")) {
         ValueInput input = TagValueInput.create(ProblemReporter.DISCARDING, this.getProvider(), compound);

         for(ItemStackWithSlot item : input.listOrEmpty("Items", ItemStackWithSlot.CODEC)) {
            net.minecraft.world.item.ItemStack nms = item.stack();
            if (!nms.isEmpty()) {
               ItemStack bukkitStack = CraftItemStack.asBukkitCopy(nms);
               if (bukkitStack.getType() != Material.AIR) {
                  String searchId = CapybaraScan.toSearchItemID(bukkitStack);
                  output.merge(searchId, bukkitStack.getAmount(), Integer::sum);
                  this.accumulateDeepItems(bukkitStack, output);
               }
            }
         }

      }
   }

   static {
      try {
         REGION_STORAGE_CTOR = RegionFileStorage.class.getDeclaredConstructor(RegionStorageInfo.class, Path.class, Boolean.TYPE);
         REGION_STORAGE_CTOR.setAccessible(true);
         GET_REGION_FILE = RegionFileStorage.class.getDeclaredMethod("getRegionFile", ChunkPos.class, Boolean.TYPE);
         GET_REGION_FILE.setAccessible(true);
      } catch (Exception e) {
         throw new RuntimeException("Failed to initialize NMS reflection for Capybara2108", e);
      }
   }

   private class ScanSession {
      final ItemStack model;
      final net.minecraft.world.item.ItemStack nmsModel;
      final Item nmsItemType;
      final String modelMMOID;

      ScanSession(ItemStack model) {
         this.model = model;
         this.nmsModel = CraftItemStack.asNMSCopy(model);
         this.nmsItemType = this.nmsModel.getItem();
         this.modelMMOID = CapybaraScan.toMMOID(model);
      }
   }
}
