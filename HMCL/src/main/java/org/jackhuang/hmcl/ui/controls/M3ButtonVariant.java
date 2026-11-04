// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.controls;

import org.jetbrains.annotations.NotNullByDefault;

/// The visual emphasis of a Material button.
@NotNullByDefault
public enum M3ButtonVariant {
    /// A filled primary action.
    FILLED,

    /// A filled secondary action.
    TONAL,

    /// An outlined action.
    OUTLINED,

    /// An action without a resting container.
    TEXT,

    /// An action with elevation.
    ELEVATED
}
