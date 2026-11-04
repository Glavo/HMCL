// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.controls.skin;

import javafx.scene.control.skin.SplitMenuButtonSkin;
import org.jackhuang.hmcl.ui.controls.M3SplitButton;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// Adds a fading Material state layer to the native JavaFX SplitMenuButtonSkin.
@NotNullByDefault
public final class M3SplitButtonSkin extends SplitMenuButtonSkin {
    /// Overlay; absent while the superclass constructor is initializing children.
    private @Nullable M3StateLayer stateLayer;

    /// Creates a skin preserving native keyboard, pointer, and accessibility behavior.
    public M3SplitButtonSkin(M3SplitButton control) {
        super(control);
        stateLayer = new M3StateLayer(control);
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
