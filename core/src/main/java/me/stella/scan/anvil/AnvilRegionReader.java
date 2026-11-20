package me.stella.scan.anvil;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.zip.DataFormatException;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import org.jetbrains.annotations.Nullable;

/**
 * Read-only, sequential Anvil ({@code .mca} + external {@code .mcc}) reader.
 *
 * <p>Reads each region file in a single sequential pass into a reusable per-worker buffer, closes
 * the descriptor immediately (preventing FD exhaustion and write contention with Moonrise), and
 * reuses a single {@link Inflater} across millions of chunks.</p>
 */
public final class AnvilRegionReader {

   private static final int HEADER_BYTES = 8192;
   private static final int SECTOR_BYTES = 4096;
   private static final int MAX_WHOLE_FILE_BYTES = 32 * 1024 * 1024;
   private static final int COMPRESSION_GZIP = 1;
   private static final int COMPRESSION_DEFLATE = 2;
   private static final int COMPRESSION_NONE = 3;
   private static final int COMPRESSION_LZ4 = 4;
   private static final int EXTERNAL_BIT = 0x80;

   private static final @Nullable Constructor<? extends InputStream> LZ4_STREAM_CTOR = resolveLz4Constructor();

   private AnvilRegionReader() {
   }

   @FunctionalInterface
   public interface ChunkHandler {

      void onChunk(int chunkX, int chunkZ, byte[] nbtBytes, int nbtLength) throws IOException;
   }

   public static void readRegion(Path regionFile, WorkerScratch scratch, long minTimestampSec,
         java.util.function.BooleanSupplier cancelled, ChunkHandler handler, Runnable onError) {
      long[] coords = parseRegionCoords(regionFile);
      if (coords == null) {
         return;
      }
      int baseChunkX = (int) (coords[0] << 5);
      int baseChunkZ = (int) (coords[1] << 5);

      long fileSize;
      try {
         fileSize = Files.size(regionFile);
      } catch (IOException e) {
         onError.run();
         return;
      }
      if (fileSize < HEADER_BYTES) {
         return;
      }

      if (fileSize <= MAX_WHOLE_FILE_BYTES) {
         readWholeFile(regionFile, (int) fileSize, baseChunkX, baseChunkZ, scratch, minTimestampSec, cancelled, handler, onError);
      } else {
         readStreamingChannel(regionFile, fileSize, baseChunkX, baseChunkZ, scratch, minTimestampSec, cancelled, handler, onError);
      }
   }

   private static void readWholeFile(Path regionFile, int fileSize, int baseChunkX, int baseChunkZ,
         WorkerScratch scratch, long minTimestampSec, java.util.function.BooleanSupplier cancelled,
         ChunkHandler handler, Runnable onError) {
      byte[] regionBytes = scratch.ensureRegionBuffer(fileSize);
      int bytesRead;
      try (FileChannel channel = FileChannel.open(regionFile, StandardOpenOption.READ)) {
         bytesRead = readFully(channel, regionBytes, 0, fileSize);
      } catch (IOException e) {
         onError.run();
         return;
      }
      if (bytesRead < HEADER_BYTES) {
         return;
      }

      Path parentDir = regionFile.getParent();
      for (int i = 0; i < 1024; i++) {
         if ((i & 63) == 0 && cancelled.getAsBoolean()) {
            return;
         }
         int loc = readIntBE(regionBytes, i << 2);
         if (loc == 0) {
            continue;
         }
         if (minTimestampSec > 0L) {
            long ts = Integer.toUnsignedLong(readIntBE(regionBytes, 4096 + (i << 2)));
            if (ts < minTimestampSec) {
               continue;
            }
         }

         int sectorOffset = (loc >>> 8) * SECTOR_BYTES;
         int sectorCount = loc & 0xFF;
         if (sectorOffset < HEADER_BYTES || sectorOffset + 5 > bytesRead || sectorCount == 0) {
            continue;
         }

         int localX = i & 31;
         int localZ = i >>> 5;
         int chunkX = baseChunkX + localX;
         int chunkZ = baseChunkZ + localZ;

         int payloadLen = readIntBE(regionBytes, sectorOffset) - 1;
         int compressionByte = regionBytes[sectorOffset + 4] & 0xFF;

         try {
            int nbtLen;
            if ((compressionByte & EXTERNAL_BIT) != 0) {
               nbtLen = readExternalChunk(parentDir, chunkX, chunkZ, compressionByte & 0x7F, scratch);
            } else if (payloadLen > 0 && sectorOffset + 5 + payloadLen <= bytesRead) {
               nbtLen = decompress(regionBytes, sectorOffset + 5, payloadLen, compressionByte, scratch);
            } else {
               continue;
            }
            if (nbtLen > 0) {
               handler.onChunk(chunkX, chunkZ, scratch.nbtBuffer, nbtLen);
            }
         } catch (Exception firstFailure) {
            // Live server may have been mid-write; retry this single chunk once from disk.
            if (!retrySingleChunk(regionFile, parentDir, i, chunkX, chunkZ, scratch, handler)) {
               onError.run();
            }
         }
      }
   }

