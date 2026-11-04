// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.controls;

import org.jetbrains.annotations.NotNullByDefault;

/// A connected group of selectable segments, initially requiring one selection.
@NotNullByDefault
public class M3SegmentedButtonGroup extends M3SelectionButtonGroup<M3SegmentedButton> {
    /// Creates an empty group.
    public M3SegmentedButtonGroup() {
        setVariant(M3ButtonGroupVariant.CONNECTED);
        setSpacing(0);
        setEmptySelectionAllowed(false);
    }

    /// Creates a group containing the supplied distinct, unowned buttons in order.
    public M3SegmentedButtonGroup(M3SegmentedButton... items) {
        this();
        getItems().addAll(items);
    }
}
