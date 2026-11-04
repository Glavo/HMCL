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
import java.util.Locale;

/// A selectable segment that can belong to an M3SegmentedButtonGroup.
@NotNullByDefault
public class M3SegmentedButton extends M3ToggleButtonBase implements M3ButtonStyle {
    /// Factory for inherited button appearance and overlay metadata.
    private static final StyleablePropertyFactory<M3SegmentedButton> FACTORY = new StyleablePropertyFactory<>(
            ButtonBase.getClassCssMetaData());

    /// CSS metadata for the visual emphasis.
    private static final CssMetaData<M3SegmentedButton, @Nullable M3ButtonVariant> VARIANT = FACTORY.createEnumCssMetaData(
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
    private static final CssMetaData<M3SegmentedButton, @Nullable M3ButtonSize> SIZE = FACTORY.createEnumCssMetaData(
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
    private static final CssMetaData<M3SegmentedButton, @Nullable M3ButtonShape> BUTTON_SHAPE = FACTORY.createEnumCssMetaData(
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
        updatePseudoClasses(getVariant(), M3ButtonVariant.values());
        updatePseudoClasses(getSize(), M3ButtonSize.values());
        updatePseudoClasses(getButtonShape(), M3ButtonShape.values());
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
