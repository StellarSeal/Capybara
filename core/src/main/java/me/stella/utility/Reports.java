package me.stella.utility;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import me.stella.Capybara;

/** Writes scan and poll reports in the established formats ({@code scans/<nanos>_<kind>.txt}, {@code polls/...}). */
public final class Reports {

   private Reports() {
   }

   public static File scan(String kind, String seed, Map<String, Integer> data) {
      return scan(kind, seed, data.entrySet());
   }

   public static File scan(String kind, String seed, Collection<Map.Entry<String, Integer>> rows) {
      File file = file("scans", kind);
      CapybaraIO.write(seed, file, rows.stream().sorted(Map.Entry.comparingByValue()).toList());
      return file;
   }

   public static File scanByKey(String kind, String seed, Map<String, Integer> data) {
      File file = file("scans", kind);
      CapybaraIO.write(seed, file, data.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList());
      return file;
   }

   public static File distances(String kind, String seed, Map<String, Double> data) {
      File file = file("scans", kind);
      CapybaraIO.write(seed, file, data.entrySet().stream().sorted(Map.Entry.comparingByValue()).toList(), true);
      return file;
   }

   public static File poll(String header, String kind, Map<String, Long> data) {
      File file = file("polls", "poll_" + kind);
      CapybaraIO.writePoll(header, file, data.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())).toList());
      return file;
   }

   public static List<Map.Entry<String, Integer>> flatten(Map<String, Map<String, Integer>> pools) {
      List<Map.Entry<String, Integer>> rows = new ArrayList<>();
      pools.values().forEach(pool -> rows.addAll(pool.entrySet()));
      return rows;
   }

   private static File file(String folder, String kind) {
      File directory = new File(Capybara.get().getDataFolder(), folder);
      if (!directory.exists()) {
         directory.mkdirs();
      }
      return new File(directory, System.nanoTime() + "_" + kind + ".txt");
   }
}
