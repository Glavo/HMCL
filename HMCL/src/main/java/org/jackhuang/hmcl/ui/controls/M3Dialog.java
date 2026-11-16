// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.controls;

import javafx.animation.FadeTransition;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jackhuang.hmcl.ui.animation.Motion;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// Presents a Material Design 3 basic dialog inside an existing [StackPane].
///
/// Configure the headline, optional 24px graphic, supporting text or custom content, and action buttons,
/// then call [#show(StackPane)] on the JavaFX application thread. The host must fill the interaction area
/// and belong to a showing window. At most one `M3Dialog` may be shown in a scene at a time.
/// The host supplies the HMCL theme; this control loads its own component stylesheet.
///
/// An unconsumed action from a button directly in [#getActions()] requests dismissal. Consuming its
/// action event or [M3DialogEvent#CLOSE_REQUEST] prevents dismissal. Escape and primary scrim clicks
/// also request dismissal unless disabled. Tab traversal stays within the dialog; closing restores
/// the previous focus owner when it is still available. Custom content may consume Escape itself.
///
/// [#close()] forcibly removes the dialog without a close request. Host detachment or window hiding
/// has the same effect. The instance may be shown again after [M3DialogEvent#HIDDEN]. All configuration
/// and presentation operations must run on the JavaFX application thread. Do not reparent the control
/// or its presentation layer while showing.
///
/// ```java
/// M3Dialog dialog = new M3Dialog();
/// dialog.setHeaderText("Delete item?");
/// dialog.setContentText("This action cannot be undone.");
/// Button cancel = new Button("Cancel");
/// Button delete = new Button("Delete");
/// dialog.getActions().addAll(cancel, delete);
/// dialog.setOnHidden(event -> {
///     if (event.getAction() == delete) {
///         deleteItem();
///     }
/// });
/// dialog.show(host);
/// ```
@NotNullByDefault
public final class M3Dialog extends VBox {

    /// Reserves one modal presentation per scene.
    private static final Object MODAL_KEY = new Object();

    /// The optional leading graphic's container.
    private final StackPane graphicPane = new StackPane();

    /// The headline label.
    private final Label header = new Label();

    /// The supporting-text label used when custom content is absent.
    private final Label text = new Label();

    /// The scrollable content region, leaving actions visible on short windows.
    private final ScrollPane scroll = new ScrollPane();

    /// The wrapping, trailing-aligned action row.
    private final FlowPane actions = new FlowPane(8, 8);

    /// The active host, or `null` while hidden.
    private @Nullable StackPane host;

    /// The modal layer, or `null` while hidden.
    private @Nullable StackPane layer;

    /// The scene whose input and focus are constrained.
    private @Nullable Scene scene;

    /// The focus owner to restore on removal.
    private @Nullable Node previousFocus;

    /// The active lifetime observation.
    private @Nullable M3OverlaySupport observation;

    /// The current entrance or exit transition.
    private @Nullable FadeTransition transition;

    /// Prevents recursive close requests and repeated actions during dismissal.
    private boolean closing;

    /// The action retained through an accepted exit transition.
    private @Nullable Button closingAction;

    /// Restricts keyboard input to the modal subtree.
    private final EventHandler<KeyEvent> keyFilter = this::filterKey;

    /// Redirects programmatic focus requests outside the modal subtree.
    private final ChangeListener<@Nullable Node> focusListener = (observable, oldValue, newValue) -> {
        if (!M3OverlaySupport.contains(this, newValue))
            requestFocus();
    };

