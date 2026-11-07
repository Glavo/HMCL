// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.controls;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.PseudoClass;
import javafx.event.ActionEvent;
import javafx.geometry.Pos;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Skin;
import org.jackhuang.hmcl.ui.controls.skin.M3ButtonSkin;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Objects;

/// A baseline Material Design 3 command button with text and an optional graphic.
/// Uses a 40-pixel pill inside a minimum 48-pixel interaction target. Feedback colors the entire
/// state layer without a ripple. Standard labeled properties, mnemonics, and action handlers are supported.
/// Default and cancel buttons handle unconsumed Enter and Escape events in their scene.
@NotNullByDefault
public class M3Button extends ButtonBase {
    /// Visual emphasis; null values resolve to FILLED, including values supplied by a binding.
    private final ObjectProperty<@Nullable M3ButtonVariant> variant = new SimpleObjectProperty<>(this, "variant", M3ButtonVariant.FILLED) {
        /// Updates the mutually exclusive variant pseudo-classes.
        @Override
        protected void invalidated() {
            for (M3ButtonVariant value : M3ButtonVariant.values())
                pseudoClassStateChanged(PseudoClass.getPseudoClass(value.name().toLowerCase(Locale.ROOT)), value == getVariant());
        }
    };

    /// Whether this button handles unconsumed Enter events.
    private final BooleanProperty defaultButton = new SimpleBooleanProperty(this, "defaultButton") {
        /// Exposes the default role to CSS.
        @Override
        protected void invalidated() {
            pseudoClassStateChanged(PseudoClass.getPseudoClass("default"), get());
        }
    };

    /// Whether this button handles unconsumed Escape events.
    private final BooleanProperty cancelButton = new SimpleBooleanProperty(this, "cancelButton") {
        /// Exposes the cancel role to CSS.
        @Override
        protected void invalidated() {
            pseudoClassStateChanged(PseudoClass.getPseudoClass("cancel"), get());
        }
    };

    /// Creates an empty filled button.
    public M3Button() {
        this("", null, M3ButtonVariant.FILLED);
    }

    /// Creates a filled button with text.
    public M3Button(@Nullable String text) {
        this(text, null, M3ButtonVariant.FILLED);
    }

    /// Creates a filled button with text and an optional graphic.
    public M3Button(@Nullable String text, @Nullable Node graphic) {
        this(text, graphic, M3ButtonVariant.FILLED);
    }

    /// Creates a text button with the requested emphasis.
    public M3Button(@Nullable String text, M3ButtonVariant variant) {
        this(text, null, variant);
    }

    /// Creates a button with text, an optional graphic, and the requested emphasis.
    public M3Button(@Nullable String text, @Nullable Node graphic, M3ButtonVariant variant) {
        super(text, graphic);
        getStyleClass().setAll("m3-button");
        setAccessibleRole(AccessibleRole.BUTTON);
        setAlignment(Pos.CENTER);
        setFocusTraversable(true);
        setMnemonicParsing(true);
        pseudoClassStateChanged(PseudoClass.getPseudoClass("filled"), true);
        setVariant(variant);
    }

    /// Returns the effective visual emphasis.
    public final M3ButtonVariant getVariant() {
        return Objects.requireNonNullElse(variant.get(), M3ButtonVariant.FILLED);
    }

    /// Sets the visual emphasis; null restores FILLED.
    public final void setVariant(@Nullable M3ButtonVariant value) {
        variant.set(value);
    }

    /// Returns the bindable visual emphasis, defaulting to FILLED.
    public final ObjectProperty<@Nullable M3ButtonVariant> variantProperty() {
        return variant;
    }

    /// Returns whether this is a default action.
    public final boolean isDefaultButton() {
        return defaultButton.get();
    }

    /// Enables or disables scene-level Enter activation.
    public final void setDefaultButton(boolean value) {
        defaultButton.set(value);
    }

    /// Returns the default-action property, initially false.
    public final BooleanProperty defaultButtonProperty() {
        return defaultButton;
    }

    /// Returns whether this is a cancel action.
    public final boolean isCancelButton() {
        return cancelButton.get();
    }

    /// Enables or disables scene-level Escape activation.
    public final void setCancelButton(boolean value) {
        cancelButton.set(value);
    }

    /// Returns the cancel-action property, initially false.
    public final BooleanProperty cancelButtonProperty() {
        return cancelButton;
    }

    /// Fires one action unless disabled, including activation by assistive technology or a mnemonic.
    @Override
    public void fire() {
        if (!isDisabled())
            fireEvent(new ActionEvent(this, this));
    }

    /// Creates the independent Material button skin.
    @Override
    protected Skin<?> createDefaultSkin() {
        return new M3ButtonSkin(this);
    }

    /// Supplies baseline styling even outside an HMCL scene.
    @Override
    public String getUserAgentStylesheet() {
        return Objects.requireNonNull(M3Button.class.getResource("/assets/css/m3-button.css")).toExternalForm();
    }
}
