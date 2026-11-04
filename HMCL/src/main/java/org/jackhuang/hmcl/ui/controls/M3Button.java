// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.controls;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// A Material command button with JavaFX Button semantics.
/// The default skin installs Enter and Escape accelerators while the corresponding role is enabled.
/// When several M3 buttons share a role, the most recently registered eligible button is activated.
@NotNullByDefault
public class M3Button extends M3ButtonBase {
    /// Creates an empty button.
    public M3Button() {
        this("", null);
    }

    /// Creates a button with the supplied optional text.
    public M3Button(@Nullable String text) {
        this(text, null);
    }

    /// Creates a button displaying the supplied graphic without text.
    public M3Button(Node graphic) {
        this("", graphic);
    }

    /// Creates a button with optional text and graphic; the graphic must not have another parent.
    public M3Button(@Nullable String text, @Nullable Node graphic) {
        super(text, graphic);
        getStyleClass().add("m3-button");
    }

    /// Whether this button supplies the scene's default action.
    private final BooleanProperty defaultButton = new SimpleBooleanProperty(this, "defaultButton") {
        /// Updates the semantic CSS state.
        @Override
        protected void invalidated() {
            pseudoClassStateChanged(PseudoClass.getPseudoClass("default"), get());
        }
    };

    /// Enables or disables the default action role.
    public final void setDefaultButton(boolean value) {
        defaultButton.set(value);
    }

    /// Returns whether the button supplies a default action.
    public final boolean isDefaultButton() {
        return defaultButton.get();
    }

    /// Returns the default-action property, initially false.
    public final BooleanProperty defaultButtonProperty() {
        return defaultButton;
    }

    /// Whether this button supplies the scene's cancel action.
    private final BooleanProperty cancelButton = new SimpleBooleanProperty(this, "cancelButton") {
        /// Updates the semantic CSS state.
        @Override
        protected void invalidated() {
            pseudoClassStateChanged(PseudoClass.getPseudoClass("cancel"), get());
        }
    };

    /// Enables or disables the cancel action role.
    public final void setCancelButton(boolean value) {
        cancelButton.set(value);
    }

    /// Returns whether the button supplies a cancel action.
    public final boolean isCancelButton() {
        return cancelButton.get();
    }

    /// Returns the cancel-action property, initially false.
    public final BooleanProperty cancelButtonProperty() {
        return cancelButton;
    }
}
