// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.controls;

import javafx.beans.property.BooleanProperty;
import javafx.css.CssMetaData;
import javafx.css.Styleable;
import javafx.css.StyleableObjectProperty;
import javafx.scene.Node;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Skin;
import javafx.scene.paint.Paint;
import org.jackhuang.hmcl.ui.controls.skin.M3MenuButtonSkin;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/// A menu button exposing the standard JavaFX MenuItem list and show/hide API.
@NotNullByDefault
public class M3MenuButton extends MenuButton implements M3ButtonStyle {
    /// Appearance and feedback properties.
    private final M3ButtonProperties properties = new M3ButtonProperties(this);

    /// Creates an empty button.
    public M3MenuButton() {
        this("", null);
    }

    /// Creates a button with optional text.
    public M3MenuButton(@Nullable String text) {
        this(text, null);
    }

    /// Creates a button with optional content and non-null menu items.
    /// Menu items must not already belong to another menu.
    public M3MenuButton(@Nullable String text, @Nullable Node graphic, MenuItem... items) {
        super(text, graphic, items);
        getStyleClass().setAll("m3-button-base", "m3-menu-button");
    }

    /// Creates the default Material skin.
    @Override
    protected Skin<?> createDefaultSkin() {
        return new M3MenuButtonSkin(this);
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
            M3Styles.metadata(MenuButton.getClassCssMetaData(), M3ButtonProperties.CSS);

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
