// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.controls;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.css.CssMetaData;
import javafx.css.SimpleStyleableObjectProperty;
import javafx.css.Styleable;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleablePropertyFactory;
import javafx.scene.Node;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Skin;
import javafx.scene.paint.Paint;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jackhuang.hmcl.ui.controls.skin.M3ToggleButtonSkin;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/// A selectable segment that can belong to an M3SegmentedButtonGroup.
@NotNullByDefault
public class M3SegmentedButton extends M3ToggleButtonBase implements M3ButtonStyle {
    /// Button appearance properties.
    private final M3ButtonProperties properties = new M3ButtonProperties(this);

    /// Factory for inherited button appearance and overlay metadata.
    private static final StyleablePropertyFactory<M3SegmentedButton> FACTORY = new StyleablePropertyFactory<>(
            M3Styles.metadata(ButtonBase.getClassCssMetaData(), M3ButtonProperties.CSS));

    /// CSS override for the state-layer paint; null follows the content paint.
    private static final CssMetaData<M3SegmentedButton, @Nullable Paint> STATE_LAYER_FILL = FACTORY.createPaintCssMetaData(
            "-m3-state-layer-fill", control -> control.stateLayerFillProperty(), null);

    /// Animation override, initially the inverse of the global animation setting.
    private final BooleanProperty disableAnimation =
            new SimpleBooleanProperty(this, "disableAnimation", !AnimationUtils.isAnimationEnabled());

    /// Optional paint used by the stationary interaction layer.
    private final StyleableObjectProperty<@Nullable Paint> stateLayerFill =
            new SimpleStyleableObjectProperty<>(STATE_LAYER_FILL, this, "stateLayerFill", null);

    /// Creates an empty button.
    public M3SegmentedButton() {
        this("", null);
    }

    /// Creates a button with optional text.
    public M3SegmentedButton(@Nullable String text) {
        this(text, null);
    }

    /// Creates a button with a graphic and no text.
    public M3SegmentedButton(Node graphic) {
        this("", graphic);
    }

    /// Creates a button with optional text and graphic.
    public M3SegmentedButton(@Nullable String text, @Nullable Node graphic) {
        super(text, graphic);
        getStyleClass().setAll("m3-button-base", "m3-segmented-button");
        setVariant(M3ButtonVariant.OUTLINED);
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
