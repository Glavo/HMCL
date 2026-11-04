// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.controls;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.css.CssMetaData;
import javafx.css.SimpleStyleableObjectProperty;
import javafx.css.Styleable;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleablePropertyFactory;
import javafx.scene.control.Control;
import javafx.scene.paint.Paint;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/// Stores per-control feedback properties without installing input handlers.
@NotNullByDefault
final class M3ControlProperties {
    /// Factory for feedback metadata shared by all Material controls.
    private static final StyleablePropertyFactory<Control> FACTORY = new StyleablePropertyFactory<>(List.of());

    /// CSS paint override; null uses the control's content paint.
    private static final CssMetaData<Control, Paint> FILL = FACTORY.createPaintCssMetaData(
            "-m3-state-layer-fill", control -> ((M3Control) control).stateLayerFillProperty(), null);

    /// Additional feedback metadata.
    static final @Unmodifiable List<CssMetaData<? extends Styleable, ?>> CSS = List.copyOf(FACTORY.getCssMetaData());

    /// Animation override.
    final BooleanProperty disableAnimation;

    /// Overlay paint override.
    final StyleableObjectProperty<@Nullable Paint> stateLayerFill;

    /// Creates properties owned by the supplied control.
    M3ControlProperties(Control control) {
        disableAnimation = new SimpleBooleanProperty(control, "disableAnimation", !AnimationUtils.isAnimationEnabled());
        stateLayerFill = new SimpleStyleableObjectProperty<>(FILL, control, "stateLayerFill", null);
    }
}
