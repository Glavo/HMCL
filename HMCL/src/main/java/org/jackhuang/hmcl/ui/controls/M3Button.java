// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.controls;

import javafx.scene.Node;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// A labeled Material Design 3 command button, initially filled, with an optional graphic.
@NotNullByDefault
public class M3Button extends M3ButtonBase {
    /// Creates an empty filled button.
    public M3Button() {
        this("", null, M3ButtonVariant.FILLED);
    }

    /// Creates a filled button with text.
    public M3Button(@Nullable String text) {
        this(text, null, M3ButtonVariant.FILLED);
    }

    /// Creates a filled button with text and an optional graphic.
    public M3Button(@Nullable String text, @Nullable Node graphic) {
        this(text, graphic, M3ButtonVariant.FILLED);
    }

    /// Creates a text button with the requested emphasis.
    public M3Button(@Nullable String text, M3ButtonVariant variant) {
        this(text, null, variant);
    }

    /// Creates a button with text, an optional graphic, and the requested emphasis.
    public M3Button(@Nullable String text, @Nullable Node graphic, M3ButtonVariant variant) {
        super(text, graphic, variant);
        getStyleClass().add("m3-button");
    }
}
