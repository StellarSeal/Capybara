package me.stella.duplication;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;

public class DiscordWebhook {
   private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withZone(ZoneId.of("GMT+07"));
   private final String webhookUrl;

   public DiscordWebhook(String webhookUrl) {
      this.webhookUrl = webhookUrl;
   }

   public void sendDuplicationAlert(SuspicionRecord record, @Nullable String customMessage) {
      if (this.webhookUrl != null && !this.webhookUrl.isEmpty() && !this.webhookUrl.contains("YOUR_WEBHOOK")) {
         try {
            String jsonPayload = this.buildDiscordPayload(record, customMessage);
            this.sendWebhook(jsonPayload);
         } catch (Exception e) {
            System.err.println("[DuplicationDetection] Failed to send Discord webhook: " + e.getMessage());
         }

      }
   }

   private String buildDiscordPayload(SuspicionRecord record, @Nullable String customMessage) {
      StringBuilder json = new StringBuilder();
      json.append("{");
      if (customMessage != null && !customMessage.isEmpty()) {
         json.append("\"content\":").append(this.escapeJson(customMessage)).append(",");
      }

      json.append("\"embeds\":[{");
      json.append("\"title\":\"\ud83d\udea8 Duplication Alert\",");
      json.append("\"description\":\"Suspicious item accumulation detected!\",");
      json.append("\"color\":15158332,");
      json.append("\"fields\":[");
      json.append("{\"name\":\"Player\",\"value\":\"").append(this.escapeJsonValue(record.getPlayerName())).append("\\n`").append(record.getPlayerUUID().toString()).append("`\",\"inline\":false},");
      json.append("{\"name\":\"Item\",\"value\":\"**").append(this.escapeJsonValue(record.getItemId())).append("**\",\"inline\":true},");
      json.append("{\"name\":\"Amount\",\"value\":\"**").append(record.getDetectedAmount()).append("** / ").append(record.getConfiguredCap()).append(" (cap)\",\"inline\":true},");
      json.append("{\"name\":\"Container\",\"value\":\"").append(this.escapeJsonValue(record.getContainerType().toString())).append("\",\"inline\":true},");
      Location loc = record.getContainerLocation();
      String locationStr = this.formatLocation(loc);
      if (locationStr != null) {
         json.append("{\"name\":\"Location\",\"value\":\"").append(this.escapeJsonValue(locationStr)).append("\",\"inline\":false},");
      }

      json.append("{\"name\":\"Scan Depth\",\"value\":\"").append(record.getRecursionDepth()).append("\",\"inline\":true}");
      json.append("],");
      String timestamp = TIMESTAMP_FORMATTER.format(record.getFlaggedAt());
      json.append("\"footer\":{\"text\":\"Detected at ").append(this.escapeJsonValue(timestamp)).append("\"},");
      json.append("\"timestamp\":\"").append(record.getFlaggedAt().toString()).append("\"");
      json.append("}]}");
      return json.toString();
   }

   private @Nullable String formatLocation(@Nullable Location loc) {
      return loc != null && loc.getWorld() != null ? String.format("%s (%d, %d, %d)", loc.getWorld().getName(), (int)loc.getX(), (int)loc.getY(), (int)loc.getZ()) : null;
   }

   private void sendWebhook(String jsonPayload) throws IOException {
      URL url = new URL(this.webhookUrl);
      HttpURLConnection connection = (HttpURLConnection)url.openConnection();

      try {
         connection.setRequestMethod("POST");
         connection.setRequestProperty("Content-Type", "application/json");
         connection.setRequestProperty("User-Agent", "Capybara-DupeDetection/1.0");
         connection.setDoOutput(true);
         byte[] payloadBytes = jsonPayload.getBytes(StandardCharsets.UTF_8);
         OutputStream os = connection.getOutputStream();

         try {
            os.write(payloadBytes);
            os.flush();
         } catch (Throwable var13) {
            if (os != null) {
               try {
                  os.close();
               } catch (Throwable var12) {
                  var13.addSuppressed(var12);
               }
            }

            throw var13;
         }

         if (os != null) {
            os.close();
         }

         int responseCode = connection.getResponseCode();
         if (responseCode < 200 || responseCode >= 300) {
            System.err.println("[DuplicationDetection] Discord webhook returned status: " + responseCode);
         }
      } finally {
         connection.disconnect();
      }

   }

   private String escapeJson(String value) {
      if (value == null) {
         return "null";
      } else {
         String var10000 = this.escapeJsonValue(value);
         return "\"" + var10000 + "\"";
      }
   }

   private String escapeJsonValue(String value) {
      return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
   }
}
