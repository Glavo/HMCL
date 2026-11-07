// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.controls.skin;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.css.PseudoClass;
import javafx.event.EventHandler;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.skin.LabeledSkinBase;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.stage.Window;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.controls.M3ButtonBase;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// Renders a labeled MD3 button with a bounded, uniform state layer and independent input handling.
@NotNullByDefault
public final class M3ButtonSkin extends LabeledSkinBase<M3ButtonBase> {

    // Fields

    /// Keyboard-focus indication shared with the switch.
    private static final PseudoClass VISUAL_FOCUSED = PseudoClass.getPseudoClass("visual-focused");

    /// Background artwork, initialized by the superclass's first updateChildren call.
    private @Nullable Region container;

    /// Uniform hover, focus, and pressed feedback above the container.
    private @Nullable Region stateLayer;

    /// A separate outline for keyboard focus.
    private @Nullable Region focusRing;

    /// Whether a primary-pointer gesture owns activation.
    private boolean mouseDown;

    /// The activation key awaiting release, including after Escape cancellation.
    private @Nullable KeyCode keyDown;

    /// Pointer input dispatch.
    private final EventHandler<MouseEvent> mouseHandler = this::handleMouse;

    /// Focused keyboard input dispatch.
    private final EventHandler<KeyEvent> keyHandler = this::handleKey;

    /// Scene-level default and cancel dispatch after focused controls have handled keys.
    private @Nullable EventHandler<KeyEvent> sceneKeyHandler;

    /// The scene with a registered default or cancel handler, or null for ordinary buttons.
    private @Nullable Scene actionScene;

    /// Prevents property callbacks from registering handlers after disposal.
    private boolean disposed;

    /// The scene observed for window changes.
    private @Nullable Scene scene;

    /// The window whose deactivation cancels pending gestures.
    private @Nullable Window window;

    /// Cancels pending activation when the window loses focus or is hidden.
    private final ChangeListener<Boolean> windowActiveListener = (observable, oldValue, newValue) -> {
        if (!newValue)
            cancelGesture();
    };

    /// Tracks a scene moving between windows.
    private final ChangeListener<@Nullable Window> windowListener = (observable, oldValue, newValue) -> updateWindow(newValue);

    // Constructors

    /// Installs artwork, gesture handling, and lifecycle cleanup.
    public M3ButtonSkin(M3ButtonBase control) {
        super(control);
        control.addEventFilter(MouseEvent.ANY, mouseHandler);
        control.addEventHandler(KeyEvent.ANY, keyHandler);
        registerChangeListener(control.disabledProperty(), value -> cancelGesture());
        registerChangeListener(control.visibleProperty(), value -> cancelGesture());
        registerChangeListener(control.focusedProperty(), value -> {
            if (!control.isFocused())
                cancelGesture();
        });
        registerChangeListener(control.sceneProperty(), value -> updateScene(control.getScene()));
        @Nullable ReadOnlyBooleanProperty focusVisible = FXUtils.focusVisibleProperty(control);
        ReadOnlyBooleanProperty focusIndicator = focusVisible != null ? focusVisible : control.focusedProperty();
        registerChangeListener(focusIndicator, value ->
                control.pseudoClassStateChanged(VISUAL_FOCUSED, focusIndicator.get() && !control.isPressed()));
        registerChangeListener(control.pressedProperty(), value -> control.pseudoClassStateChanged(VISUAL_FOCUSED, false));
        control.pseudoClassStateChanged(VISUAL_FOCUSED, focusIndicator.get());
        updateScene(control.getScene());
    }

    // Public API

    /// Removes input and scene listeners before releasing labeled content.
    @Override
    public void dispose() {
        M3ButtonBase control = getSkinnable();
        disposed = true;
        updateScene(null);
        control.removeEventFilter(MouseEvent.ANY, mouseHandler);
        control.removeEventHandler(KeyEvent.ANY, keyHandler);
        control.pseudoClassStateChanged(VISUAL_FOCUSED, false);
        super.dispose();
    }

