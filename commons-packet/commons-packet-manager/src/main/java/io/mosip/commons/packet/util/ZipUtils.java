package io.mosip.commons.packet.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOCase;

/**
 * Utility class to unzip packets and extract specific files from them.
 */
public class ZipUtils {

	/**
	 * Unzips the in-memory zip packet and returns the specified file as an
	 * InputStream.
	 *
	 * @param packet         the zip file as a byte array
	 * @param targetFileName the file name (without extension) to extract
	 * @return InputStream of the extracted file if found; otherwise null
	 * @throws IOException if any I/O error occurs during unzipping
	 */
	public static InputStream unzipAndGetFile(byte[] packet, String targetFileName) throws IOException {
		try (ByteArrayInputStream packetStream = new ByteArrayInputStream(packet);
				ZipInputStream zis = new ZipInputStream(packetStream)) {
			ZipEntry ze;
			byte[] buffer = new byte[2048];

			while ((ze = zis.getNextEntry()) != null) {
				String currentFileName = FilenameUtils.removeExtension(ze.getName());

				if (FilenameUtils.equals(currentFileName, targetFileName, true, IOCase.INSENSITIVE)) {
					try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
						int len;
						while ((len = zis.read(buffer)) > 0) {
							out.write(buffer, 0, len);
						}
						return new ByteArrayInputStream(out.toByteArray());
					}
				}

				zis.closeEntry();
			}
		}

		// File not found in ZIP
		return null;
	}
}