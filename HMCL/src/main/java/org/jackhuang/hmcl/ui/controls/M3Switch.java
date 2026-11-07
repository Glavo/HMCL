// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.controls;

import org.jackhuang.hmcl.ui.controls.skin.M3SwitchSkin;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.CssMetaData;
import javafx.css.PseudoClass;
import javafx.css.SimpleStyleableDoubleProperty;
import javafx.css.Styleable;
import javafx.css.StyleableDoubleProperty;
import javafx.css.StyleableProperty;
import javafx.css.converter.SizeConverter;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.AccessibleAction;
import javafx.scene.AccessibleAttribute;
import javafx.scene.AccessibleRole;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// A Material Design 3 switch for a boolean setting.
///
/// Supports clicking, dragging, and Space-key activation. Labels belong to the surrounding layout;
/// supply an accessible text description. Size and colors are configurable through CSS.
@NotNullByDefault
public class M3Switch extends Control {

    /// Creates an unselected switch.
    public M3Switch() {
        getStyleClass().setAll("m3-switch");
        setFocusTraversable(true);
        setAccessibleRole(AccessibleRole.TOGGLE_BUTTON);
    }

    /// Selection before its observable property is requested.
    private boolean selectedValue;

    /// Optional observable selection state.
    private @Nullable BooleanProperty selected;

    /// Changes selection without firing an action event or allocating its property.
    public final void setSelected(boolean value) {
        if (selected != null) {
            selected.set(value);
        } else if (selectedValue != value) {
            selectedValue = value;
            updateSelected();
        }
    }

    /// Returns whether the switch is selected.
    public final boolean isSelected() {
        return selected == null ? selectedValue : selected.get();
    }

    /// Returns the bindable selection state, allocating it on first access with the current value.
    public final BooleanProperty selectedProperty() {
        if (selected == null) {
            selected = new SimpleBooleanProperty(this, "selected", selectedValue) {
                /// Updates visual and accessible selection without a skin listener.
                @Override
                protected void invalidated() {
                    updateSelected();
                }
            };
        }
        return selected;
    }

    /// Returns whether selection is bound without allocating its property.
    public final boolean isSelectionBound() {
        return selected != null && selected.isBound();
    }

    /// Validates bound selection and notifies CSS, accessibility, and the installed skin.
    private void updateSelected() {
        pseudoClassStateChanged(SELECTED, isSelected());
        notifyAccessibleAttributeChanged(AccessibleAttribute.SELECTED);
        if (getSkin() instanceof M3SwitchSkin skin)
            skin.updateSelection();
    }

    /// Activation state before its observable property is requested.
    private boolean armedValue;

    /// Optional observable activation state.
    private @Nullable ReadOnlyBooleanWrapper armed;

    /// Returns whether the switch is armed.
    public final boolean isArmed() {
        return armed == null ? armedValue : armed.get();
    }

    /// Returns the pending-activation state, allocating its observable wrapper on first access.
    public final ReadOnlyBooleanProperty armedProperty() {
        if (armed == null) {
            armed = new ReadOnlyBooleanWrapper(this, "armed", armedValue) {
                /// Updates input feedback without a skin listener.
                @Override
                protected void invalidated() {
                    updateArmed();
                }
            };
        }
        return armed.getReadOnlyProperty();
    }

    /// Changes input feedback without allocating its observable wrapper.
    private void setArmed(boolean value) {
        if (armed != null) {
            armed.set(value);
        } else if (armedValue != value) {
            armedValue = value;
            updateArmed();
        }
    }

    /// Validates activation state and updates CSS and the installed skin.
    private void updateArmed() {
        pseudoClassStateChanged(ARMED, isArmed());
        if (getSkin() instanceof M3SwitchSkin skin)
            skin.updateArmed();
    }

    /// Optional action handler.
    private @Nullable ObjectProperty<@Nullable EventHandler<ActionEvent>> onAction;

    /// Sets or clears the action handler.
    public final void setOnAction(@Nullable EventHandler<ActionEvent> handler) {
        if (onAction != null || handler != null)
            onActionProperty().set(handler);
    }

    /// Returns the action handler, or null when none is installed.
    public final @Nullable EventHandler<ActionEvent> getOnAction() {
        return onAction == null ? null : onAction.get();
    }

    /// Returns the handler property for activation events.
    public final ObjectProperty<@Nullable EventHandler<ActionEvent>> onActionProperty() {
        if (onAction == null) {
            onAction = new SimpleObjectProperty<>(this, "onAction") {
                /// Updates the action handler.
                @Override
                protected void invalidated() {
                    setEventHandler(ActionEvent.ACTION, get());
                }
            };
        }
        return onAction;
    }

    /// Optional styleable track-height property.
    private @Nullable StyleableDoubleProperty size;

    /// Sets the track height; see [#sizeProperty()].
    public final void setSize(double value) {
        if (size != null || value != 32.0)
            sizeProperty().set(value);
    }

    /// Returns the requested track height in logical pixels without allocating its property.
    public final double getSize() {
        return size == null ? 32.0 : size.get();
    }

    /// Track height in logical pixels, styled by `-m3-size`; the default is 32.
    /// Allocated on property access or assignment. Nonpositive or nonfinite values render at the default size.
    public final StyleableDoubleProperty sizeProperty() {
        if (size == null) {
            size = new SimpleStyleableDoubleProperty(SIZE, this, "size", 32.0) {
                /// Validates bound values and requests layout without a skin listener.
                @Override
                protected void invalidated() {
                    get();
                    requestLayout();
                }
            };
        }
        return size;
    }

    /// Optional selected-icon property.
    private @Nullable BooleanProperty showSelectedIcon;

