/*
 * Copyright (c) 2021, Harald Kuhr
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

package com.twelvemonkeys.imageio.color;

import java.awt.color.ColorSpace;
import java.awt.color.ICC_ColorSpace;
import java.awt.color.ICC_Profile;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Objects;

/**
 * A helper class for working with ICC color profiles.
 * <p>
 * Standard ICC color profiles are read from system-specific locations
 * for known operating systems.
 * </p>
 * <p>
 * Color profiles may be configured by placing a property-file
 * {@code com/twelvemonkeys/imageio/color/icc_profiles.properties}
 * on the classpath, specifying the full path to the profiles.
 * ICC color profiles are probably already present on your system, or
 * can be downloaded from
 * <a href="http://www.color.org/profiles2.xalter">ICC</a>,
 * <a href="http://www.adobe.com/downloads/">Adobe</a> or other places.
 * * </p>
 * <p>
 * Example property file:
 * </p>
 * <pre>
 * # icc_profiles.properties
 * ADOBE_RGB_1998=/path/to/Adobe RGB 1998.icc
 * GENERIC_CMYK=/path/to/Generic CMYK.icc
 * </pre>
 *
 * @author <a href="mailto:harald.kuhr@gmail.com">Harald Kuhr</a>
 * @author last modified by $Author: haraldk$
 * @version $Id: ColorSpaces.java,v 1.0 24.01.11 17.51 haraldk Exp$
 */
public final class ColorProfiles {
    /**
     * We need special ICC profile handling for KCMS vs LCMS. Delegate to specific strategy.
     */
    private final static ICCProfileSanitizer profileCleaner = ICCProfileSanitizer.Factory.get();

    static final int ICC_PROFILE_MAGIC = 'a' << 24 | 'c' << 16 | 's' << 8 | 'p';
    static final int ICC_PROFILE_HEADER_SIZE = 128;

    static {
        // In case we didn't activate through SPI already
        ProfileDeferralActivator.activateProfiles();
    }

    private ColorProfiles() {
    }

    static byte[] getProfileHeaderWithProfileId(final ICC_Profile profile) {
        // Get *entire profile data*... :-/
        return getProfileHeaderWithProfileId(profile.getData());
    }

    static byte[] getProfileHeaderWithProfileId(byte[] data) {
        // ICC profile header is the first 128 bytes
        byte[] header = Arrays.copyOf(data, ICC_PROFILE_HEADER_SIZE);

        // Clear out preferred CMM, platform & creator, as these don't affect the profile in any way
        // - LCMS updates CMM + creator to "lcms" and platform to current platform
        // - KCMS keeps the values in the file...
        Arrays.fill(header, ICC_Profile.icHdrCmmId, ICC_Profile.icHdrCmmId + 4, (byte) 0);
        Arrays.fill(header, ICC_Profile.icHdrPlatform, ICC_Profile.icHdrPlatform + 4, (byte) 0);
        // + Clear out rendering intent, as this may be updated by application
        Arrays.fill(header, ICC_Profile.icHdrRenderingIntent, ICC_Profile.icHdrRenderingIntent + 4, (byte) 0);
        Arrays.fill(header, ICC_Profile.icHdrCreator, ICC_Profile.icHdrCreator + 4, (byte) 0);

        // Clear out any existing MD5, as it is no longer correct
        Arrays.fill(header, ICC_Profile.icHdrProfileID, ICC_Profile.icHdrProfileID + 16, (byte) 0);

        // Generate new MD5 and store in header
        byte[] md5 = computeMD5(header, data);
        System.arraycopy(md5, 0, header, ICC_Profile.icHdrProfileID, md5.length);

        return header;
    }

