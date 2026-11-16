// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.controls;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.AccessibleAttribute;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.jackhuang.hmcl.ui.animation.Motion;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

/// Owns one overlay pane's snackbar queue, surface animation and display timer.
@NotNullByDefault
final class M3SnackbarPresenter extends StackPane {

    /// The pane coordinating this layer with modal overlays.
    private final M3OverlayPane host;

    /// Messages waiting in submission order.
    private final ObservableList<M3Snackbar> pending = FXCollections.observableArrayList();

    /// The live read-only view of pending messages.
    private final @UnmodifiableView ObservableList<M3Snackbar> queue = FXCollections.unmodifiableObservableList(pending);

    /// The current message, or `null` while idle.
    private final ReadOnlyObjectWrapper<@Nullable M3Snackbar> current;

    /// Counts the remaining visible time of the current plain message.
    private final PauseTransition timer = new PauseTransition();

    /// The active entrance animation, or `null` after entrance.
    private @Nullable FadeTransition entrance;

    /// Whether a modal layer currently suspends the timer.
    private boolean modalBlocked;

    /// Rejects queue submissions while all messages are being discarded.
    private boolean clearing;

    /// Creates a persistent, input-transparent snackbar layer in the supplied host.
    M3SnackbarPresenter(M3OverlayPane host, ReadOnlyObjectWrapper<@Nullable M3Snackbar> current) {
        this.host = host;
        this.current = current;
        setPickOnBounds(false);
        setPadding(new Insets(24));
        setAlignment(Pos.BOTTOM_CENTER);
        timer.setOnFinished(event -> dismissCurrent());
    }

    /// Returns the live, unmodifiable queue, excluding the current message.
    @UnmodifiableView ObservableList<M3Snackbar> getQueue() {
        return queue;
    }

    /// Claims a detached message and appends it to this host's queue.
    void enqueue(M3Snackbar message) {
        if (clearing || message.isQueued() || message.getParent() != null)
            throw new IllegalStateException("The snackbar is already queued or its queue is being cleared");
        message.validate();
        message.setHost(host);
        pending.add(message);
        advance();
    }

    /// Dismisses the current message, if any.
    void dismissCurrent() {
        @Nullable M3Snackbar message = current.get();
        if (message != null)
            dismiss(message);
    }

    /// Removes one current or pending message and advances the queue when needed.
    void dismiss(M3Snackbar message) {
        if (current.get() != message) {
            if (pending.remove(message))
                message.setHost(null);
            return;
        }
        stopMotion();
        getChildren().clear();
        message.setHost(null);
        message.setOpacity(1);
        current.set(null);
        message.setShowing(false);
        advance();
    }

    /// Releases all pending messages without changing the current message.
    void clearQueue() {
        for (M3Snackbar message : pending)
            message.setHost(null);
        pending.clear();
    }

    /// Discards all entries and cancels animation and timeout progress.
    void dismissAll() {
        if (clearing)
            return;
        clearing = true;
        try {
            clearQueue();
            dismissCurrent();
        } finally {
            clearing = false;
        }
    }

    /// Suspends or resumes the remaining timeout when the host's modal stack changes.
    void setModalBlocked(boolean value) {
        modalBlocked = value;
        updateTimer();
    }

    /// Restarts the timeout after a current message's duration or affordances change.
    void messageChanged(M3Snackbar message) {
        if (current.get() == message)
            restartTimer();
    }

    /// Pauses for modality, hover or persistent affordances; otherwise resumes remaining display time.
    void updateTimer() {
        @Nullable M3Snackbar message = current.get();
        if (message == null || entrance != null)
            return;
        if (modalBlocked || message.isHover() || message.hasAction() || message.isCloseButtonVisible()
                || message.getDuration().isIndefinite()) {
            timer.pause();
        } else {
            timer.play();
        }
    }

    /// Promotes the first pending message when the presentation slot is available.
    private void advance() {
        if (clearing || current.get() != null || pending.isEmpty())
            return;
        M3Snackbar next = pending.remove(0);
        next.setOpacity(1);
        getChildren().setAll(next);
        current.set(next);
        if (current.get() != next)
            return;
        next.setShowing(true);
        if (current.get() != next)
            return;
        next.notifyAccessibleAttributeChanged(AccessibleAttribute.TEXT);
        if (!next.isAnimated()) {
            restartTimer();
            return;
        }
        FadeTransition fade = new FadeTransition(Motion.SHORT4, next);
        entrance = fade;
        next.setOpacity(0);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.setInterpolator(Motion.STANDARD_DECELERATE);
        fade.setOnFinished(event -> {
            entrance = null;
            restartTimer();
        });
        fade.play();
    }

    /// Starts a fresh display interval after entrance or a configuration change.
    private void restartTimer() {
        timer.stop();
        @Nullable M3Snackbar message = current.get();
        if (message != null) {
            timer.setDuration(message.getDuration().isIndefinite() ? Duration.seconds(4) : message.getDuration());
            updateTimer();
        }
    }

    /// Stops timeouts and entrance motion before detaching the current surface.
    private void stopMotion() {
        timer.stop();
        if (entrance != null) {
            entrance.stop();
            entrance = null;
        }
    }
}
