// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.controls.skin;

import org.jackhuang.hmcl.ui.controls.M3SplitButton;
import org.jetbrains.annotations.NotNullByDefault;

/// Renders a Material split button with shared labeled content and popup behavior.
@NotNullByDefault
public final class M3SplitButtonSkin extends M3MenuButtonSkinBase<M3SplitButton> {
    /// Creates the Material split button skin.
    public M3SplitButtonSkin(M3SplitButton control) {
        super(control, true);
    }
}
