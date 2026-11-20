package me.stella.scan.anvil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;

/**
 * Zero-DOM streaming binary NBT scanner over a decompressed byte array.
 *
 * <p>Skips terrain {@code sections}, {@code Heightmaps}, {@code structures}, and lighting arrays
 * via direct byte-offset arithmetic without allocating any {@code Tag} objects, halting immediately
 * after {@code block_entities} (or {@code Entities}) completes. Supports both 1.20.5+ data
 * components and legacy pre-1.20.5 {@code tag} item layouts, including recursive shulker boxes
 * and bundles.</p>
 */
public final class StreamingNbtScanner {

   private static final int TAG_END = 0;
   private static final int TAG_BYTE = 1;
   private static final int TAG_SHORT = 2;
   private static final int TAG_INT = 3;
   private static final int TAG_LONG = 4;
   private static final int TAG_FLOAT = 5;
   private static final int TAG_DOUBLE = 6;
   private static final int TAG_BYTE_ARRAY = 7;
   private static final int TAG_STRING = 8;
   private static final int TAG_LIST = 9;
   private static final int TAG_COMPOUND = 10;
   private static final int TAG_INT_ARRAY = 11;
   private static final int TAG_LONG_ARRAY = 12;

   private static final int MAX_NESTING_DEPTH = 12;

   private static final byte[] KEY_BLOCK_ENTITIES = ascii("block_entities");
   private static final byte[] KEY_ENTITIES = ascii("Entities");
   private static final byte[] KEY_INVENTORY = ascii("Inventory");
   private static final byte[] KEY_ENDER_ITEMS = ascii("EnderItems");
   private static final byte[] KEY_BUKKIT = ascii("bukkit");
   private static final byte[] KEY_LAST_KNOWN_NAME = ascii("lastKnownName");

   private static final byte[] KEY_ID = ascii("id");
   private static final byte[] KEY_X = ascii("x");
   private static final byte[] KEY_Y = ascii("y");
   private static final byte[] KEY_Z = ascii("z");
   private static final byte[] KEY_POS = ascii("Pos");
   private static final byte[] KEY_ITEMS = ascii("Items");
   private static final byte[] KEY_ITEM_LOWER = ascii("item");
   private static final byte[] KEY_ITEM_UPPER = ascii("Item");
   private static final byte[] KEY_RECORD_ITEM = ascii("RecordItem");
   private static final byte[] KEY_BOOK = ascii("Book");
   private static final byte[] KEY_ARMOR_ITEMS = ascii("ArmorItems");
   private static final byte[] KEY_HAND_ITEMS = ascii("HandItems");
   private static final byte[] KEY_EQUIPMENT = ascii("equipment");

   private static final byte[] KEY_COUNT_LOWER = ascii("count");
   private static final byte[] KEY_COUNT_UPPER = ascii("Count");
   private static final byte[] KEY_COMPONENTS = ascii("components");
   private static final byte[] KEY_TAG = ascii("tag");
   private static final byte[] KEY_CUSTOM_DATA = ascii("minecraft:custom_data");
   private static final byte[] KEY_CONTAINER = ascii("minecraft:container");
   private static final byte[] KEY_BUNDLE_CONTENTS = ascii("minecraft:bundle_contents");
   private static final byte[] KEY_BLOCK_ENTITY_TAG = ascii("BlockEntityTag");
   private static final byte[] KEY_PUBLIC_BUKKIT_VALUES = ascii("PublicBukkitValues");
   private static final byte[] KEY_MMO_TYPE = ascii("MMOITEMS_ITEM_TYPE");
   private static final byte[] KEY_MMO_ID = ascii("MMOITEMS_ITEM_ID");
   private static final byte[] KEY_CRATE_KEY = ascii("excellentcrates:crate_key.id");
   private static final byte[] KEY_VOUCHER = ascii("crazyvouchers:crazyvouchers_voucher_item");

   private StreamingNbtScanner() {
   }

