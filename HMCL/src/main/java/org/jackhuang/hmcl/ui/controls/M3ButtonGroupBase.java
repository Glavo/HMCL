// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.controls;

import javafx.beans.DefaultProperty;
import javafx.beans.InvalidationListener;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.ModifiableObservableListBase;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Orientation;
import javafx.scene.AccessibleRole;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.jackhuang.hmcl.ui.controls.skin.M3ButtonGroupSkin;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;

/// Arranges distinct buttons in logical order and provides directional focus navigation.
/// Items belong to at most one Material button group and must not be parented elsewhere.
/// Removing an item releases ownership without changing its action handlers.
/// All mutations must occur on the JavaFX application thread.
///
/// @param <T> the accepted button type
@DefaultProperty("items")
@NotNullByDefault
public abstract class M3ButtonGroupBase<T extends ButtonBase> extends Control {
    /// Creates an empty horizontal group.
    protected M3ButtonGroupBase() {
        getStyleClass().setAll("m3-button-group");
        setFocusTraversable(false);
        setAccessibleRole(AccessibleRole.TOOL_BAR);
        orientation.addListener(layoutInvalidation);
        variant.addListener(layoutInvalidation);
        spacing.addListener(observable -> requestLayout());
        items.addListener((ListChangeListener<T>) change -> {
            while (change.next()) {
                for (T removed : change.getRemoved()) {
                    removed.managedProperty().removeListener(layoutInvalidation);
                    removed.visibleProperty().removeListener(layoutInvalidation);
                    setRole(removed, -1, 0);
                }
                for (T added : change.getAddedSubList()) {
                    added.managedProperty().addListener(layoutInvalidation);
                    added.visibleProperty().addListener(layoutInvalidation);
                }
            }
            updateRoles();
        });
        // Handle group navigation before individual toggle-group navigation.
        addEventFilter(KeyEvent.KEY_PRESSED, this::navigate);
        updateRoles();
    }

    /// Buttons in presentation order.
    private final ObservableList<T> items = new ItemList();

    /// Returns the live item list. Nulls, duplicate identities, and items owned elsewhere are rejected.
    public final ObservableList<T> getItems() {
        return items;
    }

    /// Layout direction; null is interpreted as horizontal.
    private final ObjectProperty<@Nullable Orientation> orientation =
            new SimpleObjectProperty<>(this, "orientation", Orientation.HORIZONTAL);

    /// Sets the layout orientation, or null for horizontal.
    public final void setOrientation(@Nullable Orientation value) {
        orientation.set(value);
    }

    /// Returns the effective layout orientation.
    public final Orientation getOrientation() {
        @Nullable Orientation value = orientation.get();
        return value == null ? Orientation.HORIZONTAL : value;
    }

    /// Returns the layout orientation property; null means horizontal.
    public final ObjectProperty<@Nullable Orientation> orientationProperty() {
        return orientation;
    }

    /// Spacing between adjacent buttons, in logical pixels.
    private final DoubleProperty spacing = new SimpleDoubleProperty(this, "spacing", 8);

    /// Sets the requested spacing in logical pixels.
    public final void setSpacing(double value) {
        spacing.set(value);
    }

    /// Returns the requested spacing in logical pixels.
    public final double getSpacing() {
        return spacing.get();
    }

    /// Returns the inter-button spacing; negative or nonfinite values are laid out as zero.
    public final DoubleProperty spacingProperty() {
        return spacing;
    }

    /// Arrangement style; null is interpreted as standard.
    private final ObjectProperty<@Nullable M3ButtonGroupVariant> variant =
            new SimpleObjectProperty<>(this, "variant", M3ButtonGroupVariant.STANDARD);

    /// Sets the arrangement style without modifying spacing or selection.
    public final void setVariant(@Nullable M3ButtonGroupVariant value) {
        variant.set(value);
    }

    /// Returns the effective arrangement style.
    public final M3ButtonGroupVariant getVariant() {
        @Nullable M3ButtonGroupVariant value = variant.get();
        return value == null ? M3ButtonGroupVariant.STANDARD : value;
    }

    /// Returns the arrangement style property; null means standard.
    public final ObjectProperty<@Nullable M3ButtonGroupVariant> variantProperty() {
        return variant;
    }

    /// Checks subclass-specific membership requirements before insertion.
    protected void validateItem(T item) {
    }

    /// Moves focus to an item chosen by directional navigation.
    protected void focusItem(T item) {
        item.requestFocus();
    }

    /// Creates a layout skin that observes the live item list.
    @Override
    protected Skin<?> createDefaultSkin() {
        return new M3ButtonGroupSkin<>(this);
    }

    /// Property-map key used to prevent simultaneous membership in two groups.
    private static final Object OWNER_KEY = new Object();

    /// Refreshes corner roles when items enter or leave layout.
    private final InvalidationListener layoutInvalidation = observable -> updateRoles();

