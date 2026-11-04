// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.controls.skin;

import javafx.beans.InvalidationListener;
import javafx.event.EventHandler;
import javafx.geometry.NodeOrientation;
import javafx.scene.Node;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import org.jackhuang.hmcl.ui.controls.M3ToggleButtonBase;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/// Owns one skin's pointer and keyboard activation lifecycle using ButtonBase alone.
@NotNullByDefault
final class M3ButtonBehavior {
    /// Control receiving actions.
    private final ButtonBase control;

    /// Whether a primary pointer gesture owns activation.
    private boolean mouseDown;

    /// Whether Space is awaiting release, including after Escape cancellation.
    private boolean keyDown;

    /// Pointer handler removed on disposal.
    private final EventHandler<MouseEvent> mouseHandler = this::mouse;

    /// Keyboard handler removed on disposal.
    private final EventHandler<KeyEvent> keyHandler = this::key;

    /// Cancels gestures when the control becomes unavailable or changes scenes.
    private final InvalidationListener unavailable = observable -> cancel();

    /// Cancels activation when focus leaves the button.
    private final InvalidationListener focusChanged = observable -> cancelIfUnfocused();

    /// Installs input handling without consuming unhandled traversal keys.
    M3ButtonBehavior(ButtonBase control) {
        this.control = control;
        control.addEventHandler(MouseEvent.ANY, mouseHandler);
        control.addEventHandler(KeyEvent.ANY, keyHandler);
        control.disabledProperty().addListener(unavailable);
        control.sceneProperty().addListener(unavailable);
        control.focusedProperty().addListener(focusChanged);
    }

    /// Returns whether an event belongs to this button rather than a nested control.
    private boolean owns(@Nullable Object target) {
        if (!(target instanceof Node node))
            return true;
        while (node != control) {
            if (node instanceof ButtonBase)
                return false;
            @Nullable Node parent = node.getParent();
            if (parent == null)
                return false;
            node = parent;
        }
        return true;
    }

    /// Arms on primary press and fires once on an inside release.
    private void mouse(MouseEvent event) {
        if (event.isConsumed() || !owns(event.getTarget()))
            return;
        if (event.getEventType() == MouseEvent.MOUSE_CLICKED && event.getButton() == MouseButton.PRIMARY) {
            event.consume();
        } else if (event.getEventType() == MouseEvent.MOUSE_PRESSED) {
            if (control.isDisabled() || keyDown || event.getButton() != MouseButton.PRIMARY
                    || event.isShiftDown() || event.isControlDown() || event.isAltDown() || event.isMetaDown()
                    || event.isSecondaryButtonDown() || event.isMiddleButtonDown())
                return;
            if (control.isFocusTraversable())
                control.requestFocus();
            mouseDown = true;
            control.arm();
            event.consume();
        } else if (event.getEventType() == MouseEvent.MOUSE_RELEASED && event.getButton() == MouseButton.PRIMARY && mouseDown) {
            boolean activate = control.isArmed() && !control.isDisabled() && control.contains(event.getX(), event.getY());
            mouseDown = false;
            control.disarm();
            event.consume();
            if (activate)
                control.fire();
        } else if (mouseDown && event.getEventType() == MouseEvent.MOUSE_EXITED) {
            control.disarm();
        } else if (mouseDown && event.getEventType() == MouseEvent.MOUSE_ENTERED && !control.isDisabled()) {
            control.arm();
        }
    }

    /// Handles Space release, Enter activation, Escape cancellation, and grouped toggle navigation.
    private void key(KeyEvent event) {
        if (event.isConsumed() || !owns(event.getTarget()))
            return;
        if (event.getEventType() == KeyEvent.KEY_RELEASED && event.getCode() == KeyCode.SPACE && keyDown) {
            boolean activate = control.isArmed() && !control.isDisabled();
            keyDown = false;
            control.disarm();
            event.consume();
            if (activate)
                control.fire();
            return;
        }
        if (event.getEventType() != KeyEvent.KEY_PRESSED || control.isDisabled())
            return;
        if (event.getCode() == KeyCode.ESCAPE && (mouseDown || keyDown)) {
            mouseDown = false;
            control.disarm();
            event.consume();
            return;
        }
        if (event.isAltDown() || event.isControlDown() || event.isMetaDown() || event.isShiftDown())
            return;
        if (event.getCode() == KeyCode.SPACE) {
            if (!keyDown && !mouseDown) {
                keyDown = true;
                control.arm();
            }
            event.consume();
        } else if (event.getCode() == KeyCode.ENTER && !mouseDown && !keyDown) {
            event.consume();
            control.fire();
        } else {
            navigateToggleGroup(event);
        }
    }

    /// Moves selection and focus through a ToggleGroup without dispatching an action.
    private void navigateToggleGroup(KeyEvent event) {
        if (!(control instanceof M3ToggleButtonBase toggle))
            return;
        @Nullable ToggleGroup group = toggle.getToggleGroup();
        if (group == null)
            return;
        int delta = switch (event.getCode()) {
            case UP -> -1;
            case DOWN -> 1;
            case LEFT -> control.getEffectiveNodeOrientation() == NodeOrientation.RIGHT_TO_LEFT ? 1 : -1;
            case RIGHT -> control.getEffectiveNodeOrientation() == NodeOrientation.RIGHT_TO_LEFT ? -1 : 1;
            default -> 0;
        };
        if (delta == 0)
            return;
        @Unmodifiable List<Toggle> toggles = List.copyOf(group.getToggles());
        int index = toggles.indexOf(toggle);
        for (int offset = 1; offset < toggles.size(); offset++) {
            Toggle next = toggles.get(Math.floorMod(index + delta * offset, toggles.size()));
            if (next instanceof Node node && node.isVisible() && !node.isDisabled()
                    && node.isFocusTraversable() && !next.selectedProperty().isBound()) {
                group.selectToggle(next);
                node.requestFocus();
                break;
            }
        }
        event.consume();
    }

    /// Cancels transient activation without firing.
    private void cancel() {
        mouseDown = false;
        keyDown = false;
        control.disarm();
    }

    /// Cancels activation after focus moves away from the button.
    private void cancelIfUnfocused() {
        if (!control.isFocused())
            cancel();
    }

    /// Cancels activation and removes all listeners and event handlers.
    void dispose() {
        cancel();
        control.removeEventHandler(MouseEvent.ANY, mouseHandler);
        control.removeEventHandler(KeyEvent.ANY, keyHandler);
        control.disabledProperty().removeListener(unavailable);
        control.sceneProperty().removeListener(unavailable);
        control.focusedProperty().removeListener(focusChanged);
    }
}