    /// Registers scene key handling only while the button has a default or cancel role.
    /// Called directly by role-property invalidation without requesting those properties.
    public void updateSceneActions() {
        @Nullable Scene target = !disposed && (getSkinnable().isDefaultButton() || getSkinnable().isCancelButton()) ? scene : null;
        if (actionScene == target)
            return;
        if (actionScene != null && sceneKeyHandler != null)
            actionScene.removeEventHandler(KeyEvent.KEY_PRESSED, sceneKeyHandler);
        actionScene = target;
        if (actionScene != null) {
            if (sceneKeyHandler == null)
                sceneKeyHandler = this::handleSceneKey;
            actionScene.addEventHandler(KeyEvent.KEY_PRESSED, sceneKeyHandler);
        }
    }

    // Layout and sizing

    /// Restores artwork when labeled content changes, including during superclass construction.
    @Override
    protected void updateChildren() {
        super.updateChildren();
        if (container == null)
            container = artwork("button-container");
        if (stateLayer == null)
            stateLayer = artwork("button-state-layer");
        if (focusRing == null)
            focusRing = artwork("button-focus-ring");
        getChildren().addAll(0, java.util.List.of(container, stateLayer, focusRing));
    }

    /// Centers the 40-pixel visual within its 48-pixel target and lays out standard labeled content.
    @Override
    protected void layoutChildren(double x, double y, double width, double height) {
        M3ButtonBase control = getSkinnable();
        double visualHeight = Math.max(0, control.getHeight() - 8);
        if (container != null)
            container.resizeRelocate(0, 4, control.getWidth(), visualHeight);
        if (stateLayer != null)
            stateLayer.resizeRelocate(0, 4, control.getWidth(), visualHeight);
        if (focusRing != null)
            focusRing.resizeRelocate(-3, 1, control.getWidth() + 6, visualHeight + 6);
        super.layoutChildren(x, y + 4, width, Math.max(0, height - 8));
    }

    /// Preserves a minimum interaction width while allowing standard text elision.
    @Override
    protected double computeMinWidth(double height, double top, double right, double bottom, double left) {
        return Math.max(48, super.computeMinWidth(height, top, right, bottom, left));
    }

    /// Reserves the target height and sufficient space for labeled content.
    @Override
    protected double computeMinHeight(double width, double top, double right, double bottom, double left) {
        return Math.max(48, super.computeMinHeight(width, top, right, bottom, left) + 8);
    }

    /// Measures text and graphics while retaining the minimum interaction width.
    @Override
    protected double computePrefWidth(double height, double top, double right, double bottom, double left) {
        return Math.max(48, super.computePrefWidth(height, top, right, bottom, left));
    }

    /// Measures content without reducing the accessible interaction target.
    @Override
    protected double computePrefHeight(double width, double top, double right, double bottom, double left) {
        return Math.max(48, super.computePrefHeight(width, top, right, bottom, left) + 8);
    }

    /// Includes the target margin and vertical alignment when aligning this button with adjacent text.
    @Override
    public double computeBaselineOffset(double top, double right, double bottom, double left) {
        M3ButtonBase control = getSkinnable();
        double contentHeight = super.computePrefHeight(control.getWidth(), top, right, bottom, left);
        double targetHeight = control.getHeight() > 0 ? control.getHeight() : Math.max(48, contentHeight + 8);
        double extra = Math.max(0, targetHeight - contentHeight - 8);
        @Nullable Pos alignment = control.getAlignment();
        double offset = switch ((alignment == null ? Pos.CENTER : alignment).getVpos()) {
            case CENTER -> extra / 2;
            case BOTTOM -> extra;
            default -> 0;
        };
        return 4 + offset + super.computeBaselineOffset(top, right, bottom, left);
    }

    // Implementation

    /// Creates a skin-owned decoration that cannot intercept input or affect content measurement.
    private static Region artwork(String styleClass) {
        Region region = new Region();
        region.getStyleClass().setAll(styleClass);
        region.setManaged(false);
        region.setMouseTransparent(true);
        return region;
    }

