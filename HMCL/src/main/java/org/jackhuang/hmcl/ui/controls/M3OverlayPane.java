// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.controls;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.VPos;
import javafx.scene.AccessibleAttribute;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.InputEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Region;
import javafx.stage.Window;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// Hosts application content, regular overlays, snackbars and modal overlays in that visual order.
///
/// Install one pane as the scene root or as the container filling the application's interaction area.
/// Only [#getContent()] contributes to minimum and preferred size. Presentation layers occupy the same
/// client area without resizing the application. Showing a layer never replaces the scene root.
///
/// Modal layers form a stack. Only the uppermost modal receives input or appears in this pane's
/// accessibility children. Focus remains in that layer; removal restores focus to the preceding
/// modal or application content. Snackbar timeouts pause while any modal is present.
///
/// The pane owns host observation and releases all presentations when detached from its scene or
/// when its window changes or hides. A detached node or message can subsequently be shown again.
/// All configuration and presentation operations must run on the JavaFX application thread.
/// Presentations require a showing host window. Nodes must not be reparented while presented.
///
/// ```java
/// M3OverlayPane overlays = new M3OverlayPane();
/// overlays.setContent(applicationContent);
/// stage.setScene(new Scene(overlays));
/// stage.show();
/// overlays.showDialog(dialog);
/// overlays.showSnackbar(new M3Snackbar("Saved"));
/// ```
@NotNullByDefault
public final class M3OverlayPane extends Region {

    /// The persistent snackbar layer between regular and modal overlays.
    private final M3SnackbarPresenter snackbars;

    /// Regular layers in bottom-to-top order.
    private final List<OverlayHandle> regularOverlays = new ArrayList<>();

    /// Modal layers in bottom-to-top order.
    private final List<OverlayHandle> modalOverlays = new ArrayList<>();

    /// The scene currently observed for input, focus and window changes.
    private @Nullable Scene observedScene;

    /// The window currently observed for hiding.
    private @Nullable Window observedWindow;

    /// Rejects new presentations during forced bulk removal.
    private boolean clearing;

    /// Applies input isolation through the current scene's capture phase.
    private final EventHandler<InputEvent> inputFilter = this::filterInput;

    /// Prevents lower-layer programmatic button activation while a modal is present.
    private final EventHandler<ActionEvent> actionFilter = this::filterModalEvent;

    /// Redirects escaped focus into the uppermost modal layer.
    private final ChangeListener<@Nullable Node> focusListener = (observable, oldValue, newValue) -> focusModal(null);

    /// Ends presentations when the scene is assigned to another window.
    private final ChangeListener<@Nullable Window> windowListener = (observable, oldValue, newValue) -> {
        try {
            clear();
        } finally {
            observeWindow(newValue);
        }
    };

    /// Ends presentations when the owning window becomes hidden.
    private final ChangeListener<Boolean> showingListener = (observable, oldValue, newValue) -> {
        if (!newValue)
            clear();
    };

    /// Creates an empty host with an idle snackbar layer.
    public M3OverlayPane() {
        getStyleClass().add("m3-overlay-pane");
        getStylesheets().add(Objects.requireNonNull(M3OverlayPane.class.getResource("m3-overlays.css")).toExternalForm());
        setAccessibleRole(AccessibleRole.PARENT);
        setPickOnBounds(false);
        snackbars = new M3SnackbarPresenter(this, snackbar);
        getChildren().add(snackbars);
        sceneProperty().addListener((observable, oldValue, newValue) -> observeScene(newValue));
    }

    /// The application content beneath all presentation layers.
    private final ReadOnlyObjectWrapper<@Nullable Node> content = new ReadOnlyObjectWrapper<>(this, "content");

    /// Returns the application content, or `null` when empty.
    public @Nullable Node getContent() {
        return content.get();
    }

    /// Installs unparented content, or removes it when `null`; assigning the current node has no effect.
    ///
    /// @throws IllegalArgumentException if the node is this pane or an ancestor
    /// @throws IllegalStateException if the node already has a parent or belongs to a scene
    public void setContent(@Nullable Node value) {
        checkThread();
        @Nullable Node previous = getContent();
        if (value == previous)
            return;
        if (value != null) {
            validateNode(value);
            getChildren().add(0, value);
        }
        if (previous != null)
            getChildren().remove(previous);
        content.set(value);
        requestLayout();
    }

