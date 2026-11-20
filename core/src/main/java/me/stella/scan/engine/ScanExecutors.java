package me.stella.scan.engine;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Isolated thread pools separating scan coordination, heavy I/O workers, and real-time duplication detection.
 *
 * <p>Keeping coordinators off the worker pool prevents {@code CompletableFuture.join()} deadlocks, and
 * lowering worker thread priority preserves server tick / Folia region scheduling during large scans.</p>
 */
public final class ScanExecutors {

   private static final int WORKER_THREADS = Math.max(2, Math.min(8, Runtime.getRuntime().availableProcessors() / 2));

   private static final ExecutorService COORDINATOR = Executors.newCachedThreadPool(
         daemonFactory("Capybara-Coordinator-%d", Thread.NORM_PRIORITY));

   private static final ThreadPoolExecutor WORKERS = new ThreadPoolExecutor(
         WORKER_THREADS, WORKER_THREADS, 60L, TimeUnit.SECONDS,
         new LinkedBlockingQueue<>(),
         daemonFactory("Capybara-Worker-%d", Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 2)));

   private static final ExecutorService DUPLICATION_IO = Executors.newSingleThreadExecutor(
         daemonFactory("Capybara-DupeIO", Thread.NORM_PRIORITY - 1));

   private static volatile ExecutorService duplicationWorkers = createDuplicationPool(2);

   static {
      WORKERS.allowCoreThreadTimeOut(true);
   }

   private ScanExecutors() {
   }

   public static int workerCount() {
      return WORKER_THREADS;
   }

   public static void runCoordinator(Runnable task) {
      COORDINATOR.execute(task);
   }

   public static CompletableFuture<Void> runWorker(Runnable task) {
      return CompletableFuture.runAsync(task, WORKERS);
   }

   public static void runDuplication(Runnable task) {
      duplicationWorkers.execute(task);
   }

   public static void runDuplicationIo(Runnable task) {
      DUPLICATION_IO.execute(task);
   }

   public static synchronized void configureDuplicationWorkers(int workers) {
      int clamped = Math.max(1, Math.min(16, workers));
      ExecutorService previous = duplicationWorkers;
      duplicationWorkers = createDuplicationPool(clamped);
      if (previous != null) {
         previous.shutdown();
      }
   }

   public static void shutdown() {
      COORDINATOR.shutdownNow();
      WORKERS.shutdownNow();
      duplicationWorkers.shutdownNow();
      DUPLICATION_IO.shutdown();
      try {
         DUPLICATION_IO.awaitTermination(2L, TimeUnit.SECONDS);
      } catch (InterruptedException ignored) {
         Thread.currentThread().interrupt();
      }
   }

   private static ExecutorService createDuplicationPool(int threads) {
      ThreadPoolExecutor pool = new ThreadPoolExecutor(
            threads, threads, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(16384),
            daemonFactory("Capybara-Dupe-%d", Thread.NORM_PRIORITY - 1),
            new ThreadPoolExecutor.DiscardOldestPolicy());
      pool.allowCoreThreadTimeOut(true);
      return pool;
   }

   private static ThreadFactory daemonFactory(String pattern, int priority) {
      return new ThreadFactoryBuilder()
            .setNameFormat(pattern)
            .setPriority(priority)
            .setDaemon(true)
            .build();
   }
}
