// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.controls;

import javafx.animation.FadeTransition;
import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.input.InputEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.ui.animation.Motion;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// Adapts one dialog's dismissal events and animation to a host-owned modal overlay.
@NotNullByDefault
final class M3DialogPresentation {

    /// The pane owning stacking, focus, input isolation and host observation.
    private final M3OverlayPane host;

    /// The dialog content for this presentation.
    private final M3Dialog dialog;

    /// The Material scrim surrounding the dialog.
    private final StackPane layer = new StackPane();

    /// The handle identifying this exact modal layer.
    private final M3OverlayPane.OverlayHandle handle;

    /// The transition reused for entrance and accepted dismissal.
    private final FadeTransition transition = new FadeTransition();

    /// Whether a close request is being dispatched or an accepted exit is running.
    private boolean closing;

    /// The initiating action retained through the accepted dismissal.
    private @Nullable Button closingAction;

    /// Configures a detached dialog layer and its component-specific input behavior.
    M3DialogPresentation(M3OverlayPane host, M3Dialog dialog) {
        this.host = host;
        this.dialog = dialog;
        handle = new M3OverlayPane.OverlayHandle(host, layer, true, dialog, this::hidden);
        transition.setNode(layer);
        layer.getStyleClass().add("m3-dialog-overlay");
        layer.setOnMouseClicked(event -> {
            if (event.getTarget() == layer && event.getButton() == MouseButton.PRIMARY && dialog.isDismissOnScrimClick())
                requestClose(null);
            event.consume();
        });
        layer.addEventFilter(InputEvent.ANY, event -> {
            if (closing)
                event.consume();
        });
        layer.addEventFilter(ActionEvent.ACTION, event -> {
            if (closing)
                event.consume();
        });
        layer.addEventHandler(ActionEvent.ACTION, event -> {
            if (event.getTarget() instanceof Button button && dialog.getActions().contains(button))
                requestClose(button);
        });
        layer.addEventHandler(KeyEvent.KEY_PRESSED, this::handleKey);
    }

    /// Installs the dialog in the host's modal stack and starts entrance if it remains attached.
    M3OverlayPane.OverlayHandle show() {
        layer.getChildren().add(dialog);
        host.installOverlay(handle);
        if (handle.isShowing()) {
            dialog.setPresentation(this);
            animate(0, 1, () -> dialog.fireEvent(new M3DialogEvent(dialog, M3DialogEvent.SHOWN, null)));
        }
        return handle;
    }

    /// Requests cancellable dismissal of this presentation only.
    void requestClose(@Nullable Button action) {
        if (!handle.isShowing() || closing)
            return;
        closing = true;
        M3DialogEvent event = new M3DialogEvent(dialog, M3DialogEvent.CLOSE_REQUEST, action);
        try {
            dialog.fireEvent(event);
        } catch (RuntimeException | Error failure) {
            closing = false;
            throw failure;
        }
        if (!handle.isShowing())
            return;
        if (event.isConsumed()) {
            closing = false;
            return;
        }
        closingAction = action;
        animate(layer.getOpacity(), 0, this::hide);
    }

    /// Forcibly removes this presentation through its host-owned handle.
    void hide() {
        handle.hide();
    }

    /// Releases content and reports completion after the host has removed the modal layer.
    private void hidden() {
        transition.stop();
        layer.getChildren().clear();
        dialog.setPresentation(null);
        dialog.fireEvent(new M3DialogEvent(dialog, M3DialogEvent.HIDDEN, closingAction));
    }

    /// Animates the scrim and dialog together while the handle remains attached.
    private void animate(double from, double to, Runnable finished) {
        if (!handle.isShowing())
            return;
        transition.stop();
        layer.setOpacity(from);
        if (!dialog.isAnimated()) {
            layer.setOpacity(to);
            finished.run();
            return;
        }
        transition.setDuration(to == 1 ? Motion.MEDIUM2 : Motion.SHORT3);
        transition.setFromValue(from);
        transition.setToValue(to);
        transition.setInterpolator(to == 1 ? Motion.STANDARD_DECELERATE : Motion.STANDARD_ACCELERATE);
        transition.setOnFinished(event -> {
            if (handle.isShowing())
                finished.run();
        });
        transition.play();
    }

    /// Handles unconsumed Escape and Enter after the focused content has processed the key.
    private void handleKey(KeyEvent event) {
        if (!handle.isShowing())
            return;
        if (event.getCode() == KeyCode.ESCAPE) {
            if (dialog.isCloseOnEscape())
                requestClose(null);
            event.consume();
        } else if (event.getCode() == KeyCode.ENTER && !(event.getTarget() instanceof TextArea)) {
            if (dialog.getScene().getFocusOwner() instanceof Button button) {
                button.fire();
            } else {
                dialog.getActions().stream().filter(Button.class::isInstance).map(Button.class::cast)
                        .filter(button -> button.isDefaultButton() && !button.isDisabled() && button.isVisible())
                        .findFirst().ifPresent(Button::fire);
            }
            // Prevent scene-level default buttons from receiving an unhandled dialog activation.
            event.consume();
        }
    }
}
