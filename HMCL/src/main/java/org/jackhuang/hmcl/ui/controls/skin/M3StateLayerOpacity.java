// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.controls.skin;

import javafx.animation.Transition;
import javafx.beans.property.DoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.css.StyleOrigin;
import javafx.css.StyleableProperty;
import org.jackhuang.hmcl.ui.animation.Motion;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;

/// Animates a CSS opacity target without changing its style origin or geometry.
@NotNullByDefault
final class M3StateLayerOpacity implements ChangeListener<Number> {
    /// The styleable opacity being animated.
    private final DoubleProperty property;

    /// Whether CSS changes must be applied immediately.
    private final BooleanSupplier immediate;

    /// Active fade, if any.
    private @Nullable Transition transition;

    /// Latest CSS opacity target.
    private double target;

    /// Origin of the latest CSS value.
    private @Nullable StyleOrigin origin;

    /// Prevents animation writes from being interpreted as new CSS targets.
    private boolean applying;

    /// Observes a styleable opacity property until disposal.
    M3StateLayerOpacity(DoubleProperty property, BooleanSupplier immediate) {
        this.property = property;
        this.immediate = immediate;
        target = 0;
        apply(0);
        property.addListener(this);
    }

    /// Starts or reverses the whole-layer fade from its current visible opacity.
    @Override
    public void changed(ObservableValue<? extends Number> observable, Number previous, Number next) {
        if (applying)
            return;
        if (!immediate.getAsBoolean() && transition != null && next.doubleValue() == target) {
            apply(previous.doubleValue());
            return;
        }
        if (transition != null)
            transition.stop();
        target = next.doubleValue();
        origin = ((StyleableProperty<?>) property).getStyleOrigin();
        if (immediate.getAsBoolean())
            return;
        double start = previous.doubleValue();
        transition = new Transition() {
            {
                setCycleDuration(Motion.SHORT3);
                setInterpolator(Motion.STANDARD);
            }

            /// Updates the entire layer's opacity.
            @Override
            protected void interpolate(double fraction) {
                apply(start + (target - start) * fraction);
            }
        };
        apply(start);
        transition.play();
    }

    /// Writes an animation frame while preserving CSS priority.
    @SuppressWarnings("unchecked")
    private void apply(double value) {
        applying = true;
        try {
            ((StyleableProperty<Number>) property).applyStyle(origin, value);
        } finally {
            applying = false;
        }
    }

    /// Stops the current transition at its latest target.
    void finish() {
        if (transition != null) {
            transition.stop();
            transition = null;
        }
        apply(target);
    }

    /// Stops animation and removes the property listener.
    void dispose() {
        finish();
        property.removeListener(this);
    }
}
