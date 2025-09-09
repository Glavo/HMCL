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
package org.jackhuang.hmcl.setting;

import com.google.gson.annotations.SerializedName;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;

import java.util.LinkedHashSet;
import java.util.UUID;

/// @author Glavo
public final class InstanceGameSetting extends GameSetting {

    private final ObjectProperty<UUID> parent = new SimpleObjectProperty<>(this, "parent");

    public ObjectProperty<UUID> parentProperty() {
        return parent;
    }

    private final ObservableSet<String> overrides = FXCollections.observableSet(new LinkedHashSet<>());

    public ObservableSet<String> getOverrides() {
        return overrides;
    }

    @SerializedName("versionIcon")
    private final ObjectProperty<VersionIconType> versionIcon = new SimpleObjectProperty<>(this, "versionIcon", VersionIconType.DEFAULT);

    public ObjectProperty<VersionIconType> versionIconProperty() {
        return versionIcon;
    }

    @SerializedName("isolation")
    private final BooleanProperty isolation = new SimpleBooleanProperty(this, "isolation", false);

    public BooleanProperty isolationProperty() {
        return isolation;
    }

}