   private static void readStreamingChannel(Path regionFile, long fileSize, int baseChunkX, int baseChunkZ,
         WorkerScratch scratch, long minTimestampSec, java.util.function.BooleanSupplier cancelled,
         ChunkHandler handler, Runnable onError) {
      byte[] header = new byte[HEADER_BYTES];
      Path parentDir = regionFile.getParent();
      try (FileChannel channel = FileChannel.open(regionFile, StandardOpenOption.READ)) {
         if (readFully(channel, header, 0, HEADER_BYTES) < HEADER_BYTES) {
            return;
         }
         for (int i = 0; i < 1024; i++) {
            if ((i & 63) == 0 && cancelled.getAsBoolean()) {
               return;
            }
            int loc = readIntBE(header, i << 2);
            if (loc == 0) {
               continue;
            }
            if (minTimestampSec > 0L && Integer.toUnsignedLong(readIntBE(header, 4096 + (i << 2))) < minTimestampSec) {
               continue;
            }
            long offset = (long) (loc >>> 8) * SECTOR_BYTES;
            int maxBytes = (loc & 0xFF) * SECTOR_BYTES;
            if (offset < HEADER_BYTES || offset + 5 > fileSize || maxBytes <= 0) {
               continue;
            }
            int localX = i & 31;
            int localZ = i >>> 5;
            int chunkX = baseChunkX + localX;
            int chunkZ = baseChunkZ + localZ;

            try {
               int toRead = (int) Math.min(maxBytes, fileSize - offset);
               byte[] chunkBuf = scratch.ensureChunkBuffer(toRead);
               channel.position(offset);
               int got = readFully(channel, chunkBuf, 0, toRead);
               if (got < 5) {
                  continue;
               }
               int payloadLen = readIntBE(chunkBuf, 0) - 1;
               int compressionByte = chunkBuf[4] & 0xFF;
               int nbtLen;
               if ((compressionByte & EXTERNAL_BIT) != 0) {
                  nbtLen = readExternalChunk(parentDir, chunkX, chunkZ, compressionByte & 0x7F, scratch);
               } else if (payloadLen > 0 && 5 + payloadLen <= got) {
                  nbtLen = decompress(chunkBuf, 5, payloadLen, compressionByte, scratch);
               } else {
                  continue;
               }
               if (nbtLen > 0) {
                  handler.onChunk(chunkX, chunkZ, scratch.nbtBuffer, nbtLen);
               }
            } catch (Exception e) {
               onError.run();
            }
         }
      } catch (IOException e) {
         onError.run();
      }
   }