   @FunctionalInterface
   public interface ContainerVisitor {

      void onContainer(String containerKey, int matchedCount);
   }

   /** Scans a decompressed chunk or entity-region NBT payload, delivering every container's items. */
   public static void scanChunk(byte[] data, int length, boolean entityRegion,
         Consumer<RawItem> itemSink, @Nullable ContainerSink containerSink) throws IOException {
      Cursor cursor = new Cursor(data, 0, length);
      if (!cursor.enterRootCompound()) {
         return;
      }

      byte[] targetRootKey = entityRegion ? KEY_ENTITIES : KEY_BLOCK_ENTITIES;
      while (cursor.hasRemaining()) {
         int type = cursor.readUnsignedByte();
         if (type == TAG_END) {
            return;
         }
         int nameLen = cursor.readUnsignedShort();
         int nameStart = cursor.pos;
         cursor.skip(nameLen);

         if (type == TAG_LIST && equalsAscii(data, nameStart, nameLen, targetRootKey)) {
            int elemType = cursor.readUnsignedByte();
            int count = cursor.readInt();
            if (elemType == TAG_COMPOUND && count > 0) {
               for (int i = 0; i < count; i++) {
                  parseContainerCompound(cursor, !entityRegion, itemSink, containerSink);
               }
            }
            // Halt immediately once the target root list finishes; skip remaining chunk bytes.
            return;
         }
         skipPayload(cursor, type);
      }
   }

   /** Scans a decompressed playerdata {@code .dat} NBT payload for Inventory or EnderItems. */
   public static @Nullable String scanPlayerData(byte[] data, int length, boolean enderChest,
         Consumer<RawItem> itemSink) throws IOException {
      Cursor cursor = new Cursor(data, 0, length);
      if (!cursor.enterRootCompound()) {
         return null;
      }

      byte[] targetListKey = enderChest ? KEY_ENDER_ITEMS : KEY_INVENTORY;
      String lastKnownName = null;
      boolean foundList = false;

      while (cursor.hasRemaining()) {
         int type = cursor.readUnsignedByte();
         if (type == TAG_END) {
            break;
         }
         int nameLen = cursor.readUnsignedShort();
         int nameStart = cursor.pos;
         cursor.skip(nameLen);

         if (type == TAG_LIST && equalsAscii(data, nameStart, nameLen, targetListKey)) {
            parseItemList(cursor, itemSink, 0);
            foundList = true;
            if (lastKnownName != null) {
               return lastKnownName;
            }
         } else if (type == TAG_COMPOUND && equalsAscii(data, nameStart, nameLen, KEY_BUKKIT)) {
            lastKnownName = readBukkitLastKnownName(cursor);
            if (foundList) {
               return lastKnownName;
            }
         } else {
            skipPayload(cursor, type);
         }
      }
      return lastKnownName;
   }

   @FunctionalInterface
   public interface ContainerSink {

      void finishContainer(String id, boolean isBlock, int bx, int by, int bz, double ex, double ey, double ez);
   }

   private static @Nullable String readBukkitLastKnownName(Cursor cursor) throws IOException {
      String found = null;
      while (cursor.hasRemaining()) {
         int type = cursor.readUnsignedByte();
         if (type == TAG_END) {
            return found;
         }
         int nameLen = cursor.readUnsignedShort();
         int nameStart = cursor.pos;
         cursor.skip(nameLen);

         if (type == TAG_STRING && equalsAscii(cursor.data, nameStart, nameLen, KEY_LAST_KNOWN_NAME)) {
            found = cursor.readUtf();
         } else {
            skipPayload(cursor, type);
         }
      }
      return found;
   }

