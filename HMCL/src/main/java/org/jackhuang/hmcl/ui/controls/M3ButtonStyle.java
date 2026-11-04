// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.controls;

import javafx.css.StyleableObjectProperty;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// Shared appearance properties for command, menu, and selectable buttons.
/// Null assignments are interpreted as the documented default, including bound null values.
@NotNullByDefault
public interface M3ButtonStyle extends M3Control {
    /// Returns the visual emphasis, styled with `-m3-variant`; the default is [M3ButtonVariant#FILLED].
    StyleableObjectProperty<@Nullable M3ButtonVariant> variantProperty();

    /// Returns the effective visual emphasis.
    default M3ButtonVariant getVariant() {
        @Nullable M3ButtonVariant value = variantProperty().get();
        return value == null ? M3ButtonVariant.FILLED : value;
    }

    /// Sets the visual emphasis, or null to restore the default.
    default void setVariant(@Nullable M3ButtonVariant value) {
        variantProperty().set(value);
    }

    /// Returns the size role, styled with `-m3-size`; the default is [M3ButtonSize#SMALL].
    StyleableObjectProperty<@Nullable M3ButtonSize> sizeProperty();

    /// Returns the effective size role.
    default M3ButtonSize getSize() {
        @Nullable M3ButtonSize value = sizeProperty().get();
        return value == null ? M3ButtonSize.SMALL : value;
    }

    /// Sets the size role, or null to restore the default.
    default void setSize(@Nullable M3ButtonSize value) {
        sizeProperty().set(value);
    }

    /// Returns the resting shape, styled with `-m3-shape`; the default is [M3ButtonShape#ROUND].
    StyleableObjectProperty<@Nullable M3ButtonShape> buttonShapeProperty();

    /// Returns the effective resting shape.
    default M3ButtonShape getButtonShape() {
        @Nullable M3ButtonShape value = buttonShapeProperty().get();
        return value == null ? M3ButtonShape.ROUND : value;
    }

    /// Sets the resting shape, or null to restore the default.
    default void setButtonShape(@Nullable M3ButtonShape value) {
        buttonShapeProperty().set(value);
    }

}
