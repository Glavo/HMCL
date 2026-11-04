// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.controls;

import javafx.beans.property.BooleanProperty;
import javafx.css.CssMetaData;
import javafx.css.Styleable;
import javafx.css.StyleableObjectProperty;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Skin;
import javafx.scene.paint.Paint;
import org.jackhuang.hmcl.ui.controls.skin.M3ButtonSkin;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/// Base for Material command buttons, retaining JavaFX default/cancel, mnemonic, and action behavior.
/// Appearance does not change action semantics. Graphic nodes must not be shared between controls.
@NotNullByDefault
public abstract class M3ButtonBase extends Button implements M3ButtonStyle {
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
    }

    /// Creates a skin that retains JavaFX button behavior and adds fading feedback.
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
            M3Styles.metadata(Button.getClassCssMetaData(), M3ButtonProperties.CSS);

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
