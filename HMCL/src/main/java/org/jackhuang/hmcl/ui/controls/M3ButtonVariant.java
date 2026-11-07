// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.controls;

import org.jetbrains.annotations.NotNullByDefault;

/// The five baseline Material Design 3 command-button emphasis levels.
@NotNullByDefault
public enum M3ButtonVariant {
    /// A high-emphasis primary action.
    FILLED,
    /// A medium-emphasis action with a secondary container.
    TONAL,
    /// A medium-emphasis action with an outline.
    OUTLINED,
    /// A low-emphasis action without a resting container.
    TEXT,
    /// An action raised above its surface.
    ELEVATED
}