   private static boolean retrySingleChunk(Path regionFile, Path parentDir, int index,
         int chunkX, int chunkZ, WorkerScratch scratch, ChunkHandler handler) {
      try {
         Thread.sleep(5L);
         try (FileChannel channel = FileChannel.open(regionFile, StandardOpenOption.READ)) {
            byte[] locBytes = new byte[4];
            channel.position((long) index << 2);
            if (readFully(channel, locBytes, 0, 4) < 4) {
               return false;
            }
            int loc = readIntBE(locBytes, 0);
            if (loc == 0) {
               return true;
            }
            long offset = (long) (loc >>> 8) * SECTOR_BYTES;
            int maxBytes = (loc & 0xFF) * SECTOR_BYTES;
            byte[] chunkBuf = scratch.ensureChunkBuffer(maxBytes);
            channel.position(offset);
            int got = readFully(channel, chunkBuf, 0, maxBytes);
            if (got < 5) {
               return false;
            }
            int payloadLen = readIntBE(chunkBuf, 0) - 1;
            int compressionByte = chunkBuf[4] & 0xFF;
            int nbtLen = (compressionByte & EXTERNAL_BIT) != 0
                  ? readExternalChunk(parentDir, chunkX, chunkZ, compressionByte & 0x7F, scratch)
                  : decompress(chunkBuf, 5, payloadLen, compressionByte, scratch);
            if (nbtLen > 0) {
               handler.onChunk(chunkX, chunkZ, scratch.nbtBuffer, nbtLen);
            }
            return true;
         }
      } catch (Exception ignored) {
         return false;
      }
   }

   private static int readExternalChunk(@Nullable Path regionDir, int chunkX, int chunkZ,
         int compression, WorkerScratch scratch) throws IOException {
      if (regionDir == null) {
         return 0;
      }
      Path external = regionDir.resolve("c." + chunkX + "." + chunkZ + ".mcc");
      if (!Files.isRegularFile(external)) {
         return 0;
      }
      byte[] raw = Files.readAllBytes(external);
      return decompress(raw, 0, raw.length, compression, scratch);
   }

   public static int decompressGzip(byte[] compressed, int offset, int length, WorkerScratch scratch) throws IOException {
      return readStreamIntoScratch(new GZIPInputStream(new ByteArrayInputStream(compressed, offset, length), 8192), scratch);
   }

   private static int decompress(byte[] src, int offset, int length, int compression, WorkerScratch scratch) throws IOException {
      return switch (compression) {
         case COMPRESSION_DEFLATE -> inflate(src, offset, length, scratch);
         case COMPRESSION_GZIP -> decompressGzip(src, offset, length, scratch);
         case COMPRESSION_NONE -> {
            scratch.ensureNbtBuffer(length);
            System.arraycopy(src, offset, scratch.nbtBuffer, 0, length);
            yield length;
         }
         case COMPRESSION_LZ4 -> decompressLz4(src, offset, length, scratch);
         default -> throw new IOException("Unsupported Anvil compression type: " + compression);
      };
   }

   private static int inflate(byte[] src, int offset, int length, WorkerScratch scratch) throws IOException {
      Inflater inflater = scratch.inflater;
      inflater.reset();
      inflater.setInput(src, offset, length);
      int total = 0;
      try {
         while (!inflater.finished()) {
            if (total == scratch.nbtBuffer.length) {
               scratch.growNbtBuffer();
            }
            int produced = inflater.inflate(scratch.nbtBuffer, total, scratch.nbtBuffer.length - total);
            if (produced == 0) {
               if (inflater.needsInput() || inflater.needsDictionary()) {
                  break;
               }
            }
            total += produced;
         }
      } catch (DataFormatException e) {
         throw new IOException("Corrupt zlib chunk stream", e);
      }
      return total;
   }

   private static int decompressLz4(byte[] src, int offset, int length, WorkerScratch scratch) throws IOException {
      if (LZ4_STREAM_CTOR == null) {
         throw new IOException("LZ4 chunk encountered but net.jpountz.lz4.LZ4BlockInputStream is unavailable");
      }
      try (InputStream in = LZ4_STREAM_CTOR.newInstance(new ByteArrayInputStream(src, offset, length))) {
         return readStreamIntoScratch(in, scratch);
      } catch (ReflectiveOperationException e) {
         throw new IOException("Failed to open LZ4 stream", e);
      }
   }

