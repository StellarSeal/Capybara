package me.stella.duplication;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import me.stella.CapybaraScan;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

public class DuplicationDetectionModule {
   private static final DateTimeFormatter LOG_FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");
   private static final String STAFF_NOTIFICATION_PERMISSION = "capybara.staff.dupe";
   private static final Map<String, Boolean> permission = new ConcurrentHashMap<>();
   private final DuplicationDetectionConfig config;
   private final Deque<SuspicionRecord> suspicionLog;
   private final File logsDirectory;
   private final DiscordWebhook discordWebhook;
   private volatile String currentLogDate;
   private volatile boolean initialized;

   public DuplicationDetectionModule(DuplicationDetectionConfig config, @Nullable File logsDirectory) {
      this.config = config;
      this.logsDirectory = logsDirectory;
      this.suspicionLog = new ConcurrentLinkedDeque();
      this.initialized = false;
      this.currentLogDate = null;
      if (config.discordWebhookEnabled() && config.discordWebhookUrl() != null && !config.discordWebhookUrl().isEmpty()) {
         this.discordWebhook = new DiscordWebhook(config.discordWebhookUrl());
      } else {
         this.discordWebhook = null;
      }

   }

   public boolean register(Player player) {
      return (Boolean)permission.computeIfAbsent(player.getName(), (name) -> player.hasPermission("capybara.staff.dupe"));
   }

   public void initialize() {
      if (!this.initialized) {
         if (this.logsDirectory != null) {
            try {
               if (!this.logsDirectory.exists()) {
                  this.logsDirectory.mkdirs();
               }
            } catch (Exception e) {
               System.err.println("[DuplicationDetection] Failed to create logs directory");
               e.printStackTrace();
            }
         }

         this.initialized = true;
         ConsoleCommandSender var10000 = Bukkit.getConsoleSender();
         boolean var10001 = this.config.enabled();
         var10000.sendMessage("[DuplicationDetection] Module initialized - Enabled: " + var10001 + " | Max Recursion: " + this.config.maxRecursionDepth() + " | Monitored Items: " + this.config.itemCaps().size());
      }
   }

   private File getCurrentLogFile() {
      if (this.logsDirectory == null) {
         return null;
      } else {
         String today = LocalDate.now().format(LOG_FILE_DATE_FORMAT);
         if (!today.equals(this.currentLogDate)) {
            this.currentLogDate = today;
         }

         File logFile = new File(this.logsDirectory, today + ".log");
         if (!logFile.exists()) {
            try {
               logFile.createNewFile();
            } catch (IOException e) {
               System.err.println("[DuplicationDetection] Failed to create log file: " + logFile.getName());
               e.printStackTrace();
            }
         }

         return logFile;
      }
   }

   public void logSuspicion(SuspicionRecord record) {
      if (this.config.enabled()) {
         this.suspicionLog.addLast(record);
         Bukkit.getConsoleSender().sendMessage(record.toString());
         boolean notStaff = !(Boolean)permission.getOrDefault(record.getPlayerName(), false);
         if (notStaff) {
            this.notifyStaff(record);
         }

         if (this.discordWebhook != null && this.config.discordWebhookEnabled() && notStaff) {
            try {
               this.discordWebhook.sendDuplicationAlert(record, this.config.discordWebhookMessage());
            } catch (Exception e) {
               System.err.println("[DuplicationDetection] Failed to send Discord webhook");
               e.printStackTrace();
            }
         }

         File logFile = this.getCurrentLogFile();
         if (logFile != null) {
            try {
               FileWriter writer = new FileWriter(logFile, true);

               try {
                  writer.append(record.toString()).append("\n");
               } catch (Throwable var8) {
                  try {
                     writer.close();
                  } catch (Throwable var7) {
                     var8.addSuppressed(var7);
                  }

                  throw var8;
               }

               writer.close();
            } catch (IOException e) {
               System.err.println("[DuplicationDetection] Failed to write suspicion log");
               e.printStackTrace();
            }
         }

      }
   }

   private void notifyStaff(SuspicionRecord record) {
      String message = this.formatStaffNotification(record);

      for(Player player : Bukkit.getOnlinePlayers()) {
         if (player.hasPermission("capybara.staff.dupe")) {
            player.sendMessage(message);
         }
      }

   }

   private String formatStaffNotification(SuspicionRecord record) {
      StringBuilder message = new StringBuilder();
      message.append(ChatColor.RED).append(ChatColor.BOLD).append("[Capybara] Suspicious Activity Detected!\n").append(ChatColor.YELLOW).append(record.getPlayerName()).append(ChatColor.GRAY).append(" (").append(record.getPlayerUUID()).append(")").append("\n").append(ChatColor.GOLD).append("Item: ").append(ChatColor.WHITE).append(record.getItemId()).append(ChatColor.GRAY).append(" | ").append(ChatColor.GOLD).append("Amount: ").append(ChatColor.WHITE).append(record.getDetectedAmount()).append(ChatColor.GRAY).append(" (Cap: ").append(record.getConfiguredCap()).append(")").append("\n").append(ChatColor.GOLD).append("Container: ").append(ChatColor.WHITE).append(record.getContainerType());
      if (record.getContainerLocation() != null) {
         Location loc = record.getContainerLocation();
         message.append(ChatColor.GRAY).append(" @ ").append(ChatColor.WHITE);
         if (loc.getWorld() != null) {
            message.append(loc.getWorld().getName());
         } else {
            message.append("Unknown");
         }

         message.append(" ").append((int)loc.getX()).append(", ").append((int)loc.getY()).append(", ").append((int)loc.getZ());
      }

      if (record.getEntityIdentifier() != null) {
         message.append("\n").append(ChatColor.GOLD).append("Source: ").append(ChatColor.WHITE).append(record.getEntityIdentifier());
      }

      return message.toString();
   }

   public void scanInventory(InventorySnapshot snapshot) {
      if (this.config.enabled()) {
         DuplicationScanTask task = new DuplicationScanTask(snapshot, this.config, this);
         CapybaraScan.process(task);
      }
   }

   public List<SuspicionRecord> getSuspicionHistory(int limit) {
      int size = Math.min(limit, this.suspicionLog.size());
      List<SuspicionRecord> result = new ArrayList<>(size);
      Iterator<SuspicionRecord> it = this.suspicionLog.descendingIterator();

      for(int i = 0; i < size && it.hasNext(); ++i) {
         result.add((SuspicionRecord)it.next());
      }

      return result;
   }

   public List<SuspicionRecord> getSuspicionsByPlayer(UUID playerUUID, int limit) {
      List<SuspicionRecord> result = new ArrayList<>();
      int count = 0;
      Iterator<SuspicionRecord> it = this.suspicionLog.descendingIterator();

      while(it.hasNext() && count < limit) {
         SuspicionRecord record = (SuspicionRecord)it.next();
         if (record.getPlayerUUID().equals(playerUUID)) {
            result.add(record);
            ++count;
         }
      }

      return result;
   }

   public DuplicationDetectionConfig getConfig() {
      return this.config;
   }

   public void shutdown() {
      this.suspicionLog.clear();
   }
}