   private static void parseContainerCompound(Cursor cursor, boolean isBlock,
         Consumer<RawItem> itemSink, @Nullable ContainerSink containerSink) throws IOException {
      String id = "";
      int bx = 0;
      int by = 0;
      int bz = 0;
      double ex = 0.0;
      double ey = 0.0;
      double ez = 0.0;

      while (cursor.hasRemaining()) {
         int type = cursor.readUnsignedByte();
         if (type == TAG_END) {
            break;
         }
         int nameLen = cursor.readUnsignedShort();
         int nameStart = cursor.pos;
         cursor.skip(nameLen);

         if (type == TAG_STRING && equalsAscii(cursor.data, nameStart, nameLen, KEY_ID)) {
            id = cursor.readUtf();
         } else if (isBlock && type == TAG_INT && equalsAscii(cursor.data, nameStart, nameLen, KEY_X)) {
            bx = cursor.readInt();
         } else if (isBlock && type == TAG_INT && equalsAscii(cursor.data, nameStart, nameLen, KEY_Y)) {
            by = cursor.readInt();
         } else if (isBlock && type == TAG_INT && equalsAscii(cursor.data, nameStart, nameLen, KEY_Z)) {
            bz = cursor.readInt();
         } else if (!isBlock && type == TAG_LIST && equalsAscii(cursor.data, nameStart, nameLen, KEY_POS)) {
            int elemType = cursor.readUnsignedByte();
            int len = cursor.readInt();
            if (elemType == TAG_DOUBLE && len >= 3) {
               ex = cursor.readDouble();
               ey = cursor.readDouble();
               ez = cursor.readDouble();
               cursor.skip((len - 3) * 8);
            } else {
               skipListElements(cursor, elemType, len);
            }
         } else if (type == TAG_LIST && (equalsAscii(cursor.data, nameStart, nameLen, KEY_ITEMS)
               || (!isBlock && (equalsAscii(cursor.data, nameStart, nameLen, KEY_ARMOR_ITEMS)
               || equalsAscii(cursor.data, nameStart, nameLen, KEY_HAND_ITEMS))))) {
            parseItemList(cursor, itemSink, 0);
         } else if (type == TAG_COMPOUND && (equalsAscii(cursor.data, nameStart, nameLen, KEY_ITEM_LOWER)
               || equalsAscii(cursor.data, nameStart, nameLen, KEY_ITEM_UPPER)
               || equalsAscii(cursor.data, nameStart, nameLen, KEY_RECORD_ITEM)
               || equalsAscii(cursor.data, nameStart, nameLen, KEY_BOOK))) {
            parseItemCompound(cursor, itemSink, 0);
         } else if (!isBlock && type == TAG_COMPOUND && equalsAscii(cursor.data, nameStart, nameLen, KEY_EQUIPMENT)) {
            parseEquipmentCompound(cursor, itemSink);
         } else {
            skipPayload(cursor, type);
         }
      }

      if (containerSink != null) {
         containerSink.finishContainer(id, isBlock, bx, by, bz, ex, ey, ez);
      }
   }

   private static void parseEquipmentCompound(Cursor cursor, Consumer<RawItem> itemSink) throws IOException {
      while (cursor.hasRemaining()) {
         int type = cursor.readUnsignedByte();
         if (type == TAG_END) {
            return;
         }
         int nameLen = cursor.readUnsignedShort();
         cursor.skip(nameLen);
         if (type == TAG_COMPOUND) {
            parseItemCompound(cursor, itemSink, 0);
         } else {
            skipPayload(cursor, type);
         }
      }
   }

   private static void parseItemList(Cursor cursor, Consumer<RawItem> itemSink, int depth) throws IOException {
      int elemType = cursor.readUnsignedByte();
      int len = cursor.readInt();
      if (elemType != TAG_COMPOUND || len <= 0) {
         skipListElements(cursor, elemType, len);
         return;
      }
      for (int i = 0; i < len; i++) {
         parseItemCompound(cursor, itemSink, depth);
      }
   }

