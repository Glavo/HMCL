// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.controls;

import javafx.beans.property.BooleanProperty;
import javafx.css.CssMetaData;
import javafx.css.SimpleStyleableObjectProperty;
import javafx.css.Styleable;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleablePropertyFactory;
import javafx.scene.control.ButtonBase;
import javafx.scene.AccessibleRole;
import javafx.scene.control.Skin;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import org.jackhuang.hmcl.ui.controls.skin.M3RadioButtonSkin;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/// A Material radio button with independent or grouped selection.
/// Activating an already selected group member has no effect. An independent radio button toggles on each activation.
/// Selection colors use the JFoenix-style property names; CSS uses the `-m3-` prefix.
@NotNullByDefault
public class M3RadioButton extends M3ToggleButtonBase implements M3Control {
    /// Shared feedback properties.
    private final M3ControlProperties properties = new M3ControlProperties(this);

    /// Factory for inherited metadata and indicator paints.
    private static final StyleablePropertyFactory<M3RadioButton> FACTORY = new StyleablePropertyFactory<>(
            M3Styles.metadata(ButtonBase.getClassCssMetaData(), M3ControlProperties.CSS));

    /// Metadata for the selected paint.
    private static final CssMetaData<M3RadioButton, Color> SELECTED = FACTORY.createColorCssMetaData(
            "-m3-selected-color", control -> control.selectedColorProperty(), Color.DARKSLATEBLUE);

    /// The selected indicator paint.
    private final StyleableObjectProperty<@Nullable Color> selectedColor =
            new SimpleStyleableObjectProperty<>(SELECTED, this, "selectedColor", Color.DARKSLATEBLUE);

    /// Returns the selected paint property; null renders as transparent.
    public final StyleableObjectProperty<@Nullable Color> selectedColorProperty() {
        return selectedColor;
    }

    /// Returns the selected paint, or null for transparent.
    public final @Nullable Color getSelectedColor() {
        return selectedColor.get();
    }

    /// Sets the selected paint, or null for transparent.
    public final void setSelectedColor(@Nullable Color value) {
        selectedColor.set(value);
    }

    /// Metadata for the unselected paint.
    private static final CssMetaData<M3RadioButton, Color> UNSELECTED = FACTORY.createColorCssMetaData(
            "-m3-unselected-color", control -> control.unSelectedColorProperty(), Color.GRAY);

    /// The unselected indicator paint.
    private final StyleableObjectProperty<@Nullable Color> unSelectedColor =
            new SimpleStyleableObjectProperty<>(UNSELECTED, this, "unSelectedColor", Color.GRAY);

    /// Returns the unselected paint property; null renders as transparent.
    public final StyleableObjectProperty<@Nullable Color> unSelectedColorProperty() {
        return unSelectedColor;
    }

    /// Returns the unselected paint, or null for transparent.
    public final @Nullable Color getUnSelectedColor() {
        return unSelectedColor.get();
    }

    /// Sets the unselected paint, or null for transparent.
    public final void setUnSelectedColor(@Nullable Color value) {
        unSelectedColor.set(value);
    }

    /// Creates an empty, unselected control.
    public M3RadioButton() {
        this("");
    }

    /// Creates an unselected control with optional text.
    public M3RadioButton(@Nullable String text) {
        super(text, null);
        setAccessibleRole(AccessibleRole.RADIO_BUTTON);
        getStyleClass().setAll("m3-radio-button");
    }

    /// Toggles independently, or selects an unselected group member, unless disabled or selection is bound.
    @Override
    public void fire() {
        if (getToggleGroup() == null || !isSelected())
            super.fire();
    }

    /// Creates the default Material selection skin.
    @Override
    protected Skin<?> createDefaultSkin() {
        return new M3RadioButtonSkin(this);
    }

    /// Returns this control's animation override.
    @Override
    public final BooleanProperty disableAnimationProperty() {
        return properties.disableAnimation;
    }

    /// Returns the styleable overlay paint override.
    @Override
    public final StyleableObjectProperty<@Nullable Paint> stateLayerFillProperty() {
        return properties.stateLayerFill;
    }

    /// Immutable metadata for this control.
    private static final @Unmodifiable List<CssMetaData<? extends Styleable, ?>> CSS = List.copyOf(FACTORY.getCssMetaData());

    /// Returns inherited metadata and Material feedback and indicator properties.
    public static @Unmodifiable List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return CSS;
    }

    /// Returns this control's CSS metadata.
    @Override
    public @Unmodifiable List<CssMetaData<? extends Styleable, ?>> getControlCssMetaData() {
        return CSS;
    }

    /// Returns the selection stylesheet, which uses the scene's Monet palette.
    @Override
    public String getUserAgentStylesheet() {
        return M3Styles.stylesheet("m3-selection");
    }
}