    /// Returns the read-only content property, initially `null`.
    public ReadOnlyObjectProperty<@Nullable Node> contentProperty() {
        return content.getReadOnlyProperty();
    }

    /// The current message shown by this pane, or `null` while idle.
    private final ReadOnlyObjectWrapper<@Nullable M3Snackbar> snackbar = new ReadOnlyObjectWrapper<>(this, "snackbar");

    /// Returns the current snackbar, or `null` while idle.
    public @Nullable M3Snackbar getSnackbar() {
        return snackbar.get();
    }

    /// Returns the read-only current-message property, initially `null`.
    public ReadOnlyObjectProperty<@Nullable M3Snackbar> snackbarProperty() {
        return snackbar.getReadOnlyProperty();
    }

    /// Returns a live, unmodifiable view of pending snackbars, excluding the current message.
    public @UnmodifiableView ObservableList<M3Snackbar> getSnackbarQueue() {
        return snackbars.getQueue();
    }

    /// Shows an unparented node above content and below snackbar and modal layers.
    ///
    /// @return a handle that removes only this presentation
    /// @throws IllegalStateException if the host cannot present or the node is already attached
    /// @throws IllegalArgumentException if the node is this pane or an ancestor
    public OverlayHandle showOverlay(Node overlay) {
        return showOverlay(overlay, false);
    }

    /// Shows an unparented node as the top modal layer; the node supplies its own visual scrim if needed.
    ///
    /// @return a handle that removes only this presentation
    /// @throws IllegalStateException if the host cannot present or the node is already attached
    /// @throws IllegalArgumentException if the node is this pane or an ancestor
    public OverlayHandle showModalOverlay(Node overlay) {
        return showOverlay(overlay, true);
    }

    /// Presents a dialog above existing modal layers and starts its entrance animation.
    ///
    /// The returned handle forcibly removes this exact presentation. Use [M3Dialog#requestClose()]
    /// for cancellable dismissal. The same dialog instance cannot be presented concurrently.
    ///
    /// @throws IllegalStateException if the host cannot present or the dialog is already attached
    public OverlayHandle showDialog(M3Dialog dialog) {
        checkReady();
        validateNode(dialog);
        if (dialog.isShowing())
            throw new IllegalStateException("The dialog is already showing");
        M3DialogPresentation presentation = new M3DialogPresentation(this, dialog);
        return presentation.show();
    }

    /// Appends a message to this host's FIFO queue, showing it immediately when the layer is idle.
    ///
    /// @throws IllegalStateException if the host cannot present, the message is already queued or attached,
    ///         or the snackbar queue is being cleared
    public void showSnackbar(M3Snackbar snackbar) {
        checkReady();
        validateNode(snackbar);
        snackbars.enqueue(snackbar);
    }

    /// Dismisses the current snackbar and promotes the next pending message, if any.
    public void dismissSnackbar() {
        checkThread();
        snackbars.dismissCurrent();
    }

    /// Removes pending messages without dismissing the current snackbar.
    public void clearSnackbarQueue() {
        checkThread();
        snackbars.clearQueue();
    }

    /// Discards current and pending snackbars; reentrant submissions during removal are rejected.
    public void dismissAllSnackbars() {
        checkThread();
        snackbars.dismissAll();
    }

    /// Immediately removes every presentation while retaining application content.
    /// Reentrant show requests during cleanup are rejected; repeated calls while clearing have no effect.
    /// Callback failures are rethrown after remaining presentations have been removed.
    public void clear() {
        checkThread();
        if (clearing)
            return;
        clearing = true;
        @Nullable Throwable failure = null;
        try {
            try {
                snackbars.dismissAll();
            } catch (RuntimeException | Error exception) {
                failure = exception;
            }
            // Handles are detached before their callbacks run, so a failing callback cannot stop cleanup.
            while (!modalOverlays.isEmpty() || !regularOverlays.isEmpty()) {
                List<OverlayHandle> layers = modalOverlays.isEmpty() ? regularOverlays : modalOverlays;
                try {
                    layers.get(layers.size() - 1).hide();
                } catch (RuntimeException | Error exception) {
                    if (failure == null)
                        failure = exception;
                    else if (failure != exception)
                        failure.addSuppressed(exception);
                }
            }
        } finally {
            clearing = false;
        }
        if (failure instanceof RuntimeException exception)
            throw exception;
        if (failure instanceof Error error)
            throw error;
    }

