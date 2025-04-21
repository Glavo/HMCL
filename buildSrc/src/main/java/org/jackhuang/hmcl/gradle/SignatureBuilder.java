/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025 huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.gradle;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Map;
import java.util.TreeMap;

/**
 * @author Glavo
 */
public final class SignatureBuilder {
    private final Path keyLocation;
    final TreeMap<String, byte[]> sha512 = new TreeMap<>();
    final MessageDigest md;

    public SignatureBuilder(Path keyLocation) throws NoSuchAlgorithmException {
        this.keyLocation = keyLocation;
        this.md = MessageDigest.getInstance("SHA-512");
    }

    public void add(String name, byte[] content) {
        md.reset();
        md.update(content);
        sha512.put(name, md.digest());
    }

    public byte[] sign() throws GeneralSecurityException, IOException {
        Signature signer = Signature.getInstance("SHA512withRSA");
        signer.initSign(KeyFactory.getInstance("RSA").generatePrivate(
                new PKCS8EncodedKeySpec(Files.readAllBytes(keyLocation))));

        for (Map.Entry<String, byte[]> entry : sha512.entrySet()) {
            String name = entry.getKey();
            byte[] content = entry.getValue();
            md.reset();
            md.update(name.getBytes(StandardCharsets.UTF_8));

            signer.update(md.digest());
            signer.update(content);
        }

        return signer.sign();
    }
}
