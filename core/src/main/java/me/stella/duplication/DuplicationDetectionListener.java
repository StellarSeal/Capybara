package me.stella.duplication;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;
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

public class DuplicationDetectionListener implements Listener {
   private static final String BYPASS = "capybara.duplication.bypass";
   private static final long OPENING_COMMAND_MAX_AGE_MILLIS = 10000L;
   private final DuplicationDetectionModule module;
   private final Map<UUID, RecentCommand> recentCommands = new ConcurrentHashMap<>();
   private final Map<UUID, CustomInventoryContext> openCustomInventories = new ConcurrentHashMap<>();

   public DuplicationDetectionListener(DuplicationDetectionModule module) {
      this.module = module;

      for(Player player : Bukkit.getOnlinePlayers()) {
         module.register(player);
      }

   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onPlayerJoin(PlayerJoinEvent event) {
      this.module.register(event.getPlayer());
   }

   @EventHandler(
      priority = EventPriority.LOWEST,
      ignoreCancelled = true
   )
   public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
      String command = event.getMessage().trim();
      if (!command.isEmpty()) {
         this.recentCommands.put(event.getPlayer().getUniqueId(), new RecentCommand(command, System.currentTimeMillis()));
      }

   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onInventoryOpen(InventoryOpenEvent event) {
      HumanEntity var3 = event.getPlayer();
      if (var3 instanceof Player player) {
         if (!player.hasPermission("capybara.duplication.bypass")) {
            Inventory inv = event.getInventory();
            InventoryHolder holder = inv.getHolder();
            if (holder instanceof Player) {
               Player inventoryOwner = (Player)holder;
               if (inventoryOwner.getUniqueId().equals(player.getUniqueId()) && inv.getType() == InventoryType.PLAYER) {
                  InventorySnapshot snapshot = InventorySnapshotFactory.capturePlayerInventory(player);
                  this.module.scanInventory(snapshot);
               } else if (inventoryOwner.getUniqueId().equals(player.getUniqueId()) && inv.getType() == InventoryType.ENDER_CHEST) {
                  this.module.scanInventory(InventorySnapshotFactory.captureEnderChest(player));
               } else {
                  this.scanCustomInventoryOpen(player, inv);
               }
            } else if (holder instanceof BlockState) {
               BlockState blockState = (BlockState)holder;
               if (blockState instanceof Container) {
                  InventorySnapshot snapshot = InventorySnapshotFactory.captureContainer(player, blockState);
                  this.module.scanInventory(snapshot);
               }
            } else if (holder instanceof Entity) {
               Entity entity = (Entity)holder;
               InventorySnapshot snapshot = InventorySnapshotFactory.captureEntityInventory(player, entity);
               if (snapshot != null) {
                  this.module.scanInventory(snapshot);
               } else {
                  this.scanCustomInventoryOpen(player, inv);
               }
            } else {
               this.scanCustomInventoryOpen(player, inv);
            }

         }
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onInteract(PlayerInteractEvent event) {
      Player player = event.getPlayer();
      if (!player.hasPermission("capybara.duplication.bypass")) {
         Block clickedBlock = event.getClickedBlock();
         if (clickedBlock != null) {
            BlockState state = clickedBlock.getState();
            if (state instanceof Container) {
               InventorySnapshot snapshot = InventorySnapshotFactory.captureContainer(player, state);
               this.module.scanInventory(snapshot);
            }

         }
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onInteractEntity(PlayerInteractEntityEvent event) {
      Player player = event.getPlayer();
      if (!player.hasPermission("capybara.duplication.bypass")) {
         Entity entity = event.getRightClicked();
         InventorySnapshot snapshot = InventorySnapshotFactory.captureEntityInventory(player, entity);
         if (snapshot != null) {
            this.module.scanInventory(snapshot);
         }

      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onInventoryClose(InventoryCloseEvent event) {
      HumanEntity var3 = event.getPlayer();
      if (var3 instanceof Player player) {
         if (!player.hasPermission("capybara.duplication.bypass")) {
            Inventory inv = event.getInventory();
            InventoryHolder holder = inv.getHolder();
            if (holder instanceof Player) {
               Player inventoryOwner = (Player)holder;
               if (inventoryOwner.getUniqueId().equals(player.getUniqueId()) && inv.getType() == InventoryType.PLAYER) {
                  InventorySnapshot snapshot = InventorySnapshotFactory.capturePlayerInventory(player);
                  this.module.scanInventory(snapshot);
               } else if (inventoryOwner.getUniqueId().equals(player.getUniqueId()) && inv.getType() == InventoryType.ENDER_CHEST) {
                  this.module.scanInventory(InventorySnapshotFactory.captureEnderChest(player));
               } else {
                  this.scanCustomInventoryClose(player, inv);
               }
            } else if (holder instanceof BlockState) {
               BlockState blockState = (BlockState)holder;
               if (blockState instanceof Container) {
                  InventorySnapshot snapshot = InventorySnapshotFactory.captureContainer(player, blockState);
                  this.module.scanInventory(snapshot);
               }
            } else if (holder instanceof Entity) {
               Entity entity = (Entity)holder;
               InventorySnapshot snapshot = InventorySnapshotFactory.captureEntityInventory(player, entity);
               if (snapshot != null) {
                  this.module.scanInventory(snapshot);
               } else {
                  this.scanCustomInventoryClose(player, inv);
               }
            } else {
               this.scanCustomInventoryClose(player, inv);
            }

         }
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onItemPickup(EntityPickupItemEvent event) {
      LivingEntity var3 = event.getEntity();
      if (var3 instanceof Player player) {
         if (!player.hasPermission("capybara.duplication.bypass")) {
            int pickedUp = event.getItem().getItemStack().getAmount() - event.getRemaining();
            if (pickedUp <= 0) {
               return;
            }

            this.module.scanInventory(InventorySnapshotFactory.captureItemTransfer(player, event.getItem().getItemStack(), pickedUp, InventorySnapshot.ContainerType.ITEM_PICKUP, event.getItem().getLocation()));
            return;
         }
      }

   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onItemDrop(PlayerDropItemEvent event) {
      Player player = event.getPlayer();
      if (!player.hasPermission("capybara.duplication.bypass")) {
         this.module.scanInventory(InventorySnapshotFactory.captureItemTransfer(player, event.getItemDrop().getItemStack(), event.getItemDrop().getItemStack().getAmount(), InventorySnapshot.ContainerType.ITEM_DROP, event.getItemDrop().getLocation()));
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onPlayerQuit(PlayerQuitEvent event) {
      Player player = event.getPlayer();
      this.recentCommands.remove(player.getUniqueId());
      this.openCustomInventories.remove(player.getUniqueId());
      if (!player.hasPermission("capybara.duplication.bypass")) {
         InventorySnapshot playerInvSnapshot = InventorySnapshotFactory.capturePlayerInventory(player);
         this.module.scanInventory(playerInvSnapshot);
         InventorySnapshot enderChestSnapshot = InventorySnapshotFactory.captureEnderChest(player);
         this.module.scanInventory(enderChestSnapshot);
         Inventory openInventory = player.getOpenInventory().getTopInventory();
         if (openInventory != null) {
            InventoryHolder holder = openInventory.getHolder();
            if (holder instanceof BlockState) {
               BlockState blockState = (BlockState)holder;
               if (blockState instanceof Container) {
                  InventorySnapshot snapshot = InventorySnapshotFactory.captureContainer(player, blockState);
                  this.module.scanInventory(snapshot);
                  return;
               }
            }

            if (holder instanceof Entity) {
               Entity entity = (Entity)holder;
               InventorySnapshot snapshot = InventorySnapshotFactory.captureEntityInventory(player, entity);
               if (snapshot != null) {
                  this.module.scanInventory(snapshot);
               }
            }
         }

      }
   }

   private String deriveOpeningCommand(UUID playerId) {
      RecentCommand recent = (RecentCommand)this.recentCommands.get(playerId);
      return recent != null && System.currentTimeMillis() - recent.timestamp() <= 10000L ? recent.command() : null;
   }

   private void scanCustomInventoryOpen(Player player, Inventory inventory) {
      String openingCommand = this.deriveOpeningCommand(player.getUniqueId());
      this.openCustomInventories.put(player.getUniqueId(), new CustomInventoryContext(inventory, openingCommand));
      this.module.scanInventory(InventorySnapshotFactory.captureCustomInventory(player, inventory, openingCommand));
   }

   private void scanCustomInventoryClose(Player player, Inventory inventory) {
      CustomInventoryContext context = (CustomInventoryContext)this.openCustomInventories.remove(player.getUniqueId());
      String openingCommand = context != null && context.inventory() == inventory ? context.openingCommand() : this.deriveOpeningCommand(player.getUniqueId());
      this.module.scanInventory(InventorySnapshotFactory.captureCustomInventory(player, inventory, openingCommand));
   }

   private static record RecentCommand(String command, long timestamp) {
   }

   private static record CustomInventoryContext(Inventory inventory, String openingCommand) {
   }
}