    /// Exposes only the top modal subtree to accessibility clients while a modal is present.
    @Override
    public @Nullable Object queryAccessibleAttribute(AccessibleAttribute attribute, @Nullable Object... parameters) {
        @Nullable Node modal = topModal();
        if (modal == null)
            return super.queryAccessibleAttribute(attribute, parameters);
        return switch (attribute) {
            case CHILDREN -> FXCollections.singletonObservableList(modal);
            case CONTENTS -> modal;
            case FOCUS_NODE -> observedScene != null && contains(modal, observedScene.getFocusOwner())
                    ? observedScene.getFocusOwner() : modal;
            default -> super.queryAccessibleAttribute(attribute, parameters);
        };
    }

    /// Returns the sizing dependency of application content, or `null` when there is none.
    @Override
    public @Nullable Orientation getContentBias() {
        @Nullable Node node = getContent();
        return node == null ? null : node.getContentBias();
    }

    /// Sizes the host using only application content and horizontal insets.
    @Override
    protected double computeMinWidth(double height) {
        @Nullable Node node = getContent();
        double inner = height < 0 ? -1 : Math.max(0, height - snappedTopInset() - snappedBottomInset());
        return snappedLeftInset() + snappedRightInset() + (node == null ? 0 : node.minWidth(inner));
    }

    /// Sizes the host using only application content and vertical insets.
    @Override
    protected double computeMinHeight(double width) {
        @Nullable Node node = getContent();
        double inner = width < 0 ? -1 : Math.max(0, width - snappedLeftInset() - snappedRightInset());
        return snappedTopInset() + snappedBottomInset() + (node == null ? 0 : node.minHeight(inner));
    }

    /// Computes preferred width independently of presentation layers.
    @Override
    protected double computePrefWidth(double height) {
        @Nullable Node node = getContent();
        double inner = height < 0 ? -1 : Math.max(0, height - snappedTopInset() - snappedBottomInset());
        return snappedLeftInset() + snappedRightInset() + (node == null ? 0 : node.prefWidth(inner));
    }

    /// Computes preferred height independently of presentation layers.
    @Override
    protected double computePrefHeight(double width) {
        @Nullable Node node = getContent();
        double inner = width < 0 ? -1 : Math.max(0, width - snappedLeftInset() - snappedRightInset());
        return snappedTopInset() + snappedBottomInset() + (node == null ? 0 : node.prefHeight(inner));
    }

    /// Lays out every layer within the same inset client area.
    @Override
    protected void layoutChildren() {
        double x = snappedLeftInset();
        double y = snappedTopInset();
        double width = Math.max(0, getWidth() - x - snappedRightInset());
        double height = Math.max(0, getHeight() - y - snappedBottomInset());
        for (Node child : getChildren())
            layoutInArea(child, x, y, width, height, 0, Insets.EMPTY, true, true, HPos.CENTER, VPos.CENTER);
    }

    /// Removes a particular message when requested through its control.
    void dismissSnackbar(M3Snackbar message) {
        snackbars.dismiss(message);
    }

    /// Applies live duration or action changes to the current message.
    void snackbarChanged(M3Snackbar message) {
        snackbars.messageChanged(message);
    }

    /// Resumes or pauses snackbar timing after hover changes.
    void updateSnackbarTimer() {
        snackbars.updateTimer();
    }

    /// Installs one prepared presentation in the appropriate visual layer.
    void installOverlay(OverlayHandle handle) {
        List<OverlayHandle> layers = handle.modal ? modalOverlays : regularOverlays;
        layers.add(handle);
        getChildren().add(handle.modal ? getChildren().size() : getChildren().indexOf(snackbars), handle.node);
        updateModality();
        if (handle.modal)
            focusModal(null);
    }

    /// Rejects operations outside the JavaFX application thread.
    static void checkThread() {
        if (!Platform.isFxApplicationThread())
            throw new IllegalStateException("Overlay operations require the JavaFX application thread");
    }

