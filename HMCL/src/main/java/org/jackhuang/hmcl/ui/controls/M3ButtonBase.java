// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.controls;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.css.CssMetaData;
import javafx.css.PseudoClass;
import javafx.css.SimpleStyleableObjectProperty;
import javafx.css.Styleable;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleablePropertyFactory;
import javafx.event.ActionEvent;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Skin;
import javafx.scene.paint.Paint;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jackhuang.hmcl.ui.controls.skin.M3ButtonSkin;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Locale;

/// Base for Material command buttons with action, mnemonic, and default/cancel semantics.
/// The default skin installs Enter and Escape accelerators while the corresponding role is enabled.
/// When several M3 buttons share a role, the most recently registered eligible button is activated.
/// Appearance does not change action semantics. Graphic nodes must not be shared between controls.
@NotNullByDefault
public abstract class M3ButtonBase extends ButtonBase implements M3ButtonStyle {
    /// Factory for inherited button appearance and overlay metadata.
    private static final StyleablePropertyFactory<M3ButtonBase> FACTORY = new StyleablePropertyFactory<>(
            ButtonBase.getClassCssMetaData());

    /// CSS metadata for the visual emphasis.
    private static final CssMetaData<M3ButtonBase, @Nullable M3ButtonVariant> VARIANT = FACTORY.createEnumCssMetaData(
            M3ButtonVariant.class, "-m3-variant", control -> control.variantProperty(), M3ButtonVariant.FILLED);

    /// The visual emphasis; null uses [M3ButtonVariant#FILLED].
    private final StyleableObjectProperty<@Nullable M3ButtonVariant> variant =
            new SimpleStyleableObjectProperty<>(VARIANT, this, "variant", M3ButtonVariant.FILLED) {
                /// Reflects the effective value in CSS, including changes from bindings.
                @Override
                protected void invalidated() {
                    updatePseudoClasses(getVariant(), M3ButtonVariant.values());
                }
            };

    /// CSS metadata for the size role.
    private static final CssMetaData<M3ButtonBase, @Nullable M3ButtonSize> SIZE = FACTORY.createEnumCssMetaData(
            M3ButtonSize.class, "-m3-size", control -> control.sizeProperty(), M3ButtonSize.SMALL);

    /// The size role; null uses [M3ButtonSize#SMALL].
    private final StyleableObjectProperty<@Nullable M3ButtonSize> size =
            new SimpleStyleableObjectProperty<>(SIZE, this, "size", M3ButtonSize.SMALL) {
                /// Reflects the effective value in CSS, including changes from bindings.
                @Override
                protected void invalidated() {
                    updatePseudoClasses(getSize(), M3ButtonSize.values());
                }
            };

    /// CSS metadata for the resting shape.
    private static final CssMetaData<M3ButtonBase, @Nullable M3ButtonShape> BUTTON_SHAPE = FACTORY.createEnumCssMetaData(
            M3ButtonShape.class, "-m3-shape", control -> control.buttonShapeProperty(), M3ButtonShape.ROUND);

    /// The resting shape; null uses [M3ButtonShape#ROUND].
    private final StyleableObjectProperty<@Nullable M3ButtonShape> buttonShape =
            new SimpleStyleableObjectProperty<>(BUTTON_SHAPE, this, "buttonShape", M3ButtonShape.ROUND) {
                /// Reflects the effective value in CSS, including changes from bindings.
                @Override
                protected void invalidated() {
                    updatePseudoClasses(getButtonShape(), M3ButtonShape.values());
                }
            };

    /// CSS override for the state-layer paint; null follows the content paint.
    private static final CssMetaData<M3ButtonBase, @Nullable Paint> STATE_LAYER_FILL = FACTORY.createPaintCssMetaData(
            "-m3-state-layer-fill", control -> control.stateLayerFillProperty(), null);

    /// Animation override, initially the inverse of the global animation setting.
    private final BooleanProperty disableAnimation =
            new SimpleBooleanProperty(this, "disableAnimation", !AnimationUtils.isAnimationEnabled());

    /// Optional paint used by the stationary interaction layer.
    private final StyleableObjectProperty<@Nullable Paint> stateLayerFill =
            new SimpleStyleableObjectProperty<>(STATE_LAYER_FILL, this, "stateLayerFill", null);

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
        updatePseudoClasses(getVariant(), M3ButtonVariant.values());
        updatePseudoClasses(getSize(), M3ButtonSize.values());
        updatePseudoClasses(getButtonShape(), M3ButtonShape.values());
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
        return disableAnimation;
    }

    /// Returns the styleable overlay paint override.
    @Override
    public final StyleableObjectProperty<@Nullable Paint> stateLayerFillProperty() {
        return stateLayerFill;
    }

    /// Returns the styleable visual emphasis.
    @Override
    public final StyleableObjectProperty<@Nullable M3ButtonVariant> variantProperty() {
        return variant;
    }

    /// Returns the styleable size role.
    @Override
    public final StyleableObjectProperty<@Nullable M3ButtonSize> sizeProperty() {
        return size;
    }

    /// Returns the styleable resting shape.
    @Override
    public final StyleableObjectProperty<@Nullable M3ButtonShape> buttonShapeProperty() {
        return buttonShape;
    }

    /// Activates one pseudo-class in an appearance family and refreshes layout.
    private <E extends Enum<E>> void updatePseudoClasses(E value, E[] values) {
        for (E candidate : values)
            pseudoClassStateChanged(PseudoClass.getPseudoClass(
                    candidate.name().toLowerCase(Locale.ROOT).replace('_', '-')), candidate == value);
        requestLayout();
    }

    /// Immutable metadata including the inherited JavaFX properties.
    private static final @Unmodifiable List<CssMetaData<? extends Styleable, ?>> CSS =
            List.copyOf(FACTORY.getCssMetaData());

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
