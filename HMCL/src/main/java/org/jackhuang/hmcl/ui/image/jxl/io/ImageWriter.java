package org.jackhuang.hmcl.ui.image.jxl.io;

import org.jackhuang.hmcl.ui.image.jxl.JXLImage;
import org.jackhuang.hmcl.ui.image.jxl.JXLatte;
import org.jackhuang.hmcl.ui.image.jxl.color.CIEPrimaries;
import org.jackhuang.hmcl.ui.image.jxl.color.CIEXY;
import org.jackhuang.hmcl.ui.image.jxl.color.ColorFlags;
import org.jackhuang.hmcl.ui.image.jxl.color.ColorManagement;
import org.jackhuang.hmcl.ui.image.jxl.util.ImageBuffer;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * @author Glavo
 */
public class ImageWriter {
    private final int bitDepth;
    private final ImageBuffer[] buffer;
    private DataOutputStream out;
    private final int height;
    private final int width;
    private final boolean gray;
    private final int colorMode;
    private final int colorChannels;
    private final int alphaIndex;
    private final int deflateLevel;
    private final CIEPrimaries primaries;
    private final CIEXY whitePoint;
    private final CRC32 crc32 = new CRC32();
    private byte[] iccProfile = null;
    private final boolean hdr;
    private final int tf;
    private final int maxValue;

    public ImageWriter(JXLImage image, int bitDepth, int deflateLevel, boolean hdr, int peakDetect) {
        if (bitDepth <= 0)
            bitDepth = hdr || image.getHeader().getBitDepthHeader().bitsPerSample > 8 ? 16 : 8;
        if (bitDepth != 8 && bitDepth != 16)
            throw new IllegalArgumentException("PNG only supports 8 and 16");
        this.hdr = hdr;
        this.gray = image.getColorEncoding() == ColorFlags.CE_GRAY;
        this.primaries = hdr ? ColorManagement.PRI_BT2100 : ColorManagement.PRI_SRGB;
        this.whitePoint = ColorManagement.WP_D65;
        this.tf = hdr ? ColorFlags.TF_PQ : ColorFlags.TF_SRGB;
        this.iccProfile = image.getICCProfile();
        image = iccProfile != null ? image : image.transform(primaries, whitePoint, tf, peakDetect);
        this.bitDepth = bitDepth;
        this.maxValue = ~(~0 << bitDepth);
        this.width = image.getWidth();
        this.height = image.getHeight();
        this.alphaIndex = image.getAlphaIndex();
        this.deflateLevel = deflateLevel;
        if (gray) {
            this.colorMode = alphaIndex >= 0 ? 4 : 0;
            this.colorChannels = 1;
        } else {
            this.colorMode = alphaIndex >= 0 ? 6 : 2;
            this.colorChannels = 3;
        }
        boolean coerce = image.isAlphaPremultiplied();
        this.buffer = image.getBuffer(true);
        if (!coerce) {
            for (int c = 0; c < buffer.length; c++) {
                if (buffer[c].isInt() && image.getTaggedBitDepth(c) != bitDepth) {
                    coerce = true;
                    break;
                }
            }
        }
        if (coerce) {
            for (int c = 0; c < buffer.length; c++) {
                buffer[c].castToFloatIfInt(~(~0 << image.getTaggedBitDepth(c)));
            }
        }
        if (image.isAlphaPremultiplied()) {
            float[][] a = buffer[alphaIndex].getFloatBuffer();
            for (int c = 0; c < colorChannels; c++) {
                float[][] buff = buffer[c].getFloatBuffer();
                for (int y = 0; y < buffer[c].height; y++) {
                    for (int x = 0; x < buffer[c].width; x++) {
                        buff[y][x] /= a[y][x];
                    }
                }
            }
        }
        for (int c = 0; c < buffer.length; c++) {
            if (buffer[c].isInt() && image.getTaggedBitDepth(c) == bitDepth) {
                buffer[c].clamp(maxValue);
            } else {
                buffer[c].castToIntIfFloat(maxValue);
            }
        }
    }

