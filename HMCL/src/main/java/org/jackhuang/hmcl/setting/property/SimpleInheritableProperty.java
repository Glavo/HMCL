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
package org.jackhuang.hmcl.setting.property;

import javafx.beans.property.SimpleObjectProperty;
import org.jackhuang.hmcl.setting.GameSetting;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

/// @author Glavo
@NotNullByDefault
public final class SimpleInheritableProperty<T>
        extends SimpleObjectProperty<@Nullable T>
        implements InheritableProperty<T> {
    private final @UnknownNullability T defaultValue;

    public SimpleInheritableProperty(GameSetting bean, String name) {
        super(bean, name);
        this.defaultValue = null;
    }

    public SimpleInheritableProperty(GameSetting bean, String name, T defaultValue) {
        super(bean, name, bean instanceof GameSetting.Global ? defaultValue : null);
        this.defaultValue = defaultValue;
    }

    @Override
    public GameSetting getBean() {
        return (GameSetting) super.getBean();
    }

    @Override
    public @UnknownNullability T defaultValue() {
        return defaultValue;
    }
}
