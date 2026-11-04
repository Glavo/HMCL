// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.controls;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.geometry.Side;
import javafx.scene.AccessibleAction;
import javafx.scene.AccessibleAttribute;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Skin;
import org.jackhuang.hmcl.ui.controls.skin.M3MenuButtonSkin;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// A Material command button owning a popup of JavaFX menu items.
/// [#show()] requires an enabled button in a showing window. Hiding is idempotent.
/// Activation toggles the popup and then fires an action event. Popup auto-hide updates [#showingProperty()].
@NotNullByDefault
public class M3MenuButton extends M3ButtonBase {
    /// Creates an empty menu button.
    public M3MenuButton() {
        this("", null);
    }

    /// Creates an empty menu with optional button text.
    public M3MenuButton(@Nullable String text) {
        this(text, null);
    }

    /// Creates a button with optional content and non-null items not owned by another menu.
    public M3MenuButton(@Nullable String text, @Nullable Node graphic, MenuItem... items) {
        super(text, graphic);
        getStyleClass().add("m3-menu-button");
        setAccessibleRole(AccessibleRole.MENU_BUTTON);
        popup.getItems().addAll(items);
        popup.showingProperty().addListener(observable -> {
            showing.set(popup.isShowing());
            pseudoClassStateChanged(PseudoClass.getPseudoClass("showing"), isShowing());
            notifyAccessibleAttributeChanged(AccessibleAttribute.EXPANDED);
        });
        disabledProperty().addListener(observable -> {
            if (isDisabled())
                hide();
        });
        visibleProperty().addListener(observable -> {
            if (!isVisible())
                hide();
        });
        sceneProperty().addListener(observable -> hide());
    }

    /// Popup composed by this control; it owns the menu item list.
    private final ContextMenu popup = new ContextMenu();

    /// Returns the live menu-item list owned by this popup.
    public final ObservableList<MenuItem> getItems() {
        return popup.getItems();
    }

    /// Actual popup visibility.
    private final ReadOnlyBooleanWrapper showing = new ReadOnlyBooleanWrapper(this, "showing");

    /// Returns whether the popup is actually visible.
    public final boolean isShowing() {
        return showing.get();
    }

    /// Observes actual popup visibility, including auto-hide.
    public final ReadOnlyBooleanProperty showingProperty() {
        return showing.getReadOnlyProperty();
    }

    /// Preferred popup placement; null is interpreted as below the button.
    private final ObjectProperty<@Nullable Side> popupSide = new SimpleObjectProperty<>(this, "popupSide", Side.BOTTOM);

    /// Sets the preferred side for the next popup opening.
    public final void setPopupSide(@Nullable Side side) {
        popupSide.set(side);
    }

    /// Returns the effective popup side.
    public final Side getPopupSide() {
        @Nullable Side side = popupSide.get();
        return side == null ? Side.BOTTOM : side;
    }

    /// Returns the preferred placement property; null means bottom.
    public final ObjectProperty<@Nullable Side> popupSideProperty() {
        return popupSide;
    }

    /// Opens the popup if this button is enabled and belongs to a showing window.
    public final void show() {
        if (!isDisabled() && !isShowing() && getScene() != null && getScene().getWindow() != null
                && getScene().getWindow().isShowing())
            popup.show(this, getPopupSide(), 0, 0);
    }

    /// Hides the popup if it is visible.
    public final void hide() {
        popup.hide();
    }

    /// Toggles the popup and dispatches an action unless disabled.
    @Override
    public void fire() {
        if (!isDisabled()) {
            if (isShowing())
                hide();
            else
                show();
            super.fire();
        }
    }

    /// Creates the Material labeled menu skin.
    @Override
    protected Skin<?> createDefaultSkin() {
        return new M3MenuButtonSkin(this);
    }

    /// Exposes popup expansion to assistive technology.
    @Override
    public @Nullable Object queryAccessibleAttribute(AccessibleAttribute attribute, @Nullable Object... parameters) {
        return attribute == AccessibleAttribute.EXPANDED ? isShowing() : super.queryAccessibleAttribute(attribute, parameters);
    }

    /// Handles accessible expansion and collapse without firing the primary action.
    @Override
    public void executeAccessibleAction(AccessibleAction action, @Nullable Object... parameters) {
        switch (action) {
            case SHOW_MENU, EXPAND -> show();
            case COLLAPSE -> hide();
            default -> super.executeAccessibleAction(action, parameters);
        }
    }
}