    /// Sets the value of [#showSelectedIconProperty()].
    public final void setShowSelectedIcon(boolean value) {
        if (showSelectedIcon != null || value)
            showSelectedIconProperty().set(value);
    }

    /// Returns whether the selected icon is shown.
    public final boolean isShowSelectedIcon() {
        return showSelectedIcon != null && showSelectedIcon.get();
    }

    /// Whether to show a check mark when selected; defaults to false.
    public final BooleanProperty showSelectedIconProperty() {
        if (showSelectedIcon == null)
            showSelectedIcon = createOptionProperty("showSelectedIcon", false, false);
        return showSelectedIcon;
    }

    /// Optional unselected-icon property.
    private @Nullable BooleanProperty showUnselectedIcon;

    /// Sets the value of [#showUnselectedIconProperty()].
    public final void setShowUnselectedIcon(boolean value) {
        if (showUnselectedIcon != null || value)
            showUnselectedIconProperty().set(value);
    }

    /// Returns whether the unselected icon is shown.
    public final boolean isShowUnselectedIcon() {
        return showUnselectedIcon != null && showUnselectedIcon.get();
    }

    /// Whether to show a cross when unselected; defaults to false.
    public final BooleanProperty showUnselectedIconProperty() {
        if (showUnselectedIcon == null)
            showUnselectedIcon = createOptionProperty("showUnselectedIcon", false, false);
        return showUnselectedIcon;
    }

    /// Optional animation policy override.
    private @Nullable BooleanProperty disableAnimation;

    /// Sets the value of [#disableAnimationProperty()].
    public final void setDisableAnimation(boolean value) {
        if (disableAnimation != null || value == AnimationUtils.isAnimationEnabled())
            disableAnimationProperty().set(value);
    }

    /// Returns whether animation is disabled.
    public final boolean isDisableAnimation() {
        return disableAnimation == null ? !AnimationUtils.isAnimationEnabled() : disableAnimation.get();
    }

    /// Disables animation, defaulting to the inverse of the global animation-enabled setting.
    public final BooleanProperty disableAnimationProperty() {
        if (disableAnimation == null)
            disableAnimation = createOptionProperty("disableAnimation", !AnimationUtils.isAnimationEnabled(), true);
        return disableAnimation;
    }

    /// Arms input feedback unless the switch is disabled or its selection is bound.
    public final void arm() {
        if (!isDisabled() && !isSelectionBound())
            setArmed(true);
    }

    /// Clears input feedback without firing an action.
    public final void disarm() {
        setArmed(false);
    }

    /// Toggles selection and fires one action; disabled or bound switches ignore activation.
    public void fire() {
        if (!isDisabled() && !isSelectionBound()) {
            setSelected(!isSelected());
            fireEvent(new ActionEvent(this, this));
        }
    }

    /// Creates the default switch skin.
    @Override
    protected Skin<?> createDefaultSkin() {
        return new M3SwitchSkin(this);
    }

    /// Supplies component styling; the containing scene must provide the Monet palette.
    @Override
    public String getUserAgentStylesheet() {
        return Objects.requireNonNull(M3Switch.class.getResource("/assets/css/m3-switch.css")).toExternalForm();
    }

    /// Creates a property that updates the skin, optionally settling active animations.
    private BooleanProperty createOptionProperty(String name, boolean initialValue, boolean finishAnimation) {
        return new SimpleBooleanProperty(this, name, initialValue) {
            /// Validates the value even without a skin, so subsequent changes also reach this callback.
            @Override
            protected void invalidated() {
                get();
                if (getSkin() instanceof M3SwitchSkin skin)
                    skin.updateOptions(finishAnimation);
            }
        };
    }

    /// Indicates that the switch is selected.
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");

    /// Indicates a pending activation.
    private static final PseudoClass ARMED = PseudoClass.getPseudoClass("armed");

    /// CSS metadata for track height; settable checks must not instantiate the property.
    private static final CssMetaData<M3Switch, Number> SIZE =
            new CssMetaData<>("-m3-size", SizeConverter.getInstance(), 32.0) {
                /// Allows CSS assignment only when the optional property is not bound.
                @Override
                public boolean isSettable(M3Switch control) {
                    return control.size == null || !control.size.isBound();
                }

                /// Allocates the property only when CSS needs to access it.
                @Override
                public StyleableProperty<Number> getStyleableProperty(M3Switch control) {
                    return control.sizeProperty();
                }
            };

    /// Switch and inherited CSS metadata.
    private static final @Unmodifiable List<CssMetaData<? extends Styleable, ?>> CSS_META_DATA;

    static {
        List<CssMetaData<? extends Styleable, ?>> metadata = new ArrayList<>(Control.getClassCssMetaData());
        metadata.add(SIZE);
        CSS_META_DATA = List.copyOf(metadata);
    }

    /// Returns immutable CSS metadata, including inherited control properties.
    public static @Unmodifiable List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return CSS_META_DATA;
    }

    /// Returns the metadata used to style this switch.
    @Override
    public @Unmodifiable List<CssMetaData<? extends Styleable, ?>> getControlCssMetaData() {
        return getClassCssMetaData();
    }

    /// Exposes selection to assistive technology.
    @Override
    public @Nullable Object queryAccessibleAttribute(AccessibleAttribute attribute, @Nullable Object... parameters) {
        return attribute == AccessibleAttribute.SELECTED ? isSelected() : super.queryAccessibleAttribute(attribute, parameters);
    }

    /// Handles accessible activation.
    @Override
    public void executeAccessibleAction(AccessibleAction action, @Nullable Object... parameters) {
        if (action == AccessibleAction.FIRE)
            fire();
        else
            super.executeAccessibleAction(action, parameters);
    }
}
