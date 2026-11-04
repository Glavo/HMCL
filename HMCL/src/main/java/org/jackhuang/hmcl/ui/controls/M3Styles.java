// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.controls;

import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Resolves bundled Material component stylesheets.
@NotNullByDefault
final class M3Styles {
    /// Prevents instantiation.
    private M3Styles() {
    }

    /// Returns the bundled stylesheet URL.
    static String stylesheet(String name) {
        return Objects.requireNonNull(M3Styles.class.getResource("/assets/css/controls/" + name + ".css")).toExternalForm();
    }

}
