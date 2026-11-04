// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.controls;

import javafx.scene.Node;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// A prominent action button. Nonempty text creates an extended floating action button.
@NotNullByDefault
public class M3FloatingActionButton extends M3ButtonBase {
    /// Creates an empty button.
    public M3FloatingActionButton() {
        this("", null);
    }

    /// Creates a button with the supplied optional text.
    public M3FloatingActionButton(@Nullable String text) {
        this(text, null);
    }

    /// Creates a button displaying the supplied graphic without text.
    public M3FloatingActionButton(Node graphic) {
        this("", graphic);
    }

    /// Creates a button with optional text and graphic; the graphic must not have another parent.
    public M3FloatingActionButton(@Nullable String text, @Nullable Node graphic) {
        super(text, graphic);
        getStyleClass().add("m3-fab");
        setVariant(M3ButtonVariant.TONAL);
    }
}
