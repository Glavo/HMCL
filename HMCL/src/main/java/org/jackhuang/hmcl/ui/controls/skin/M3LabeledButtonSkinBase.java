// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.controls.skin;

import javafx.scene.control.ButtonBase;
import javafx.scene.control.skin.LabeledSkinBase;
import org.jackhuang.hmcl.ui.controls.M3Button;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// Shares labeled content, input handling, and a stationary fading layer across Material buttons.
///
/// @param <C> the button controlled by this skin
@NotNullByDefault
abstract class M3LabeledButtonSkinBase<C extends ButtonBase> extends LabeledSkinBase<C> {
    /// Overlay, absent during superclass construction.
    private @Nullable M3StateLayer stateLayer;

    /// Input handlers owned by this skin.
    private final M3ButtonBehavior behavior;

    /// Scene accelerator registration for command buttons.
    private final @Nullable M3ButtonAccelerators accelerators;

    /// Creates labeled content and installs Material behavior and feedback.
    M3LabeledButtonSkinBase(C control) {
        super(control);
        consumeMouseEvents(false);
        stateLayer = new M3StateLayer(control);
        getChildren().add(0, stateLayer);
        behavior = new M3ButtonBehavior(control);
        accelerators = control instanceof M3Button button ? new M3ButtonAccelerators(button) : null;
    }

    /// Restores the state layer after changes to labeled content rebuild the children.
    @Override
    protected void updateChildren() {
        super.updateChildren();
        if (stateLayer != null)
            getChildren().add(0, stateLayer);
    }

    /// Lays out the labeled content and full-button feedback.
    @Override
    protected void layoutChildren(double x, double y, double width, double height) {
        super.layoutChildren(x, y, width, height);
        layoutStateLayer(null);
    }

    /// Positions feedback over the button or the named selection indicator.
    protected final void layoutStateLayer(@Nullable String indicatorSelector) {
        if (stateLayer != null)
            stateLayer.layoutFeedback(indicatorSelector);
    }

    /// Limits computed width to the preferred labeled-content width.
    @Override
    protected double computeMaxWidth(double height, double top, double right, double bottom, double left) {
        return computePrefWidth(height, top, right, bottom, left);
    }

    /// Limits computed height to the preferred labeled-content height.
    @Override
    protected double computeMaxHeight(double width, double top, double right, double bottom, double left) {
        return computePrefHeight(width, top, right, bottom, left);
    }

    /// Stops animations and releases input, scene, and content listeners.
    @Override
    public void dispose() {
        behavior.dispose();
        if (accelerators != null)
            accelerators.dispose();
        if (stateLayer != null) {
            stateLayer.dispose();
            getChildren().remove(stateLayer);
            stateLayer = null;
        }
        super.dispose();
    }
}
