package me.stella.duplication.config;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.Nullable;

public record DuplicationDetectionConfig(
      boolean enabled,
      int maxRecursionDepth,
      Map<String, Integer> itemCaps,
      int maxWorkers,
      boolean discordWebhookEnabled,
      String discordWebhookUrl,
      String discordWebhookMessage) {

   private static final String MMOITEMS_PREFIX = "MMOITEMS:";

   public DuplicationDetectionConfig {
      itemCaps = new ConcurrentHashMap<>(itemCaps != null ? itemCaps : Map.of());
      maxWorkers = Math.max(1, maxWorkers);
      discordWebhookMessage = discordWebhookMessage != null ? discordWebhookMessage : "";
   }

   public static DuplicationDetectionConfig fromYaml(@Nullable FileConfiguration config) {
      if (config == null) {
         return createDefault();
      }
      boolean enabled = config.getBoolean("duplication-detection.enabled", true);
      int maxDepth = config.getInt("duplication-detection.max-recursion-depth", 5);
      int maxWorkers = config.getInt("duplication-detection.async-workers", 2);
      boolean webhookEnabled = config.getBoolean("duplication-detection.discord-webhook.enabled", false);
      String webhookUrl = config.getString("duplication-detection.discord-webhook.url", "");
      String webhookMessage = config.getString("duplication-detection.discord-webhook.message", "");

      Map<String, Integer> caps = new ConcurrentHashMap<>();
      ConfigurationSection capsSection = config.getConfigurationSection("duplication-detection.item-caps");
      if (capsSection != null) {
         for (String key : capsSection.getKeys(false)) {
            caps.put(key, capsSection.getInt(key, Integer.MAX_VALUE));
         }
      }
      return new DuplicationDetectionConfig(enabled, maxDepth, caps, maxWorkers, webhookEnabled, webhookUrl, webhookMessage);
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

   @Override
   public Map<String, Integer> itemCaps() {
      return Collections.unmodifiableMap(itemCaps);
   }

   public @Nullable Integer getCapForItem(@Nullable String itemId) {
      if (itemId == null) {
         return null;
      }
      Integer cap = itemCaps.get(itemId);
      if (cap != null) {
         return cap;
      }
      return itemId.startsWith(MMOITEMS_PREFIX) ? itemCaps.get(itemId.substring(MMOITEMS_PREFIX.length())) : null;
   }

   public void setItemCap(String itemId, int cap) {
      itemCaps.put(itemId, cap);
   }

   public void removeItemCap(String itemId) {
      itemCaps.remove(itemId);
   }
}
