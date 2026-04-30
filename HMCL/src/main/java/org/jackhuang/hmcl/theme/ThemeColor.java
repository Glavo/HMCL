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
package org.jackhuang.hmcl.theme;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.WeakListener;
import javafx.beans.property.Property;
import javafx.scene.control.ColorPicker;
import javafx.scene.paint.Color;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.util.Lang;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.List;

/// @author Glavo
@NotNullByDefault
public sealed interface ThemeColor {

    Preset DEFAULT = new Preset("default", Color.web("#5C6BC0"));

    List<Preset> PRESETS = List.of(
            DEFAULT,
            new Preset("blue", Color.web("#5C6BC0")),
            new Preset("darker_blue", Color.web("#283593")),
            new Preset("green", Color.web("#43A047")),
            new Preset("orange", Color.web("#E67E22")),
            new Preset("purple", Color.web("#9C27B0")),
            new Preset("red", Color.web("#B71C1C"))
    );

    List<ThemeColor> BUILTIN = Lang.merge(PRESETS, List.of(
            FollowSystem.INSTANCE,
            FollowBackground.INSTANCE
    ));

    static @Nullable ThemeColor of(@Nullable String name) {
        if (name == null)
            return null;
        if (!name.startsWith("#")) {
            for (ThemeColor builtin : BUILTIN) {
                if (name.equalsIgnoreCase(builtin.name()))
                    return builtin;
            }
        }

        try {
            return new Custom(Color.web(name));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static ThemeColor fromJson(JsonElement json) throws JsonParseException {
        if (json instanceof JsonPrimitive primitive) {
            //noinspection DataFlowIssue
            return ThemeColor.of(primitive.getAsString());
        }

        throw new JsonParseException("Invalid JSON element for ThemeColor: " + json);
    }

    String name();

    record Preset(String name, Color color) implements ThemeColor {
    }

    final class FollowSystem implements ThemeColor {
        public static FollowSystem INSTANCE = new FollowSystem();

        private FollowSystem() {
        }

        @Override
        public String name() {
            return "follow_system";
        }
    }

    final class FollowBackground implements ThemeColor {
        public static FollowBackground INSTANCE = new FollowBackground();

        private FollowBackground() {
        }

        @Override
        public String name() {
            return "follow_background";
        }
    }

    record Custom(Color color) implements ThemeColor {
        private static final class BidirectionalBinding implements InvalidationListener, WeakListener {
            private final WeakReference<ColorPicker> colorPickerRef;
            private final WeakReference<Property<@Nullable Custom>> propertyRef;
            private final int hashCode;

            private boolean updating = false;

            private BidirectionalBinding(ColorPicker colorPicker, Property<@Nullable Custom> property) {
                this.colorPickerRef = new WeakReference<>(colorPicker);
                this.propertyRef = new WeakReference<>(property);
                this.hashCode = System.identityHashCode(colorPicker) ^ System.identityHashCode(property);
            }

            @Override
            public void invalidated(Observable sourceProperty) {
                if (!updating) {
                    final ColorPicker colorPicker = colorPickerRef.get();
                    final Property<@Nullable Custom> property = propertyRef.get();

                    if (colorPicker == null || property == null) {
                        if (colorPicker != null) {
                            colorPicker.valueProperty().removeListener(this);
                        }

                        if (property != null) {
                            property.removeListener(this);
                        }
                    } else {
                        updating = true;
                        try {
                            if (property == sourceProperty) {
                                Custom newValue = property.getValue();
                                colorPicker.setValue(newValue != null ? newValue.color() : null);
                            } else {
                                Color newValue = colorPicker.getValue();
                                property.setValue(newValue != null ? new Custom(newValue) : null);
                            }
                        } finally {
                            updating = false;
                        }
                    }
                }
            }

            @Override
            public boolean wasGarbageCollected() {
                return colorPickerRef.get() == null || propertyRef.get() == null;
            }

            @Override
            public int hashCode() {
                return hashCode;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o)
                    return true;
                if (!(o instanceof BidirectionalBinding that))
                    return false;

                final ColorPicker colorPicker = this.colorPickerRef.get();
                final Property<@Nullable Custom> property = this.propertyRef.get();

                final ColorPicker thatColorPicker = that.colorPickerRef.get();
                final Property<?> thatProperty = that.propertyRef.get();

                if (colorPicker == null || property == null || thatColorPicker == null || thatProperty == null)
                    return false;

                return colorPicker == thatColorPicker && property == thatProperty;
            }
        }

        public static void bindBidirectional(ColorPicker colorPicker, Property<@Nullable Custom> property) {
            var binding = new BidirectionalBinding(colorPicker, property);

            colorPicker.valueProperty().removeListener(binding);
            property.removeListener(binding);

            Custom themeColor = property.getValue();
            colorPicker.setValue(themeColor != null ? themeColor.color() : null);

            colorPicker.valueProperty().addListener(binding);
            property.addListener(binding);
        }

        @Override
        public String name() {
            return FXUtils.getColorDisplayName(color);
        }
    }

}
