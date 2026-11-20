import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Cooks NMS server binaries out of Paperclip-style server distributions (Paperclip / Leaper bundles).
 *
 * <p>For every bundle found in the given directories, a dedicated server instance is started inside
 * {@code cook-nms/instances/<bundle>/} with {@code -Dpaperclip.patchonly=true}: the launcher downloads the
 * vanilla server, applies its patches, unpacks {@code versions/} + {@code libraries/} and then exits
 * without booting Minecraft (so no EULA has to be accepted). The patched server binary is then fetched
 * from {@code versions/}, verified against the bundle's {@code META-INF/versions.list} checksum, and
 * published to {@code cook-nms/nms/<binary>.jar} where the version modules pick it up.</p>
 *
 * <p>Jars that are not bundles (i.e. already-cooked NMS binaries) are skipped. Up-to-date outputs are
 * not re-cooked unless {@code -Dcook.force=true} is set.</p>
 *
 * <pre>usage: java Cook.java [-Dcook.dir=&lt;cook-nms dir&gt;] &lt;dir-or-jar&gt;...</pre>
 */
public final class Cook {

    private static final String VERSIONS_LIST = "META-INF/versions.list";

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("usage: java Cook.java <dir-or-jar>...");
            System.exit(2);
        }
        Path cookDir = Path.of(System.getProperty("cook.dir", ".")).toAbsolutePath().normalize();
        Path outDir = cookDir.resolve("nms");
        Path instancesDir = cookDir.resolve("instances");
        boolean force = Boolean.getBoolean("cook.force");
        Files.createDirectories(outDir);

        List<Path> bundles = new ArrayList<>();
        for (String arg : args) {
            Path p = Path.of(arg).toAbsolutePath().normalize();
            if (Files.isDirectory(p)) {
                try (Stream<Path> s = Files.list(p)) {
                    s.filter(f -> f.getFileName().toString().endsWith(".jar")).sorted().forEach(bundles::add);
                }
            } else if (Files.isRegularFile(p)) {
                bundles.add(p);
            } else {
                System.out.println("[cook-nms] skipping missing input " + p);
            }
        }

        int cooked = 0;
        for (Path jar : bundles) {
            VersionEntry entry = readVersionEntry(jar);
            if (entry == null) {
                System.out.println("[cook-nms] " + jar.getFileName() + ": already an NMS binary - nothing to cook");
                continue;
            }
            Path target = outDir.resolve(entry.fileName());
            if (!force && Files.isRegularFile(target)
                    && Files.getLastModifiedTime(target).compareTo(Files.getLastModifiedTime(jar)) >= 0
                    && entry.sha256().equalsIgnoreCase(sha256(target))) {
                System.out.println("[cook-nms] " + jar.getFileName() + ": up-to-date -> " + cookDir.relativize(target));
                continue;
            }
            cook(jar, entry, instancesDir.resolve(stripJar(jar.getFileName().toString())), target);
            cooked++;
        }
        System.out.println("[cook-nms] done (" + cooked + " cooked)");
    }

    private static void cook(Path bundle, VersionEntry entry, Path instance, Path target) throws Exception {
        Files.createDirectories(instance);
        String java = System.getProperty("cook.java",
                Path.of(System.getProperty("java.home"), "bin", isWindows() ? "java.exe" : "java").toString());
        List<String> command = List.of(java, "-Dpaperclip.patchonly=true", "-jar", bundle.toString());
        System.out.println("[cook-nms] " + bundle.getFileName() + ": starting server instance in " + instance);
        System.out.println("[cook-nms]   $ " + String.join(" ", command));

        Process process = new ProcessBuilder(command).directory(instance.toFile()).inheritIO().start();
        int exit = process.waitFor();
        if (exit != 0) {
            throw new IllegalStateException(bundle.getFileName() + ": server instance exited with code " + exit);
        }

        Path binary = instance.resolve("versions").resolve(entry.path());
        if (!Files.isRegularFile(binary)) {
            throw new IllegalStateException(bundle.getFileName() + ": expected binary not found at " + binary);
        }
        String actual = sha256(binary);
        if (!entry.sha256().equalsIgnoreCase(actual)) {
            throw new IllegalStateException(bundle.getFileName() + ": checksum mismatch for " + binary
                    + " (expected " + entry.sha256() + ", got " + actual + ")");
        }
        Files.copy(binary, target, StandardCopyOption.REPLACE_EXISTING);
        System.out.println("[cook-nms] " + bundle.getFileName() + ": cooked " + entry.id() + " -> " + target);
    }

    /** Returns the server binary described by a bundle's versions.list, or {@code null} if the jar is not a bundle. */
    private static VersionEntry readVersionEntry(Path jar) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry list = zip.getEntry(VERSIONS_LIST);
            if (list == null) {
                return null;
            }
            try (InputStream in = zip.getInputStream(list)) {
                for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                    String[] parts = line.trim().split("\t");
                    if (parts.length == 3) {
                        return new VersionEntry(parts[0], parts[1], parts[2]);
                    }
                }
            }
        }
        throw new IllegalStateException(jar + ": malformed " + VERSIONS_LIST);
    }

    private static String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[1 << 16];
            for (int n; (n = in.read(buffer)) > 0; ) {
                digest.update(buffer, 0, n);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String stripJar(String name) {
        return name.endsWith(".jar") ? name.substring(0, name.length() - 4) : name;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private record VersionEntry(String sha256, String id, String path) {
        String fileName() {
            return Path.of(path).getFileName().toString();
        }
    }
}
