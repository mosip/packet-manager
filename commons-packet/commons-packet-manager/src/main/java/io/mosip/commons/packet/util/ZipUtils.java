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
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Class to unzip the packets
 */
public class ZipUtils {

    /**
     * Method to unzip the file in-memeory and search the required file and return
     * it
     *
     * @param packet zip file to be unzipped
     * @param file   file to search within zip file
     * @return return the corresponding file as inputStream
     * @throws IOException if any error occored while unzipping the file
     */
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
