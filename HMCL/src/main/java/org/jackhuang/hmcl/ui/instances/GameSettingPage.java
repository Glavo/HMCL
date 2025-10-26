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

import javafx.beans.property.*;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.scene.image.Image;
import org.jackhuang.hmcl.setting.*;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.construct.PageAware;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.ui.versions.VersionIconDialog;
import org.jackhuang.hmcl.ui.versions.VersionPage;
import org.jetbrains.annotations.NotNull;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// @author Glavo
public final class GameSettingPage<S extends GameSetting> extends Control
        implements DecoratorPage, VersionPage.VersionLoadable, PageAware {
    final Class<S> settingType;

    private final ObjectProperty<State> state = new SimpleObjectProperty<>(new State("", null, false, false, false));
    final ObjectProperty<S> gameSetting = new SimpleObjectProperty<>();

    Profile profile;
    String instanceId;
    final IntegerProperty maxMemory = new SimpleIntegerProperty();
    final ObjectProperty<Image> icon = new SimpleObjectProperty<>();
    final BooleanProperty modpack = new SimpleBooleanProperty();

    public GameSettingPage(@NotNull Class<S> settingType) {
        assert settingType == InstanceGameSetting.class || settingType == GlobalGameSetting.class;
        this.settingType = settingType;
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new GameSettingPageSkin<>(this);
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state;
    }

    @Override
    public void loadVersion(Profile profile, String instanceId) {
        this.profile = profile;
        this.instanceId = instanceId;

        if (instanceId == null) {
            assert settingType == GlobalGameSetting.class;

            // profile.getGlobal() ???

            state.set(State.fromTitle(Profiles.getProfileDisplayName(profile) + " - " + i18n("settings.type.global.manage")));
        } else {
            assert settingType == InstanceGameSetting.class;

            this.gameSetting.set(settingType.cast(profile.getRepository().getInstanceGameSetting(instanceId)));
        }
    }

    void loadIcon() {
        if (instanceId != null && settingType == InstanceGameSetting.class) {
            icon.set(profile.getRepository().getVersionIconImage(instanceId));
        }
    }

    void onExploreIcon() {
        if (instanceId != null && settingType == InstanceGameSetting.class) {
            Controllers.dialog(new VersionIconDialog(profile, instanceId, this::loadIcon));
        }
    }

    void onDeleteIcon() {
        if (instanceId != null && settingType == InstanceGameSetting.class) {
            profile.getRepository().deleteIconFile(instanceId);
            VersionSetting localVersionSetting = profile.getRepository().getLocalVersionSettingOrCreate(instanceId);
            if (localVersionSetting != null)
                localVersionSetting.setVersionIcon(VersionIconType.DEFAULT);
            loadIcon();
        }
    }
}
