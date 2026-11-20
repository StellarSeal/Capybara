package me.stella.duplication.alert;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;

public class DiscordWebhook {

   private static final DateTimeFormatter TIMESTAMP_FORMATTER =
         DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withZone(ZoneId.of("GMT+07"));

   private final String webhookUrl;

   public DiscordWebhook(String webhookUrl) {
      this.webhookUrl = webhookUrl;
   }

   public void sendDuplicationAlert(SuspicionRecord record, @Nullable String customMessage) {
      if (webhookUrl == null || webhookUrl.isEmpty() || webhookUrl.contains("YOUR_WEBHOOK")) {
         return;
      }
      try {
         sendWebhook(buildDiscordPayload(record, customMessage));
      } catch (Exception e) {
         System.err.println("[DuplicationDetection] Failed to send Discord webhook: " + e.getMessage());
      }
   }

   private String buildDiscordPayload(SuspicionRecord record, @Nullable String customMessage) {
      StringBuilder json = new StringBuilder();
      json.append("{");
      if (customMessage != null && !customMessage.isEmpty()) {
         json.append("\"content\":").append(escapeJson(customMessage)).append(",");
      }
      json.append("\"embeds\":[{");
      json.append("\"title\":\"\ud83d\udea8 Duplication Alert\",");
      json.append("\"description\":\"Suspicious item accumulation detected!\",");
      json.append("\"color\":15158332,");
      json.append("\"fields\":[");
      json.append("{\"name\":\"Player\",\"value\":\"").append(escapeJsonValue(record.getPlayerName()))
            .append("\\n`").append(record.getPlayerUUID()).append("`\",\"inline\":false},");
      json.append("{\"name\":\"Item\",\"value\":\"**").append(escapeJsonValue(record.getItemId()))
            .append("**\",\"inline\":true},");
      json.append("{\"name\":\"Amount\",\"value\":\"**").append(record.getDetectedAmount())
            .append("** / ").append(record.getConfiguredCap()).append(" (cap)\",\"inline\":true},");
      json.append("{\"name\":\"Container\",\"value\":\"")
            .append(escapeJsonValue(record.getContainerType().toString())).append("\",\"inline\":true},");

      String locationStr = formatLocation(record.getContainerLocation());
      if (locationStr != null) {
         json.append("{\"name\":\"Location\",\"value\":\"").append(escapeJsonValue(locationStr))
               .append("\",\"inline\":false},");
      }
      json.append("{\"name\":\"Scan Depth\",\"value\":\"").append(record.getRecursionDepth())
            .append("\",\"inline\":true}");
      json.append("],");

      String timestamp = TIMESTAMP_FORMATTER.format(record.getFlaggedAt());
      json.append("\"footer\":{\"text\":\"Detected at ").append(escapeJsonValue(timestamp)).append("\"},");
      json.append("\"timestamp\":\"").append(record.getFlaggedAt()).append("\"");
      json.append("}]}");
      return json.toString();
   }

   private @Nullable String formatLocation(@Nullable Location loc) {
      if (loc == null || loc.getWorld() == null) {
         return null;
      }
      return String.format("%s (%d, %d, %d)", loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
   }

   private void sendWebhook(String jsonPayload) throws IOException {
      HttpURLConnection connection = (HttpURLConnection) URI.create(webhookUrl).toURL().openConnection();
      try {
         connection.setRequestMethod("POST");
         connection.setRequestProperty("Content-Type", "application/json");
         connection.setRequestProperty("User-Agent", "Capybara-DupeDetection/1.0");
         connection.setDoOutput(true);

         byte[] payloadBytes = jsonPayload.getBytes(StandardCharsets.UTF_8);
         try (OutputStream os = connection.getOutputStream()) {
            os.write(payloadBytes);
            os.flush();
         }

         int responseCode = connection.getResponseCode();
         if (responseCode < 200 || responseCode >= 300) {
            System.err.println("[DuplicationDetection] Discord webhook returned status: " + responseCode);
         }
      } finally {
         connection.disconnect();
      }
   }

   private String escapeJson(@Nullable String value) {
      return value == null ? "null" : "\"" + escapeJsonValue(value) + "\"";
   }

   private String escapeJsonValue(@Nullable String value) {
      if (value == null) {
         return "";
      }
      return value.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
   }
}
