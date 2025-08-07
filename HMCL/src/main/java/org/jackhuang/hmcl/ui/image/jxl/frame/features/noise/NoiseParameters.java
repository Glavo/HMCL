package org.jackhuang.hmcl.ui.image.jxl.frame.features.noise;

import java.io.IOException;

import org.jackhuang.hmcl.ui.image.jxl.io.Bitreader;

public class NoiseParameters {

    public final float[] lut = new float[8];

    public NoiseParameters(Bitreader reader) throws IOException {
        for (int i = 0; i < lut.length; i++)
            lut[i] = reader.readBits(10) / 1024f;
    }
}
