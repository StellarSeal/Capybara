package me.stella.utility;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.text.NumberFormat;
import java.util.List;
import java.util.Map;

public class CapybaraIO {
   public static void write(String seed, File out, List<Map.Entry<String, Integer>> data) {
      try {
         BufferedWriter writer = new BufferedWriter(new FileWriter(out));
         writer.write(String.format("Seed:%s\n", seed));
         writer.newLine();

         for(Map.Entry<String, Integer> d : data) {
            String var10001 = (String)d.getKey();
            writer.write(var10001 + " - " + NumberFormat.getInstance().format(d.getValue()));
            writer.newLine();
         }

         writer.flush();
         writer.close();
      } catch (Throwable t) {
         t.printStackTrace();
      }

   }

   public static void write(String seed, File out, List<Map.Entry<String, Double>> data, boolean useDouble) {
      try {
         BufferedWriter writer = new BufferedWriter(new FileWriter(out));
         writer.write(String.format("Seed:%s\n", seed));
         writer.newLine();

         for(Map.Entry<String, Double> d : data) {
            String var10001 = (String)d.getKey();
            writer.write(var10001 + " - " + NumberFormat.getInstance().format(d.getValue()));
            writer.newLine();
         }

         writer.flush();
         writer.close();
      } catch (Throwable t) {
         t.printStackTrace();
      }

   }

   public static void writePoll(String header, File out, List<Map.Entry<String, Integer>> data) {
      try {
         BufferedWriter writer = new BufferedWriter(new FileWriter(out));
         writer.write(String.format("Poll Type: %s\n", header));
         writer.write(String.format("Timestamp: %d\n", System.currentTimeMillis()));
         writer.newLine();
         writer.write("Search ID -> Count");
         writer.newLine();
         writer.write("=".repeat(50));
         writer.newLine();

         for(Map.Entry<String, Integer> d : data) {
            String var10001 = (String)d.getKey();
            writer.write(var10001 + " -> " + NumberFormat.getInstance().format(d.getValue()));
            writer.newLine();
         }

         writer.flush();
         writer.close();
      } catch (Throwable t) {
         t.printStackTrace();
      }

   }
}