    /// Returns whether the supplied node belongs to a subtree.
    static boolean contains(Node root, @Nullable Node node) {
        for (@Nullable Node current = node; current != null; current = current.getParent()) {
            if (current == root)
                return true;
        }
        return false;
    }

    /// Validates and installs a regular or custom modal node.
    private OverlayHandle showOverlay(Node node, boolean modal) {
        checkReady();
        validateNode(node);
        OverlayHandle handle = new OverlayHandle(this, node, modal, node, () -> { });
        installOverlay(handle);
        return handle;
    }

    /// Rejects presentations without a showing window or during bulk cleanup.
    private void checkReady() {
        checkThread();
        @Nullable Scene scene = getScene();
        @Nullable Window window = scene == null ? null : scene.getWindow();
        if (clearing || window == null || !window.isShowing())
            throw new IllegalStateException("The overlay host must belong to a showing window and must not be clearing");
    }

    /// Rejects existing scene ownership and ancestor cycles before attachment.
    private void validateNode(Node node) {
        Objects.requireNonNull(node, "node");
        if (contains(node, this))
            throw new IllegalArgumentException("An overlay host cannot contain itself or an ancestor");
        if (node.getParent() != null || node.getScene() != null)
            throw new IllegalStateException("The node is already attached");
    }

    /// Removes exactly one owned layer and restores focus before dispatching its cleanup callback.
    private boolean hideOverlay(OverlayHandle handle) {
        List<OverlayHandle> layers = handle.modal ? modalOverlays : regularOverlays;
        boolean wasTop = handle.node == topModal();
        if (handle.owner != this || !layers.remove(handle))
            return false;
        handle.owner = null;
        for (OverlayHandle remaining : modalOverlays) {
            if (contains(handle.node, remaining.previousFocus))
                remaining.previousFocus = handle.previousFocus;
        }
        if (wasTop) {
            if (topModal() != null) {
                focusModal(handle.previousFocus);
            } else if (handle.previousFocus != null && canFocus(handle.previousFocus)) {
                handle.previousFocus.requestFocus();
            }
        }
        getChildren().remove(handle.node);
        updateModality();
        handle.onHidden.run();
        return true;
    }

    /// Returns the highest modal node, or `null` when the stack is empty.
    private @Nullable Node topModal() {
        return modalOverlays.isEmpty() ? null : modalOverlays.get(modalOverlays.size() - 1).node;
    }

    /// Updates snackbar timing and notifies accessibility clients after modal stack changes.
    private void updateModality() {
        snackbars.setModalBlocked(!modalOverlays.isEmpty());
        notifyAccessibleAttributeChanged(AccessibleAttribute.CHILDREN);
        notifyAccessibleAttributeChanged(AccessibleAttribute.CONTENTS);
    }

    /// Blocks events targeted outside the top modal subtree.
    private void filterModalEvent(Event event) {
        @Nullable Node modal = topModal();
        if (modal != null && (!(event.getTarget() instanceof Node node) || !contains(modal, node)))
            event.consume();
    }

    /// Applies modal input isolation and local Tab traversal before child controls handle input.
    private void filterInput(InputEvent event) {
        filterModalEvent(event);
        @Nullable Node modal = topModal();
        if (event.isConsumed() || modal == null)
            return;
        if (event instanceof KeyEvent key && key.getEventType() == KeyEvent.KEY_PRESSED
                && key.getCode() == KeyCode.TAB && !key.isControlDown() && !key.isAltDown() && !key.isMetaDown()) {
            List<Node> targets = new ArrayList<>();
            Node root = modalOverlays.get(modalOverlays.size() - 1).focusRoot;
            if (root instanceof Parent parent)
                collectFocusTargets(parent, targets);
            if (!targets.isEmpty()) {
                int index = targets.indexOf(observedScene == null ? null : observedScene.getFocusOwner());
                int next = index < 0 ? (key.isShiftDown() ? targets.size() - 1 : 0)
                        : Math.floorMod(index + (key.isShiftDown() ? -1 : 1), targets.size());
                targets.get(next).requestFocus();
            }
            key.consume();
        }
    }