   private static void parseItemCompound(Cursor cursor, Consumer<RawItem> itemSink, int depth) throws IOException {
      if (depth > MAX_NESTING_DEPTH) {
         skipCompound(cursor);
         return;
      }

      int rawStart = cursor.pos;
      String id = null;
      int count = 1;
      boolean hasExtraMeta = false;
      CustomDataHolder customData = null;

      while (cursor.hasRemaining()) {
         int type = cursor.readUnsignedByte();
         if (type == TAG_END) {
            break;
         }
         int nameLen = cursor.readUnsignedShort();
         int nameStart = cursor.pos;
         cursor.skip(nameLen);

         if (type == TAG_STRING && equalsAscii(cursor.data, nameStart, nameLen, KEY_ID)) {
            id = normalizeItemId(cursor.readUtf());
         } else if (equalsAscii(cursor.data, nameStart, nameLen, KEY_COUNT_LOWER)
               || equalsAscii(cursor.data, nameStart, nameLen, KEY_COUNT_UPPER)) {
            if (type == TAG_INT) {
               count = cursor.readInt();
            } else if (type == TAG_BYTE) {
               count = cursor.readByte() & 0xFF;
            } else {
               skipPayload(cursor, type);
            }
         } else if (type == TAG_COMPOUND && equalsAscii(cursor.data, nameStart, nameLen, KEY_COMPONENTS)) {
            if (customData == null) {
               customData = new CustomDataHolder();
            }
            if (parseComponentsCompound(cursor, customData, itemSink, depth)) {
               hasExtraMeta = true;
            }
         } else if (type == TAG_COMPOUND && equalsAscii(cursor.data, nameStart, nameLen, KEY_TAG)) {
            if (customData == null) {
               customData = new CustomDataHolder();
            }
            if (parseLegacyTagCompound(cursor, customData, itemSink, depth)) {
               hasExtraMeta = true;
            }
         } else {
            skipPayload(cursor, type);
         }
      }

      int rawEnd = cursor.pos;
      if (id != null && !id.isEmpty() && !id.equals("minecraft:air") && count > 0) {
         itemSink.accept(new RawItem(
               id,
               count,
               customData != null ? customData.mmoType : null,
               customData != null ? customData.mmoId : null,
               customData != null ? customData.crateKeyId : null,
               customData != null ? customData.voucherId : null,
               hasExtraMeta,
               cursor.data,
               rawStart,
               rawEnd));
      }
   }

   /** Returns true if any non-container component is present on the item. */
   private static boolean parseComponentsCompound(Cursor cursor, CustomDataHolder holder,
         Consumer<RawItem> itemSink, int depth) throws IOException {
      boolean hasExtra = false;
      while (cursor.hasRemaining()) {
         int type = cursor.readUnsignedByte();
         if (type == TAG_END) {
            return hasExtra;
         }
         int nameLen = cursor.readUnsignedShort();
         int nameStart = cursor.pos;
         cursor.skip(nameLen);

         if (type == TAG_COMPOUND && equalsAscii(cursor.data, nameStart, nameLen, KEY_CUSTOM_DATA)) {
            hasExtra = true;
            parseCustomDataCompound(cursor, holder);
         } else if (type == TAG_LIST && equalsAscii(cursor.data, nameStart, nameLen, KEY_CONTAINER)) {
            parseContainerComponentList(cursor, itemSink, depth + 1);
         } else if (type == TAG_LIST && equalsAscii(cursor.data, nameStart, nameLen, KEY_BUNDLE_CONTENTS)) {
            parseItemList(cursor, itemSink, depth + 1);
         } else {
            hasExtra = true;
            skipPayload(cursor, type);
         }
      }
      return hasExtra;
   }

