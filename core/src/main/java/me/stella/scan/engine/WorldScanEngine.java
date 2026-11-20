package me.stella.scan.engine;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import me.stella.CapybaraNative;
import me.stella.scan.anvil.AnvilRegionReader;
import me.stella.scan.anvil.RawItemMatcher;
import me.stella.scan.anvil.StreamingNbtScanner;
import me.stella.scan.model.ScanProgress;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;

/**
 * High-throughput, read-only world scanner targeting 10M+ chunk worlds.
 *
 * <p>Scans both {@code region/*.mca} (block entities) and {@code entities/*.mca} (chest minecarts,
 * chest boats, chested mobs, item frames, dropped items), schedules largest region files first (LPT),
 * aggregates per worker with zero cross-thread lock contention, and updates {@link ScanProgress} via
 * O(1) atomic counters.</p>
 */
public final class WorldScanEngine {

   private WorldScanEngine() {
   }

   public static Map<String, Integer> scanWorld(CapybaraNative natives, ItemStack target, String worldName,
         ScanProgress progress) {
      return scanWorld(natives, target, worldName, 0L, progress);
   }

   public static Map<String, Integer> scanWorld(CapybaraNative natives, ItemStack target, String worldName,
         long minTimestampSec, ScanProgress progress) {
      World world = Bukkit.getWorld(worldName);
      if (world == null) {
         return new ConcurrentHashMap<>();
      }
      List<RegionWork> tasks = collectRegionTasks(natives.dimensionPath(world));
      if (tasks.isEmpty()) {
         return new ConcurrentHashMap<>();
      }

      progress.trackAtomic(true).total(tasks.size());
      RawItemMatcher matcher = RawItemMatcher.compile(target, natives);
      AtomicInteger nextIndex = new AtomicInteger();
      int workers = Math.min(ScanExecutors.workerCount(), tasks.size());
      List<Map<String, Integer>> locals = new ArrayList<>(workers);
      List<CompletableFuture<Void>> futures = new ArrayList<>(workers);

      for (int i = 0; i < workers; i++) {
         Map<String, Integer> local = new HashMap<>();
         locals.add(local);
         futures.add(ScanExecutors.runWorker(() -> runTargetWorker(
               tasks, nextIndex, matcher, minTimestampSec, progress, local)));
      }

      CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
      Map<String, Integer> merged = new HashMap<>();
      for (Map<String, Integer> local : locals) {
         local.forEach((key, count) -> merged.merge(key, count, Integer::sum));
      }
      return merged;
   }

   public static Map<String, Long> pollWorld(CapybaraNative natives, String worldName, ScanProgress progress) {
      return pollWorld(natives, worldName, 0L, progress);
   }

   public static Map<String, Long> pollWorld(CapybaraNative natives, String worldName, long minTimestampSec,
         ScanProgress progress) {
      World world = Bukkit.getWorld(worldName);
      if (world == null) {
         return new HashMap<>();
      }
      List<RegionWork> tasks = collectRegionTasks(natives.dimensionPath(world));
      if (tasks.isEmpty()) {
         return new HashMap<>();
      }

      progress.trackAtomic(true).total(tasks.size());
      AtomicInteger nextIndex = new AtomicInteger();
      int workers = Math.min(ScanExecutors.workerCount(), tasks.size());
      List<Object2LongOpenHashMap<String>> locals = new ArrayList<>(workers);
      List<CompletableFuture<Void>> futures = new ArrayList<>(workers);
      Map<String, Boolean> seenTypes = new ConcurrentHashMap<>();

      for (int i = 0; i < workers; i++) {
         Object2LongOpenHashMap<String> local = new Object2LongOpenHashMap<>(256);
         locals.add(local);
         futures.add(ScanExecutors.runWorker(() -> runPollWorker(
               tasks, nextIndex, minTimestampSec, progress, local, seenTypes)));
      }

      CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
      Map<String, Long> merged = new HashMap<>(seenTypes.size());
      for (Object2LongOpenHashMap<String> local : locals) {
         for (Object2LongMap.Entry<String> entry : local.object2LongEntrySet()) {
            merged.merge(entry.getKey(), entry.getLongValue(), Long::sum);
         }
      }
      return merged;
   }

