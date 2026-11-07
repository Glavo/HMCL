// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.controls;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Control;
import org.jackhuang.hmcl.ui.controls.skin.M3AnimatedSkin;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.List;

/// Temporarily suppresses cooperating M3 animations in a node's subtree without changing control properties.
/// All operations belong on the JavaFX application thread. Pair each [#suspend(Node)] with [#resume(Node)]
/// in a `finally` block. Scopes nest independently on the same node and on its ancestors.
@NotNullByDefault
public final class M3Animations {

    // Constants

    /// Private node-property key; only nodes explicitly used as scope roots receive a state object.
    private static final Object SUSPENSION_KEY = new Object();

    // Constructors

    /// Prevents instantiation of the subtree animation utility.
    private M3Animations() {
    }

    // Methods

    /// Enters a suppression scope and settles existing animations on its first entry.
    /// Does not install skins or change the global animation policy or property bindings.
    public static void suspend(Node root) {
        checkThread();
        @Nullable SuspensionState state = state(root);
        if (state == null) {
            state = new SuspensionState();
            root.getProperties().put(SUSPENSION_KEY, state);
        }
        state.depth = Math.incrementExact(state.depth);
        if (state.depth == 1) {
            try {
                finishSubtree(root);
            } catch (RuntimeException | Error failure) {
                state.depth--;
                throw failure;
            }
        }
    }

    /// Leaves one scope without replaying suppressed changes.
    /// The outermost exit also settles animations on nodes added since entry, even before their next pulse.
    /// Throws [IllegalStateException] if this root has no matching scope.
    public static void resume(Node root) {
        checkThread();
        @Nullable SuspensionState state = state(root);
        if (state == null || state.depth == 0)
            throw new IllegalStateException("No animation suppression scope is active on this node");
        try {
            if (state.depth == 1)
                finishSubtree(root);
        } finally {
            state.depth--;
        }
    }

    /// Returns whether this node or any current ancestor is suspended, without allocating properties.
    /// Animated skins must check both when scheduling a transition and when advancing an active transition.
    public static boolean isSuspended(Node node) {
        for (@Nullable Node current = node; current != null; current = current.getParent()) {
            @Nullable SuspensionState state = state(current);
            if (state != null && state.depth != 0)
                return true;
        }
        return false;
    }

    // Implementation

    /// Reads an existing marker without creating a node's properties map.
    private static @Nullable SuspensionState state(Node node) {
        return node.hasProperties() ? (SuspensionState) node.getProperties().get(SUSPENSION_KEY) : null;
    }

    /// Settles installed participating skins, including the root itself and currently detached content trees.
    private static void finishSubtree(Node root) {
        if (root instanceof Control control && control.getSkin() instanceof M3AnimatedSkin skin)
            skin.finishAnimations();
        if (root instanceof Parent parent) {
            @UnmodifiableView List<Node> children = parent.getChildrenUnmodifiable();
            for (int i = 0; i < children.size(); i++)
                finishSubtree(children.get(i));
        }
    }

    /// Rejects scope mutations outside the scene graph's owning thread.
    private static void checkThread() {
        if (!Platform.isFxApplicationThread())
            throw new IllegalStateException("Animation suppression requires the JavaFX application thread");
    }

    // Supporting types

    /// Retains a reusable nesting counter on an explicitly used scope root.
    @NotNullByDefault
    private static final class SuspensionState {

        /// Creates an inactive marker that can be reused across cell refreshes.
        private SuspensionState() {
        }

        /// Number of unmatched entries on this root; zero leaves ancestor scopes in effect.
        private int depth;
    }
}
