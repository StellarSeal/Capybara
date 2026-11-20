package me.stella.duplication;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.Nullable;

public record DuplicationDetectionConfig(boolean enabled, int maxRecursionDepth, Map<String, Integer> itemCaps, int maxWorkers, boolean discordWebhookEnabled, String discordWebhookUrl, String discordWebhookMessage) {
   public DuplicationDetectionConfig(boolean enabled, int maxRecursionDepth, Map<String, Integer> itemCaps, int maxWorkers, boolean discordWebhookEnabled, String discordWebhookUrl, String discordWebhookMessage) {
      this.enabled = enabled;
      this.maxRecursionDepth = maxRecursionDepth;
      this.itemCaps = new ConcurrentHashMap<>((Map)(itemCaps != null ? itemCaps : new HashMap<>()));
      this.maxWorkers = Math.max(1, maxWorkers);
      this.discordWebhookEnabled = discordWebhookEnabled;
      this.discordWebhookUrl = discordWebhookUrl;
      this.discordWebhookMessage = discordWebhookMessage != null ? discordWebhookMessage : "";
   }

   public static DuplicationDetectionConfig fromYaml(@Nullable FileConfiguration config) {
      if (config == null) {
         return createDefault();
      } else {
         boolean enabled = config.getBoolean("duplication-detection.enabled", true);
         int maxDepth = config.getInt("duplication-detection.max-recursion-depth", 5);
         int maxWorkers = config.getInt("duplication-detection.async-workers", 2);
         boolean discordWebhookEnabled = config.getBoolean("duplication-detection.discord-webhook.enabled", false);
         String discordWebhookUrl = config.getString("duplication-detection.discord-webhook.url", "");
         String discordWebhookMessage = config.getString("duplication-detection.discord-webhook.message", "");
         Map<String, Integer> itemCaps = new ConcurrentHashMap<>();
         ConfigurationSection capsSection = config.getConfigurationSection("duplication-detection.item-caps");
         if (capsSection != null) {
            for(String key : capsSection.getKeys(false)) {
               itemCaps.put(key, capsSection.getInt(key, 2147483647));
            }
         }

         return new DuplicationDetectionConfig(enabled, maxDepth, itemCaps, maxWorkers, discordWebhookEnabled, discordWebhookUrl, discordWebhookMessage);
      }
   }

   public static DuplicationDetectionConfig createDefault() {
      Map<String, Integer> defaults = new ConcurrentHashMap<>();
      defaults.put("DIAMOND_BLOCK", 256);
      defaults.put("NETHERITE_BLOCK", 256);
      defaults.put("SPAWNER", 64);
      defaults.put("ELYTRA", 24);
      defaults.put("ENCHANTED_GOLDEN_APPLE", 256);
      defaults.put("EMERALD_BLOCK", 256);
      return new DuplicationDetectionConfig(true, 5, defaults, 2, false, "", "");
   }

   public Map<String, Integer> itemCaps() {
      return Collections.unmodifiableMap(this.itemCaps);
   }

   public @Nullable Integer getCapForItem(String itemId) {
      Integer cap = (Integer)this.itemCaps.get(itemId);
      if (cap != null) {
         return cap;
      } else {
         String mmoItemsPrefix = "MMOITEMS:";
         return itemId != null && itemId.startsWith(mmoItemsPrefix) ? (Integer)this.itemCaps.get(itemId.substring(mmoItemsPrefix.length())) : null;
      }
   }

   public void setItemCap(String itemId, int cap) {
      this.itemCaps.put(itemId, cap);
   }

   public void removeItemCap(String itemId) {
      this.itemCaps.remove(itemId);
   }
}
