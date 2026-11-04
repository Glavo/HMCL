// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.controls.skin;

import javafx.scene.layout.Region;
import org.jackhuang.hmcl.ui.controls.M3CheckBox;
import org.jetbrains.annotations.NotNullByDefault;

/// Renders the Material check box indicator and labeled content.
@NotNullByDefault
public final class M3CheckBoxSkin extends M3SelectionControlSkinBase<M3CheckBox> {
    /// Creates the indicator and observes the control's selection paints.
    public M3CheckBoxSkin(M3CheckBox control) {
        super(control, "box", "mark");
        registerChangeListener(control.checkedColorProperty(), value -> control.requestLayout());
        registerChangeListener(control.unCheckedColorProperty(), value -> control.requestLayout());
        registerChangeListener(control.selectedProperty(), value -> control.requestLayout());
        registerChangeListener(control.indeterminateProperty(), value -> control.requestLayout());
    }

    /// Applies the current indicator paints while preserving CSS geometry.
    @Override
    protected void updatePaints(Region indicator, Region mark) {
        M3CheckBox control = getSkinnable();
        boolean checked = control.isSelected() || control.isIndeterminate();
        M3SelectionPaints.outline(indicator, checked ? control.getCheckedColor() : control.getUnCheckedColor());
        M3SelectionPaints.fill(indicator, checked ? control.getCheckedColor() : null);
    }
}
