// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.controls;

import javafx.scene.control.ButtonBase;
import org.jetbrains.annotations.NotNullByDefault;

/// A group of command buttons with optional connected corners.
@NotNullByDefault
public class M3ButtonGroup extends M3ButtonGroupBase<ButtonBase> {
    /// Creates an empty group.
    public M3ButtonGroup() {
    }

    /// Creates a group containing the supplied distinct, unowned buttons in order.
    public M3ButtonGroup(ButtonBase... items) {
        this();
        getItems().addAll(items);
    }
}
