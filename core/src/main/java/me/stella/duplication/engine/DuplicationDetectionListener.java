package me.stella.duplication.engine;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.stella.duplication.snapshot.InventorySnapshot;
import me.stella.duplication.snapshot.InventorySnapshotFactory;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.Nullable;

public class DuplicationDetectionListener implements Listener {

   private static final String BYPASS = "capybara.duplication.bypass";
   private static final long OPENING_COMMAND_MAX_AGE_MILLIS = 10000L;

   private final DuplicationDetectionModule module;
   private final Map<UUID, RecentCommand> recentCommands = new ConcurrentHashMap<>();
   private final Map<UUID, CustomInventoryContext> openCustomInventories = new ConcurrentHashMap<>();

   public DuplicationDetectionListener(DuplicationDetectionModule module) {
      this.module = module;
      for (Player player : Bukkit.getOnlinePlayers()) {
         module.register(player);
      }
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onPlayerJoin(PlayerJoinEvent event) {
      module.register(event.getPlayer());
   }

   @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
   public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
      String command = event.getMessage().trim();
      if (!command.isEmpty()) {
         recentCommands.put(event.getPlayer().getUniqueId(), new RecentCommand(command, System.currentTimeMillis()));
      }
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onInventoryOpen(InventoryOpenEvent event) {
      if (event.getPlayer() instanceof Player player && !player.hasPermission(BYPASS)) {
         handleInventoryAccess(player, event.getInventory(), true);
      }
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onInteract(PlayerInteractEvent event) {
      Player player = event.getPlayer();
      if (player.hasPermission(BYPASS)) {
         return;
      }
      Block clickedBlock = event.getClickedBlock();
      if (clickedBlock != null && clickedBlock.getState() instanceof Container) {
         module.scanInventory(InventorySnapshotFactory.captureContainer(player, clickedBlock.getState()));
      }
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onInteractEntity(PlayerInteractEntityEvent event) {
      Player player = event.getPlayer();
      if (player.hasPermission(BYPASS)) {
         return;
      }
      InventorySnapshot snapshot = InventorySnapshotFactory.captureEntityInventory(player, event.getRightClicked());
      if (snapshot != null) {
         module.scanInventory(snapshot);
      }
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onInventoryClose(InventoryCloseEvent event) {
      if (event.getPlayer() instanceof Player player && !player.hasPermission(BYPASS)) {
         handleInventoryAccess(player, event.getInventory(), false);
      }
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onItemPickup(EntityPickupItemEvent event) {
      if (!(event.getEntity() instanceof Player player) || player.hasPermission(BYPASS)) {
         return;
      }
      int pickedUp = event.getItem().getItemStack().getAmount() - event.getRemaining();
      if (pickedUp <= 0) {
         return;
      }
      module.scanInventory(InventorySnapshotFactory.captureItemTransfer(
            player, event.getItem().getItemStack(), pickedUp,
            InventorySnapshot.ContainerType.ITEM_PICKUP, event.getItem().getLocation()));
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onItemDrop(PlayerDropItemEvent event) {
      Player player = event.getPlayer();
      if (player.hasPermission(BYPASS)) {
         return;
      }
      module.scanInventory(InventorySnapshotFactory.captureItemTransfer(
            player, event.getItemDrop().getItemStack(), event.getItemDrop().getItemStack().getAmount(),
            InventorySnapshot.ContainerType.ITEM_DROP, event.getItemDrop().getLocation()));
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onPlayerQuit(PlayerQuitEvent event) {
      Player player = event.getPlayer();
      recentCommands.remove(player.getUniqueId());
      openCustomInventories.remove(player.getUniqueId());
      if (player.hasPermission(BYPASS)) {
         return;
      }

      module.scanInventory(InventorySnapshotFactory.capturePlayerInventory(player));
      module.scanInventory(InventorySnapshotFactory.captureEnderChest(player));

      Inventory openInventory = player.getOpenInventory().getTopInventory();
      if (openInventory == null) {
         return;
      }
      InventoryHolder holder = openInventory.getHolder();
      if (holder instanceof BlockState blockState && blockState instanceof Container) {
         module.scanInventory(InventorySnapshotFactory.captureContainer(player, blockState));
      } else if (holder instanceof Entity entity) {
         InventorySnapshot snapshot = InventorySnapshotFactory.captureEntityInventory(player, entity);
         if (snapshot != null) {
            module.scanInventory(snapshot);
         }
      }
   }

   private void handleInventoryAccess(Player player, Inventory inv, boolean opening) {
      InventoryHolder holder = inv.getHolder();
      if (holder instanceof Player owner && owner.getUniqueId().equals(player.getUniqueId())) {
         if (inv.getType() == InventoryType.PLAYER) {
            module.scanInventory(InventorySnapshotFactory.capturePlayerInventory(player));
            return;
         }
         if (inv.getType() == InventoryType.ENDER_CHEST) {
            module.scanInventory(InventorySnapshotFactory.captureEnderChest(player));
            return;
         }
      } else if (holder instanceof BlockState blockState) {
         if (blockState instanceof Container) {
            module.scanInventory(InventorySnapshotFactory.captureContainer(player, blockState));
         }
         return;
      } else if (holder instanceof Entity entity) {
         InventorySnapshot snapshot = InventorySnapshotFactory.captureEntityInventory(player, entity);
         if (snapshot != null) {
            module.scanInventory(snapshot);
            return;
         }
      }

      if (opening) {
         scanCustomInventoryOpen(player, inv);
      } else {
         scanCustomInventoryClose(player, inv);
      }
   }

   private @Nullable String deriveOpeningCommand(UUID playerId) {
      RecentCommand recent = recentCommands.get(playerId);
      return recent != null && System.currentTimeMillis() - recent.timestamp() <= OPENING_COMMAND_MAX_AGE_MILLIS
            ? recent.command()
            : null;
   }

   private void scanCustomInventoryOpen(Player player, Inventory inventory) {
      String openingCommand = deriveOpeningCommand(player.getUniqueId());
      openCustomInventories.put(player.getUniqueId(), new CustomInventoryContext(inventory, openingCommand));
      module.scanInventory(InventorySnapshotFactory.captureCustomInventory(player, inventory, openingCommand));
   }

   private void scanCustomInventoryClose(Player player, Inventory inventory) {
      CustomInventoryContext context = openCustomInventories.remove(player.getUniqueId());
      String openingCommand = context != null && context.inventory() == inventory
            ? context.openingCommand()
            : deriveOpeningCommand(player.getUniqueId());
      module.scanInventory(InventorySnapshotFactory.captureCustomInventory(player, inventory, openingCommand));
   }

   private record RecentCommand(String command, long timestamp) {
   }

   private record CustomInventoryContext(Inventory inventory, @Nullable String openingCommand) {
   }
}