    /// Focuses the preferred reachable modal target, preserving focus already inside the top layer.
    private void focusModal(@Nullable Node preferred) {
        @Nullable Node modal = topModal();
        if (modal == null || observedScene == null || contains(modal, observedScene.getFocusOwner()))
            return;
        if (preferred != null && canFocus(preferred) && contains(modal, preferred)) {
            preferred.requestFocus();
            return;
        }
        Node root = modalOverlays.get(modalOverlays.size() - 1).focusRoot;
        if (canFocus(root) && root.isFocusTraversable()) {
            root.requestFocus();
            return;
        }
        List<Node> targets = new ArrayList<>();
        if (root instanceof Parent parent)
            collectFocusTargets(parent, targets);
        if (!targets.isEmpty())
            targets.get(0).requestFocus();
        else
            modal.requestFocus();
    }

    /// Returns whether a target and its ancestors are visible and enabled in the observed scene.
    private boolean canFocus(@Nullable Node node) {
        if (node == null || node.getScene() != observedScene)
            return false;
        for (@Nullable Node current = node; current != null; current = current.getParent()) {
            if (!current.isVisible() || current.isDisabled())
                return false;
        }
        return true;
    }

    /// Collects visible, enabled traversal targets in scene-graph order.
    private static void collectFocusTargets(Parent parent, List<Node> targets) {
        for (Node child : parent.getChildrenUnmodifiable()) {
            if (!child.isVisible() || child.isDisabled())
                continue;
            if (child.isFocusTraversable())
                targets.add(child);
            if (child instanceof Parent nested)
                collectFocusTargets(nested, targets);
        }
    }

    /// Releases the old scene's presentations and listeners before observing a new scene.
    private void observeScene(@Nullable Scene scene) {
        try {
            clear();
        } finally {
            if (observedScene != null) {
                observedScene.removeEventFilter(InputEvent.ANY, inputFilter);
                observedScene.removeEventFilter(ActionEvent.ACTION, actionFilter);
                observedScene.focusOwnerProperty().removeListener(focusListener);
                observedScene.windowProperty().removeListener(windowListener);
            }
            observeWindow(null);
            observedScene = scene;
            if (scene != null) {
                scene.addEventFilter(InputEvent.ANY, inputFilter);
                scene.addEventFilter(ActionEvent.ACTION, actionFilter);
                scene.focusOwnerProperty().addListener(focusListener);
                scene.windowProperty().addListener(windowListener);
                observeWindow(scene.getWindow());
            }
        }
    }

    /// Moves the window-hiding listener to the scene's current window.
    private void observeWindow(@Nullable Window window) {
        if (observedWindow != null)
            observedWindow.showingProperty().removeListener(showingListener);
        observedWindow = window;
        if (window != null)
            window.showingProperty().addListener(showingListener);
    }

    /// Controls one regular, custom modal or dialog presentation independently of later reuse.
    @NotNullByDefault
    public static final class OverlayHandle {

        /// The owning host, cleared before the layer's hidden callback runs.
        private @Nullable M3OverlayPane owner;

        /// The node installed by this presentation.
        private final Node node;

        /// Whether this layer belongs to the modal stack.
        private final boolean modal;

        /// The content root used for initial focus and local traversal, excluding a surrounding scrim.
        private final Node focusRoot;

        /// The focus target restored when this modal is removed.
        private @Nullable Node previousFocus;

        /// Releases component-specific resources after detachment.
        private final Runnable onHidden;

        /// Creates a host-owned handle before its node is installed.
        OverlayHandle(M3OverlayPane owner, Node node, boolean modal, Node focusRoot, Runnable onHidden) {
            this.owner = owner;
            this.node = node;
            this.modal = modal;
            this.focusRoot = focusRoot;
            this.onHidden = onHidden;
            previousFocus = owner.observedScene == null ? null : owner.observedScene.getFocusOwner();
        }

        /// Returns whether this presentation has not yet been removed, including exit animation.
        public boolean isShowing() {
            return owner != null;
        }

        /// Immediately removes this presentation; returns `false` after its first successful removal.
        /// Dialog removal through this method bypasses the cancellable close request.
        public boolean hide() {
            checkThread();
            return owner != null && owner.hideOverlay(this);
        }
    }
}
