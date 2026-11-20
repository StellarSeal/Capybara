package me.stella.scan.anvil;

import org.jetbrains.annotations.Nullable;

/**
 * Lightweight item projection extracted directly from binary NBT without NMS/Bukkit decoding.
 *
 * <p>{@code rawData}/{@code rawStart}/{@code rawEnd} slice the decompressed chunk buffer so exact
 * NMS fallback deserialization only allocates when an item's Minecraft ID actually matches a
 * custom non-basic vanilla target.</p>
 */
public record RawItem(
      String id,
      int count,
      @Nullable String mmoType,
      @Nullable String mmoId,
      @Nullable String crateKeyId,
      @Nullable String voucherId,
      boolean hasExtraMeta,
      byte[] rawData,
      int rawStart,
      int rawEnd) {

   public @Nullable String pluginItemId() {
      if (mmoType != null && !mmoType.isEmpty() && mmoId != null && !mmoId.isEmpty()) {
         return "MMOITEMS:" + mmoType + ":" + mmoId;
      }
      if (crateKeyId != null && !crateKeyId.isEmpty()) {
         return "EXCELLENTCRATES:" + crateKeyId;
      }
      if (voucherId != null && !voucherId.isEmpty()) {
         return "CRAZYVOUCHERS:" + voucherId;
      }
      return null;
   }

   public String searchItemId() {
      String pluginId = pluginItemId();
      if (pluginId != null) {
         return pluginId;
      }
      int colon = id.indexOf(':');
      String key = colon >= 0 ? id.substring(colon + 1) : id;
      return key.toUpperCase(java.util.Locale.ROOT);
   }

   /** Wraps the raw compound payload with a 3-byte empty root TAG_Compound header for {@code NbtIo.read}. */
   public byte[] toNamedRootCompound() {
      int length = rawEnd - rawStart;
      byte[] packet = new byte[length + 3];
      packet[0] = 10;
      packet[1] = 0;
      packet[2] = 0;
      System.arraycopy(rawData, rawStart, packet, 3, length);
      return packet;
   }
}
