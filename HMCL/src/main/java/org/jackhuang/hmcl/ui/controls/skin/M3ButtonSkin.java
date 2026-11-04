// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.controls.skin;

import org.jackhuang.hmcl.ui.controls.M3ButtonBase;
import org.jetbrains.annotations.NotNullByDefault;

/// Renders labeled Material button content with shared activation and fading feedback.
@NotNullByDefault
public final class M3ButtonSkin extends M3LabeledButtonSkinBase<M3ButtonBase> {
    /// Creates a skin for the supplied Material button.
    public M3ButtonSkin(M3ButtonBase control) {
        super(control);
    }
}
