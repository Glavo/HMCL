/*
 * Copyright (c) 2008, Harald Kuhr
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

package com.twelvemonkeys.imageio;

import javax.imageio.*;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;

/**
 * Abstract base class for image readers.
 *
 * @author <a href="mailto:harald.kuhr@gmail.com">Harald Kuhr</a>
 * @author last modified by $Author: haraldk$
 * @version $Id: ImageReaderBase.java,v 1.0 Sep 20, 2007 5:28:37 PM haraldk Exp$
 */
public abstract class ImageReaderBase extends ImageReader {

    /**
     * For convenience. Only set if the input is an {@code ImageInputStream}.
     * @see #setInput(Object, boolean, boolean)
     */
    protected ImageInputStream imageInput;

    /**
     * Constructs an {@code ImageReader} and sets its
     * {@code originatingProvider} field to the supplied value.
     * <p>
     * Subclasses that make use of extensions should provide a
     * constructor with signature {@code (ImageReaderSpi,
     * Object)} in order to retrieve the extension object.  If
     * the extension object is unsuitable, an
     * {@code IllegalArgumentException} should be thrown.
     * </p>
     *
     * @param provider the {@code ImageReaderSpi} that is invoking this constructor, or {@code null}.
     */
    protected ImageReaderBase(final ImageReaderSpi provider) {
        super(provider);
    }

    /**
     * Overrides {@code setInput}, to allow easy access to the input, in case
     * it is an {@code ImageInputStream}.
     *
     * @param input the {@code ImageInputStream} or other
     * {@code Object} to use for future decoding.
     * @param seekForwardOnly if {@code true}, images and metadata
     * may only be read in ascending order from this input source.
     * @param ignoreMetadata if {@code true}, metadata
     * may be ignored during reads.
     *
     * @exception IllegalArgumentException if {@code input} is
     * not an instance of one of the classes returned by the
     * originating service provider's {@code getInputTypes}
     * method, or is not an {@code ImageInputStream}.
     *
     * @see ImageInputStream
     */
    @Override
    public void setInput(final Object input, final boolean seekForwardOnly, final boolean ignoreMetadata) {
        resetMembers();
        super.setInput(input, seekForwardOnly, ignoreMetadata);

        if (input instanceof ImageInputStream) {
            imageInput = (ImageInputStream) input;
        }
        else {
            imageInput = null;
        }
    }

    @Override
    public void dispose() {
        resetMembers();
        super.dispose();
    }

    @Override
    public void reset() {
        resetMembers();
        super.reset();
    }

    /**
     * Resets all member variables. This method is by default invoked from:
     * <ul>
     *  <li>{@link #setInput(Object, boolean, boolean)}</li>
     *  <li>{@link #dispose()}</li>
     *  <li>{@link #reset()}</li>
     * </ul>
     *
     */
    protected abstract void resetMembers();

    /**
     * Default implementation that always returns {@code null}.
     *
     * @param imageIndex ignored, unless overridden
     * @return {@code null}, unless overridden
     */
    public IIOMetadata getImageMetadata(int imageIndex) {
        return null;
    }

    /**
     * Default implementation that always returns {@code null}.
     *
     * @return {@code null}, unless overridden
     * @throws IOException never, unless overridden.
     */
    public IIOMetadata getStreamMetadata() throws IOException {
        return null;
    }

    /**
     * Default implementation that always returns {@code 1}.
     *
     * @param allowSearch ignored, unless overridden
     * @return {@code 1}, unless overridden
     * @throws IOException never, unless overridden
     */
    public int getNumImages(boolean allowSearch) throws IOException {
        assertInput();
        return 1;
    }

    /**
     * Convenience method to make sure image index is within bounds.
     *
     * @param index the image index
     *
     * @throws IOException if an error occurs during reading
     * @throws IndexOutOfBoundsException if not {@code minIndex <= index < numImages}
     */
    protected void checkBounds(int index) throws IOException {
        assertInput();
        if (index < getMinIndex()) {
            throw new IndexOutOfBoundsException("index < minIndex");
        }

        int numImages = getNumImages(false);
        if (numImages != -1 && index >= numImages) {
            throw new IndexOutOfBoundsException("index >= numImages (" + index + " >= " + numImages + ")");
        }
    }

    /**
     * Makes sure input is set.
     *
     * @throws IllegalStateException if {@code getInput() == null}.
     */
    protected void assertInput() {
        if (getInput() == null) {
            throw new IllegalStateException("getInput() == null");
        }
    }