   /** Parses 1.20.5+ {@code minecraft:container} list of {@code {slot: int, item: Compound}}. */
   private static void parseContainerComponentList(Cursor cursor, Consumer<RawItem> itemSink, int depth) throws IOException {
      int elemType = cursor.readUnsignedByte();
      int len = cursor.readInt();
      if (elemType != TAG_COMPOUND || len <= 0) {
         skipListElements(cursor, elemType, len);
         return;
      }
      for (int i = 0; i < len; i++) {
         while (cursor.hasRemaining()) {
            int type = cursor.readUnsignedByte();
            if (type == TAG_END) {
               break;
            }
            int nameLen = cursor.readUnsignedShort();
            int nameStart = cursor.pos;
            cursor.skip(nameLen);
            if (type == TAG_COMPOUND && equalsAscii(cursor.data, nameStart, nameLen, KEY_ITEM_LOWER)) {
               parseItemCompound(cursor, itemSink, depth);
            } else {
               skipPayload(cursor, type);
            }
         }
      }
   }

   /** Parses pre-1.20.5 legacy {@code tag} compound (MMOItems, PDC, BlockEntityTag shulkers, bundles). */
   private static boolean parseLegacyTagCompound(Cursor cursor, CustomDataHolder holder,
         Consumer<RawItem> itemSink, int depth) throws IOException {
      boolean hasExtra = false;
      while (cursor.hasRemaining()) {
         int type = cursor.readUnsignedByte();
         if (type == TAG_END) {
            return hasExtra;
         }
         int nameLen = cursor.readUnsignedShort();
         int nameStart = cursor.pos;
         cursor.skip(nameLen);

         if (type == TAG_STRING && equalsAscii(cursor.data, nameStart, nameLen, KEY_MMO_TYPE)) {
            holder.mmoType = cursor.readUtf();
            hasExtra = true;
         } else if (type == TAG_STRING && equalsAscii(cursor.data, nameStart, nameLen, KEY_MMO_ID)) {
            holder.mmoId = cursor.readUtf();
            hasExtra = true;
         } else if (type == TAG_COMPOUND && equalsAscii(cursor.data, nameStart, nameLen, KEY_PUBLIC_BUKKIT_VALUES)) {
            parsePublicBukkitValues(cursor, holder);
            hasExtra = true;
         } else if (type == TAG_COMPOUND && equalsAscii(cursor.data, nameStart, nameLen, KEY_BLOCK_ENTITY_TAG)) {
            parseLegacyBlockEntityTag(cursor, itemSink, depth + 1);
         } else if (type == TAG_LIST && equalsAscii(cursor.data, nameStart, nameLen, KEY_ITEMS)) {
            parseItemList(cursor, itemSink, depth + 1);
         } else {
            hasExtra = true;
            skipPayload(cursor, type);
         }
      }
      return hasExtra;
   }

   private static void parseLegacyBlockEntityTag(Cursor cursor, Consumer<RawItem> itemSink, int depth) throws IOException {
      while (cursor.hasRemaining()) {
         int type = cursor.readUnsignedByte();
         if (type == TAG_END) {
            return;
         }
         int nameLen = cursor.readUnsignedShort();
         int nameStart = cursor.pos;
         cursor.skip(nameLen);
         if (type == TAG_LIST && equalsAscii(cursor.data, nameStart, nameLen, KEY_ITEMS)) {
            parseItemList(cursor, itemSink, depth);
         } else {
            skipPayload(cursor, type);
         }
      }
   }

   private static void parseCustomDataCompound(Cursor cursor, CustomDataHolder holder) throws IOException {
      while (cursor.hasRemaining()) {
         int type = cursor.readUnsignedByte();
         if (type == TAG_END) {
            return;
         }
         int nameLen = cursor.readUnsignedShort();
         int nameStart = cursor.pos;
         cursor.skip(nameLen);

         if (type == TAG_STRING && equalsAscii(cursor.data, nameStart, nameLen, KEY_MMO_TYPE)) {
            holder.mmoType = cursor.readUtf();
         } else if (type == TAG_STRING && equalsAscii(cursor.data, nameStart, nameLen, KEY_MMO_ID)) {
            holder.mmoId = cursor.readUtf();
         } else if (type == TAG_COMPOUND && equalsAscii(cursor.data, nameStart, nameLen, KEY_PUBLIC_BUKKIT_VALUES)) {
            parsePublicBukkitValues(cursor, holder);
         } else {
            skipPayload(cursor, type);
         }
      }
   }

