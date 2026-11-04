// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.controls;

import javafx.css.CssMetaData;
import javafx.css.PseudoClass;
import javafx.css.SimpleStyleableObjectProperty;
import javafx.css.Styleable;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleablePropertyFactory;
import javafx.scene.control.Control;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Locale;

/// Stores button appearance and reflects semantic values in CSS pseudo-classes.
@NotNullByDefault
final class M3ButtonProperties {
    /// Factory for appearance metadata.
    private static final StyleablePropertyFactory<Control> FACTORY = new StyleablePropertyFactory<>(List.of());

    /// CSS metadata for the visual emphasis.
    private static final CssMetaData<Control, M3ButtonVariant> VARIANT = FACTORY.createEnumCssMetaData(
            M3ButtonVariant.class, "-m3-variant", control -> ((M3ButtonStyle) control).variantProperty(), M3ButtonVariant.FILLED);

    /// CSS metadata for the size role.
    private static final CssMetaData<Control, M3ButtonSize> SIZE = FACTORY.createEnumCssMetaData(
            M3ButtonSize.class, "-m3-size", control -> ((M3ButtonStyle) control).sizeProperty(), M3ButtonSize.SMALL);

    /// CSS metadata for the resting shape.
    private static final CssMetaData<Control, M3ButtonShape> BUTTONSHAPE = FACTORY.createEnumCssMetaData(
            M3ButtonShape.class, "-m3-shape", control -> ((M3ButtonStyle) control).buttonShapeProperty(), M3ButtonShape.ROUND);

    /// Additional button appearance metadata.
    static final @Unmodifiable List<CssMetaData<? extends Styleable, ?>> CSS = List.copyOf(FACTORY.getCssMetaData());

    /// The visual emphasis.
    final StyleableObjectProperty<@Nullable M3ButtonVariant> variant;

    /// The size role.
    final StyleableObjectProperty<@Nullable M3ButtonSize> size;

    /// The resting shape.
    final StyleableObjectProperty<@Nullable M3ButtonShape> buttonShape;

    /// Creates properties and initializes their pseudo-classes.
    M3ButtonProperties(Control control) {
        variant = new SimpleStyleableObjectProperty<>(VARIANT, control, "variant", M3ButtonVariant.FILLED);
        variant.addListener(observable -> update(control, variant.get(), M3ButtonVariant.FILLED, M3ButtonVariant.values()));
        update(control, variant.get(), M3ButtonVariant.FILLED, M3ButtonVariant.values());
        size = new SimpleStyleableObjectProperty<>(SIZE, control, "size", M3ButtonSize.SMALL);
        size.addListener(observable -> update(control, size.get(), M3ButtonSize.SMALL, M3ButtonSize.values()));
        update(control, size.get(), M3ButtonSize.SMALL, M3ButtonSize.values());
        buttonShape = new SimpleStyleableObjectProperty<>(BUTTONSHAPE, control, "buttonShape", M3ButtonShape.ROUND);
        buttonShape.addListener(observable -> update(control, buttonShape.get(), M3ButtonShape.ROUND, M3ButtonShape.values()));
        update(control, buttonShape.get(), M3ButtonShape.ROUND, M3ButtonShape.values());
    }

    /// Activates exactly one pseudo-class in an enum family; null selects the default.
    private static <E extends Enum<E>> void update(Control control, @Nullable E value, E fallback, E[] values) {
        E effective = value == null ? fallback : value;
        for (E candidate : values)
            control.pseudoClassStateChanged(PseudoClass.getPseudoClass(
                    candidate.name().toLowerCase(Locale.ROOT).replace('_', '-')), candidate == effective);
        control.requestLayout();
    }
}
