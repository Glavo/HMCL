// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.controls;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.ToggleButton;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/// Coordinates selection without firing actions when enforcing a group policy.
/// Items must have unbound selection properties and must not belong to a JavaFX ToggleGroup while contained.
/// An empty group has no selection even when empty selection is disallowed.
///
/// @param <T> the accepted toggle-button type
@NotNullByDefault
public abstract class M3SelectionButtonGroup<T extends ToggleButton> extends M3ButtonGroupBase<T> {
    /// Selection policy; null is interpreted as single selection.
    private final ObjectProperty<@Nullable SelectionMode> selectionMode =
            new SimpleObjectProperty<>(this, "selectionMode", SelectionMode.SINGLE);

    /// Whether the last selected item may be cleared.
    private final BooleanProperty emptySelectionAllowed = new SimpleBooleanProperty(this, "emptySelectionAllowed", true);

    /// Selected items in presentation order.
    private final ObservableList<T> selectedItems = FXCollections.observableArrayList();

    /// Read-only live view of selected items.
    private final @UnmodifiableView ObservableList<T> selectedItemsView = FXCollections.unmodifiableObservableList(selectedItems);

    /// First selected item in presentation order, or null.
    private final ReadOnlyObjectWrapper<@Nullable T> selectedItem = new ReadOnlyObjectWrapper<>(this, "selectedItem");

    /// Per-item selection listeners, released on removal.
    private final Map<T, ChangeListener<Boolean>> listeners = new IdentityHashMap<>();

    /// Prevents recursive normalization while selection is being repaired.
    private boolean updating;

    /// Whether a synchronous observer changed selection during the current reconciliation.
    private boolean selectionDirty;

    /// Most recently selected item awaiting reconciliation.
    private @Nullable T pendingSelection;

    /// Creates an empty group allowing empty single selection.
    protected M3SelectionButtonGroup() {
        selectionMode.addListener(observable -> normalize(null));
        emptySelectionAllowed.addListener(observable -> normalize(null));
        getItems().addListener((ListChangeListener<T>) change -> {
            while (change.next()) {
                for (T item : change.getRemoved()) {
                    @Nullable ChangeListener<Boolean> listener = listeners.remove(item);
                    if (listener != null)
                        item.selectedProperty().removeListener(listener);
                }
                for (T item : change.getAddedSubList()) {
                    ChangeListener<Boolean> listener = (observable, oldValue, newValue) -> normalize(newValue ? item : null);
                    listeners.put(item, listener);
                    item.selectedProperty().addListener(listener);
                }
            }
            normalize(null);
        });
    }

    /// Returns the selection-mode property; null means single selection.
    public final ObjectProperty<@Nullable SelectionMode> selectionModeProperty() {
        return selectionMode;
    }

    /// Returns the effective selection mode.
    public final SelectionMode getSelectionMode() {
        return selectionMode.get() == SelectionMode.MULTIPLE ? SelectionMode.MULTIPLE : SelectionMode.SINGLE;
    }

    /// Sets the policy; switching to single mode retains the first selected item in presentation order.
    public final void setSelectionMode(@Nullable SelectionMode value) {
        selectionMode.set(value);
    }

    /// Returns whether an existing selection may be cleared; the default is true.
    public final BooleanProperty emptySelectionAllowedProperty() {
        return emptySelectionAllowed;
    }

    /// Returns whether empty selection is permitted.
    public final boolean isEmptySelectionAllowed() {
        return emptySelectionAllowed.get();
    }

    /// Changes the empty-selection policy, selecting the first item if necessary.
    public final void setEmptySelectionAllowed(boolean value) {
        emptySelectionAllowed.set(value);
    }

    /// Returns a live, unmodifiable view of selected items in presentation order.
    public final @UnmodifiableView ObservableList<T> getSelectedItems() {
        return selectedItemsView;
    }

    /// Returns the first selected item, or null if selection is empty.
    public final @Nullable T getSelectedItem() {
        return selectedItem.get();
    }

    /// Observes the first selected item in presentation order.
    public final ReadOnlyObjectProperty<@Nullable T> selectedItemProperty() {
        return selectedItem.getReadOnlyProperty();
    }

    /// Rejects competing selection ownership before insertion.
    @Override
    protected void validateItem(T item) {
        if (item.selectedProperty().isBound() || item.getToggleGroup() != null)
            throw new IllegalArgumentException("Group items require unbound selection and no ToggleGroup");
    }

    /// Moves focus and selects the target in single-selection mode without firing an action.
    @Override
    protected void focusItem(T item) {
        super.focusItem(item);
        if (getSelectionMode() == SelectionMode.SINGLE)
            item.setSelected(true);
    }

    /// Reconciles selection after an item or policy change, preferring a newly selected item.
    private void normalize(@Nullable T preferred) {
        selectionDirty = true;
        if (preferred != null)
            pendingSelection = preferred;
        if (updating)
            return;
        updating = true;
        try {
            while (selectionDirty) {
                selectionDirty = false;
                @Unmodifiable List<T> items = List.copyOf(getItems());
                @Nullable T keep = pendingSelection;
                pendingSelection = null;
                if (keep == null || !items.contains(keep) || !keep.isSelected())
                    keep = items.stream().filter(ToggleButton::isSelected).findFirst().orElse(null);
                if (keep == null && !isEmptySelectionAllowed() && !items.isEmpty()) {
                    // Retain the previous choice when the user tries to clear the last selection.
                    @Nullable T previous = getSelectedItem();
                    keep = previous != null && items.contains(previous) ? previous : items.get(0);
                    keep.setSelected(true);
                }
                if (getSelectionMode() == SelectionMode.SINGLE) {
                    for (T item : items) {
                        if (item != keep && item.isSelected())
                            item.setSelected(false);
                    }
                }
                @Unmodifiable List<T> next = items.stream().filter(ToggleButton::isSelected).toList();
                if (!selectedItems.equals(next))
                    selectedItems.setAll(next);
                selectedItem.set(next.isEmpty() ? null : next.get(0));
            }
        } finally {
            updating = false;
        }
    }
}
