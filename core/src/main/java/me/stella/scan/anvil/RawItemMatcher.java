package me.stella.scan.anvil;

import java.util.Locale;
import java.util.Objects;
import me.stella.CapybaraNative;
import me.stella.CapybaraScan;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;

/**
 * Precompiled matcher for a scan target item.
 *
 * <p>Avoids repeating {@code getItemMeta()}, MMOItems reflection, or CraftItemStack conversions per item.
 * >99% of non-matching items are rejected by a single string comparison on the raw NBT {@code id}.</p>
 */
public final class RawItemMatcher {

   private final ItemStack model;
   private final String targetMinecraftId;
   private final @Nullable String targetPluginId;
   private final boolean basicVanilla;
   private final CapybaraNative natives;

   private RawItemMatcher(ItemStack model, String targetMinecraftId, @Nullable String targetPluginId,
         boolean basicVanilla, CapybaraNative natives) {
      this.model = model;
      this.targetMinecraftId = targetMinecraftId;
      this.targetPluginId = targetPluginId;
      this.basicVanilla = basicVanilla;
      this.natives = natives;
   }

   public static RawItemMatcher compile(ItemStack model, CapybaraNative natives) {
      String mcId = "minecraft:" + model.getType().getKey().getKey().toLowerCase(Locale.ROOT);
      String pluginId = CapybaraScan.toPluginItemID(model);
      boolean basic = pluginId == null && isStrictlyBasic(model);
      return new RawItemMatcher(model, mcId, pluginId, basic, natives);
   }

   private static boolean isStrictlyBasic(ItemStack stack) {
      if (!stack.hasItemMeta()) {
         return true;
      }
      ItemMeta meta = stack.getItemMeta();
      if (meta == null) {
         return true;
      }
      return !meta.hasLore()
            && !meta.hasDisplayName()
            && !meta.hasEnchants()
            && !meta.hasCustomModelData()
            && meta.getPersistentDataContainer().getKeys().isEmpty();
   }

   public boolean matches(RawItem item) {
      if (item.count() <= 0) {
         return false;
      }
      if (targetPluginId != null) {
         return Objects.equals(targetPluginId, item.pluginItemId());
      }
      if (!targetMinecraftId.equals(item.id())) {
         return false;
      }
      if (item.pluginItemId() != null) {
         return false;
      }
      if (basicVanilla) {
         return !item.hasExtraMeta();
      }
      return natives.matchesRawItemNbt(item.toNamedRootCompound(), model);
   }

   public static boolean isContainerItemId(String id) {
      return id.endsWith("shulker_box") || id.endsWith("bundle");
   }
}
