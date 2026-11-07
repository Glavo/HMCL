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

import java.util.Objects;

/// Shared properties, action semantics, and styling for Material Design 3 command buttons.
/// Uses a 40-pixel pill inside a minimum 48-pixel interaction target. Feedback colors the entire
/// state layer without a ripple. Standard labeled properties, mnemonics, and action handlers are supported.
/// Default and cancel buttons handle unconsumed Enter and Escape events in their scene.
@NotNullByDefault
public abstract class M3ButtonBase extends ButtonBase {

    // Constructors

    /// Creates an empty filled button.
    protected M3ButtonBase() {
        this("", null, M3ButtonVariant.FILLED);
    }

    /// Creates a filled button with text.
    protected M3ButtonBase(@Nullable String text) {
        this(text, null, M3ButtonVariant.FILLED);
    }

    /// Creates a filled button with text and an optional graphic.
    protected M3ButtonBase(@Nullable String text, @Nullable Node graphic) {
        this(text, graphic, M3ButtonVariant.FILLED);
    }

    /// Creates a text button with the requested emphasis.
    protected M3ButtonBase(@Nullable String text, M3ButtonVariant variant) {
        this(text, null, variant);
    }

    /// Creates a button with text, an optional graphic, and the requested emphasis.
    protected M3ButtonBase(@Nullable String text, @Nullable Node graphic, M3ButtonVariant variant) {
        super(text, graphic);
        getStyleClass().setAll("m3-button-base");
        setAccessibleRole(AccessibleRole.BUTTON);
        setAlignment(Pos.CENTER);
        setFocusTraversable(true);
        setMnemonicParsing(true);
        pseudoClassStateChanged(M3ButtonVariant.FILLED.pseudoClass(), true);
        setVariant(variant);
    }

    // Properties

    /// Latest visual emphasis, also retained before the observable property is requested.
    private @Nullable M3ButtonVariant variantValue = M3ButtonVariant.FILLED;

    /// Optional observable visual emphasis.
    private @Nullable ObjectProperty<@Nullable M3ButtonVariant> variant;

    /// Sets the visual emphasis; null restores FILLED.
    public final void setVariant(@Nullable M3ButtonVariant value) {
        if (variant == null) {
            updateVariant(value);
        } else {
            variant.set(value);
        }
    }

    /// Returns the effective visual emphasis without allocating its property.
    public final M3ButtonVariant getVariant() {
        return Objects.requireNonNullElse(variant == null ? variantValue : variant.get(), M3ButtonVariant.FILLED);
    }

    /// Returns the visual-emphasis property, allocating it on first access and preserving the current value.
    public final ObjectProperty<@Nullable M3ButtonVariant> variantProperty() {
        if (variant == null) {
            variant = new SimpleObjectProperty<>(this, "variant", variantValue) {
                /// Validates bound values and updates visual emphasis without a skin listener.
                @Override
                protected void invalidated() {
                    updateVariant(get());
                }
            };
        }
        return variant;
    }

    /// Default-action role before the observable property is requested.
    private boolean defaultButtonValue;

    /// Optional observable default-action role.
    private @Nullable BooleanProperty defaultButton;

    /// Enables or disables scene-level Enter activation.
    public final void setDefaultButton(boolean value) {
        if (defaultButton != null) {
            defaultButton.set(value);
        } else if (defaultButtonValue != value) {
            defaultButtonValue = value;
            updateDefaultButton();
        }
    }

    /// Returns whether this is a default action without allocating its property.
    public final boolean isDefaultButton() {
        return defaultButton == null ? defaultButtonValue : defaultButton.get();
    }

    /// Returns the default-action property, allocating it on first access and preserving the current value.
    public final BooleanProperty defaultButtonProperty() {
        if (defaultButton == null) {
            defaultButton = new SimpleBooleanProperty(this, "defaultButton", defaultButtonValue) {
                /// Updates CSS and scene key handling directly, including changes from bindings.
                @Override
                protected void invalidated() {
                    updateDefaultButton();
                }
            };
        }
        return defaultButton;
    }

    /// Cancel-action role before the observable property is requested.
    private boolean cancelButtonValue;

    /// Optional observable cancel-action role.
    private @Nullable BooleanProperty cancelButton;

    /// Enables or disables scene-level Escape activation.
    public final void setCancelButton(boolean value) {
        if (cancelButton != null) {
            cancelButton.set(value);
        } else if (cancelButtonValue != value) {
            cancelButtonValue = value;
            updateCancelButton();
        }
    }

    /// Returns whether this is a cancel action without allocating its property.
    public final boolean isCancelButton() {
        return cancelButton == null ? cancelButtonValue : cancelButton.get();
    }

    /// Returns the cancel-action property, allocating it on first access and preserving the current value.
    public final BooleanProperty cancelButtonProperty() {
        if (cancelButton == null) {
            cancelButton = new SimpleBooleanProperty(this, "cancelButton", cancelButtonValue) {
                /// Updates CSS and scene key handling directly, including changes from bindings.
                @Override
                protected void invalidated() {
                    updateCancelButton();
                }
            };
        }
        return cancelButton;
    }

    // Methods

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

    // Implementation

    /// Retains the raw value and updates only the two CSS states affected by an emphasis change.
    private void updateVariant(@Nullable M3ButtonVariant value) {
        M3ButtonVariant previous = Objects.requireNonNullElse(variantValue, M3ButtonVariant.FILLED);
        variantValue = value;
        M3ButtonVariant current = Objects.requireNonNullElse(value, M3ButtonVariant.FILLED);
        if (previous != current) {
            pseudoClassStateChanged(previous.pseudoClass(), false);
            pseudoClassStateChanged(current.pseudoClass(), true);
        }
    }

    /// Reflects the default role in CSS and updates the installed skin's scene handler.
    private void updateDefaultButton() {
        pseudoClassStateChanged(PseudoClass.getPseudoClass("default"), isDefaultButton());
        if (getSkin() instanceof M3ButtonSkin skin)
            skin.updateSceneActions();
    }

    /// Reflects the cancel role in CSS and updates the installed skin's scene handler.
    private void updateCancelButton() {
        pseudoClassStateChanged(PseudoClass.getPseudoClass("cancel"), isCancelButton());
        if (getSkin() instanceof M3ButtonSkin skin)
            skin.updateSceneActions();
    }

    // Stylesheet handling

    /// Supplies component styling; the containing scene must provide the Monet palette.
    @Override
    public String getUserAgentStylesheet() {
        return Objects.requireNonNull(M3ButtonBase.class.getResource("/assets/css/m3-button.css")).toExternalForm();
    }
}
