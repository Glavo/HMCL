// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.controls.skin;

import javafx.scene.layout.Region;
import org.jackhuang.hmcl.ui.controls.M3RadioButton;
import org.jetbrains.annotations.NotNullByDefault;

/// Renders the Material radio button indicator and labeled content.
@NotNullByDefault
public final class M3RadioButtonSkin extends M3SelectionControlSkinBase<M3RadioButton> {
    /// Creates the indicator and observes the control's selection paints.
    public M3RadioButtonSkin(M3RadioButton control) {
        super(control, "radio", "dot");
        registerChangeListener(control.selectedColorProperty(), value -> control.requestLayout());
        registerChangeListener(control.unSelectedColorProperty(), value -> control.requestLayout());
        registerChangeListener(control.selectedProperty(), value -> control.requestLayout());
    }

    /// Applies the current indicator paints while preserving CSS geometry.
    @Override
    protected void updatePaints(Region indicator, Region mark) {
        M3RadioButton control = getSkinnable();
        M3SelectionPaints.outline(indicator, control.isSelected() ? control.getSelectedColor() : control.getUnSelectedColor());
        M3SelectionPaints.fill(mark, control.getSelectedColor());
    }
}
