package io.mosip.commons.packet.util;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOCase;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Class to unzip the packets
 */
public class ZipUtils {

    /**
     * Extracts all entries from a zip in a single pass, keyed by filename
     * (without extension, uppercased) for case-insensitive lookup.
     *
     * @param packet zip bytes
     * @return map of normalised name → file contents
     * @throws IOException if any error occurs while reading the zip
     */
    public static Map<String, byte[]> unzipAll(byte[] packet) throws IOException {
        Map<String, byte[]> entries = new HashMap<>();
        byte[] buffer = new byte[2048];
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(packet))) {
            ZipEntry ze;
            while ((ze = zis.getNextEntry()) != null) {
                String key = FilenameUtils.removeExtension(ze.getName()).toUpperCase();
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    out.write(buffer, 0, len);
                }
                entries.put(key, out.toByteArray());
                zis.closeEntry();
            }
        }
        return entries;
    }

    public static InputStream unzipAndGetFile(byte[] packet, String file) throws IOException {
        ByteArrayInputStream packetStream = new ByteArrayInputStream(packet);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        boolean flag = false;
        byte[] buffer = new byte[2048];
        try (ZipInputStream zis = new ZipInputStream(packetStream)) {
            ZipEntry ze = zis.getNextEntry();
            while (ze != null) {
                String fileName = ze.getName();
                String fileNameWithOutExt = FilenameUtils.removeExtension(fileName);
                if (FilenameUtils.equals(fileNameWithOutExt, file, true, IOCase.INSENSITIVE)) {
                    int len;
                    flag = true;
                    while ((len = zis.read(buffer)) > 0) {
                        out.write(buffer, 0, len);
                    }
                    break;
                }
                zis.closeEntry();
                ze = zis.getNextEntry();
            }
            zis.closeEntry();
        } finally {
            packetStream.close();
            out.close();
        }
        if (flag) {
            return new ByteArrayInputStream(out.toByteArray());
        }

        return null;
    }
}
