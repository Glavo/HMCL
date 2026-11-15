// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.controls;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Window;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/// Observes the lifetime of an overlay without retaining a detached host or window.
@NotNullByDefault
final class M3OverlaySupport implements AutoCloseable {

    /// The host whose scene must remain unchanged.
    private final StackPane host;
    /// The scene that owns the overlay.
    private final Scene scene;
    /// The window whose disappearance ends the presentation.
    private final Window window;
    /// The node installed in the host.
    private final Node layer;
    /// Removes the presentation when its host becomes unavailable.
    private final ChangeListener<Object> unavailable;

    /// Starts observing an attached overlay in a showing window.
    M3OverlaySupport(StackPane host, Node layer, Runnable cleanup) {
        this.host = host;
        this.layer = layer;
        scene = Objects.requireNonNull(host.getScene());
        window = Objects.requireNonNull(scene.getWindow());
        unavailable = (observable, oldValue, newValue) -> cleanup.run();
        host.sceneProperty().addListener(unavailable);
        layer.parentProperty().addListener(unavailable);
        scene.windowProperty().addListener(unavailable);
        window.showingProperty().addListener(unavailable);
    }

    /// Requires the JavaFX application thread and a host in a showing window.
    ///
    /// @throws IllegalStateException if the thread or host cannot present an overlay
    static void checkHost(StackPane host) {
        checkThread();
        @Nullable Scene scene = host.getScene();
        @Nullable Window window = scene == null ? null : scene.getWindow();
        if (window == null || !window.isShowing())
            throw new IllegalStateException("The overlay host must belong to a showing window");
    }

    /// Rejects presentation operations on other threads.
    static void checkThread() {
        if (!Platform.isFxApplicationThread())
            throw new IllegalStateException("Overlay operations require the JavaFX application thread");
    }

    /// Returns whether a node belongs to the supplied subtree.
    static boolean contains(Node root, @Nullable Node node) {
        for (@Nullable Node current = node; current != null; current = current.getParent()) {
            if (current == root)
                return true;
        }
        return false;
    }

    /// Releases all observations before the overlay is removed from its host.
    @Override
    public void close() {
        host.sceneProperty().removeListener(unavailable);
        layer.parentProperty().removeListener(unavailable);
        scene.windowProperty().removeListener(unavailable);
        window.showingProperty().removeListener(unavailable);
    }
}
