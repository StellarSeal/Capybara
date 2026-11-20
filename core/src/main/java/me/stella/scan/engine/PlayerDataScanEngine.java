package me.stella.scan.engine;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import me.stella.CapybaraNative;
import me.stella.scan.anvil.AnvilRegionReader;
import me.stella.scan.anvil.RawItemMatcher;
import me.stella.scan.anvil.StreamingNbtScanner;
import me.stella.scan.model.ScanProgress;
import org.bukkit.inventory.ItemStack;

/**
 * Streaming, isolated-pool scanner for {@code playerdata/*.dat} files (inventories and ender chests).
 *
 * <p>Replaces {@code ForkJoinPool.commonPool()} parallel streams with {@link ScanExecutors} workers
 * and zero-DOM binary NBT parsing.</p>
 */
public final class PlayerDataScanEngine {

   private PlayerDataScanEngine() {
   }

   public static Map<String, Integer> scan(CapybaraNative natives, ItemStack target, boolean enderChest,
         ScanProgress progress) {
      File[] files = CapybaraNative.playerDataFiles();
      if (files.length == 0) {
         return new HashMap<>();
      }

      progress.trackAtomic(true).total(files.length);
      RawItemMatcher matcher = RawItemMatcher.compile(target, natives);
      AtomicInteger nextIndex = new AtomicInteger();
      int workers = Math.min(ScanExecutors.workerCount(), files.length);
      List<Map<String, Integer>> locals = new ArrayList<>(workers);
      List<CompletableFuture<Void>> futures = new ArrayList<>(workers);

      for (int i = 0; i < workers; i++) {
         Map<String, Integer> local = new HashMap<>();
         locals.add(local);
         futures.add(ScanExecutors.runWorker(() -> runTargetWorker(files, nextIndex, enderChest, matcher, progress, local)));
      }

      CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
      Map<String, Integer> merged = new HashMap<>();
      for (Map<String, Integer> local : locals) {
         local.forEach((name, count) -> merged.merge(name, count, Integer::sum));
      }
      return merged;
   }

   public static Map<String, Long> poll(boolean enderChest, ScanProgress progress) {
      File[] files = CapybaraNative.playerDataFiles();
      if (files.length == 0) {
         return new HashMap<>();
      }

      progress.trackAtomic(true).total(files.length);
      AtomicInteger nextIndex = new AtomicInteger();
      int workers = Math.min(ScanExecutors.workerCount(), files.length);
      List<Object2LongOpenHashMap<String>> locals = new ArrayList<>(workers);
      List<CompletableFuture<Void>> futures = new ArrayList<>(workers);
      Map<String, Boolean> seenTypes = new ConcurrentHashMap<>();

      for (int i = 0; i < workers; i++) {
         Object2LongOpenHashMap<String> local = new Object2LongOpenHashMap<>(256);
         locals.add(local);
         futures.add(ScanExecutors.runWorker(() -> runPollWorker(files, nextIndex, enderChest, progress, local, seenTypes)));
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

   private static void runTargetWorker(File[] files, AtomicInteger nextIndex, boolean enderChest,
         RawItemMatcher matcher, ScanProgress progress, Map<String, Integer> local) {
      int[] matched = {0};
      try (AnvilRegionReader.WorkerScratch scratch = new AnvilRegionReader.WorkerScratch()) {
         int idx;
         while (!progress.isCancelled() && (idx = nextIndex.getAndIncrement()) < files.length) {
            File file = files[idx];
            try {
               int nbtLen = decompressPlayerFile(file, scratch);
               matched[0] = 0;
               String lastKnownName = StreamingNbtScanner.scanPlayerData(scratch.nbtBuffer(), nbtLen, enderChest, rawItem -> {
                  if (matcher.matches(rawItem)) {
                     matched[0] += rawItem.count();
                  }
               });
               if (matched[0] > 0) {
                  String key = (lastKnownName != null && !lastKnownName.isEmpty())
                        ? lastKnownName
                        : file.getName().replace(".dat", "");
                  Integer prev = local.merge(key, matched[0], Integer::sum);
                  progress.recordMatch(prev == matched[0] ? 1L : 0L, matched[0]);
               }
            } catch (Exception e) {
               progress.recordError();
            } finally {
               progress.advance();
            }
         }
      }
   }

   private static void runPollWorker(File[] files, AtomicInteger nextIndex, boolean enderChest,
         ScanProgress progress, Object2LongOpenHashMap<String> local, Map<String, Boolean> seenTypes) {
      try (AnvilRegionReader.WorkerScratch scratch = new AnvilRegionReader.WorkerScratch()) {
         int idx;
         while (!progress.isCancelled() && (idx = nextIndex.getAndIncrement()) < files.length) {
            File file = files[idx];
            try {
               int nbtLen = decompressPlayerFile(file, scratch);
               StreamingNbtScanner.scanPlayerData(scratch.nbtBuffer(), nbtLen, enderChest, rawItem -> {
                  String searchId = rawItem.searchItemId();
                  int count = rawItem.count();
                  local.addTo(searchId, count);
                  long newType = seenTypes.putIfAbsent(searchId, Boolean.TRUE) == null ? 1L : 0L;
                  progress.recordMatch(newType, count);
               });
            } catch (Exception e) {
               progress.recordError();
            } finally {
               progress.advance();
            }
         }
      }
   }

   private static int decompressPlayerFile(File file, AnvilRegionReader.WorkerScratch scratch) throws IOException {
      byte[] raw = Files.readAllBytes(file.toPath());
      if (raw.length >= 2 && (raw[0] & 0xFF) == 0x1F && (raw[1] & 0xFF) == 0x8B) {
         return AnvilRegionReader.decompressGzip(raw, 0, raw.length, scratch);
      }
      byte[] buf = scratch.ensureNbtBuffer(raw.length);
      System.arraycopy(raw, 0, buf, 0, raw.length);
      return raw.length;
   }
}
