// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.controls;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.PseudoClass;
import javafx.event.ActionEvent;
import javafx.scene.AccessibleAttribute;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// Base for selectable Material buttons interoperating with JavaFX [ToggleGroup].
/// Direct selection changes do not fire actions. A disabled button or a button with bound selection ignores activation.
/// Toggle-group membership must not be bound; the group's live toggle list can change this property.
@NotNullByDefault
public abstract class M3ToggleButtonBase extends ButtonBase implements Toggle {
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
                    group.selectToggle(M3ToggleButtonBase.this);
                else if (group.getSelectedToggle() == M3ToggleButtonBase.this
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
                    previous.getToggles().remove(M3ToggleButtonBase.this);
                if (next != null) {
                    if (!next.getToggles().contains(M3ToggleButtonBase.this))
                        next.getToggles().add(M3ToggleButtonBase.this);
                    if (isSelected())
                        next.selectToggle(M3ToggleButtonBase.this);
                }
            } finally {
                updatingGroup = false;
            }
        }
    };

    /// Creates an unselected, focus-traversable button with optional labeled content.
    protected M3ToggleButtonBase(@Nullable String text, @Nullable Node graphic) {
        super(text, graphic);
        setFocusTraversable(true);
        setMnemonicParsing(true);
        setAccessibleRole(AccessibleRole.TOGGLE_BUTTON);
    }

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

    /// Toggles selection and fires an action unless disabled or selection is bound.
    @Override
    public void fire() {
        if (!isDisabled() && !selected.isBound()) {
            setSelected(!isSelected());
            fireEvent(new ActionEvent(this, this));
        }
    }

    /// Exposes the selected state to assistive technology.
    @Override
    public @Nullable Object queryAccessibleAttribute(AccessibleAttribute attribute, @Nullable Object... parameters) {
        return attribute == AccessibleAttribute.SELECTED ? isSelected() : super.queryAccessibleAttribute(attribute, parameters);
    }
}
