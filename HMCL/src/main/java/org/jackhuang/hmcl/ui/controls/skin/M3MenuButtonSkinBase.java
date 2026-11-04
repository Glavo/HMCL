// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.controls.skin;

import javafx.event.EventHandler;
import javafx.scene.AccessibleRole;
import javafx.scene.control.ContentDisplay;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import org.jackhuang.hmcl.ui.controls.M3Button;
import org.jackhuang.hmcl.ui.controls.M3MenuButton;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// Shares menu-arrow layout and popup keyboard behavior without a JavaFX menu-button skin.
///
/// @param <C> the menu control type
@NotNullByDefault
abstract class M3MenuButtonSkinBase<C extends M3MenuButton> extends M3LabeledButtonSkinBase<C> {
    /// Trailing decoration or independently activatable menu action.
    private @Nullable Region trailing;

    /// Independent trailing action for split buttons only.
    private @Nullable M3Button menuAction;

    /// Popup keyboard handling, removed on disposal.
    private final EventHandler<KeyEvent> menuKeys = this::handleMenuKey;

    /// Creates a menu arrow, optionally as an independent button.
    M3MenuButtonSkinBase(C control, boolean split) {
        super(control);
        Region arrow = new Region();
        arrow.getStyleClass().setAll("arrow");
        arrow.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        if (split) {
            M3Button action = new M3Button("", arrow);
            action.getStyleClass().setAll("arrow-button", "m3-split-menu-action");
            action.setAccessibleRole(AccessibleRole.MENU_BUTTON);
            action.accessibleTextProperty().bind(control.accessibleTextProperty());
            action.textProperty().bind(control.textProperty());
            action.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            action.setMnemonicParsing(false);
            action.setStateLayerFill(Color.TRANSPARENT);
            action.disableAnimationProperty().bind(control.disableAnimationProperty());
            action.setOnAction(event -> {
                if (control.isShowing())
                    control.hide();
                else
                    control.show();
                event.consume();
            });
            menuAction = action;
            trailing = action;
        } else {
            StackPane decoration = new StackPane(arrow);
            decoration.getStyleClass().setAll("arrow-button");
            decoration.setMouseTransparent(true);
            trailing = decoration;
        }
        trailing.setManaged(false);
        getChildren().add(trailing);
        control.addEventFilter(KeyEvent.KEY_PRESSED, menuKeys);
    }

    /// Restores the trailing target when labeled content changes.
    @Override
    protected void updateChildren() {
        super.updateChildren();
        if (trailing != null)
            getChildren().add(trailing);
    }

    /// Opens or closes the popup using unmodified Down, Up, F4, and Escape.
    private void handleMenuKey(KeyEvent event) {
        C control = getSkinnable();
        if (control.isDisabled() || event.isControlDown() || event.isMetaDown() || event.isShiftDown())
            return;
        KeyCode code = event.getCode();
        if (code == KeyCode.DOWN || code == KeyCode.UP || code == KeyCode.F4) {
            if (control.isShowing())
                control.hide();
            else
                control.show();
            event.consume();
        } else if (code == KeyCode.ESCAPE && control.isShowing()) {
            control.hide();
            control.requestFocus();
            event.consume();
        }
    }

    /// Returns the CSS-resolved trailing-target width.
    private double trailingWidth() {
        return trailing == null ? 0 : snapSizeX(trailing.prefWidth(-1));
    }

    /// Reserves the trailing target beside the primary content.
    @Override
    protected double computeMinWidth(double height, double top, double right, double bottom, double left) {
        return super.computeMinWidth(height, top, right, bottom, left) + trailingWidth();
    }

    /// Reserves the trailing target beside the primary content.
    @Override
    protected double computePrefWidth(double height, double top, double right, double bottom, double left) {
        return super.computePrefWidth(height, top, right, bottom, left) + trailingWidth();
    }

    /// Lays out labeled content followed by the full-height menu target.
    @Override
    protected void layoutChildren(double x, double y, double width, double height) {
        double tw = trailingWidth();
        super.layoutChildren(x, y, Math.max(0, width - tw), height);
        if (trailing != null)
            trailing.resizeRelocate(getSkinnable().getWidth() - tw, 0, tw, getSkinnable().getHeight());
    }

    /// Hides the owned popup and releases trailing-action bindings and handlers.
    @Override
    public void dispose() {
        C control = getSkinnable();
        control.hide();
        control.removeEventFilter(KeyEvent.KEY_PRESSED, menuKeys);
        if (menuAction != null) {
            menuAction.accessibleTextProperty().unbind();
            menuAction.textProperty().unbind();
            menuAction.disableAnimationProperty().unbind();
            menuAction.setOnAction(null);
            menuAction.setSkin(null);
        }
        if (trailing != null)
            getChildren().remove(trailing);
        super.dispose();
    }
}
