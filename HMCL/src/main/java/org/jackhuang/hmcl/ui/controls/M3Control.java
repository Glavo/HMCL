// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.controls;

import javafx.beans.property.BooleanProperty;
import javafx.css.StyleableObjectProperty;
import javafx.scene.paint.Paint;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// Common options for Material controls with a fading interaction layer.
/// All properties must be accessed on the JavaFX application thread.
@NotNullByDefault
public interface M3Control {
    /// Returns the animation override, initially the inverse of the global animation setting.
    BooleanProperty disableAnimationProperty();

    /// Returns whether visual transitions are disabled.
    default boolean isDisableAnimation() {
        return disableAnimationProperty().get();
    }

    /// Enables or disables visual transitions; disabling settles the current state immediately.
    default void setDisableAnimation(boolean value) {
        disableAnimationProperty().set(value);
    }

    /// Returns the overlay paint, styled with `-m3-state-layer-fill`.
    /// A null value uses the labeled control's text paint. The layer fades as a whole and never expands.
    StyleableObjectProperty<@Nullable Paint> stateLayerFillProperty();

    /// Returns the explicit overlay paint, or null to follow the text paint.
    default @Nullable Paint getStateLayerFill() {
        return stateLayerFillProperty().get();
    }

    /// Sets the overlay paint, or null to follow the text paint.
    default void setStateLayerFill(@Nullable Paint value) {
        stateLayerFillProperty().set(value);
    }
}
