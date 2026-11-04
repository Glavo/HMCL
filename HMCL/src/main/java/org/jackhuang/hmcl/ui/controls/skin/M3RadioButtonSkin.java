// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.controls.skin;

import javafx.scene.control.skin.RadioButtonSkin;
import javafx.scene.layout.Region;
import org.jackhuang.hmcl.ui.controls.M3RadioButton;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// Adds a fading Material state layer to the native JavaFX RadioButtonSkin.
@NotNullByDefault
public final class M3RadioButtonSkin extends RadioButtonSkin {
    /// Overlay; absent while the superclass constructor is initializing children.
    private @Nullable M3StateLayer stateLayer;

    /// Creates a skin preserving native keyboard, pointer, and accessibility behavior.
    public M3RadioButtonSkin(M3RadioButton control) {
        super(control);
        stateLayer = new M3StateLayer(control);
        getChildren().add(0, stateLayer);
        registerChangeListener(control.selectedColorProperty(), value -> control.requestLayout());
        registerChangeListener(control.unSelectedColorProperty(), value -> control.requestLayout());
        registerChangeListener(control.selectedProperty(), value -> control.requestLayout());
    }

    /// Restores the layer when native labeled-content changes rebuild the child list.
    @Override
    protected void updateChildren() {
        super.updateChildren();
        if (stateLayer != null && !getChildren().contains(stateLayer))
            getChildren().add(0, stateLayer);
    }

    /// Lays out native content before positioning the stationary overlay.
    @Override
    protected void layoutChildren(double x, double y, double width, double height) {
        super.layoutChildren(x, y, width, height);
        if (stateLayer != null) {
            if (!getChildren().contains(stateLayer))
                getChildren().add(0, stateLayer);
            stateLayer.layoutFeedback(".radio");
        }
        M3RadioButton control = (M3RadioButton) getSkinnable();
        if (control.lookup(".radio") instanceof Region indicator) {
            M3SelectionPaints.outline(indicator, control.isSelected() ? control.getSelectedColor() : control.getUnSelectedColor());
            if (indicator.lookup(".dot") instanceof Region dot)
                M3SelectionPaints.fill(dot, control.getSelectedColor());
        }
    }

    /// Stops feedback and releases native input handlers and listeners.
    @Override
    public void dispose() {
        if (stateLayer != null) {
            stateLayer.dispose();
            getChildren().remove(stateLayer);
            stateLayer = null;
        }
        super.dispose();
    }
}
