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

import java.awt.color.ColorSpace;
import java.awt.image.DataBuffer;
import java.awt.image.PixelInterleavedSampleModel;
import java.awt.image.SampleModel;

import javax.imageio.ImageTypeSpecifier;

import com.twelvemonkeys.imageio.color.UInt32ColorModel;

/**
 * ImageTypeSpecifier for interleaved 32 bit unsigned integral samples.
 *
 * @see UInt32ColorModel
 * @author <a href="mailto:harald.kuhr@gmail.com">Harald Kuhr</a>
 * @author last modified by $Author: haraldk$
 * @version $Id: UInt32ImageTypeSpecifier.java,v 1.0 24.01.11 17.51 haraldk Exp$
 */
final class UInt32ImageTypeSpecifier extends ImageTypeSpecifier {
    private UInt32ImageTypeSpecifier(final ColorSpace cs, final boolean hasAlpha, final boolean isAlphaPremultiplied, final SampleModel sampleModel) {
        super(new UInt32ColorModel(cs, hasAlpha, isAlphaPremultiplied), sampleModel);
    }

    static ImageTypeSpecifier createInterleaved(final ColorSpace cs, final int[] bandOffsets, final boolean hasAlpha, final boolean isAlphaPremultiplied) {
        return new UInt32ImageTypeSpecifier(
                cs, hasAlpha, isAlphaPremultiplied,
                new PixelInterleavedSampleModel(
                        DataBuffer.TYPE_INT, 1, 1,
                        cs.getNumComponents() + (hasAlpha ? 1 : 0),
                        cs.getNumComponents() + (hasAlpha ? 1 : 0),
                        bandOffsets
                )
        );
    }

    @Override
    public boolean equals(final Object other) {
        if (!(other instanceof UInt32ImageTypeSpecifier)) {
            return false;
        }

        UInt32ImageTypeSpecifier that = (UInt32ImageTypeSpecifier) other;
        return colorModel.equals(that.colorModel) && sampleModel.equals(that.sampleModel);
    }
}
