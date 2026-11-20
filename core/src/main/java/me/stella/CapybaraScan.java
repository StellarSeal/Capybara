package me.stella;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
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

public class CapybaraScan {
   private static final NamespacedKey EXCELLENT_CRATES_ID = new NamespacedKey("excellentcrates", "crate_key.id");
   private static final NamespacedKey CRAZY_VOUCHERS_ID = new NamespacedKey("crazyvouchers", "crazyvouchers_voucher_item");
   private static final MutableOptional<CapybaraNative> scanner = MutableOptional.<CapybaraNative>empty();
   private static final ThreadPoolExecutor executor;

   public static void process(Runnable r) {
      executor.execute(r);
   }

   public static CompletableFuture<Void> runAsync(Runnable r) {
      return CompletableFuture.runAsync(r, executor);
   }

   public static CapybaraNative get() {
      return (CapybaraNative)scanner.asOptional().orElseThrow(() -> new IllegalStateException("Plugin has not loaded the native framework!"));
   }

   public static CapybaraNative load(@NotNull CapybaraNative instance) {
      return scanner.provide(() -> instance);
   }

   private static boolean isPluginEnabled(String plugin) {
      return Bukkit.getPluginManager().getPlugin(plugin) != null && Bukkit.getPluginManager().isPluginEnabled(plugin);
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

   public static String toMMOID(ItemStack itemStack) {
      if (!supportMMOItems()) {
         return null;
      } else if (itemStack != null && itemStack.getType() != Material.AIR) {
         Type t = Type.get(itemStack);
         if (t == null) {
            return null;
         } else {
            String var10000 = t.getId();
            return var10000 + ":" + MMOItems.getID(itemStack);
         }
      } else {
         return null;
      }
   }

   public static String toPluginItemID(ItemStack itemStack) {
      if (itemStack != null && itemStack.getType() != Material.AIR) {
         String mmoId = toMMOID(itemStack);
         if (mmoId != null) {
            return "MMOITEMS:" + mmoId;
         } else {
            ItemMeta meta = itemStack.getItemMeta();
            if (meta == null) {
               return null;
            } else {
               PersistentDataContainer pdc = meta.getPersistentDataContainer();
               String crateId = (String)pdc.get(EXCELLENT_CRATES_ID, PersistentDataType.STRING);
               if (crateId != null) {
                  return "EXCELLENTCRATES:" + crateId;
               } else {
                  String voucherId = (String)pdc.get(CRAZY_VOUCHERS_ID, PersistentDataType.STRING);
                  return voucherId != null ? "CRAZYVOUCHERS:" + voucherId : null;
               }
            }
         }
      } else {
         return null;
      }
   }

   public static String toSearchItemID(ItemStack itemStack) {
      String pluginId = toPluginItemID(itemStack);
      return pluginId != null ? pluginId : itemStack.getType().name();
   }

   public static void shutdown() {
      executor.shutdownNow();
   }

   static {
      executor = new ThreadPoolExecutor(8, 8, 60L, TimeUnit.SECONDS, new LinkedBlockingDeque<>(24576), (new ThreadFactoryBuilder()).setPriority(5).setNameFormat("Capybara Scanner Thread").setThreadFactory(Thread::new).build());
   }
}
