package me.stella.scan.engine;

import java.io.File;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import me.stella.scan.model.ScanJob;
import me.stella.scan.model.ScanProgress;
import me.stella.scan.model.ScanSession;
import me.stella.scan.monitor.ScanMonitor;
import me.stella.ui.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Owns the scan lifecycle: admits one scan per administrator, dispatches coordination on an isolated pool,
 * supports cooperative cancellation, and attaches a {@link ScanMonitor} until the scan settles.
 */
public final class ScanService {

   private static final UUID CONSOLE_ID = new UUID(0L, 0L);

   private final Plugin plugin;
   private final Map<UUID, ScanSession> sessions = new ConcurrentHashMap<>();
   private final Set<ScanMonitor> monitors = ConcurrentHashMap.newKeySet();
   private final AtomicInteger sequence = new AtomicInteger();

   public ScanService(Plugin plugin) {
      this.plugin = plugin;
   }

   public Collection<ScanSession> sessions() {
      return sessions.values().stream().sorted(Comparator.comparingInt(ScanSession::id)).toList();
   }

   public Optional<ScanSession> sessionOf(CommandSender sender) {
      return Optional.ofNullable(sessions.get(idOf(sender)));
   }

   public boolean submit(CommandSender owner, ScanJob job) {
      UUID ownerId = idOf(owner);
      ScanSession session = new ScanSession(sequence.incrementAndGet(), ownerId, owner.getName(), job);
      ScanSession running = sessions.putIfAbsent(ownerId, session);
      if (running != null) {
         owner.sendMessage(Messages.alreadyScanning(running));
         return false;
      }
      owner.sendMessage(Messages.scanStarted(session));
      observe(owner, session);
      ScanExecutors.runCoordinator(() -> execute(session));
      return true;
   }

   public Optional<ScanSession> cancel(CommandSender owner) {
      ScanSession session = sessions.get(idOf(owner));
      if (session == null) {
         return Optional.empty();
      }
      session.cancel();
      return Optional.of(session);
   }

   public void observe(CommandSender audience, ScanSession session) {
      monitors.add(ScanMonitor.start(plugin, this, audience, session));
   }

   public void handOver(ScanMonitor retired, ScanSession session) {
      release(retired);
      observe(Bukkit.getConsoleSender(), session);
   }

   public void release(ScanMonitor monitor) {
      monitors.remove(monitor);
   }

   private void execute(ScanSession session) {
      session.progress().phase(ScanProgress.PHASE_SCANNING);
      try {
         File report = session.job().work().run(session.progress());
         if (session.cancelled()) {
            session.cancel();
         } else {
            session.complete(report);
         }
      } catch (Throwable failure) {
         if (session.cancelled()) {
            session.cancel();
         } else {
            session.fail(failure);
            plugin.getLogger().log(Level.WARNING,
                  "Scan #" + session.id() + " (" + session.job().title() + ") failed", failure);
         }
      } finally {
         sessions.remove(session.ownerId(), session);
      }
   }

   public void shutdown() {
      sessions.values().forEach(ScanSession::cancel);
      monitors.forEach(ScanMonitor::cancel);
      monitors.clear();
      sessions.clear();
   }

   private static UUID idOf(CommandSender sender) {
      return sender instanceof Player player ? player.getUniqueId() : CONSOLE_ID;
   }
}
