package io.mosip.commons.packet.util;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOCase;
import org.apache.commons.io.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * High-performance ZIP utility for in-memory packet extraction.
 * Optimized for:
 *  - Large packet throughput
 *  - Early file exit
 *  - Reduced object creation
 *  - Optimal buffer sizing
 */
public final class ZipUtils {

    // 8KB is JVM default optimal buffer size for stream copy
    private static final int BUFFER_SIZE = 8192;

    private ZipUtils() {
        // Prevent instantiation
    }

    /**
     * Extracts all entries from ZIP in single pass.
     * Key = filename without extension (uppercased for case-insensitive lookup).
     *
     * @param packet zip bytes
     * @return map of normalized name → file content
     * @throws IOException on read error
     */
    public static Map<String, byte[]> unzipAll(byte[] packet) throws IOException {

        if (packet == null || packet.length == 0) {
            return Map.of(); // Java 9+ immutable empty map
        }

        Map<String, byte[]> entries = new HashMap<>(16); // small initial capacity
        byte[] buffer = new byte[BUFFER_SIZE];

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(packet))) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {

                if (entry.isDirectory()) {
                    continue;
                }

                String key = FilenameUtils
                        .removeExtension(entry.getName())
                        .toUpperCase();

                // Pre-size if size known (optimization)
                ByteArrayOutputStream out =
                        entry.getSize() > 0 && entry.getSize() < Integer.MAX_VALUE
                                ? new ByteArrayOutputStream((int) entry.getSize())
                                : new ByteArrayOutputStream(BUFFER_SIZE);

                int len;
                while ((len = zis.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                }

                entries.put(key, out.toByteArray());
                zis.closeEntry();
            }
        }

        return entries;
    }

    /**
     * Extracts a specific file (without extension match, case-insensitive).
     * Stops immediately when file is found (high performance).
     *
     * @param packet zip bytes
     * @param file   filename without extension
     * @return InputStream of file content or null if not found
     * @throws IOException on error
     */
    public static InputStream unzipAndGetFile(byte[] packet, String file) throws IOException {

        if (packet == null || packet.length == 0 || file == null || file.isBlank()) {
            return null;
        }

        byte[] buffer = new byte[BUFFER_SIZE];

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(packet))) {

            ZipEntry entry;

            while ((entry = zis.getNextEntry()) != null) {

                if (entry.isDirectory()) {
                    continue;
                }

                String fileNameWithoutExt =
                        FilenameUtils.removeExtension(entry.getName());

                if (FilenameUtils.equals(fileNameWithoutExt, file, true, IOCase.INSENSITIVE)) {

                    ByteArrayOutputStream out =
                            entry.getSize() > 0 && entry.getSize() < Integer.MAX_VALUE
                                    ? new ByteArrayOutputStream((int) entry.getSize())
                                    : new ByteArrayOutputStream(BUFFER_SIZE);

                    int len;
                    while ((len = zis.read(buffer)) != -1) {
                        out.write(buffer, 0, len);
                    }

                    return new ByteArrayInputStream(out.toByteArray()); // Early return
                }

                zis.closeEntry();
            }
        }

        return null;
    }

    /**
     * Even faster version when byte[] return is acceptable.
     * Avoids extra ByteArrayInputStream wrapper.
     */
    public static byte[] unzipAndGetFileBytes(byte[] packet, String file) throws IOException {

        if (packet == null || packet.length == 0 || file == null || file.isBlank()) {
            return null;
        }

        byte[] buffer = new byte[BUFFER_SIZE];

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(packet))) {

            ZipEntry entry;

            while ((entry = zis.getNextEntry()) != null) {

                if (entry.isDirectory()) {
                    continue;
                }

                String fileNameWithoutExt =
                        FilenameUtils.removeExtension(entry.getName());

                if (FilenameUtils.equals(fileNameWithoutExt, file, true, IOCase.INSENSITIVE)) {

                    ByteArrayOutputStream out =
                            entry.getSize() > 0 && entry.getSize() < Integer.MAX_VALUE
                                    ? new ByteArrayOutputStream((int) entry.getSize())
                                    : new ByteArrayOutputStream(BUFFER_SIZE);

                    IOUtils.copy(zis, out); // Faster than manual loop

                    return out.toByteArray();
                }

                zis.closeEntry();
            }
        }

        return null;
    }
}