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
import javafx.scene.AccessibleAttribute;
import javafx.scene.AccessibleRole;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Skin;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jackhuang.hmcl.ui.controls.skin.M3CheckBoxSkin;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/// A Material check box with optional three-state activation.
/// With indeterminate activation enabled, firing cycles through unchecked, indeterminate, checked, and unchecked.
/// Direct state changes do not fire actions; disabled controls and bound state properties ignore activation.
/// Selection colors use the JFoenix-style property names; CSS uses the `-m3-` prefix.
@NotNullByDefault
public class M3CheckBox extends ButtonBase implements M3Control {
    /// Factory for inherited metadata and indicator paints.
    private static final StyleablePropertyFactory<M3CheckBox> FACTORY = new StyleablePropertyFactory<>(
            ButtonBase.getClassCssMetaData());

    /// CSS override for the state-layer paint; null follows the content paint.
    private static final CssMetaData<M3CheckBox, @Nullable Paint> STATE_LAYER_FILL = FACTORY.createPaintCssMetaData(
            "-m3-state-layer-fill", control -> control.stateLayerFillProperty(), null);

    /// Animation override, initially the inverse of the global animation setting.
    private final BooleanProperty disableAnimation =
            new SimpleBooleanProperty(this, "disableAnimation", !AnimationUtils.isAnimationEnabled());

    /// Optional paint used by the stationary interaction layer.
    private final StyleableObjectProperty<@Nullable Paint> stateLayerFill =
            new SimpleStyleableObjectProperty<>(STATE_LAYER_FILL, this, "stateLayerFill", null);

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

    /// The selected state, initially false.
    private final BooleanProperty selected = new SimpleBooleanProperty(this, "selected") {
        /// Updates CSS and accessibility before notifying application listeners.
        @Override
        protected void invalidated() {
            pseudoClassStateChanged(PseudoClass.getPseudoClass("selected"), get());
            notifyAccessibleAttributeChanged(AccessibleAttribute.SELECTED);
        }
    };

    /// Returns the selected state property.
    public final BooleanProperty selectedProperty() {
        return selected;
    }

    /// Returns the selected state.
    public final boolean isSelected() {
        return selected.get();
    }

    /// Sets the selected state without firing an action.
    public final void setSelected(boolean value) {
        selected.set(value);
    }

    /// The indeterminate state, initially false.
    private final BooleanProperty indeterminate = new SimpleBooleanProperty(this, "indeterminate") {
        /// Updates CSS and accessibility before notifying application listeners.
        @Override
        protected void invalidated() {
            pseudoClassStateChanged(PseudoClass.getPseudoClass("indeterminate"), get());
            notifyAccessibleAttributeChanged(AccessibleAttribute.INDETERMINATE);
        }
    };

    /// Returns the indeterminate state property.
    public final BooleanProperty indeterminateProperty() {
        return indeterminate;
    }

    /// Returns the indeterminate state.
    public final boolean isIndeterminate() {
        return indeterminate.get();
    }

    /// Sets the indeterminate state without firing an action.
    public final void setIndeterminate(boolean value) {
        indeterminate.set(value);
    }

    /// The three-state activation policy, initially false.
    private final BooleanProperty allowIndeterminate = new SimpleBooleanProperty(this, "allowIndeterminate");

    /// Returns the three-state activation policy property.
    public final BooleanProperty allowIndeterminateProperty() {
        return allowIndeterminate;
    }

    /// Returns the three-state activation policy.
    public final boolean isAllowIndeterminate() {
        return allowIndeterminate.get();
    }

    /// Sets the three-state activation policy without firing an action.
    public final void setAllowIndeterminate(boolean value) {
        allowIndeterminate.set(value);
    }

    /// Exposes checked and indeterminate states to assistive technology.
    @Override
    public @Nullable Object queryAccessibleAttribute(AccessibleAttribute attribute, @Nullable Object... parameters) {
        return switch (attribute) {
            case SELECTED -> isSelected();
            case INDETERMINATE -> isIndeterminate();
            default -> super.queryAccessibleAttribute(attribute, parameters);
        };
    }

    /// Creates an empty, unselected control.
    public M3CheckBox() {
        this("");
    }

    /// Creates an unselected control with optional text.
    public M3CheckBox(@Nullable String text) {
        super(text);
        getStyleClass().setAll("m3-check-box");
        setFocusTraversable(true);
        setMnemonicParsing(true);
        setAccessibleRole(AccessibleRole.CHECK_BOX);
    }

    /// Ignores activation when selection or indeterminate state is bound.
    @Override
    public void fire() {
        if (isDisabled() || selected.isBound() || indeterminate.isBound())
            return;
        if (isAllowIndeterminate()) {
            if (isIndeterminate()) {
                setIndeterminate(false);
                setSelected(true);
            } else if (!isSelected()) {
                setIndeterminate(true);
            } else {
                setSelected(false);
            }
        } else {
            setIndeterminate(false);
            setSelected(!isSelected());
        }
        fireEvent(new ActionEvent(this, this));
    }

    /// Creates the default Material selection skin.
    @Override
    protected Skin<?> createDefaultSkin() {
        return new M3CheckBoxSkin(this);
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
