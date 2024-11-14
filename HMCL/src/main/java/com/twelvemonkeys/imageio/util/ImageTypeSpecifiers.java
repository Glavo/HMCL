/*
 * Copyright (c) 2014, Harald Kuhr
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * * Neither the name of the copyright holder nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.twelvemonkeys.imageio.util;

import javax.imageio.ImageTypeSpecifier;
import java.awt.color.*;
import java.awt.image.*;
import java.util.Objects;

import static com.twelvemonkeys.lang.Validate.isTrue;

/**
 * Factory class for creating {@code ImageTypeSpecifier}s.
 * Fixes some subtle bugs in {@code ImageTypeSpecifier}'s factory methods, but
 * in most cases, this class will delegate to the corresponding methods in {@link ImageTypeSpecifier}.
 *
 * @see ImageTypeSpecifier
 * @author <a href="mailto:harald.kuhr@gmail.com">Harald Kuhr</a>
 * @author last modified by $Author: haraldk$
 * @version $Id: ImageTypeSpecifiers.java,v 1.0 24.01.11 17.51 haraldk Exp$
 */
public final class ImageTypeSpecifiers {

    private static final ImageTypeSpecifier TYPE_INT_RGB = createPackedOddBits(ColorSpace.getInstance(ColorSpace.CS_sRGB), 24,
                                                                               0xFF0000,
                                                                               0x00FF00,
                                                                               0x0000FF,
                                                                               0x0,
                                                                               DataBuffer.TYPE_INT,
                                                                               false);
    private static final ImageTypeSpecifier TYPE_INT_BGR = createPackedOddBits(ColorSpace.getInstance(ColorSpace.CS_sRGB), 24,
                                                                               0x0000FF,
                                                                               0x00FF00,
                                                                               0xFF0000,
                                                                               0x0,
                                                                               DataBuffer.TYPE_INT,
                                                                               false);
    private static final ImageTypeSpecifier TYPE_USHORT_565_RGB = createPackedOddBits(ColorSpace.getInstance(ColorSpace.CS_sRGB), 16,
                                                                                      0xF800,
                                                                                      0x07E0,
                                                                                      0x001F,
                                                                                      0x0,
                                                                                      DataBuffer.TYPE_USHORT,
                                                                                      false);
    private static final ImageTypeSpecifier TYPE_USHORT_555_RGB = createPackedOddBits(ColorSpace.getInstance(ColorSpace.CS_sRGB), 15,
                                                                                      0x7C00,
                                                                                      0x03E0,
                                                                                      0x001F,
                                                                                      0x0,
                                                                                      DataBuffer.TYPE_USHORT,
                                                                                      false);

    private ImageTypeSpecifiers() {}

    public static ImageTypeSpecifier createFromBufferedImageType(final int bufferedImageType) {
        switch (bufferedImageType) {
            // ImageTypeSpecifier unconditionally uses bits == 32, we'll use a workaround for the INT_RGB and USHORT types
            case BufferedImage.TYPE_INT_RGB:
                return TYPE_INT_RGB;

            case BufferedImage.TYPE_INT_BGR:
                return TYPE_INT_BGR;

            case BufferedImage.TYPE_USHORT_565_RGB:
                return TYPE_USHORT_565_RGB;

            case BufferedImage.TYPE_USHORT_555_RGB:
                return TYPE_USHORT_555_RGB;

            default:
        }

        return ImageTypeSpecifier.createFromBufferedImageType(bufferedImageType);
    }

    static ImageTypeSpecifier createPackedOddBits(final ColorSpace colorSpace, int bits,
                                                  final int redMask, final int greenMask,
                                                  final int blueMask, final int alphaMask,
                                                  final int transferType, boolean isAlphaPremultiplied) {
        // ImageTypeSpecifier unconditionally uses bits == 32, we'll use a workaround
        Objects.requireNonNull(colorSpace, "colorSpace");
        isTrue(colorSpace.getType() == ColorSpace.TYPE_RGB, colorSpace, "ColorSpace must be TYPE_RGB");
        isTrue(redMask != 0 || greenMask != 0 || blueMask != 0 || alphaMask != 0, "No mask has at least 1 bit set");

        ColorModel colorModel = new DirectColorModel(colorSpace, bits, redMask, greenMask, blueMask, alphaMask,
                                                     isAlphaPremultiplied, transferType);

        return new ImageTypeSpecifier(colorModel, colorModel.createCompatibleSampleModel(1, 1));
    }

    public static ImageTypeSpecifier createInterleaved(final ColorSpace colorSpace,
                                                       final int[] bandOffsets,
                                                       final int dataType,
                                                       final boolean hasAlpha,
                                                       final boolean isAlphaPremultiplied) {
        // As the ComponentColorModel is broken for 32 bit unsigned int, we'll use our own version
        if (dataType == DataBuffer.TYPE_INT) {
            return UInt32ImageTypeSpecifier.createInterleaved(colorSpace, bandOffsets, hasAlpha, isAlphaPremultiplied);
        }

        // ...or fall back to default for anything else
        return ImageTypeSpecifier.createInterleaved(colorSpace, bandOffsets, dataType, hasAlpha, isAlphaPremultiplied);
    }

}
