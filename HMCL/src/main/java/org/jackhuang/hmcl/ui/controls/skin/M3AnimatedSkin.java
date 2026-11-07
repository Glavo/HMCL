// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.controls.skin;

import org.jetbrains.annotations.NotNullByDefault;

/// A skin whose visual transitions can be settled by a subtree animation scope.
@NotNullByDefault
public interface M3AnimatedSkin {

    /// Stops active and pending transitions and renders the latest logical state without firing control actions.
    /// Deferred CSS must apply directly, and pointer-driven dragging must remain functional.
    /// Called on the JavaFX application thread; disposed skins ignore this request.
    void finishAnimations();
}
