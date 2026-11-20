package me.stella.utility;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.List;
import java.util.Map;

public final class CapybaraIO {

   private static final int BUFFER_BYTES = 64 * 1024;

   private CapybaraIO() {
   }

   public static void write(String seed, File out, List<Map.Entry<String, Integer>> data) {
      NumberFormat format = NumberFormat.getInstance();
      try (BufferedWriter writer = new BufferedWriter(new FileWriter(out), BUFFER_BYTES)) {
         writer.write("Seed:" + seed);
         writer.newLine();
         writer.newLine();
         for (Map.Entry<String, Integer> entry : data) {
            writer.write(entry.getKey() + " - " + format.format(entry.getValue()));
            writer.newLine();
         }
         writer.flush();
      } catch (IOException e) {
         e.printStackTrace();
      }
   }

   public static void write(String seed, File out, List<Map.Entry<String, Double>> data, boolean useDouble) {
      NumberFormat format = NumberFormat.getInstance();
      try (BufferedWriter writer = new BufferedWriter(new FileWriter(out), BUFFER_BYTES)) {
         writer.write("Seed:" + seed);
         writer.newLine();
         writer.newLine();
         for (Map.Entry<String, Double> entry : data) {
            writer.write(entry.getKey() + " - " + format.format(entry.getValue()));
            writer.newLine();
         }
         writer.flush();
      } catch (IOException e) {
         e.printStackTrace();
      }
   }

   public static void writePoll(String header, File out, List<Map.Entry<String, Long>> data) {
      NumberFormat format = NumberFormat.getInstance();
      try (BufferedWriter writer = new BufferedWriter(new FileWriter(out), BUFFER_BYTES)) {
         writer.write("Poll Type: " + header);
         writer.newLine();
         writer.write("Timestamp: " + System.currentTimeMillis());
         writer.newLine();
         writer.newLine();
         writer.write("Search ID -> Count");
         writer.newLine();
         writer.write("=".repeat(50));
         writer.newLine();
         for (Map.Entry<String, Long> entry : data) {
            writer.write(entry.getKey() + " -> " + format.format(entry.getValue()));
            writer.newLine();
         }
         writer.flush();
      } catch (IOException e) {
         e.printStackTrace();
      }
   }
}
