// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.controls;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.CssMetaData;
import javafx.css.PseudoClass;
import javafx.css.Styleable;
import javafx.css.StyleableObjectProperty;
import javafx.scene.Node;
import javafx.scene.control.Skin;
import javafx.scene.control.ToggleButton;
import javafx.scene.paint.Paint;
import org.jackhuang.hmcl.ui.controls.skin.M3ToggleButtonSkin;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/// An icon toggle with JavaFX ToggleGroup and selection semantics.
@NotNullByDefault
public class M3IconToggleButton extends ToggleButton implements M3ButtonStyle {
    /// Appearance and feedback properties.
    private final M3ButtonProperties properties = new M3ButtonProperties(this);

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
    public M3IconToggleButton() {
        this("", null);
    }

    /// Creates a button with optional text.
    public M3IconToggleButton(@Nullable String text) {
        this(text, null);
    }

    /// Creates a button with a graphic and no text.
    public M3IconToggleButton(Node graphic) {
        this("", graphic);
    }

    /// Creates a button with optional text and graphic.
    public M3IconToggleButton(@Nullable String text, @Nullable Node graphic) {
        super(text, graphic);
        getStyleClass().setAll("m3-button-base", "m3-icon-toggle-button");
        setVariant(M3ButtonVariant.TEXT);
    }

    /// Toggles and fires unless disabled or the selection property is bound.
    @Override
    public void fire() {
        if (!selectedProperty().isBound())
            super.fire();
    }

    /// Creates the default Material skin.
    @Override
    protected Skin<?> createDefaultSkin() {
        return new M3ToggleButtonSkin(this);
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
            M3Styles.metadata(ToggleButton.getClassCssMetaData(), M3ButtonProperties.CSS);

    /// Returns the inherited and Material CSS metadata.
    public static @Unmodifiable List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return CSS;
    }

    /// Returns the CSS metadata for this control.
    @Override
    public @Unmodifiable List<CssMetaData<? extends Styleable, ?>> getControlCssMetaData() {
        return CSS;
    }

    /// Returns the standalone Material stylesheet.
    @Override
    public String getUserAgentStylesheet() {
        return M3Styles.stylesheet("m3-button");
    }
}
