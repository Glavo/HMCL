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

package com.twelvemonkeys.lang;

/**
 * A utility class with some useful system-related functions.
 * <p>
 * <em>NOTE: This class is not considered part of the public API and may be
 * changed without notice</em>
 * </p>
 *
 * @author <a href="mailto:harald.kuhr@gmail.com">Harald Kuhr</a>
 * @author last modified by $Author: haku $
 * @version $Id: //depot/branches/personal/haraldk/twelvemonkeys/release-2/twelvemonkeys-core/src/main/java/com/twelvemonkeys/lang/SystemUtil.java#3 $
 */
public final class SystemUtil {

    // Disallow creating objects of this type    
    private SystemUtil() {
    }

    /**
     * Tests if a named class is generally available.
     * If a class is considered available, a call to
     * {@code Class.forName(pClassName)} will not result in an exception.
     *
     * @param pClassName the class name to test
     * @return {@code true} if available
     */
    public static boolean isClassAvailable(String pClassName) {
        return isClassAvailable(pClassName, null);
    }

    private static boolean isClassAvailable(String pClassName, ClassLoader pLoader) {
        try {
            // TODO: Sometimes init is not needed, but need to find a way to know...
            getClass(pClassName, true, pLoader);
            return true;
        } catch (SecurityException | ClassNotFoundException | LinkageError ignore) {
            // Ignore            
        }

        return false;
    }

    private static Class<?> getClass(String pClassName, boolean pInitialize, ClassLoader pLoader) throws ClassNotFoundException {
        // NOTE: We need the context class loader, as SystemUtil's
        // class loader may have a totally different class loader than
        // the original caller class (as in Class.forName(cn, false, null)).
        ClassLoader loader = pLoader != null ? pLoader :
                Thread.currentThread().getContextClassLoader();

        return Class.forName(pClassName, pInitialize, loader);
    }
}