    /// Updates logical corner roles and orientation styling.
    private void updateRoles() {
        pseudoClassStateChanged(PseudoClass.getPseudoClass("vertical"), getOrientation() == Orientation.VERTICAL);
        pseudoClassStateChanged(PseudoClass.getPseudoClass("connected"), getVariant() == M3ButtonGroupVariant.CONNECTED);
        @Unmodifiable List<T> visible = items.stream().filter(item -> item.isVisible() && item.isManaged()).toList();
        for (T item : items)
            setRole(item, visible.indexOf(item), visible.size());
        requestLayout();
    }

    /// Assigns corner roles without changing application style classes.
    private void setRole(T item, int index, int count) {
        item.pseudoClassStateChanged(PseudoClass.getPseudoClass("group-first"), index == 0 && count > 1);
        item.pseudoClassStateChanged(PseudoClass.getPseudoClass("group-middle"), index > 0 && index < count - 1);
        item.pseudoClassStateChanged(PseudoClass.getPseudoClass("group-last"), index > 0 && index == count - 1);
    }

    /// Navigates enabled visible items, mirroring horizontal arrows in RTL layouts.
    private void navigate(KeyEvent event) {
        if (event.isAltDown() || event.isControlDown() || event.isMetaDown() || event.isShiftDown())
            return;
        @Unmodifiable List<T> eligible = items.stream().filter(item -> item.isVisible() && item.isManaged()
                && !item.isDisabled() && item.isFocusTraversable()).toList();
        if (eligible.isEmpty())
            return;
        int index = -1;
        for (int i = 0; i < eligible.size(); i++) {
            if (getScene() != null && getScene().getFocusOwner() == eligible.get(i))
                index = i;
        }
        if (index < 0)
            return;
        KeyCode code = event.getCode();
        int delta;
        if (getOrientation() == Orientation.VERTICAL)
            delta = code == KeyCode.UP ? -1 : code == KeyCode.DOWN ? 1 : 0;
        else {
            delta = code == KeyCode.LEFT ? -1 : code == KeyCode.RIGHT ? 1 : 0;
            if (getEffectiveNodeOrientation() == NodeOrientation.RIGHT_TO_LEFT)
                delta = -delta;
        }
        if (code == KeyCode.HOME)
            index = 0;
        else if (code == KeyCode.END)
            index = eligible.size() - 1;
        else if (delta != 0)
            index = Math.floorMod(index + delta, eligible.size());
        else
            return;
        focusItem(eligible.get(index));
        event.consume();
    }

    /// Rejects invalid ownership before committing each list mutation.
    @NotNullByDefault
    private final class ItemList extends ModifiableObservableListBase<T> {
        /// Backing items in presentation order.
        private final List<T> values = new ArrayList<>();

        /// Returns the item at the requested index.
        @Override
        public T get(int index) {
            return values.get(index);
        }

        /// Returns the number of items.
        @Override
        public int size() {
            return values.size();
        }

        /// Validates an item before assigning ownership.
        private void validate(T item) {
            Objects.requireNonNull(item, "item");
            if (item.getProperties().containsKey(OWNER_KEY) || item.getParent() != null
                    && item.getParent().getParent() != M3ButtonGroupBase.this)
                throw new IllegalArgumentException("The button already belongs to a parent or group");
            validateItem(item);
        }

        /// Atomically replaces or reorders items after checking the entire incoming collection.
        @Override
        public boolean setAll(Collection<? extends T> collection) {
            List<T> replacement = new ArrayList<>(collection);
            var identities = new IdentityHashMap<T, Boolean>();
            for (T item : replacement) {
                Objects.requireNonNull(item, "item");
                if (identities.put(item, true) != null)
                    throw new IllegalArgumentException("Duplicate button");
                if (!values.contains(item))
                    validate(item);
            }
            if (values.equals(replacement))
                return false;
            List<T> previous = new ArrayList<>(values);
            beginChange();
            try {
                for (T item : previous)
                    item.getProperties().remove(OWNER_KEY);
                values.clear();
                values.addAll(replacement);
                for (T item : values)
                    item.getProperties().put(OWNER_KEY, M3ButtonGroupBase.this);
                modCount++;
                nextReplace(0, size(), previous);
            } finally {
                endChange();
            }
            return true;
        }

        /// Inserts an unowned item.
        @Override
        protected void doAdd(int index, T element) {
            validate(element);
            values.add(index, element);
            element.getProperties().put(OWNER_KEY, M3ButtonGroupBase.this);
        }

        /// Replaces an item and transfers ownership.
        @Override
        protected T doSet(int index, T element) {
            T old = values.get(index);
            if (old == element)
                return old;
            validate(element);
            values.set(index, element);
            old.getProperties().remove(OWNER_KEY);
            element.getProperties().put(OWNER_KEY, M3ButtonGroupBase.this);
            return old;
        }

        /// Removes an item and releases its logical ownership.
        @Override
        protected T doRemove(int index) {
            T old = values.remove(index);
            old.getProperties().remove(OWNER_KEY);
            return old;
        }
    }

    /// Returns the standalone group stylesheet.
    @Override
    public String getUserAgentStylesheet() {
        return M3Styles.stylesheet("m3-button-group");
    }
}
