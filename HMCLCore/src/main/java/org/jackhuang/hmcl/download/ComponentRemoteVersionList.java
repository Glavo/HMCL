/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.download;

import org.jackhuang.hmcl.download.game.GameRemoteVersion;
import org.jackhuang.hmcl.game.GameComponentType;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.AbstractList;
import java.util.SortedSet;

@NotNullByDefault
public final class ComponentRemoteVersionList<V extends ComponentRemoteVersion> extends AbstractList<V> {
    private static final ComponentRemoteVersionList<?>[] EMPTY_LISTS = new ComponentRemoteVersionList<?>[GameComponentType.ALL.size()];

    static {
        GameRemoteVersion[] emptyArray = new GameRemoteVersion[0];

        for (GameComponentType type : GameComponentType.ALL) {
            EMPTY_LISTS[type.ordinal()] = new ComponentRemoteVersionList<>(type, emptyArray);
        }
    }


    public static <V extends ComponentRemoteVersion> ComponentRemoteVersionList<V> of(GameComponentType type) {
        @SuppressWarnings("unchecked")
        ComponentRemoteVersionList<V> list = (ComponentRemoteVersionList<V>) EMPTY_LISTS[type.ordinal()];
        return list;
    }

    @SuppressWarnings("unchecked")
    public static <V extends ComponentRemoteVersion> ComponentRemoteVersionList<V> of(GameComponentType type, SortedSet<? extends V> elements) {
        return new ComponentRemoteVersionList<>(type, elements.toArray((V[]) new GameRemoteVersion[elements.size()]));
    }

    private final GameComponentType type;
    private final V[] elements;

    private ComponentRemoteVersionList(GameComponentType type, V[] elements) {
        this.elements = elements;
        this.type = type;
    }

    @Override
    public V get(int index) {
        return elements[index];
    }

    @Override
    public int size() {
        return elements.length;
    }
}