   private static void parsePublicBukkitValues(Cursor cursor, CustomDataHolder holder) throws IOException {
      while (cursor.hasRemaining()) {
         int type = cursor.readUnsignedByte();
         if (type == TAG_END) {
            return;
         }
         int nameLen = cursor.readUnsignedShort();
         int nameStart = cursor.pos;
         cursor.skip(nameLen);

         if (type == TAG_STRING && equalsAscii(cursor.data, nameStart, nameLen, KEY_CRATE_KEY)) {
            holder.crateKeyId = cursor.readUtf();
         } else if (type == TAG_STRING && equalsAscii(cursor.data, nameStart, nameLen, KEY_VOUCHER)) {
            holder.voucherId = cursor.readUtf();
         } else {
            skipPayload(cursor, type);
         }
      }
   }

   private static void skipPayload(Cursor cursor, int type) throws IOException {
      switch (type) {
         case TAG_END -> {
         }
         case TAG_BYTE -> cursor.skip(1);
         case TAG_SHORT -> cursor.skip(2);
         case TAG_INT, TAG_FLOAT -> cursor.skip(4);
         case TAG_LONG, TAG_DOUBLE -> cursor.skip(8);
         case TAG_BYTE_ARRAY -> skipArray(cursor, 1);
         case TAG_STRING -> cursor.skip(cursor.readUnsignedShort());
         case TAG_LIST -> skipListElements(cursor, cursor.readUnsignedByte(), cursor.readInt());
         case TAG_COMPOUND -> skipCompound(cursor);
         case TAG_INT_ARRAY -> skipArray(cursor, 4);
         case TAG_LONG_ARRAY -> skipArray(cursor, 8);
         default -> throw new IOException("Unknown NBT tag type: " + type);
      }
   }

   private static void skipArray(Cursor cursor, int elementBytes) throws IOException {
      int len = cursor.readInt();
      if (len < 0 || (long) len * elementBytes > cursor.limit - cursor.pos) {
         throw new IOException("Invalid NBT array length: " + len);
      }
      cursor.skip(len * elementBytes);
   }

   private static void skipCompound(Cursor cursor) throws IOException {
      while (cursor.hasRemaining()) {
         int type = cursor.readUnsignedByte();
         if (type == TAG_END) {
            return;
         }
         cursor.skip(cursor.readUnsignedShort());
         skipPayload(cursor, type);
      }
   }

   private static void skipListElements(Cursor cursor, int elemType, int len) throws IOException {
      if (len <= 0) {
         return;
      }
      switch (elemType) {
         case TAG_END -> {
         }
         case TAG_BYTE -> cursor.skip(len);
         case TAG_SHORT -> cursor.skip(len * 2);
         case TAG_INT, TAG_FLOAT -> cursor.skip(len * 4);
         case TAG_LONG, TAG_DOUBLE -> cursor.skip(len * 8);
         default -> {
            for (int i = 0; i < len; i++) {
               skipPayload(cursor, elemType);
            }
         }
      }
   }

   private static String normalizeItemId(String raw) {
      String lower = raw.toLowerCase(Locale.ROOT);
      return lower.indexOf(':') >= 0 ? lower : "minecraft:" + lower;
   }

   private static byte[] ascii(String s) {
      return s.getBytes(StandardCharsets.US_ASCII);
   }

   private static boolean equalsAscii(byte[] data, int offset, int length, byte[] expected) {
      if (length != expected.length) {
         return false;
      }
      for (int i = 0; i < length; i++) {
         if (data[offset + i] != expected[i]) {
            return false;
         }
      }
      return true;
   }

   private static final class CustomDataHolder {

      String mmoType;
      String mmoId;
      String crateKeyId;
      String voucherId;
   }

   private static final class Cursor {

      final byte[] data;
      final int limit;
      int pos;