    /// Creates an empty dialog; actions and empty content regions are initially hidden.
    public M3Dialog() {
        getStyleClass().add("m3-dialog");
        getStylesheets().add(Objects.requireNonNull(M3Dialog.class.getResource("m3-overlays.css")).toExternalForm());
        setAccessibleRole(AccessibleRole.PARENT);
        accessibleTextProperty().bind(header.textProperty());
        setFocusTraversable(true);
        setMaxSize(560, USE_PREF_SIZE);
        setMinSize(0, 0);

        header.getStyleClass().add("m3-dialog-headline");
        header.setWrapText(true);
        header.setMinWidth(0);
        header.setMaxWidth(Double.MAX_VALUE);

        text.getStyleClass().add("m3-dialog-text");
        text.setWrapText(true);
        text.setMinWidth(0);
        text.setMaxWidth(Double.MAX_VALUE);

        graphicPane.getStyleClass().add("m3-dialog-graphic");

        scroll.getStyleClass().add("m3-dialog-content");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setMinHeight(0);
        scroll.setContent(text);

        actions.getStyleClass().add("m3-dialog-actions");
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.setMinHeight(USE_PREF_SIZE);
        actions.visibleProperty().bind(javafx.beans.binding.Bindings.isNotEmpty(actions.getChildren()));
        actions.managedProperty().bind(actions.visibleProperty());

        header.visibleProperty().bind(header.textProperty().isNotEmpty());
        header.managedProperty().bind(header.visibleProperty());
        graphicPane.visibleProperty().bind(graphic.isNotNull());
        graphicPane.managedProperty().bind(graphicPane.visibleProperty());
        scroll.visibleProperty().bind(content.isNotNull().or(text.textProperty().isNotEmpty()));
        scroll.managedProperty().bind(scroll.visibleProperty());

        content.addListener((observable, oldValue, newValue) -> scroll.setContent(newValue == null ? text : newValue));
        graphic.addListener((observable, oldValue, newValue) -> {
            graphicPane.getChildren().clear();
            if (newValue != null)
                graphicPane.getChildren().add(newValue);
            header.setAlignment(newValue == null ? Pos.CENTER_LEFT : Pos.CENTER);
        });
        onShown.addListener((observable, oldValue, newValue) -> setEventHandler(M3DialogEvent.SHOWN, newValue));
        onCloseRequest.addListener((observable, oldValue, newValue) -> setEventHandler(M3DialogEvent.CLOSE_REQUEST, newValue));
        onHidden.addListener((observable, oldValue, newValue) -> setEventHandler(M3DialogEvent.HIDDEN, newValue));

        getChildren().addAll(graphicPane, header, scroll, actions);

        addEventFilter(ActionEvent.ACTION, event -> {
            if (closing)
                event.consume();
        });
        addEventFilter(MouseEvent.ANY, event -> {
            if (closing)
                event.consume();
        });
        addEventHandler(ActionEvent.ACTION, event -> {
            if (event.getTarget() instanceof Button button && actions.getChildren().contains(button))
                requestClose(button);
        });
        addEventHandler(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                if (isCloseOnEscape())
                    requestClose();
                event.consume();
            } else if (event.getCode() == KeyCode.ENTER && !(event.getTarget() instanceof TextArea)) {
                @Nullable Scene activeScene = scene;
                @Nullable Node focused = activeScene == null ? null : activeScene.getFocusOwner();
                if (focused instanceof Button button) {
                    button.fire();
                } else {
                    actions.getChildren().stream().filter(Button.class::isInstance).map(Button.class::cast)
                            .filter(button -> button.isDefaultButton() && !button.isDisabled() && button.isVisible())
                            .findFirst().ifPresent(Button::fire);
                }
                // Stop background default-button accelerators after content has handled the press.
                event.consume();
            }
        });
    }

    /// Returns the headline, or `null` when unset.
    public @Nullable String getHeaderText() {
        return header.getText();
    }

    /// Sets the headline; `null` and empty strings hide the text region.
    public void setHeaderText(@Nullable String value) {
        header.setText(value);
    }

    /// Returns the bindable headline property, initially empty.
    public StringProperty headerTextProperty() {
        return header.textProperty();
    }

    /// Returns the supporting text, or `null` when unset.
    public @Nullable String getContentText() {
        return text.getText();
    }

    /// Sets the supporting text; `null` and empty strings hide the text region.
    public void setContentText(@Nullable String value) {
        text.setText(value);
    }

    /// Returns the bindable supporting text property, initially empty.
    public StringProperty contentTextProperty() {
        return text.textProperty();
    }

    /// The custom content, or `null` to display supporting text.
    private final ObjectProperty<@Nullable Node> content = new SimpleObjectProperty<>(this, "content");

    /// Returns the custom content; `null` selects the supporting text.
    public @Nullable Node getContent() {
        return content.get();
    }

    /// Sets the custom content; `null` selects the supporting text. The node must not belong to another parent.
    public void setContent(@Nullable Node value) {
        content.set(value);
    }

    /// Returns the bindable content property, initially `null`.
    public ObjectProperty<@Nullable Node> contentProperty() {
        return content;
    }

    /// The optional leading graphic.
    private final ObjectProperty<@Nullable Node> graphic = new SimpleObjectProperty<>(this, "graphic");

    /// Returns the leading graphic; `null` hides it.
    public @Nullable Node getGraphic() {
        return graphic.get();
    }

    /// Sets the leading graphic; `null` hides it. The node must not belong to another parent.
    public void setGraphic(@Nullable Node value) {
        graphic.set(value);
    }

    /// Returns the bindable graphic property, initially `null`.
    public ObjectProperty<@Nullable Node> graphicProperty() {
        return graphic;
    }

    /// Returns the mutable list of action nodes, in reading order. Prefer text [Button] instances.
    /// Direct button actions automatically request dismissal unless their event is consumed.
    public ObservableList<Node> getActions() {
        return actions.getChildren();
    }

    /// Whether Escape requests dismissal.
    private final BooleanProperty closeOnEscape = new SimpleBooleanProperty(this, "closeOnEscape", true);

    /// Returns whether Escape requests dismissal.
    public boolean isCloseOnEscape() {
        return closeOnEscape.get();
    }

    /// Sets whether Escape requests dismissal. Animation changes apply to the next transition.
    public void setCloseOnEscape(boolean value) {
        closeOnEscape.set(value);
    }

    /// Returns the bindable property controlling whether Escape requests dismissal; initially true.
    public BooleanProperty closeOnEscapeProperty() {
        return closeOnEscape;
    }

    /// Whether primary scrim activation requests dismissal.
    private final BooleanProperty dismissOnScrimClick = new SimpleBooleanProperty(this, "dismissOnScrimClick", true);

    /// Returns whether primary scrim clicks request dismissal.
    public boolean isDismissOnScrimClick() {
        return dismissOnScrimClick.get();
    }

    /// Sets whether primary scrim clicks request dismissal. Animation changes apply to the next transition.
    public void setDismissOnScrimClick(boolean value) {
        dismissOnScrimClick.set(value);
    }

    /// Returns the bindable property controlling whether primary scrim clicks request dismissal; initially true.
    public BooleanProperty dismissOnScrimClickProperty() {
        return dismissOnScrimClick;
    }

    /// Whether transitions run; initially follows HMCL's animation preference.
    private final BooleanProperty animated = new SimpleBooleanProperty(this, "animated", AnimationUtils.isAnimationEnabled());

    /// Returns whether presentation transitions are animated.
    public boolean isAnimated() {
        return animated.get();
    }

    /// Sets whether presentation transitions are animated. Animation changes apply to the next transition.
    public void setAnimated(boolean value) {
        animated.set(value);
    }

    /// Returns the bindable property controlling whether presentation transitions are animated; initially the HMCL animation preference.
    public BooleanProperty animatedProperty() {
        return animated;
    }

    /// Whether the dialog is attached, including its exit transition.
    private final ReadOnlyBooleanWrapper showing = new ReadOnlyBooleanWrapper(this, "showing");

    /// Returns whether the dialog is attached, including entrance and exit transitions.
    public boolean isShowing() {
        return showing.get();
    }

    /// Returns the read-only presentation state, initially `false`.
    public ReadOnlyBooleanProperty showingProperty() {
        return showing.getReadOnlyProperty();
    }

    /// The optional singleton handler for [M3DialogEvent#SHOWN].
    private final ObjectProperty<@Nullable EventHandler<M3DialogEvent>> onShown = new SimpleObjectProperty<>(this, "onShown");

    /// Returns the singleton handler for [M3DialogEvent#SHOWN], or `null` when absent.
    public @Nullable EventHandler<M3DialogEvent> getOnShown() {
        return onShown.get();
    }

    /// Sets the handler for [M3DialogEvent#SHOWN], or removes it when `null`.
    public void setOnShown(@Nullable EventHandler<M3DialogEvent> handler) {
        onShown.set(handler);
    }

    /// Returns the bindable handler for [M3DialogEvent#SHOWN], initially `null`.
    public ObjectProperty<@Nullable EventHandler<M3DialogEvent>> onShownProperty() {
        return onShown;
    }

    /// The optional singleton handler for [M3DialogEvent#CLOSE_REQUEST].
    private final ObjectProperty<@Nullable EventHandler<M3DialogEvent>> onCloseRequest = new SimpleObjectProperty<>(this, "onCloseRequest");

    /// Returns the singleton handler for [M3DialogEvent#CLOSE_REQUEST], or `null` when absent.
    public @Nullable EventHandler<M3DialogEvent> getOnCloseRequest() {
        return onCloseRequest.get();
    }

    /// Sets the handler for [M3DialogEvent#CLOSE_REQUEST], or removes it when `null`.
    public void setOnCloseRequest(@Nullable EventHandler<M3DialogEvent> handler) {
        onCloseRequest.set(handler);
    }

    /// Returns the bindable handler for [M3DialogEvent#CLOSE_REQUEST], initially `null`.
    public ObjectProperty<@Nullable EventHandler<M3DialogEvent>> onCloseRequestProperty() {
        return onCloseRequest;
    }

    /// The optional singleton handler for [M3DialogEvent#HIDDEN].
    private final ObjectProperty<@Nullable EventHandler<M3DialogEvent>> onHidden = new SimpleObjectProperty<>(this, "onHidden");

    /// Returns the singleton handler for [M3DialogEvent#HIDDEN], or `null` when absent.
    public @Nullable EventHandler<M3DialogEvent> getOnHidden() {
        return onHidden.get();
    }

    /// Sets the handler for [M3DialogEvent#HIDDEN], or removes it when `null`.
    public void setOnHidden(@Nullable EventHandler<M3DialogEvent> handler) {
        onHidden.set(handler);
    }

    /// Returns the bindable handler for [M3DialogEvent#HIDDEN], initially `null`.
    public ObjectProperty<@Nullable EventHandler<M3DialogEvent>> onHiddenProperty() {
        return onHidden;
    }

    /// Presents this dialog asynchronously and starts its entrance transition.
    ///
    /// @param host the container to cover with a scrim and this dialog
    /// @throws IllegalStateException if called off the FX thread, the host window is not showing,
    ///         this control is already parented, or another Material dialog occupies the scene
    public void show(StackPane host) {
        M3OverlaySupport.checkHost(host);
        Scene targetScene = Objects.requireNonNull(host.getScene());
        if (isShowing() || getParent() != null || targetScene.getProperties().containsKey(MODAL_KEY))
            throw new IllegalStateException("The dialog or scene is already occupied");
        this.host = host;
        scene = targetScene;
        previousFocus = targetScene.getFocusOwner();
        StackPane overlay = new StackPane(this);
        overlay.getStyleClass().add("m3-dialog-overlay");
        overlay.getStylesheets().addAll(getStylesheets());
        overlay.setViewOrder(-100);
        overlay.setOnMouseClicked(event -> {
            if (event.getTarget() == overlay && event.getButton() == MouseButton.PRIMARY && isDismissOnScrimClick())
                requestClose();
            event.consume();
        });
        layer = overlay;
        targetScene.getProperties().put(MODAL_KEY, this);
        host.getChildren().add(overlay);
        observation = new M3OverlaySupport(host, overlay, this::close);
        targetScene.addEventFilter(KeyEvent.ANY, keyFilter);
        targetScene.focusOwnerProperty().addListener(focusListener);
        closing = false;
        closingAction = null;
        showing.set(true);
        // A showing-property listener may already have closed this presentation.
        if (layer != overlay)
            return;
        requestFocus();
        animate(0, 1, () -> fireEvent(new M3DialogEvent(this, M3DialogEvent.SHOWN, null)));
    }

    /// Requests a cancellable dismissal. Repeated requests while hidden or closing have no effect.
    public void requestClose() {
        requestClose(null);
    }

    /// Immediately removes this dialog and releases listeners, even if a close request was rejected.
    /// Does nothing while hidden. Fires [M3DialogEvent#HIDDEN] after cleanup and permits reuse there.
    public void close() {
        M3OverlaySupport.checkThread();
        if (host == null)
            return;
        if (transition != null) {
            transition.stop();
            transition = null;
        }
        if (observation != null) {
            observation.close();
            observation = null;
        }
        Scene oldScene = Objects.requireNonNull(scene);
        oldScene.removeEventFilter(KeyEvent.ANY, keyFilter);
        oldScene.focusOwnerProperty().removeListener(focusListener);
        oldScene.getProperties().remove(MODAL_KEY);
        StackPane oldLayer = Objects.requireNonNull(layer);
        host.getChildren().remove(oldLayer);
        oldLayer.getChildren().clear();
        @Nullable Node focus = previousFocus;
        @Nullable Button action = closingAction;
        host = null;
        layer = null;
        scene = null;
        previousFocus = null;
        closingAction = null;
        closing = false;
        showing.set(false);
        if (focus != null && focus.getScene() == oldScene && focus.isVisible() && !focus.isDisabled())
            focus.requestFocus();
        fireEvent(new M3DialogEvent(this, M3DialogEvent.HIDDEN, action));
    }

    /// Dispatches a cancellable request and retains its initiating action through the exit transition.
    private void requestClose(@Nullable Button action) {
        M3OverlaySupport.checkThread();
        if (!isShowing() || closing)
            return;
        StackPane requestedLayer = Objects.requireNonNull(layer);
        closing = true;
        M3DialogEvent event = new M3DialogEvent(this, M3DialogEvent.CLOSE_REQUEST, action);
        try {
            fireEvent(event);
        } catch (RuntimeException | Error failure) {
            closing = false;
            throw failure;
        }
        if (layer != requestedLayer)
            return;
        if (event.isConsumed()) {
            closing = false;
            return;
        }
        closingAction = action;
        animate(Objects.requireNonNull(layer).getOpacity(), 0, this::close);
    }

    /// Animates the entire presentation; disabled motion completes synchronously.
    private void animate(double from, double to, Runnable finished) {
        if (transition != null)
            transition.stop();
        StackPane overlay = Objects.requireNonNull(layer);
        overlay.setOpacity(from);
        if (!isAnimated()) {
            overlay.setOpacity(to);
            finished.run();
            return;
        }
        FadeTransition fade = new FadeTransition(to == 1 ? Motion.MEDIUM2 : Motion.SHORT3, overlay);
        transition = fade;
        fade.setFromValue(from);
        fade.setToValue(to);
        fade.setInterpolator(to == 1 ? Motion.STANDARD_DECELERATE : Motion.STANDARD_ACCELERATE);
        fade.setOnFinished(event -> {
            transition = null;
            finished.run();
        });
        fade.play();
    }

    /// Keeps Tab traversal local and prevents background default and cancel buttons from firing.
    private void filterKey(KeyEvent event) {
        if (!M3OverlaySupport.contains(this, event.getTarget() instanceof Node node ? node : null)) {
            event.consume();
            return;
        }
        if (closing) {
            event.consume();
            return;
        }
        if (event.getEventType() != KeyEvent.KEY_PRESSED)
            return;
        if (event.getCode() == KeyCode.TAB && !event.isControlDown() && !event.isAltDown() && !event.isMetaDown()) {
            List<Node> candidates = new ArrayList<>();
            collectFocusTargets(this, candidates);
            if (!candidates.isEmpty()) {
                int index = candidates.indexOf(Objects.requireNonNull(scene).getFocusOwner());
                int next = index < 0 ? (event.isShiftDown() ? candidates.size() - 1 : 0)
                        : Math.floorMod(index + (event.isShiftDown() ? -1 : 1), candidates.size());
                candidates.get(next).requestFocus();
            }
            event.consume();
        }
    }

    /// Collects enabled, visible traversal targets in scene-graph order.
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
}