   private static void runTargetWorker(List<RegionWork> tasks, AtomicInteger nextIndex, RawItemMatcher matcher,
         long minTimestampSec, ScanProgress progress, Map<String, Integer> local) {
      int[] containerMatched = {0};
      try (AnvilRegionReader.WorkerScratch scratch = new AnvilRegionReader.WorkerScratch()) {
         int idx;
         while (!progress.isCancelled() && (idx = nextIndex.getAndIncrement()) < tasks.size()) {
            RegionWork work = tasks.get(idx);
            try {
               AnvilRegionReader.readRegion(
                     work.file(), scratch, minTimestampSec, progress::isCancelled,
                     (cx, cz, nbtBytes, nbtLen) -> {
                        containerMatched[0] = 0;
                        StreamingNbtScanner.scanChunk(
                              nbtBytes, nbtLen, work.entityRegion(),
                              rawItem -> {
                                 if (matcher.matches(rawItem)) {
                                    containerMatched[0] += rawItem.count();
                                 }
                              },
                              (id, isBlock, bx, by, bz, ex, ey, ez) -> {
                                 int found = containerMatched[0];
                                 if (found > 0) {
                                    containerMatched[0] = 0;
                                    String key = formatContainerKey(id, isBlock, bx, by, bz, ex, ey, ez);
                                    Integer prev = local.merge(key, found, Integer::sum);
                                    progress.recordMatch(prev == found ? 1L : 0L, found);
                                 }
                              });
                     },
                     progress::recordError);
            } finally {
               progress.advance();
            }
         }
      }
   }

   private static void runPollWorker(List<RegionWork> tasks, AtomicInteger nextIndex, long minTimestampSec,
         ScanProgress progress, Object2LongOpenHashMap<String> local, Map<String, Boolean> seenTypes) {
      try (AnvilRegionReader.WorkerScratch scratch = new AnvilRegionReader.WorkerScratch()) {
         int idx;
         while (!progress.isCancelled() && (idx = nextIndex.getAndIncrement()) < tasks.size()) {
            RegionWork work = tasks.get(idx);
            try {
               AnvilRegionReader.readRegion(
                     work.file(), scratch, minTimestampSec, progress::isCancelled,
                     (cx, cz, nbtBytes, nbtLen) -> StreamingNbtScanner.scanChunk(
                           nbtBytes, nbtLen, work.entityRegion(),
                           rawItem -> {
                              String searchId = rawItem.searchItemId();
                              int count = rawItem.count();
                              local.addTo(searchId, count);
                              long newType = seenTypes.putIfAbsent(searchId, Boolean.TRUE) == null ? 1L : 0L;
                              progress.recordMatch(newType, count);
                           },
                           null),
                     progress::recordError);
            } finally {
               progress.advance();
            }
         }
      }
   }

   private static List<RegionWork> collectRegionTasks(Path dimensionPath) {
      List<RegionWork> tasks = new ArrayList<>();
      collectDir(dimensionPath.resolve("region"), false, tasks);
      collectDir(dimensionPath.resolve("entities"), true, tasks);
      // Longest-Processing-Time-First (LPT): largest regions start first to avoid single-worker tail stalls.
      tasks.sort(Comparator.comparingLong(RegionWork::sizeBytes).reversed());
      return tasks;
   }

   private static void collectDir(Path dir, boolean entityRegion, List<RegionWork> out) {
      if (!Files.isDirectory(dir)) {
         return;
      }
      try (Stream<Path> stream = Files.list(dir)) {
         stream.filter(path -> path.getFileName().toString().endsWith(".mca") && Files.isRegularFile(path))
               .forEach(path -> {
                  try {
                     long size = Files.size(path);
                     if (size >= 8192L) {
                        out.add(new RegionWork(path, entityRegion, size));
                     }
                  } catch (IOException ignored) {
                  }
               });
      } catch (IOException ignored) {
      }
   }

   private static String formatContainerKey(String id, boolean isBlock, int bx, int by, int bz,
         double ex, double ey, double ez) {
      StringBuilder key = new StringBuilder(id.length() + 24).append(id).append(';');
      if (isBlock) {
         return key.append(bx).append(';').append(by).append(';').append(bz).toString();
      }
      return key.append(ex).append(';').append(ey).append(';').append(ez).toString();
   }

   private record RegionWork(Path file, boolean entityRegion, long sizeBytes) {
   }
}
