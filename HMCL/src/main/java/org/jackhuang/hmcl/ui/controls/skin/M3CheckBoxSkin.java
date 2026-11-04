// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.controls.skin;

import javafx.scene.control.skin.CheckBoxSkin;
import javafx.scene.layout.Region;
import org.jackhuang.hmcl.ui.controls.M3CheckBox;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// Adds a fading Material state layer to the native JavaFX CheckBoxSkin.
@NotNullByDefault
public final class M3CheckBoxSkin extends CheckBoxSkin {
    /// Overlay; absent while the superclass constructor is initializing children.
    private @Nullable M3StateLayer stateLayer;

    /// Creates a skin preserving native keyboard, pointer, and accessibility behavior.
    public M3CheckBoxSkin(M3CheckBox control) {
        super(control);
        stateLayer = new M3StateLayer(control);
        getChildren().add(0, stateLayer);
        registerChangeListener(control.checkedColorProperty(), value -> control.requestLayout());
        registerChangeListener(control.unCheckedColorProperty(), value -> control.requestLayout());
        registerChangeListener(control.selectedProperty(), value -> control.requestLayout());
        registerChangeListener(control.indeterminateProperty(), value -> control.requestLayout());
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
            stateLayer.layoutFeedback(".box");
        }
        M3CheckBox control = (M3CheckBox) getSkinnable();
        if (control.lookup(".box") instanceof Region indicator) {
            boolean checked = control.isSelected() || control.isIndeterminate();
            M3SelectionPaints.outline(indicator, checked ? control.getCheckedColor() : control.getUnCheckedColor());
            M3SelectionPaints.fill(indicator, checked ? control.getCheckedColor() : null);
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
