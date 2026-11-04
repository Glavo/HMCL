// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.controls;

import org.jetbrains.annotations.NotNullByDefault;

/// A single- or multiple-selection group of icon toggles.
@NotNullByDefault
public class M3IconToggleButtonGroup extends M3SelectionButtonGroup<M3IconToggleButton> {
    /// Creates an empty group.
    public M3IconToggleButtonGroup() {
        setVariant(M3ButtonGroupVariant.CONNECTED);
        setSpacing(2);
    }

    /// Creates a group containing the supplied distinct, unowned buttons in order.
    public M3IconToggleButtonGroup(M3IconToggleButton... items) {
        this();
        getItems().addAll(items);
    }
}