    /// Arms primary presses and activates only when released inside the target.
    private void handleMouse(MouseEvent event) {
        M3ButtonBase control = getSkinnable();
        if (event.getEventType() == MouseEvent.MOUSE_PRESSED) {
            if (control.isDisabled() || keyDown != null || event.getButton() != MouseButton.PRIMARY
                    || event.isMiddleButtonDown() || event.isSecondaryButtonDown()
                    || event.isShiftDown() || event.isControlDown() || event.isAltDown() || event.isMetaDown())
                return;
            if (control.isFocusTraversable())
                control.requestFocus();
            mouseDown = true;
            control.arm();
            event.consume();
        } else if (event.getEventType() == MouseEvent.MOUSE_RELEASED && event.getButton() == MouseButton.PRIMARY && mouseDown) {
            @Nullable Point2D point = control.sceneToLocal(event.getSceneX(), event.getSceneY());
            boolean activate = control.isArmed() && point != null && control.contains(point);
            cancelGesture();
            event.consume();
            if (activate)
                control.fire();
        } else if (mouseDown && event.getEventType() == MouseEvent.MOUSE_EXITED) {
            control.disarm();
        } else if (mouseDown && event.getEventType() == MouseEvent.MOUSE_ENTERED) {
            control.arm();
        } else if (event.getEventType() == MouseEvent.MOUSE_CLICKED && event.getButton() == MouseButton.PRIMARY) {
            event.consume();
        }
    }

    /// Handles Space and Enter once per press, with Escape cancellation and modifier filtering.
    private void handleKey(KeyEvent event) {
        if (event.isConsumed())
            return;
        M3ButtonBase control = getSkinnable();
        if (event.getCode() == KeyCode.ESCAPE && event.getEventType() == KeyEvent.KEY_PRESSED && (mouseDown || keyDown != null)) {
            mouseDown = false;
            control.disarm();
            // Retain key ownership until release so auto-repeat cannot rearm a canceled press.
            event.consume();
        } else if (event.getCode() == KeyCode.SPACE || event.getCode() == KeyCode.ENTER) {
            if (event.getEventType() == KeyEvent.KEY_PRESSED) {
                if (control.isDisabled() || mouseDown || modified(event))
                    return;
                if (keyDown == null) {
                    keyDown = event.getCode();
                    control.arm();
                }
                event.consume();
            } else if (event.getEventType() == KeyEvent.KEY_RELEASED && keyDown == event.getCode()) {
                boolean activate = control.isArmed();
                cancelGesture();
                event.consume();
                if (activate)
                    control.fire();
            }
        }
    }

    /// Runs the first eligible fallback action without replacing any scene accelerators.
    private void handleSceneKey(KeyEvent event) {
        M3ButtonBase control = getSkinnable();
        if (event.isConsumed() || modified(event) || control.isDisabled() || !treeVisible(control))
            return;
        if ((event.getCode() == KeyCode.ENTER && control.isDefaultButton())
                || (event.getCode() == KeyCode.ESCAPE && control.isCancelButton())) {
            event.consume();
            control.fire();
        }
    }

    /// Returns whether a key chord includes a modifier.
    private static boolean modified(KeyEvent event) {
        return event.isShiftDown() || event.isControlDown() || event.isAltDown() || event.isMetaDown();
    }

    /// Excludes buttons hidden by an ancestor from scene-level keyboard activation.
    private static boolean treeVisible(Node node) {
        for (@Nullable Node current = node; current != null; current = current.getParent()) {
            if (!current.isVisible())
                return false;
        }
        return true;
    }

    /// Cancels ownership before moving key handling to a different scene.
    private void updateScene(@Nullable Scene value) {
        cancelGesture();
        if (scene != null) {
            scene.windowProperty().removeListener(windowListener);
        }
        scene = value;
        if (scene != null) {
            scene.windowProperty().addListener(windowListener);
        }
        updateSceneActions();
        updateWindow(scene == null ? null : scene.getWindow());
    }

    /// Moves deactivation listeners without retaining old windows.
    private void updateWindow(@Nullable Window value) {
        cancelGesture();
        if (window != null) {
            window.focusedProperty().removeListener(windowActiveListener);
            window.showingProperty().removeListener(windowActiveListener);
        }
        window = value;
        if (window != null) {
            window.focusedProperty().addListener(windowActiveListener);
            window.showingProperty().addListener(windowActiveListener);
        }
    }

    /// Clears pending activation without firing an action.
    private void cancelGesture() {
        mouseDown = false;
        keyDown = null;
        getSkinnable().disarm();
    }
}
