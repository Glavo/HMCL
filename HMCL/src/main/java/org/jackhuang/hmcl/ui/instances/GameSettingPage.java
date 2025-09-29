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
package org.jackhuang.hmcl.ui.instances;

import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import org.jackhuang.hmcl.setting.GameSetting;
import org.jackhuang.hmcl.setting.GlobalGameSetting;
import org.jackhuang.hmcl.setting.InstanceGameSetting;
import org.jetbrains.annotations.NotNull;

/// @author Glavo
public final class GameSettingPage<S extends GameSetting> extends Control {
    private final Class<S> settingType;

    public GameSettingPage(@NotNull Class<S> settingType) {
        assert settingType == InstanceGameSetting.class || settingType == GlobalGameSetting.class;
        this.settingType = settingType;
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new GameSettingPageSkin<>(this);
    }
}
