// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.controls;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
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
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jackhuang.hmcl.ui.controls.skin.M3RadioButtonSkin;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/// A Material radio button with independent or grouped selection.
/// Activating an already selected group member has no effect. An independent radio button toggles on each activation.
/// Direct selection changes do not fire actions. Activation is ignored when disabled or selection is bound.
/// Toggle-group membership must not be bound; the group's live toggle list can change this property.
/// Selection colors use the JFoenix-style property names; CSS uses the `-m3-` prefix.
@NotNullByDefault
public class M3RadioButton extends ButtonBase implements Toggle, M3Control {
    /// Selected state, reflected in CSS and accessibility before group synchronization.
    private final BooleanProperty selected = new SimpleBooleanProperty(this, "selected") {
        /// Coordinates the selected item with the group.
        @Override
        protected void invalidated() {
            boolean value = get();
            pseudoClassStateChanged(PseudoClass.getPseudoClass("selected"), value);
            notifyAccessibleAttributeChanged(AccessibleAttribute.SELECTED);
            @Nullable ToggleGroup group = getToggleGroup();
            if (group != null) {
                if (value)
                    group.selectToggle(M3RadioButton.this);
                else if (group.getSelectedToggle() == M3RadioButton.this
                        && group.getToggles().stream().noneMatch(Toggle::isSelected))
                    group.selectToggle(null);
            }
        }
    };

    /// Group synchronized with this toggle's membership property.
    private @Nullable ToggleGroup registeredGroup;

    /// Prevents recursive updates while a group's list changes the membership property.
    private boolean updatingGroup;

    /// Optional group; list mutations and property assignments share the same membership.
    private final ObjectProperty<@Nullable ToggleGroup> toggleGroup = new SimpleObjectProperty<>(this, "toggleGroup") {
        /// Transfers membership, preserving the selected state.
        @Override
        protected void invalidated() {
            @Nullable ToggleGroup next = get();
            if (updatingGroup)
                return;
            updatingGroup = true;
            try {
                @Nullable ToggleGroup previous = registeredGroup;
                registeredGroup = next;
                if (previous != null && previous != next)
                    previous.getToggles().remove(M3RadioButton.this);
                if (next != null) {
                    if (!next.getToggles().contains(M3RadioButton.this))
                        next.getToggles().add(M3RadioButton.this);
                    if (isSelected())
                        next.selectToggle(M3RadioButton.this);
                }
            } finally {
                updatingGroup = false;
            }
        }
    };

    /// Returns the selected-state property, initially false.
    @Override
    public final BooleanProperty selectedProperty() {
        return selected;
    }

    /// Returns whether this button is selected.
    @Override
    public final boolean isSelected() {
        return selected.get();
    }

    /// Changes selection without firing an action.
    @Override
    public final void setSelected(boolean value) {
        selected.set(value);
    }

    /// Returns the group-membership property, initially null.
    @Override
    public final ObjectProperty<@Nullable ToggleGroup> toggleGroupProperty() {
        return toggleGroup;
    }

    /// Returns the selection group, or null for independent selection.
    @Override
    public final @Nullable ToggleGroup getToggleGroup() {
        return toggleGroup.get();
    }

    /// Transfers this toggle to a group, or removes group membership when null.
    @Override
    public final void setToggleGroup(@Nullable ToggleGroup value) {
        toggleGroup.set(value);
    }

    /// Exposes the selected state to assistive technology.
    @Override
    public @Nullable Object queryAccessibleAttribute(AccessibleAttribute attribute, @Nullable Object... parameters) {
        return attribute == AccessibleAttribute.SELECTED ? isSelected() : super.queryAccessibleAttribute(attribute, parameters);
    }

    /// Factory for inherited metadata and indicator paints.
    private static final StyleablePropertyFactory<M3RadioButton> FACTORY = new StyleablePropertyFactory<>(
            ButtonBase.getClassCssMetaData());

    /// CSS override for the state-layer paint; null follows the content paint.
    private static final CssMetaData<M3RadioButton, @Nullable Paint> STATE_LAYER_FILL = FACTORY.createPaintCssMetaData(
            "-m3-state-layer-fill", control -> control.stateLayerFillProperty(), null);

    /// Animation override, initially the inverse of the global animation setting.
    private final BooleanProperty disableAnimation =
            new SimpleBooleanProperty(this, "disableAnimation", !AnimationUtils.isAnimationEnabled());

    /// Optional paint used by the stationary interaction layer.
    private final StyleableObjectProperty<@Nullable Paint> stateLayerFill =
            new SimpleStyleableObjectProperty<>(STATE_LAYER_FILL, this, "stateLayerFill", null);

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
        setFocusTraversable(true);
        setMnemonicParsing(true);
        setAccessibleRole(AccessibleRole.RADIO_BUTTON);
        getStyleClass().setAll("m3-radio-button");
    }

    /// Toggles independently, or selects an unselected group member, unless disabled or selection is bound.
    @Override
    public void fire() {
        if (!isDisabled() && !selected.isBound() && (getToggleGroup() == null || !isSelected())) {
            setSelected(!isSelected());
            fireEvent(new ActionEvent(this, this));
        }
    }

    /// Creates the default Material selection skin.
    @Override
    protected Skin<?> createDefaultSkin() {
        return new M3RadioButtonSkin(this);
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