    /**
     * Returns the {@code BufferedImage} to which decoded pixel data should be written.
     * <p>
     * As {@link ImageReader#getDestination} but tests if the explicit destination
     * image (if set) is valid according to the {@code ImageTypeSpecifier}s given in {@code types}.
     * </p>
     *
     * @param param an {@code ImageReadParam} to be used to get
     * the destination image or image type, or {@code null}.
     * @param types an {@code Iterator} of
     * {@code ImageTypeSpecifier}s indicating the legal image
     * types, with the default first.
     * @param width the true width of the image or tile begin decoded.
     * @param height the true width of the image or tile being decoded.
     *
     * @return the {@code BufferedImage} to which decoded pixel
     * data should be written.
     *
     * @exception IIOException if the {@code ImageTypeSpecifier} or {@code BufferedImage}
     * specified by {@code param} does not match any of the legal
     * ones from {@code types}.
     * @throws IllegalArgumentException if {@code types}
     * is {@code null} or empty, or if an object not of type
     * {@code ImageTypeSpecifier} is retrieved from it.
     * Or, if the resulting image would have a width or height less than 1,
     * or if the product of {@code width} and {@code height} of the resulting image is greater than
     * {@code Integer.MAX_VALUE}.
     */
    public static BufferedImage getDestination(final ImageReadParam param, final Iterator<ImageTypeSpecifier> types,
                                               final int width, final int height) throws IIOException {
        // Adapted from http://java.net/jira/secure/attachment/29712/TIFFImageReader.java.patch,
        // to allow reading parts/tiles of huge images.

        if (types == null || !types.hasNext()) {
            throw new IllegalArgumentException("imageTypes null or empty!");
        }

        ImageTypeSpecifier imageType = null;

        // If param is non-null, use it
        if (param != null) {
            // Try to get the explicit destination image
            BufferedImage dest = param.getDestination();

            if (dest != null) {
                boolean found = false;

                while (types.hasNext()) {
                    ImageTypeSpecifier specifier = types.next();
                    int bufferedImageType = specifier.getBufferedImageType();

                    if (bufferedImageType != 0 && bufferedImageType == dest.getType()) {
                        // Known types equal, perfect match
                        found = true;
                        break;
                    }
                    else {
                        // If types are different, or TYPE_CUSTOM, test if
                        // - transferType is ok
                        // - bands are ok
                        // TODO: Test if color model is ok?
                        if (specifier.getSampleModel().getTransferType() == dest.getSampleModel().getTransferType()
                                && Arrays.equals(specifier.getSampleModel().getSampleSize(), dest.getSampleModel().getSampleSize())
                                && specifier.getNumBands() <= dest.getSampleModel().getNumBands()) {
                            found = true;
                            break;
                        }
                    }
                }

                if (!found) {
                    throw new IIOException(String.format("Destination image from ImageReadParam does not match legal imageTypes from reader: %s", dest));
                }

                return dest;
            }

            // No image, get the image type
            imageType = param.getDestinationType();
        }

        // No info from param, use fallback image type
        if (imageType == null) {
            imageType = types.next();
        }
        else {
            boolean foundIt = false;

            while (types.hasNext()) {
                ImageTypeSpecifier type = types.next();

                if (type.equals(imageType)) {
                    foundIt = true;
                    break;
                }
            }

            if (!foundIt) {
                throw new IIOException(String.format("Destination type from ImageReadParam does not match legal imageTypes from reader: %s", imageType));
            }
        }

        Rectangle srcRegion = new Rectangle(0, 0, 0, 0);
        Rectangle destRegion = new Rectangle(0, 0, 0, 0);
        computeRegions(param, width, height, null, srcRegion, destRegion);

        int destWidth = destRegion.x + destRegion.width;
        int destHeight = destRegion.y + destRegion.height;

        long dimension = (long) destWidth * destHeight;
        if (dimension > Integer.MAX_VALUE) {
            throw new IIOException(String.format("destination width * height > Integer.MAX_VALUE: %d", dimension));
        }

        long size = dimension * imageType.getSampleModel().getNumDataElements();
        if (size > Integer.MAX_VALUE) {
            throw new IIOException(String.format("destination width * height * samplesPerPixel > Integer.MAX_VALUE: %d", size));
        }

        // Create a new image based on the type specifier
        return imageType.createBufferedImage(destWidth, destHeight);
    }

}
