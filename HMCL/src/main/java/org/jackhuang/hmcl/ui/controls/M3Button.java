// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.controls;

import javafx.scene.Node;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// A Material command button with JavaFX Button semantics.
@NotNullByDefault
public class M3Button extends M3ButtonBase {
    /// Creates an empty button.
    public M3Button() {
        this("", null);
    }

    /// Creates a button with the supplied optional text.
    public M3Button(@Nullable String text) {
        this(text, null);
    }

    /// Creates a button displaying the supplied graphic without text.
    public M3Button(Node graphic) {
        this("", graphic);
    }

    /// Creates a button with optional text and graphic; the graphic must not have another parent.
    public M3Button(@Nullable String text, @Nullable Node graphic) {
        super(text, graphic);
        getStyleClass().add("m3-button");
    }
}
