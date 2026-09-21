package com.snailtools.shoplogger.mapart;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * Minimal dependency-free RGBA PNG encoder. Only ever called from the mapart
 * upload thread — never the game thread — and works on flat byte arrays
 * (no per-byte stream writes) with a fast deflate level, so even a huge mural
 * encodes in a fraction of a second without touching the frame rate.
 */
final class PngWriter {

	private PngWriter() {}

	/** argb is width*height pixels, 0xAARRGGBB each. */
	static byte[] encode(int width, int height, int[] argb) throws IOException {
		int stride = width * 4 + 1;
		byte[] raw = new byte[height * stride];
		int o = 0;
		for (int y = 0; y < height; y++) {
			raw[o++] = 0; // filter type: none
			int row = y * width;
			for (int x = 0; x < width; x++) {
				int p = argb[row + x];
				raw[o++] = (byte) (p >> 16);
				raw[o++] = (byte) (p >> 8);
				raw[o++] = (byte) p;
				raw[o++] = (byte) (p >>> 24);
			}
		}

		Deflater deflater = new Deflater(Deflater.BEST_SPEED);
		deflater.setInput(raw);
		deflater.finish();
		ByteArrayOutputStream idat = new ByteArrayOutputStream(raw.length / 3 + 64);
		byte[] buf = new byte[32 * 1024];
		while (!deflater.finished()) {
			int n = deflater.deflate(buf);
			idat.write(buf, 0, n);
		}
		deflater.end();

		ByteArrayOutputStream out = new ByteArrayOutputStream(idat.size() + 128);
		out.write(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'});
		byte[] ihdr = new byte[13];
		putInt(ihdr, 0, width);
		putInt(ihdr, 4, height);
		ihdr[8] = 8;  // bit depth
		ihdr[9] = 6;  // RGBA
		writeChunk(out, "IHDR", ihdr);
		writeChunk(out, "IDAT", idat.toByteArray());
		writeChunk(out, "IEND", new byte[0]);
		return out.toByteArray();
	}

	private static void putInt(byte[] b, int at, int v) {
		b[at] = (byte) (v >>> 24);
		b[at + 1] = (byte) (v >>> 16);
		b[at + 2] = (byte) (v >>> 8);
		b[at + 3] = (byte) v;
	}

	private static void writeChunk(ByteArrayOutputStream out, String type, byte[] data) throws IOException {
		byte[] len = new byte[4];
		putInt(len, 0, data.length);
		out.write(len);
		byte[] typeBytes = type.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
		out.write(typeBytes);
		out.write(data);
		CRC32 crc = new CRC32();
		crc.update(typeBytes);
		crc.update(data);
		byte[] c = new byte[4];
		putInt(c, 0, (int) crc.getValue());
		out.write(c);
	}
}