      Cursor(byte[] data, int offset, int limit) {
         this.data = data;
         this.pos = offset;
         this.limit = limit;
      }

      boolean hasRemaining() {
         return pos < limit;
      }

      boolean enterRootCompound() throws IOException {
         if (!hasRemaining()) {
            return false;
         }
         int rootType = readUnsignedByte();
         if (rootType != TAG_COMPOUND) {
            return false;
         }
         skip(readUnsignedShort());
         return true;
      }

      void skip(int bytes) throws IOException {
         if (bytes < 0 || pos + bytes > limit) {
            throw new IOException("Truncated NBT stream at offset " + pos + " (need " + bytes + " bytes)");
         }
         pos += bytes;
      }

      byte readByte() throws IOException {
         if (pos >= limit) {
            throw new IOException("Unexpected EOF in NBT");
         }
         return data[pos++];
      }

      int readUnsignedByte() throws IOException {
         return readByte() & 0xFF;
      }

      int readUnsignedShort() throws IOException {
         if (pos + 2 > limit) {
            throw new IOException("Unexpected EOF in NBT short");
         }
         int value = ((data[pos] & 0xFF) << 8) | (data[pos + 1] & 0xFF);
         pos += 2;
         return value;
      }

      int readInt() throws IOException {
         if (pos + 4 > limit) {
            throw new IOException("Unexpected EOF in NBT int");
         }
         int value = ((data[pos] & 0xFF) << 24)
               | ((data[pos + 1] & 0xFF) << 16)
               | ((data[pos + 2] & 0xFF) << 8)
               | (data[pos + 3] & 0xFF);
         pos += 4;
         return value;
      }

      double readDouble() throws IOException {
         if (pos + 8 > limit) {
            throw new IOException("Unexpected EOF in NBT double");
         }
         long bits = ((long) (data[pos] & 0xFF) << 56)
               | ((long) (data[pos + 1] & 0xFF) << 48)
               | ((long) (data[pos + 2] & 0xFF) << 40)
               | ((long) (data[pos + 3] & 0xFF) << 32)
               | ((long) (data[pos + 4] & 0xFF) << 24)
               | ((long) (data[pos + 5] & 0xFF) << 16)
               | ((long) (data[pos + 6] & 0xFF) << 8)
               | (data[pos + 7] & 0xFF);
         pos += 8;
         return Double.longBitsToDouble(bits);
      }

      String readUtf() throws IOException {
         int len = readUnsignedShort();
         if (pos + len > limit) {
            throw new IOException("Unexpected EOF in NBT string");
         }
         int start = pos;
         pos += len;
         for (int i = 0; i < len; i++) {
            if (data[start + i] < 0) {
               return decodeModifiedUtf8(data, start, len);
            }
         }
         return new String(data, start, len, StandardCharsets.ISO_8859_1);
      }

      private static String decodeModifiedUtf8(byte[] buf, int start, int len) throws IOException {
         char[] chars = new char[len];
         int charCount = 0;
         int end = start + len;
         int i = start;
         while (i < end) {
            int c = buf[i] & 0xFF;
            if (c < 128) {
               i++;
               chars[charCount++] = (char) c;
            } else if ((c >> 4) == 12 || (c >> 4) == 13) {
               if (i + 1 >= end) {
                  throw new IOException("Malformed modified UTF-8");
               }
               int c2 = buf[i + 1];
               chars[charCount++] = (char) (((c & 0x1F) << 6) | (c2 & 0x3F));
               i += 2;
            } else if ((c >> 4) == 14) {
               if (i + 2 >= end) {
                  throw new IOException("Malformed modified UTF-8");
               }
               int c2 = buf[i + 1];
               int c3 = buf[i + 2];
               chars[charCount++] = (char) (((c & 0x0F) << 12) | ((c2 & 0x3F) << 6) | (c3 & 0x3F));
               i += 3;
            } else {
               throw new IOException("Malformed modified UTF-8");
            }
         }
         return new String(chars, 0, charCount);
      }
   }
}