    private static byte[] computeMD5(byte[] header, byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            digest.update(header, 0, ICC_PROFILE_HEADER_SIZE);
            digest.update(data, ICC_PROFILE_HEADER_SIZE, data.length - ICC_PROFILE_HEADER_SIZE);
            return digest.digest();
        }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Missing MD5 MessageDigest");
        }
    }

    /**
     * Tests whether an ICC color profile is equal to the default sRGB profile.
     *
     * @param profile the ICC profile to test. May not be {@code null}.
     * @return {@code true} if {@code profile} is equal to the default sRGB profile.
     * @throws IllegalArgumentException if {@code profile} is {@code null}
     * @see ColorSpace#CS_sRGB
     * @see ColorSpace#isCS_sRGB()
     */
    public static boolean isCS_sRGB(final ICC_Profile profile) {
        Objects.requireNonNull(profile, "profile");

        return profile.getColorSpaceType() == ColorSpace.TYPE_RGB && Arrays.equals(getProfileHeaderWithProfileId(profile), sRGB.header);
    }

    /**
     * Reads an ICC Profile from the given input stream, with extra validation.
     *
     * If a matching profile already exists in cache, the cached instance is returned.
     *
     * @param input the input stream to read from, may not be {@code null}
     * @return an {@code ICC_Profile} object as read from the input stream.
     * @throws IOException              If an I/O error occurs while reading the stream.
     * @throws IllegalArgumentException If {@code input} is {@code null}
     *                                  or the stream does not contain valid ICC Profile data.
     * @see ICC_Profile#getInstance(InputStream)
     */
    public static ICC_Profile readProfile(final InputStream input) throws IOException {
        Objects.requireNonNull(input, "input");

        DataInputStream dataInput = new DataInputStream(input);
        byte[] header = new byte[ICC_PROFILE_HEADER_SIZE];
        try {
            dataInput.readFully(header);

            int size = validateHeaderAndGetSize(header);
            byte[] data = Arrays.copyOf(header, size);
            dataInput.readFully(data, header.length, size - header.length);

            return createProfile(data);
        }
        catch (EOFException e) {
            throw new IllegalArgumentException("Truncated ICC Profile data", e);
        }
    }

    /**
     * Reads an ICC Profile from the given byte array, with extra validation.
     * Extraneous bytes at the end of the array are ignored.
     *
     * If a matching profile already exists in cache, the cached instance is returned.
     *
     * @param input the byte array to create a profile from, may not be {@code null}
     * @return an {@code ICC_Profile} object created from the byte array
     * @throws IllegalArgumentException If {@code input} is {@code null}
     *                                  or the byte array does not contain valid ICC Profile data.
     * @see ICC_Profile#getInstance(byte[])
     */
    public static ICC_Profile createProfile(final byte[] input) {
        int size = validateAndGetSize(input);

        // Look up in cache before returning, these are already validated
        byte[] profileHeader = getProfileHeaderWithProfileId(input);
        ICC_Profile internal = getInternalProfile(profileHeader);
        if (internal != null) {
            return internal;
        }

        ICC_ColorSpace cached = ColorSpaces.getCachedCS(profileHeader);
        if (cached != null) {
            return cached.getProfile();
        }

        ICC_Profile profile = ICC_Profile.getInstance(limit(input, size));

        // We'll validate & cache by creating a color space and returning its profile...
        // TODO: Rewrite with separate cache for profiles...
        return ColorSpaces.createColorSpace(profile).getProfile();
    }

    private static byte[] limit(byte[] input, int size) {
        return input.length == size ? input : Arrays.copyOf(input, size);
    }

    private static int validateAndGetSize(byte[] input) {
        int size = validateHeaderAndGetSize(input);

        if (size < 0 || size > input.length) {
            throw new IllegalArgumentException("Truncated ICC profile data, length < " + size + ": " + input.length);
        }

        return size;
    }

    private static int validateHeaderAndGetSize(byte[] input) {
        Objects.requireNonNull(input, "input");

        if (input.length < ICC_PROFILE_HEADER_SIZE) { // Can't be less than size of ICC header
            throw new IllegalArgumentException("Truncated ICC profile data, length < 128: " + input.length);
        }

        int size = intBigEndian(input, ICC_Profile.icHdrSize);

        if (intBigEndian(input, ICC_Profile.icHdrMagic) != ICC_PROFILE_MAGIC) {
            throw new IllegalArgumentException("Not an ICC profile, missing file signature");
        }

        return size;
    }

    private static ICC_Profile getInternalProfile(final byte[] profileHeader) {
        int profileCSType = getCsType(profileHeader);

        if (profileCSType == ColorSpace.TYPE_RGB && Arrays.equals(profileHeader, sRGB.header)) {
            return ICC_Profile.getInstance(ColorSpace.CS_sRGB);
        }
        else if (profileCSType == ColorSpace.TYPE_GRAY && Arrays.equals(profileHeader, GRAY.header)) {
            return ICC_Profile.getInstance(ColorSpace.CS_GRAY);
        }
        else if (profileCSType == ColorSpace.TYPE_3CLR && Arrays.equals(profileHeader, PYCC.header)) {
            return ICC_Profile.getInstance(ColorSpace.CS_PYCC);
        }
        else if (profileCSType == ColorSpace.TYPE_RGB && Arrays.equals(profileHeader, LINEAR_RGB.header)) {
            return ICC_Profile.getInstance(ColorSpace.CS_LINEAR_RGB);
        }
        else if (profileCSType == ColorSpace.TYPE_XYZ && Arrays.equals(profileHeader, CIEXYZ.header)) {
            return ICC_Profile.getInstance(ColorSpace.CS_CIEXYZ);
        }

        return null;
    }

    private static int intBigEndian(byte[] data, int index) {
        return (data[index] & 0xff) << 24 | (data[index + 1] & 0xff) << 16 | (data[index + 2] & 0xff) << 8 | (data[index + 3] & 0xff);
    }

    private static int getCsType(byte[] profileHeader) {
        int csSig = intBigEndian(profileHeader, ICC_Profile.icHdrColorSpace);

        switch (csSig) {
            case ICC_Profile.icSigXYZData:
                return ColorSpace.TYPE_XYZ;
            case ICC_Profile.icSigLabData:
                return ColorSpace.TYPE_Lab;
            case ICC_Profile.icSigLuvData:
                return ColorSpace.TYPE_Luv;
            case ICC_Profile.icSigYCbCrData:
                return ColorSpace.TYPE_YCbCr;
            case ICC_Profile.icSigYxyData:
                return ColorSpace.TYPE_Yxy;
            case ICC_Profile.icSigRgbData:
                return ColorSpace.TYPE_RGB;
            case ICC_Profile.icSigGrayData:
                return ColorSpace.TYPE_GRAY;
            case ICC_Profile.icSigHsvData:
                return ColorSpace.TYPE_HSV;
            case ICC_Profile.icSigHlsData:
                return ColorSpace.TYPE_HLS;
            case ICC_Profile.icSigCmykData:
                return ColorSpace.TYPE_CMYK;
            // Note: There is no TYPE_* 10...
            case ICC_Profile.icSigCmyData:
                return ColorSpace.TYPE_CMY;
            case ICC_Profile.icSigSpace2CLR:
                return ColorSpace.TYPE_2CLR;
            case ICC_Profile.icSigSpace3CLR:
                return ColorSpace.TYPE_3CLR;
            case ICC_Profile.icSigSpace4CLR:
                return ColorSpace.TYPE_4CLR;
            case ICC_Profile.icSigSpace5CLR:
                return ColorSpace.TYPE_5CLR;
            case ICC_Profile.icSigSpace6CLR:
                return ColorSpace.TYPE_6CLR;
            case ICC_Profile.icSigSpace7CLR:
                return ColorSpace.TYPE_7CLR;
            case ICC_Profile.icSigSpace8CLR:
                return ColorSpace.TYPE_8CLR;
            case ICC_Profile.icSigSpace9CLR:
                return ColorSpace.TYPE_9CLR;
            case ICC_Profile.icSigSpaceACLR:
                return ColorSpace.TYPE_ACLR;
            case ICC_Profile.icSigSpaceBCLR:
                return ColorSpace.TYPE_BCLR;
            case ICC_Profile.icSigSpaceCCLR:
                return ColorSpace.TYPE_CCLR;
            case ICC_Profile.icSigSpaceDCLR:
                return ColorSpace.TYPE_DCLR;
            case ICC_Profile.icSigSpaceECLR:
                return ColorSpace.TYPE_ECLR;
            case ICC_Profile.icSigSpaceFCLR:
                return ColorSpace.TYPE_FCLR;
            default:
                throw new IllegalArgumentException("Invalid ICC color space signature: " + csSig); // TODO: fourCC?
        }
    }

    static void fixProfile(ICC_Profile profile) {
        profileCleaner.fixProfile(profile);
    }

    static boolean validationAltersProfileHeader() {
        return profileCleaner.validationAltersProfileHeader();
    }

    // Cache header profile data to avoid excessive array creation/copying. Use static inner class for on-demand lazy init
    static class sRGB {
        static final byte[] header = getProfileHeaderWithProfileId(ICC_Profile.getInstance(ColorSpace.CS_sRGB));
    }

    static class CIEXYZ {
        static final byte[] header = getProfileHeaderWithProfileId(ICC_Profile.getInstance(ColorSpace.CS_CIEXYZ));
    }

    static class PYCC {
        static final byte[] header = getProfileHeaderWithProfileId(ICC_Profile.getInstance(ColorSpace.CS_PYCC));
    }

    static class GRAY {
        static final byte[] header = getProfileHeaderWithProfileId(ICC_Profile.getInstance(ColorSpace.CS_GRAY));
    }

    static class LINEAR_RGB {
        static final byte[] header = getProfileHeaderWithProfileId(ICC_Profile.getInstance(ColorSpace.CS_LINEAR_RGB));
    }

}
