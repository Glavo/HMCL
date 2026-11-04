// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.controls.skin;

import javafx.scene.control.ToggleButton;
import javafx.scene.control.skin.ToggleButtonSkin;
import org.jackhuang.hmcl.ui.controls.M3Control;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// Adds a fading Material state layer to the native JavaFX ToggleButtonSkin.
@NotNullByDefault
public final class M3ToggleButtonSkin extends ToggleButtonSkin {
    /// Overlay; absent while the superclass constructor is initializing children.
    private @Nullable M3StateLayer stateLayer;

    /// Creates a skin preserving native keyboard, pointer, and accessibility behavior.
    public M3ToggleButtonSkin(ToggleButton control) {
        super(control);
        if (!(control instanceof M3Control))
            throw new IllegalArgumentException("The toggle must implement M3Control");
        stateLayer = new M3StateLayer(control);
        getChildren().add(0, stateLayer);
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
            stateLayer.layoutFeedback(null);
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
