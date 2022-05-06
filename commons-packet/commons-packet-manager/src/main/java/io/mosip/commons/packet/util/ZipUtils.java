package io.mosip.commons.packet.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOCase;

import io.mosip.commons.packet.dto.Packet;

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

	static void extracted(Map<String, InputStream> checksumMap, Packet packet, List<String> allFileNames,
			List<String> notFoundFiles) throws IOException {
		ByteArrayInputStream packetStream = new ByteArrayInputStream(packet.getPacket());
		byte[] buffer = new byte[2048];
		ByteArrayOutputStream out = null;
		try (ZipInputStream zis = new ZipInputStream(packetStream)) {
			ZipEntry ze = zis.getNextEntry();
			while (ze != null && !notFoundFiles.isEmpty()) {
				out = new ByteArrayOutputStream();
				String fileName = ze.getName();
				String fileNameWithOutExt = FilenameUtils
						.removeExtension(FilenameUtils.normalize(fileName.toLowerCase()));

				if (allFileNames.contains(fileNameWithOutExt)) {
					int len;

					while ((len = zis.read(buffer)) > 0) {
						out.write(buffer, 0, len);
					}
					InputStream inputStream = new ByteArrayInputStream(out.toByteArray());
					if (inputStream != null && inputStream.available() > 0)
						checksumMap.put(FilenameUtils.removeExtension(fileName), inputStream);
					notFoundFiles.remove(fileNameWithOutExt);
				}

				zis.closeEntry();
				ze = zis.getNextEntry();
			}
			zis.closeEntry();
		} finally {
			packetStream.close();
			if (out != null)
				out.close();
		}
		
	}
}
