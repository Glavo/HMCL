// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.controls;

import javafx.css.PseudoClass;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Locale;

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
    ELEVATED;

    /// Shared CSS state, resolved once per variant rather than for each button update.
    private final PseudoClass pseudoClass = PseudoClass.getPseudoClass(name().toLowerCase(Locale.ROOT));

    /// Returns the CSS state used by this visual emphasis.
    PseudoClass pseudoClass() {
        return pseudoClass;
    }
}
