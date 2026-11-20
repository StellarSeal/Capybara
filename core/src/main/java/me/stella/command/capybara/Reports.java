package me.stella.command.capybara;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import me.stella.Capybara;
import me.stella.utility.CapybaraIO;

/** Writes scan / poll reports in the established formats ({@code scans/<nanos>_<kind>.txt}, {@code polls/...}). */
final class Reports {

    private Reports() {
    }

    /** Scan report, rows sorted ascending by count. */
    static File scan(String kind, String seed, Map<String, Integer> data) {
        return scan(kind, seed, data.entrySet());
    }

    static File scan(String kind, String seed, Collection<Map.Entry<String, Integer>> rows) {
        File file = file("scans", kind);
        CapybaraIO.write(seed, file, rows.stream().sorted(Map.Entry.comparingByValue()).toList());
        return file;
    }

    /** Scan report, rows sorted by key. */
    static File scanByKey(String kind, String seed, Map<String, Integer> data) {
        File file = file("scans", kind);
        CapybaraIO.write(seed, file, data.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList());
        return file;
    }

    /** Distance report (home scans), rows sorted ascending by distance. */
    static File distances(String kind, String seed, Map<String, Double> data) {
        File file = file("scans", kind);
        CapybaraIO.write(seed, file, data.entrySet().stream().sorted(Map.Entry.comparingByValue()).toList(), true);
        return file;
    }

    /** Poll report, rows sorted descending by count. */
    static File poll(String header, String kind, Map<String, Integer> data) {
        File file = file("polls", "poll_" + kind);
        CapybaraIO.writePoll(header, file, data.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())).toList());
        return file;
    }

    /** Flattens per-player vault pools into one row list. */
    static List<Map.Entry<String, Integer>> flatten(Map<String, Map<String, Integer>> pools) {
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
