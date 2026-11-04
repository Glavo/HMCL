// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.controls.skin;

import org.jackhuang.hmcl.ui.controls.M3ToggleButtonBase;
import org.jetbrains.annotations.NotNullByDefault;

/// Renders labeled Material button content with shared activation and fading feedback.
@NotNullByDefault
public final class M3ToggleButtonSkin extends M3LabeledButtonSkinBase<M3ToggleButtonBase> {
    /// Creates a skin for the supplied Material button.
    public M3ToggleButtonSkin(M3ToggleButtonBase control) {
        super(control);
    }
}
