// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.controls;

import javafx.beans.property.BooleanProperty;
import javafx.css.CssMetaData;
import javafx.css.Styleable;
import javafx.css.StyleableObjectProperty;
import javafx.scene.Node;
import javafx.scene.control.ButtonBase;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.css.PseudoClass;
import javafx.event.ActionEvent;
import javafx.scene.AccessibleRole;
import javafx.scene.control.Skin;
import javafx.scene.paint.Paint;
import org.jackhuang.hmcl.ui.controls.skin.M3ButtonSkin;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/// Base for Material command buttons with action, mnemonic, and default/cancel semantics.
/// The default skin installs Enter and Escape accelerators while the corresponding role is enabled.
/// When several M3 buttons share a role, the most recently registered eligible button is activated.
/// Appearance does not change action semantics. Graphic nodes must not be shared between controls.
@NotNullByDefault
public abstract class M3ButtonBase extends ButtonBase implements M3ButtonStyle {
    /// Appearance and feedback properties.
    private final M3ButtonProperties properties = new M3ButtonProperties(this);

    /// Creates an empty command button.
    protected M3ButtonBase() {
        this("", null);
    }

    /// Creates a command button with optional text and graphic.
    protected M3ButtonBase(@Nullable String text, @Nullable Node graphic) {
        super(text, graphic);
        getStyleClass().setAll("m3-button-base");
        setFocusTraversable(true);
        setMnemonicParsing(true);
        setAccessibleRole(AccessibleRole.BUTTON);
    }

    /// Whether this button supplies the scene's default action.
    private final BooleanProperty defaultButton = new SimpleBooleanProperty(this, "defaultButton") {
        /// Updates the semantic CSS state.
        @Override
        protected void invalidated() {
            pseudoClassStateChanged(PseudoClass.getPseudoClass("default"), get());
        }
    };

    /// Whether this button supplies the scene's cancel action.
    private final BooleanProperty cancelButton = new SimpleBooleanProperty(this, "cancelButton") {
        /// Updates the semantic CSS state.
        @Override
        protected void invalidated() {
            pseudoClassStateChanged(PseudoClass.getPseudoClass("cancel"), get());
        }
    };

    /// Returns the default-action property, initially false.
    public final BooleanProperty defaultButtonProperty() {
        return defaultButton;
    }

    /// Returns whether the button supplies a default action.
    public final boolean isDefaultButton() {
        return defaultButton.get();
    }

    /// Enables or disables the default action role.
    public final void setDefaultButton(boolean value) {
        defaultButton.set(value);
    }

    /// Returns the cancel-action property, initially false.
    public final BooleanProperty cancelButtonProperty() {
        return cancelButton;
    }

    /// Returns whether the button supplies a cancel action.
    public final boolean isCancelButton() {
        return cancelButton.get();
    }

    /// Enables or disables the cancel action role.
    public final void setCancelButton(boolean value) {
        cancelButton.set(value);
    }

    /// Dispatches one action event unless this button is disabled.
    @Override
    public void fire() {
        if (!isDisabled())
            fireEvent(new ActionEvent(this, this));
    }

    /// Creates the default labeled command-button skin.
    @Override
    protected Skin<?> createDefaultSkin() {
        return new M3ButtonSkin(this);
    }

    /// Returns this control's animation override.
    @Override
    public final BooleanProperty disableAnimationProperty() {
        return properties.feedback.disableAnimation;
    }

    /// Returns the styleable overlay paint override.
    @Override
    public final StyleableObjectProperty<@Nullable Paint> stateLayerFillProperty() {
        return properties.feedback.stateLayerFill;
    }

    /// Returns the styleable visual emphasis.
    @Override
    public final StyleableObjectProperty<@Nullable M3ButtonVariant> variantProperty() {
        return properties.variant;
    }

    /// Returns the styleable size role.
    @Override
    public final StyleableObjectProperty<@Nullable M3ButtonSize> sizeProperty() {
        return properties.size;
    }

    /// Returns the styleable resting shape.
    @Override
    public final StyleableObjectProperty<@Nullable M3ButtonShape> buttonShapeProperty() {
        return properties.buttonShape;
    }

    /// Immutable metadata including the inherited JavaFX properties.
    private static final @Unmodifiable List<CssMetaData<? extends Styleable, ?>> CSS =
            M3Styles.metadata(ButtonBase.getClassCssMetaData(), M3ButtonProperties.CSS);

    /// Returns the inherited and Material CSS metadata.
    public static @Unmodifiable List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return CSS;
    }

    /// Returns the CSS metadata for this control.
    @Override
    public @Unmodifiable List<CssMetaData<? extends Styleable, ?>> getControlCssMetaData() {
        return CSS;
    }

    /// Returns the component stylesheet, which uses the scene's Monet palette.
    @Override
    public String getUserAgentStylesheet() {
        return M3Styles.stylesheet("m3-button");
    }
}