   private static int readStreamIntoScratch(InputStream in, WorkerScratch scratch) throws IOException {
      int total = 0;
      try (in) {
         while (true) {
            if (total == scratch.nbtBuffer.length) {
               scratch.growNbtBuffer();
            }
            int read = in.read(scratch.nbtBuffer, total, scratch.nbtBuffer.length - total);
            if (read < 0) {
               break;
            }
            total += read;
         }
      }
      return total;
   }

   private static int readFully(FileChannel channel, byte[] target, int offset, int length) throws IOException {
      ByteBuffer buf = ByteBuffer.wrap(target, offset, length);
      int total = 0;
      while (buf.hasRemaining()) {
         int read = channel.read(buf);
         if (read < 0) {
            break;
         }
         total += read;
      }
      return total;
   }

   private static int readIntBE(byte[] b, int off) {
      return ((b[off] & 0xFF) << 24)
            | ((b[off + 1] & 0xFF) << 16)
            | ((b[off + 2] & 0xFF) << 8)
            | (b[off + 3] & 0xFF);
   }

   public static long @Nullable [] parseRegionCoords(Path file) {
      String name = file.getFileName().toString();
      if (!name.startsWith("r.") || !name.endsWith(".mca")) {
         return null;
      }
      int secondDot = name.indexOf('.', 2);
      if (secondDot < 0) {
         return null;
      }
      int thirdDot = name.indexOf('.', secondDot + 1);
      if (thirdDot != name.length() - 4) {
         return null;
      }
      try {
         long rx = Long.parseLong(name, 2, secondDot, 10);
         long rz = Long.parseLong(name, secondDot + 1, thirdDot, 10);
         return new long[]{rx, rz};
      } catch (NumberFormatException e) {
         return null;
      }
   }

   @SuppressWarnings("unchecked")
   private static @Nullable Constructor<? extends InputStream> resolveLz4Constructor() {
      try {
         Class<?> cls = Class.forName("net.jpountz.lz4.LZ4BlockInputStream");
         return (Constructor<? extends InputStream>) cls.getConstructor(InputStream.class);
      } catch (ReflectiveOperationException unavailable) {
         return null;
      }
   }

   /** Per-worker reusable I/O and decompression buffers. Must be closed when the worker finishes. */
   public static final class WorkerScratch implements AutoCloseable {

      final Inflater inflater = new Inflater();
      byte[] regionBuffer = new byte[256 * 1024];
      byte[] chunkBuffer = new byte[64 * 1024];
      byte[] nbtBuffer = new byte[128 * 1024];

      byte[] ensureRegionBuffer(int needed) {
         if (regionBuffer.length < needed) {
            regionBuffer = new byte[nextSize(regionBuffer.length, needed)];
         }
         return regionBuffer;
      }

      byte[] ensureChunkBuffer(int needed) {
         if (chunkBuffer.length < needed) {
            chunkBuffer = new byte[nextSize(chunkBuffer.length, needed)];
         }
         return chunkBuffer;
      }

      public byte[] ensureNbtBuffer(int needed) {
         if (nbtBuffer.length < needed) {
            nbtBuffer = new byte[nextSize(nbtBuffer.length, needed)];
         }
         return nbtBuffer;
      }

      void growNbtBuffer() {
         nbtBuffer = Arrays.copyOf(nbtBuffer, nbtBuffer.length * 2);
      }

      private static int nextSize(int current, int needed) {
         int size = current;
         while (size < needed) {
            size = Math.max(size * 2, needed);
         }
         return size;
      }

      public byte[] nbtBuffer() {
         return nbtBuffer;
      }

      @Override
      public void close() {
         inflater.end();
      }
   }
}