    private void writeIHDR() throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        DataOutputStream dout = new DataOutputStream(bout);
        dout.writeInt(0x49_48_44_52); // IHDR
        dout.writeInt(width);
        dout.writeInt(height);
        dout.writeByte(bitDepth);
        dout.writeByte(colorMode);
        dout.writeByte(0); // compression method 0 (zlib)
        dout.writeByte(0); // filter method 0 (standard)
        dout.writeByte(0); // not interlaced
        dout.close();
        byte[] buf = bout.toByteArray();
        crc32.reset();
        out.writeInt(buf.length - 4);
        out.write(buf);
        crc32.update(buf);
        out.writeInt((int) crc32.getValue());
    }

    private void writeSRGB() throws IOException {
        if (iccProfile != null || hdr)
            return;
        DataOutputStream dout = new DataOutputStream(out);
        dout.writeInt(0x00_00_00_01);
        dout.writeInt(0x73_52_47_42); // sRGB
        dout.write(1); // relative colorimetric
        dout.writeInt(0xD9_C9_2C_7F); // crc
        dout.flush();
    }

    private void writeICCP() throws IOException {
        boolean compressedICC = false;
        if (iccProfile == null) {
            if (hdr) {
                this.iccProfile = new byte[4866];
                try (InputStream in = JXLatte.class.getResourceAsStream("/bt2020-d65-pq.icc.zz")) {
                    IOHelper.readFully(in, this.iccProfile);
                }
                compressedICC = true;
            } else {
                return;
            }
        }
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        DataOutputStream dout = new DataOutputStream(bout);
        dout.writeInt(0x69_43_43_50); // iCCP
        byte[] s = "jxlatte".getBytes(StandardCharsets.UTF_8);
        dout.write(s);
        dout.write(0); // null terminator
        dout.write(0); // compression method 0
        if (compressedICC) {
            dout.write(iccProfile);
            dout.flush();
            dout.close();
        } else {
            DeflaterOutputStream defout = new DeflaterOutputStream(dout, new Deflater(deflateLevel));
            defout.write(iccProfile);
            defout.flush();
            defout.close();
        }
        byte[] buf = bout.toByteArray();
        out.writeInt(buf.length - 4);
        out.write(buf);
        crc32.reset();
        crc32.update(buf);
        out.writeInt((int) crc32.getValue());
    }

    private void writeSample(DataOutput dout, int sample) throws IOException {
        if (bitDepth == 8)
            dout.writeByte(sample);
        else
            dout.writeShort(sample);
    }

    private void writeIDAT() throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        bout.write(new byte[]{'I', 'D', 'A', 'T'});
        DataOutputStream dout = new DataOutputStream(new DeflaterOutputStream(bout, new Deflater(deflateLevel)));
        for (int y = 0; y < height; y++) {
            dout.writeByte(0); // filter 0
            for (int x = 0; x < width; x++) {
                for (int c = 0; c < colorChannels; c++) {
                    writeSample(dout, buffer[c].getIntBuffer()[y][x]);
                }
                if (alphaIndex >= 0)
                    writeSample(dout, buffer[colorChannels + alphaIndex].getIntBuffer()[y][x]);
            }
        }
        dout.close();
        byte[] buff = bout.toByteArray();
        out.writeInt(buff.length - 4);
        out.write(buff);
        crc32.reset();
        crc32.update(buff);
        out.writeInt((int) crc32.getValue());
    }

    public void write(OutputStream outputStream) throws IOException {
        this.out = new DataOutputStream(outputStream);
        out.writeLong(0x8950_4E47_0D0A_1A0AL); // png signature
        writeIHDR();
        if (hdr || this.iccProfile != null)
            writeICCP();
        else
            writeSRGB();
        writeIDAT();
        out.writeInt(0);
        out.writeInt(0x49_45_4E_44); // IEND
        out.writeInt(0xAE_42_60_82); // crc32 for IEND
    }

    private int getArgbPixel(int row, int col) {
        int argb = 0;
        if (gray) {
            int grayscale = buffer[0].getIntBuffer()[row][col];
            if (bitDepth == 16)
                grayscale >>>= 8;
            grayscale &= 0xFF;
            argb = (grayscale << 16) | (grayscale << 8) | grayscale;
        } else {
            for (int channel = 0; channel < colorChannels; channel++) {
                int c = buffer[channel].getIntBuffer()[row][col];
                if (bitDepth == 16)
                    c >>>= 8;
                argb = (argb << 8) | (c & 0xFF);
            }
        }

        if (alphaIndex >= 0) {
            int alpha = buffer[colorChannels + alphaIndex].getIntBuffer()[row][col];
            if (bitDepth == 16)
                alpha >>>= 8;
            return argb | ((alpha & 0xff) << 24);
        } else {
            return argb | 0xFF00_0000;
        }
    }

    public int[] toArgbPixels() {
        int[] result = new int[Math.multiplyExact(width, height)];

        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                result[row * width + col] = getArgbPixel(row, col);
            }
        }
        return result;
    }
}
