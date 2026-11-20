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
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.inventory.ItemStack;
import org.stupidcraft.linearpaper.region.IRegionFile;

public class Capybara2104 implements CapybaraNative {
   private static final MutableOptional<HolderLookup.Provider> lookupProvider = MutableOptional.<HolderLookup.Provider>empty();
   private static final Constructor<?> REGION_STORAGE_CTOR;
   private static final Method GET_REGION_FILE;

   private static HolderLookup.Provider loadProvider() {
      return ((CraftServer)Bukkit.getServer()).getHandle().getServer().registryAccess();
   }

   private HolderLookup.Provider getProvider() {
      return lookupProvider.provide(Capybara2104::loadProvider);
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
            HolderLookup.Provider provider = this.getProvider();
            CompoundTag modelNBT = ((CompoundTag)nmsModel.saveOptional(provider)).getCompound("minecraft:custom_data").getCompound("PublicBukkitValues");
            CompoundTag modelEntry = ((CompoundTag)nmsEntry.saveOptional(provider)).getCompound("minecraft:custom_data").getCompound("PublicBukkitValues");
            if (modelNBT.contains("excellentcrates:crate_key.id")) {
               return modelEntry.contains("excellentcrates:crate_key.id") && Objects.equals(modelEntry.getString("excellentcrates:crate_key.id"), modelNBT.getString("excellentcrates:crate_key.id"));
            } else if (!modelNBT.contains("crazyvouchers:crazyvouchers_voucher_item")) {
               return false;
            } else {
               return modelEntry.contains("crazyvouchers:crazyvouchers_voucher_item") && Objects.equals(modelEntry.getString("crazyvouchers:crazyvouchers_voucher_item"), modelNBT.getString("crazyvouchers:crazyvouchers_voucher_item"));
            }
         }
      } else {
         return false;
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
            HolderLookup.Provider provider = this.getProvider();
            progress.watch(output).total(Arrays.stream(files).filter((f) -> f.isFile() && f.getName().endsWith(".dat")).count());
            Arrays.stream(files).parallel().filter((f) -> f.isFile() && f.getName().endsWith(".dat")).forEach((file) -> {
               try {
                  CompoundTag dataCompound = NbtIo.readCompressed(file.getAbsoluteFile().toPath(), NbtAccounter.unlimitedHeap());
                  String name = dataCompound.getCompound("bukkit").getString("lastKnownName");
                  ListTag listTag = (ListTag)dataCompound.get("Inventory");
                  int count = this.countMatchesInList(listTag, target, provider);
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
            HolderLookup.Provider provider = this.getProvider();
            progress.watch(output).total(Arrays.stream(files).filter((f) -> f.isFile() && f.getName().endsWith(".dat")).count());
            Arrays.stream(files).parallel().filter((f) -> f.isFile() && f.getName().endsWith(".dat")).forEach((file) -> {
               try {
                  CompoundTag dataCompound = NbtIo.readCompressed(file.getAbsoluteFile().toPath(), NbtAccounter.unlimitedHeap());
                  String name = dataCompound.getCompound("bukkit").getString("lastKnownName");
                  ListTag listTag = (ListTag)dataCompound.get("EnderItems");
                  int count = this.countMatchesInList(listTag, target, provider);
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

   private int countMatchesInList(ListTag listTag, ItemStack target, HolderLookup.Provider provider) {
      if (listTag == null) {
         return 0;
      } else {
         int total = 0;

         for(Tag tag : listTag) {
            CompoundTag itemNBT = (CompoundTag)tag;
            net.minecraft.world.item.ItemStack nms = net.minecraft.world.item.ItemStack.parseOptional(provider, itemNBT);
            if (!nms.isEmpty()) {
               ItemStack bukkitItemStack = CraftItemStack.asBukkitCopy(nms);
               if (bukkitItemStack.getType() != Material.AIR) {
                  if (this.isMatch(target, bukkitItemStack)) {
                     total += bukkitItemStack.getAmount();
                  } else {
                     int depth = this.checkDepth(bukkitItemStack, target);
                     if (depth > 0) {
                        total += depth;
                     }
                  }
               }
            }
         }

         return total;
      }
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
                           try {
                              ChunkPos regionCoord = RegionFileStorage.getRegionFileCoordinates(file.toPath());
                              IRegionFile regionFile = (IRegionFile)GET_REGION_FILE.invoke(storage, regionCoord, true);

                              for(int x = 0; x < 32; ++x) {
                                 for(int z = 0; z < 32; ++z) {
                                    ChunkPos chunkCoord = new ChunkPos((regionCoord.x << 5) + x, (regionCoord.z << 5) + z);
                                    if (regionFile.hasChunk(chunkCoord)) {
                                       DataInputStream stream = regionFile.getChunkDataInputStream(chunkCoord);

                                       label191: {
                                          label190: {
                                             try {
                                                if (stream != null) {
                                                   CompoundTag chunkNBT = NbtIo.read(stream);
                                                   if (chunkNBT == null) {
                                                      break label190;
                                                   }

                                                   this.processChunkNBT(chunkNBT, session, output);
                                                   break label191;
                                                }
                                             } catch (Throwable var29) {
                                                if (stream != null) {
                                                   try {
                                                      stream.close();
                                                   } catch (Throwable var28) {
                                                      var29.addSuppressed(var28);
                                                   }
                                                }

                                                throw var29;
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
                           } catch (Throwable var30) {
                           } finally {
                              progress.advance();
                           }
                        }
                     } catch (Exception e) {
                        e.printStackTrace();
                     } finally {
                        if (storageInstance instanceof AutoCloseable) {
                           try {
                              ((AutoCloseable)storageInstance).close();
                           } catch (Exception var27) {
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
         ListTag list = (ListTag)compound.get("Items");
         if (list != null) {
            for(Tag nbtBase : list) {
               CompoundTag itemNBT = (CompoundTag)nbtBase;
               net.minecraft.world.item.ItemStack nms = net.minecraft.world.item.ItemStack.parseOptional(this.getProvider(), itemNBT);
               if (!nms.isEmpty() && nms.getItem() == session.nmsItemType) {
                  ItemStack bukkitItemStack = CraftItemStack.asBukkitCopy(nms);
                  if (this.isMatch(session.model, bukkitItemStack)) {
                     localCount += bukkitItemStack.getAmount();
                  } else {
                     int depth = this.checkDepth(bukkitItemStack, session.model);
                     if (depth > 0) {
                        localCount += depth;
                     }
                  }
               }
            }

            if (localCount > 0) {
               String id = compound.getString("id");
               StringBuilder key = new StringBuilder();
               key.append(id).append(';');
               if (isBlock) {
                  key.append(compound.getInt("x")).append(';').append(compound.getInt("y")).append(';').append(compound.getInt("z"));
               } else {
                  ListTag pos = (ListTag)compound.get("Pos");
                  if (pos != null) {
                     key.append(pos.getDouble(0)).append(';').append(pos.getDouble(1)).append(';').append(pos.getDouble(2));
                  } else {
                     key.append("0.0;0.0;0.0");
                  }
               }

               output.put(key.toString(), localCount);
            }

         }
      }
   }

   public void pollPlayerData(File file, Map<String, Integer> output) {
      try {
         CompoundTag dataCompound = NbtIo.readCompressed(file.getAbsoluteFile().toPath(), NbtAccounter.unlimitedHeap());
         ListTag listTag = (ListTag)dataCompound.get("Inventory");
         if (listTag == null) {
            return;
         }

         for(Tag tag : listTag) {
            CompoundTag itemNBT = (CompoundTag)tag;
            net.minecraft.world.item.ItemStack nms = net.minecraft.world.item.ItemStack.parseOptional(this.getProvider(), itemNBT);
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
         ListTag listTag = (ListTag)dataCompound.get("EnderItems");
         if (listTag == null) {
            return;
         }

         for(Tag tag : listTag) {
            CompoundTag itemNBT = (CompoundTag)tag;
            net.minecraft.world.item.ItemStack nms = net.minecraft.world.item.ItemStack.parseOptional(this.getProvider(), itemNBT);
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
                           try {
                              ChunkPos regionCoord = RegionFileStorage.getRegionFileCoordinates(file.toPath());
                              IRegionFile regionFile = (IRegionFile)GET_REGION_FILE.invoke(storage, regionCoord, true);

                              for(int x = 0; x < 32; ++x) {
                                 for(int z = 0; z < 32; ++z) {
                                    ChunkPos chunkCoord = new ChunkPos((regionCoord.x << 5) + x, (regionCoord.z << 5) + z);
                                    if (regionFile.hasChunk(chunkCoord)) {
                                       DataInputStream stream = regionFile.getChunkDataInputStream(chunkCoord);

                                       label191: {
                                          label190: {
                                             try {
                                                if (stream != null) {
                                                   CompoundTag chunkNBT = NbtIo.read(stream);
                                                   if (chunkNBT == null) {
                                                      break label190;
                                                   }

                                                   this.processChunkNBTForPoll(chunkNBT, output);
                                                   break label191;
                                                }
                                             } catch (Throwable var28) {
                                                if (stream != null) {
                                                   try {
                                                      stream.close();
                                                   } catch (Throwable var27) {
                                                      var28.addSuppressed(var27);
                                                   }
                                                }

                                                throw var28;
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
                           } catch (Throwable var29) {
                           } finally {
                              progress.advance();
                           }
                        }
                     } catch (Exception var30) {
                     } finally {
                        if (storageInstance instanceof AutoCloseable) {
                           try {
                              ((AutoCloseable)storageInstance).close();
                           } catch (Exception var26) {
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
         ListTag list = (ListTag)compound.get("Items");
         if (list != null) {
            for(Tag nbtBase : list) {
               CompoundTag itemNBT = (CompoundTag)nbtBase;
               net.minecraft.world.item.ItemStack nms = net.minecraft.world.item.ItemStack.parseOptional(this.getProvider(), itemNBT);
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
   }

   static {
      try {
         REGION_STORAGE_CTOR = RegionFileStorage.class.getDeclaredConstructor(RegionStorageInfo.class, Path.class, Boolean.TYPE);
         REGION_STORAGE_CTOR.setAccessible(true);
         GET_REGION_FILE = RegionFileStorage.class.getDeclaredMethod("getRegionFile", ChunkPos.class, Boolean.TYPE);
         GET_REGION_FILE.setAccessible(true);
      } catch (Exception e) {
         throw new RuntimeException("Failed to initialize NMS reflection for Capybara2104", e);
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
