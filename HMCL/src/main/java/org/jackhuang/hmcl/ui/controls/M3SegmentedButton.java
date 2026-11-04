// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.controls;

import javafx.scene.Node;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// A selectable segment that can belong to an M3SegmentedButtonGroup.
@NotNullByDefault
public class M3SegmentedButton extends M3ToggleButtonBase {
    /// Creates an empty button.
    public M3SegmentedButton() {
        this("", null);
    }

    /// Creates a button with optional text.
    public M3SegmentedButton(@Nullable String text) {
        this(text, null);
    }

    /// Creates a button with a graphic and no text.
    public M3SegmentedButton(Node graphic) {
        this("", graphic);
    }

    /// Creates a button with optional text and graphic.
    public M3SegmentedButton(@Nullable String text, @Nullable Node graphic) {
        super(text, graphic);
        getStyleClass().setAll("m3-button-base", "m3-segmented-button");
        setVariant(M3ButtonVariant.OUTLINED);
    }
}
