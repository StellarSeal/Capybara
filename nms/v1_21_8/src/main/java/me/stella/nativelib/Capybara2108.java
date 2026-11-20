package me.stella.nativelib;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.nio.file.Path;
import java.util.Objects;
import me.stella.CapybaraNative;
import me.stella.CapybaraScan;
import me.stella.utility.MutableOptional;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.inventory.ItemStack;

public class Capybara2108 implements CapybaraNative {

   private static final MutableOptional<HolderLookup.Provider> LOOKUP_PROVIDER = MutableOptional.empty();

   private static HolderLookup.Provider loadProvider() {
      return ((CraftServer) Bukkit.getServer()).getHandle().getServer().registryAccess();
   }

   private HolderLookup.Provider getProvider() {
      return LOOKUP_PROVIDER.provide(Capybara2108::loadProvider);
   }

   public static void cleanup() {
      LOOKUP_PROVIDER.set(null);
   }

   @Override
   public Path dimensionPath(World world) {
      ServerLevel level = ((CraftWorld) world).getHandle();
      return level.levelStorageAccess.getDimensionPath(level.dimension()).toAbsolutePath();
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
         return isBasic(entry);
      }
      if (entry.isSimilar(model)) {
         return true;
      }
      return deepNbtMatch(CraftItemStack.asNMSCopy(model), CraftItemStack.asNMSCopy(entry));
   }

   @Override
   public boolean matchesRawItemNbt(byte[] namedRootCompound, ItemStack model) {
      try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(namedRootCompound))) {
         CompoundTag itemTag = NbtIo.read(in);
         ValueInput input = TagValueInput.create(ProblemReporter.DISCARDING, getProvider(), itemTag);
         net.minecraft.world.item.ItemStack nms = input.read(net.minecraft.world.item.ItemStack.MAP_CODEC)
               .orElse(net.minecraft.world.item.ItemStack.EMPTY);
         if (nms.isEmpty()) {
            return false;
         }
         return isMatch(model, CraftItemStack.asBukkitCopy(nms));
      } catch (Exception ignored) {
         return false;
      }
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
}
