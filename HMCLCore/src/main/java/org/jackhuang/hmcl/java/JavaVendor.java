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
package org.jackhuang.hmcl.java;

import org.jackhuang.hmcl.util.StringUtils;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Objects;

public final class JavaVendor {

    public static final JavaVendor ORACLE = new JavaVendor("Oracle");
    public static final JavaVendor ADOPTIUM = new JavaVendor("Adoptium");
    public static final JavaVendor AMAZON = new JavaVendor("Amazon");
    public static final JavaVendor AZUL = new JavaVendor("Azul");
    public static final JavaVendor BELLSOFT = new JavaVendor("BellSoft");
    public static final JavaVendor BSD = new JavaVendor("BSD");
    public static final JavaVendor IBM = new JavaVendor("IBM");
    public static final JavaVendor MICROSOFT = new JavaVendor("Microsoft");
    public static final JavaVendor ZTHREAD = new JavaVendor("ZThread");

    public static @Nullable JavaVendor of(@Nullable String name) {
        if (StringUtils.isBlank(name) || "N/A".equalsIgnoreCase(name))
            return null;

        String lowerName = name.toLowerCase(Locale.ROOT);
        if (lowerName.contains("oracle"))
            return ORACLE;
        if (lowerName.contains("azul"))
            return AZUL;
        if (lowerName.contains("ibm") || lowerName.contains("international business machines"))
            return IBM;
        if (lowerName.contains("adoptium"))
            return ADOPTIUM;
        if (lowerName.contains("amazon"))
            return AMAZON;
        if (lowerName.contains("bellsoft"))
            return BELLSOFT;
        if (lowerName.contains("bsd porting") || lowerName.equals("bsd"))
            return BSD;
        if (lowerName.contains("microsoft"))
            return MICROSOFT;

        return new JavaVendor(name);
    }

    private final String name;

    public JavaVendor(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof JavaVendor)) return false;
        JavaVendor that = (JavaVendor) o;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name);
    }

    @Override
    public String toString() {
        return name;
    }
}
