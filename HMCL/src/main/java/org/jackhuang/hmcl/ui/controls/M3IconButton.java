// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.controls;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// An icon command button. Supply accessible text describing the action.
@NotNullByDefault
public class M3IconButton extends M3ButtonBase {
    /// Semantic width role; null selects the default width.
    private final ObjectProperty<@Nullable M3IconButtonWidth> widthRole =
            new SimpleObjectProperty<>(this, "widthRole", M3IconButtonWidth.DEFAULT) {
                /// Updates mutually exclusive width pseudo-classes.
                @Override
                protected void invalidated() {
                    M3IconButtonWidth value = getWidthRole();
                    pseudoClassStateChanged(PseudoClass.getPseudoClass("narrow"), value == M3IconButtonWidth.NARROW);
                    pseudoClassStateChanged(PseudoClass.getPseudoClass("wide"), value == M3IconButtonWidth.WIDE);
                }
            };

    /// Returns the width-role property; null means the default square width.
    public final ObjectProperty<@Nullable M3IconButtonWidth> widthRoleProperty() {
        return widthRole;
    }

    /// Returns the effective icon-button width role.
    public final M3IconButtonWidth getWidthRole() {
        @Nullable M3IconButtonWidth value = widthRole.get();
        return value == null ? M3IconButtonWidth.DEFAULT : value;
    }

    /// Sets the width role, or null for the default square width.
    public final void setWidthRole(@Nullable M3IconButtonWidth value) {
        widthRole.set(value);
    }

    /// Creates an empty button.
    public M3IconButton() {
        this("", null);
    }

    /// Creates a button with the supplied optional text.
    public M3IconButton(@Nullable String text) {
        this(text, null);
    }

    /// Creates a button displaying the supplied graphic without text.
    public M3IconButton(Node graphic) {
        this("", graphic);
    }

    /// Creates a button with optional text and graphic; the graphic must not have another parent.
    public M3IconButton(@Nullable String text, @Nullable Node graphic) {
        super(text, graphic);
        getStyleClass().add("m3-icon-button");
        setVariant(M3ButtonVariant.TEXT);
    }
}
