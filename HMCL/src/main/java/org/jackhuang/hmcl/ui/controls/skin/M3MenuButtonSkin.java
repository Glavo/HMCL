// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.controls.skin;

import org.jackhuang.hmcl.ui.controls.M3MenuButton;
import org.jetbrains.annotations.NotNullByDefault;

/// Renders a Material menu button with shared labeled content and popup behavior.
@NotNullByDefault
public final class M3MenuButtonSkin extends M3MenuButtonSkinBase<M3MenuButton> {
    /// Creates the Material menu button skin.
    public M3MenuButtonSkin(M3MenuButton control) {
        super(control, false);
    }
}
