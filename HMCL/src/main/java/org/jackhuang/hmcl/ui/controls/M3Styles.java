// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.controls;

import javafx.css.CssMetaData;
import javafx.css.Styleable;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// Resolves the standalone Material stylesheets and immutable CSS metadata.
@NotNullByDefault
final class M3Styles {
    /// Prevents instantiation.
    private M3Styles() {
    }

    /// Returns the bundled stylesheet URL.
    static String stylesheet(String name) {
        return Objects.requireNonNull(M3Styles.class.getResource("/assets/css/controls/" + name + ".css")).toExternalForm();
    }

    /// Combines inherited and additional metadata without exposing mutable lists.
    static @Unmodifiable List<CssMetaData<? extends Styleable, ?>> metadata(
            List<CssMetaData<? extends Styleable, ?>> inherited,
            List<CssMetaData<? extends Styleable, ?>> additional) {
        List<CssMetaData<? extends Styleable, ?>> result = new ArrayList<>(inherited);
        result.addAll(additional);
        return List.copyOf(result);
    }
}
