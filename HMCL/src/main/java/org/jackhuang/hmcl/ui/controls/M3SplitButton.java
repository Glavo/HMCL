// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.controls;

import javafx.event.ActionEvent;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Skin;
import org.jackhuang.hmcl.ui.controls.skin.M3SplitButtonSkin;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// A Material button with independent primary and popup actions.
/// [#fire()] invokes only the primary action. The trailing button and Down key open the menu without firing it.
@NotNullByDefault
public class M3SplitButton extends M3MenuButton {
    /// Creates an empty split button.
    public M3SplitButton() {
        this("", null);
    }

    /// Creates an empty menu with optional primary-action text.
    public M3SplitButton(@Nullable String text) {
        this(text, null);
    }

    /// Creates primary-action content and non-null menu items not owned by another menu.
    public M3SplitButton(@Nullable String text, @Nullable Node graphic, MenuItem... items) {
        super(text, graphic, items);
        getStyleClass().remove("m3-menu-button");
        getStyleClass().add("m3-split-button");
        setAccessibleRole(AccessibleRole.SPLIT_MENU_BUTTON);
    }

    /// Dispatches the primary action unless disabled, without changing popup visibility.
    @Override
    public void fire() {
        if (!isDisabled())
            fireEvent(new ActionEvent(this, this));
    }

    /// Creates the skin containing independent command and menu targets.
    @Override
    protected Skin<?> createDefaultSkin() {
        return new M3SplitButtonSkin(this);
    }
}
