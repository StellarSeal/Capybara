package me.stella.duplication.engine;

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
import me.stella.duplication.alert.DiscordWebhook;
import me.stella.duplication.alert.SuspicionRecord;
import me.stella.duplication.config.DuplicationDetectionConfig;
import me.stella.duplication.snapshot.InventorySnapshot;
import me.stella.scan.engine.ScanExecutors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

public class DuplicationDetectionModule {

   private static final DateTimeFormatter LOG_FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");
   private static final String STAFF_NOTIFICATION_PERMISSION = "capybara.staff.dupe";
   private static final int MAX_HISTORY_SIZE = 1000;
   private static final Map<String, Boolean> PERMISSION_CACHE = new ConcurrentHashMap<>();

   private final DuplicationDetectionConfig config;
   private final Deque<SuspicionRecord> suspicionLog;
   private final @Nullable File logsDirectory;
   private final @Nullable DiscordWebhook discordWebhook;
   private volatile String currentLogDate;
   private volatile boolean initialized;

   public DuplicationDetectionModule(DuplicationDetectionConfig config, @Nullable File logsDirectory) {
      this.config = config;
      this.logsDirectory = logsDirectory;
      this.suspicionLog = new ConcurrentLinkedDeque<>();
      this.initialized = false;
      this.currentLogDate = null;
      this.discordWebhook = config.discordWebhookEnabled()
            && config.discordWebhookUrl() != null && !config.discordWebhookUrl().isEmpty()
            ? new DiscordWebhook(config.discordWebhookUrl())
            : null;
   }

   public boolean register(Player player) {
      return PERMISSION_CACHE.computeIfAbsent(player.getName(), name -> player.hasPermission(STAFF_NOTIFICATION_PERMISSION));
   }

   public void initialize() {
      if (initialized) {
         return;
      }
      if (logsDirectory != null && !logsDirectory.exists()) {
         try {
            logsDirectory.mkdirs();
         } catch (Exception e) {
            System.err.println("[DuplicationDetection] Failed to create logs directory");
            e.printStackTrace();
         }
      }
      ScanExecutors.configureDuplicationWorkers(config.maxWorkers());
      initialized = true;
      Bukkit.getConsoleSender().sendMessage(
            "[DuplicationDetection] Module initialized - Enabled: " + config.enabled()
                  + " | Max Recursion: " + config.maxRecursionDepth()
                  + " | Monitored Items: " + config.itemCaps().size());
   }

   private @Nullable File getCurrentLogFile() {
      if (logsDirectory == null) {
         return null;
      }
      String today = LocalDate.now().format(LOG_FILE_DATE_FORMAT);
      if (!today.equals(currentLogDate)) {
         currentLogDate = today;
      }
      File logFile = new File(logsDirectory, today + ".log");
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

   public void logSuspicion(SuspicionRecord record) {
      if (!config.enabled()) {
         return;
      }
      suspicionLog.addLast(record);
      while (suspicionLog.size() > MAX_HISTORY_SIZE) {
         suspicionLog.pollFirst();
      }
      Bukkit.getConsoleSender().sendMessage(record.toString());

      boolean notStaff = !PERMISSION_CACHE.getOrDefault(record.getPlayerName(), false);
      if (notStaff) {
         notifyStaff(record);
      }

      // Offload blocking HTTP webhook and log file writes to the serialized I/O thread.
      ScanExecutors.runDuplicationIo(() -> {
         if (discordWebhook != null && config.discordWebhookEnabled() && notStaff) {
            try {
               discordWebhook.sendDuplicationAlert(record, config.discordWebhookMessage());
            } catch (Exception e) {
               System.err.println("[DuplicationDetection] Failed to send Discord webhook");
               e.printStackTrace();
            }
         }
         File logFile = getCurrentLogFile();
         if (logFile != null) {
            try (FileWriter writer = new FileWriter(logFile, true)) {
               writer.append(record.toString()).append('\n');
            } catch (IOException e) {
               System.err.println("[DuplicationDetection] Failed to write suspicion log");
               e.printStackTrace();
            }
         }
      });
   }

   private void notifyStaff(SuspicionRecord record) {
      Component message = formatStaffNotification(record);
      for (Player player : Bukkit.getOnlinePlayers()) {
         if (player.hasPermission(STAFF_NOTIFICATION_PERMISSION)) {
            player.sendMessage(message);
         }
      }
   }

   private Component formatStaffNotification(SuspicionRecord record) {
      TextComponent.Builder builder = Component.text()
            .append(Component.text("[Capybara] Suspicious Activity Detected!", NamedTextColor.RED, TextDecoration.BOLD))
            .appendNewline()
            .append(Component.text(record.getPlayerName(), NamedTextColor.YELLOW))
            .append(Component.text(" (" + record.getPlayerUUID() + ")", NamedTextColor.GRAY))
            .appendNewline()
            .append(Component.text("Item: ", NamedTextColor.GOLD))
            .append(Component.text(record.getItemId(), NamedTextColor.WHITE))
            .append(Component.text(" | ", NamedTextColor.GRAY))
            .append(Component.text("Amount: ", NamedTextColor.GOLD))
            .append(Component.text(record.getDetectedAmount(), NamedTextColor.WHITE))
            .append(Component.text(" (Cap: " + record.getConfiguredCap() + ")", NamedTextColor.GRAY))
            .appendNewline()
            .append(Component.text("Container: ", NamedTextColor.GOLD))
            .append(Component.text(record.getContainerType().toString(), NamedTextColor.WHITE));

      Location loc = record.getContainerLocation();
      if (loc != null) {
         String worldName = loc.getWorld() != null ? loc.getWorld().getName() : "Unknown";
         builder.append(Component.text(" @ ", NamedTextColor.GRAY))
               .append(Component.text(worldName + " " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ(),
                     NamedTextColor.WHITE));
      }
      if (record.getEntityIdentifier() != null) {
         builder.appendNewline()
               .append(Component.text("Source: ", NamedTextColor.GOLD))
               .append(Component.text(record.getEntityIdentifier(), NamedTextColor.WHITE));
      }
      return builder.build();
   }

   public void scanInventory(InventorySnapshot snapshot) {
      if (config.enabled()) {
         ScanExecutors.runDuplication(new DuplicationScanTask(snapshot, config, this));
      }
   }

   public List<SuspicionRecord> getSuspicionHistory(int limit) {
      int size = Math.min(limit, suspicionLog.size());
      List<SuspicionRecord> result = new ArrayList<>(size);
      Iterator<SuspicionRecord> it = suspicionLog.descendingIterator();
      for (int i = 0; i < size && it.hasNext(); i++) {
         result.add(it.next());
      }
      return result;
   }

   public List<SuspicionRecord> getSuspicionsByPlayer(UUID playerUUID, int limit) {
      List<SuspicionRecord> result = new ArrayList<>();
      Iterator<SuspicionRecord> it = suspicionLog.descendingIterator();
      while (it.hasNext() && result.size() < limit) {
         SuspicionRecord record = it.next();
         if (record.getPlayerUUID().equals(playerUUID)) {
            result.add(record);
         }
      }
      return result;
   }

   public DuplicationDetectionConfig getConfig() {
      return config;
   }

   public void shutdown() {
      suspicionLog.clear();
   }
}
