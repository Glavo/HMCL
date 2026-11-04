// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.controls;

import javafx.beans.property.BooleanProperty;
import javafx.css.CssMetaData;
import javafx.css.SimpleStyleableObjectProperty;
import javafx.css.Styleable;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleablePropertyFactory;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Skin;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import org.jackhuang.hmcl.ui.controls.skin.M3CheckBoxSkin;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/// A Material tri-state check box retaining JavaFX selection, action, and accessibility behavior.
/// Selection colors use the JFoenix-style property names; CSS uses the `-m3-` prefix.
@NotNullByDefault
public class M3CheckBox extends CheckBox implements M3Control {
    /// Shared feedback properties.
    private final M3ControlProperties properties = new M3ControlProperties(this);

    /// Factory for inherited metadata and indicator paints.
    private static final StyleablePropertyFactory<M3CheckBox> FACTORY = new StyleablePropertyFactory<>(
            M3Styles.metadata(CheckBox.getClassCssMetaData(), M3ControlProperties.CSS));

    /// Metadata for the checked paint.
    private static final CssMetaData<M3CheckBox, Paint> CHECKED = FACTORY.createPaintCssMetaData(
            "-m3-checked-color", control -> control.checkedColorProperty(), Color.DARKSLATEBLUE);

    /// The selected box paint.
    private final StyleableObjectProperty<@Nullable Paint> checkedColor =
            new SimpleStyleableObjectProperty<>(CHECKED, this, "checkedColor", Color.DARKSLATEBLUE);

    /// Returns the checked paint property; null renders as transparent.
    public final StyleableObjectProperty<@Nullable Paint> checkedColorProperty() {
        return checkedColor;
    }

    /// Returns the checked paint, or null for transparent.
    public final @Nullable Paint getCheckedColor() {
        return checkedColor.get();
    }

    /// Sets the checked paint, or null for transparent.
    public final void setCheckedColor(@Nullable Paint value) {
        checkedColor.set(value);
    }

    /// Metadata for the unchecked paint.
    private static final CssMetaData<M3CheckBox, Paint> UNCHECKED = FACTORY.createPaintCssMetaData(
            "-m3-unchecked-color", control -> control.unCheckedColorProperty(), Color.GRAY);

    /// The unselected outline paint.
    private final StyleableObjectProperty<@Nullable Paint> unCheckedColor =
            new SimpleStyleableObjectProperty<>(UNCHECKED, this, "unCheckedColor", Color.GRAY);

    /// Returns the unchecked paint property; null renders as transparent.
    public final StyleableObjectProperty<@Nullable Paint> unCheckedColorProperty() {
        return unCheckedColor;
    }

    /// Returns the unchecked paint, or null for transparent.
    public final @Nullable Paint getUnCheckedColor() {
        return unCheckedColor.get();
    }

    /// Sets the unchecked paint, or null for transparent.
    public final void setUnCheckedColor(@Nullable Paint value) {
        unCheckedColor.set(value);
    }

    /// Creates an empty, unselected control.
    public M3CheckBox() {
        this("");
    }

    /// Creates an unselected control with optional text.
    public M3CheckBox(@Nullable String text) {
        super(text);
        getStyleClass().setAll("m3-check-box");
    }

    /// Ignores activation when selection or indeterminate state is bound.
    @Override
    public void fire() {
        if (!selectedProperty().isBound() && !indeterminateProperty().isBound())
            super.fire();
    }

    /// Creates the default Material selection skin.
    @Override
    protected Skin<?> createDefaultSkin() {
        return new M3CheckBoxSkin(this);
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

    /// Returns the standalone selection stylesheet.
    @Override
    public String getUserAgentStylesheet() {
        return M3Styles.stylesheet("m3-selection");
    }
}
