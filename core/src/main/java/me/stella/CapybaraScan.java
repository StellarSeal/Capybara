package me.stella;

import java.util.concurrent.CompletableFuture;
import me.stella.scan.engine.ScanExecutors;
import me.stella.utility.MutableOptional;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.Type;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class CapybaraScan {

   private static final NamespacedKey EXCELLENT_CRATES_ID = new NamespacedKey("excellentcrates", "crate_key.id");
   private static final NamespacedKey CRAZY_VOUCHERS_ID = new NamespacedKey("crazyvouchers", "crazyvouchers_voucher_item");
   private static final MutableOptional<CapybaraNative> SCANNER = MutableOptional.empty();

   private CapybaraScan() {
   }

   public static void process(Runnable task) {
      ScanExecutors.runCoordinator(task);
   }

   public static CompletableFuture<Void> runAsync(Runnable task) {
      return ScanExecutors.runWorker(task);
   }

   public static CapybaraNative get() {
      return SCANNER.asOptional()
            .orElseThrow(() -> new IllegalStateException("Plugin has not loaded the native framework!"));
   }

   public static CapybaraNative load(@NotNull CapybaraNative instance) {
      return SCANNER.provide(() -> instance);
   }

   private static boolean isPluginEnabled(String plugin) {
      return Bukkit.getPluginManager().isPluginEnabled(plugin);
   }

   public static boolean supportMMOItems() {
      return isPluginEnabled("MMOItems") && isPluginEnabled("MythicLib");
   }

   public static boolean supportPlayerVaults() {
      return isPluginEnabled("PlayerVaults") || isPluginEnabled("PlayerVaultsX");
   }

   public static boolean supportZAuctionHouse() {
      return isPluginEnabled("zAuctionHouseV3");
   }

   public static @Nullable String toMMOID(@Nullable ItemStack itemStack) {
      if (!supportMMOItems() || itemStack == null || itemStack.getType() == Material.AIR) {
         return null;
      }
      Type type = Type.get(itemStack);
      return type == null ? null : type.getId() + ":" + MMOItems.getID(itemStack);
   }

   public static @Nullable String toPluginItemID(@Nullable ItemStack itemStack) {
      if (itemStack == null || itemStack.getType() == Material.AIR) {
         return null;
      }
      String mmoId = toMMOID(itemStack);
      if (mmoId != null) {
         return "MMOITEMS:" + mmoId;
      }
      ItemMeta meta = itemStack.getItemMeta();
      if (meta == null) {
         return null;
      }
      PersistentDataContainer pdc = meta.getPersistentDataContainer();
      String crateId = pdc.get(EXCELLENT_CRATES_ID, PersistentDataType.STRING);
      if (crateId != null) {
         return "EXCELLENTCRATES:" + crateId;
      }
      String voucherId = pdc.get(CRAZY_VOUCHERS_ID, PersistentDataType.STRING);
      return voucherId != null ? "CRAZYVOUCHERS:" + voucherId : null;
   }

   public static String toSearchItemID(ItemStack itemStack) {
      String pluginId = toPluginItemID(itemStack);
      return pluginId != null ? pluginId : itemStack.getType().name();
   }

   public static void shutdown() {
      ScanExecutors.shutdown();
   }
}
