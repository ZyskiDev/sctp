package com.snailtools.mapartscanner;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/** Minimal dependency-free RGBA PNG encoder (map pictures are small, no filtering needed). */
final class PngWriter {

	private PngWriter() {}

	/** argb is width*height pixels, 0xAARRGGBB each. */
	static byte[] encode(int width, int height, int[] argb) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'});

		ByteArrayOutputStream ihdr = new ByteArrayOutputStream();
		DataOutputStream ihdrData = new DataOutputStream(ihdr);
		ihdrData.writeInt(width);
		ihdrData.writeInt(height);
		ihdrData.writeByte(8); // bit depth
		ihdrData.writeByte(6); // RGBA
		ihdrData.writeByte(0); // deflate
		ihdrData.writeByte(0); // adaptive filtering
		ihdrData.writeByte(0); // no interlace
		writeChunk(out, "IHDR", ihdr.toByteArray());

		ByteArrayOutputStream raw = new ByteArrayOutputStream(height * (width * 4 + 1));
		for (int y = 0; y < height; y++) {
			raw.write(0); // filter type: none
			for (int x = 0; x < width; x++) {
				int p = argb[y * width + x];
				raw.write((p >> 16) & 0xFF);
				raw.write((p >> 8) & 0xFF);
				raw.write(p & 0xFF);
				raw.write((p >>> 24) & 0xFF);
			}
		}
		ByteArrayOutputStream idat = new ByteArrayOutputStream();
		try (DeflaterOutputStream deflater = new DeflaterOutputStream(idat, new Deflater(Deflater.BEST_COMPRESSION))) {
			deflater.write(raw.toByteArray());
		}
		writeChunk(out, "IDAT", idat.toByteArray());
		writeChunk(out, "IEND", new byte[0]);
		return out.toByteArray();
	}

	private static void writeChunk(ByteArrayOutputStream out, String type, byte[] data) throws IOException {
		DataOutputStream dos = new DataOutputStream(out);
		dos.writeInt(data.length);
		byte[] typeBytes = type.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
		dos.write(typeBytes);
		dos.write(data);
		CRC32 crc = new CRC32();
		crc.update(typeBytes);
		crc.update(data);
		dos.writeInt((int) crc.getValue());
	}
}
