// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.controls.skin;

import javafx.beans.InvalidationListener;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import org.jackhuang.hmcl.ui.controls.M3ButtonBase;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/// Registers default and cancel actions with scene-owned arbitration and reversible accelerator ownership.
@NotNullByDefault
final class M3ButtonAccelerators {
    /// Key storing the registry on each scene, avoiding global scene references.
    private static final Object REGISTRY_KEY = new Object();

    /// The registered command button.
    private final M3ButtonBase button;

    /// Registry currently owning this button, or null.
    private @Nullable Registry registry;

    /// Updates registration on role and scene changes.
    private final InvalidationListener changed = observable -> update();

    /// Observes a button's accelerator roles for the lifetime of its skin.
    M3ButtonAccelerators(M3ButtonBase button) {
        this.button = button;
        button.sceneProperty().addListener(changed);
        button.defaultButtonProperty().addListener(changed);
        button.cancelButtonProperty().addListener(changed);
        update();
    }

    /// Transfers registration to the current scene when an accelerator role is active.
    private void update() {
        @Nullable Scene scene = button.getScene();
        if (registry != null) {
            if (registry.scene == scene && (button.isDefaultButton() || button.isCancelButton()))
                return;
            registry.remove(button);
            registry = null;
        }
        if (scene != null && (button.isDefaultButton() || button.isCancelButton())) {
            Registry current = (Registry) scene.getProperties().computeIfAbsent(REGISTRY_KEY, key -> new Registry(scene));
            current.buttons.add(button);
            registry = current;
        }
    }

    /// Removes listeners and releases accelerator ownership.
    void dispose() {
        button.sceneProperty().removeListener(changed);
        button.defaultButtonProperty().removeListener(changed);
        button.cancelButtonProperty().removeListener(changed);
        if (registry != null) {
            registry.remove(button);
            registry = null;
        }
    }

    /// Owns one pair of scene accelerators while at least one Material command role is registered.
    @NotNullByDefault
    private static final class Registry {
        /// Scene retaining this registry only while it has registrations.
        private final Scene scene;

        /// Registered buttons in activation-priority order.
        private final List<M3ButtonBase> buttons = new ArrayList<>();

        /// Default-action accelerator key.
        private final KeyCombination enter = new KeyCodeCombination(KeyCode.ENTER);

        /// Cancel-action accelerator key.
        private final KeyCombination escape = new KeyCodeCombination(KeyCode.ESCAPE);

        /// Default-action handler replaced while this registry is active.
        private final @Nullable Runnable previousEnter;

        /// Cancel-action handler replaced while this registry is active.
        private final @Nullable Runnable previousEscape;

        /// Default action installed by this registry.
        private final Runnable defaultAction = () -> activate(true);

        /// Cancel action installed by this registry.
        private final Runnable cancelAction = () -> activate(false);

        /// Captures and temporarily replaces the scene's existing accelerators.
        private Registry(Scene scene) {
            this.scene = scene;
            previousEnter = scene.getAccelerators().put(enter, defaultAction);
            previousEscape = scene.getAccelerators().put(escape, cancelAction);
        }

        /// Fires the last eligible role holder, falling back to the previous scene action.
        private void activate(boolean defaultRole) {
            for (int i = buttons.size() - 1; i >= 0; i--) {
                M3ButtonBase candidate = buttons.get(i);
                if ((defaultRole ? candidate.isDefaultButton() : candidate.isCancelButton())
                        && !candidate.isDisabled() && visible(candidate)) {
                    candidate.fire();
                    return;
                }
            }
            @Nullable Runnable previous = defaultRole ? previousEnter : previousEscape;
            if (previous != null)
                previous.run();
        }

        /// Checks visibility along the complete ancestor chain.
        private boolean visible(Node node) {
            for (@Nullable Node current = node; current != null; current = current.getParent()) {
                if (!current.isVisible())
                    return false;
            }
            return true;
        }

        /// Releases a button and restores earlier accelerators when the registry becomes empty.
        private void remove(M3ButtonBase button) {
            buttons.remove(button);
            if (buttons.isEmpty()) {
                restore(enter, defaultAction, previousEnter);
                restore(escape, cancelAction, previousEscape);
                scene.getProperties().remove(REGISTRY_KEY);
            }
        }

        /// Restores only an accelerator still owned by this registry.
        private void restore(KeyCombination key, Runnable action, @Nullable Runnable previous) {
            if (scene.getAccelerators().get(key) == action) {
                if (previous == null)
                    scene.getAccelerators().remove(key);
                else
                    scene.getAccelerators().put(key, previous);
            }
        }
    }
}
