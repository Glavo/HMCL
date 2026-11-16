// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.controls;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/// Defines the content and dismissal events of a Material Design 3 basic dialog.
///
/// Configure the headline, optional 24px graphic, supporting text or custom content, and action buttons,
/// then present it with [M3OverlayPane#showDialog(M3Dialog)] on the JavaFX application thread.
/// The overlay pane owns stacking, focus containment and host lifetime observation. Nested dialogs
/// share that host; only its uppermost modal layer receives input. The host supplies the HMCL theme.
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
/// overlayPane.showDialog(dialog);
/// ```
@NotNullByDefault
public final class M3Dialog extends VBox {

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

    /// The current display lifetime, or `null` while hidden.
    private @Nullable M3DialogPresentation presentation;

    /// Creates an empty dialog; actions and empty content regions are initially hidden.
    public M3Dialog() {
        getStyleClass().add("m3-dialog");
        getStylesheets().add(Objects.requireNonNull(M3Dialog.class.getResource("m3-overlays.css")).toExternalForm());
        setAccessibleRole(AccessibleRole.PARENT);
        accessibleTextProperty().bind(header.textProperty());
        setFocusTraversable(true);
        setMaxSize(560, USE_PREF_SIZE);
        setMinSize(0, 0);

        initializeContent();
        bindContent();
        onShown.addListener((observable, oldValue, newValue) -> setEventHandler(M3DialogEvent.SHOWN, newValue));
        onCloseRequest.addListener((observable, oldValue, newValue) -> setEventHandler(M3DialogEvent.CLOSE_REQUEST, newValue));
        onHidden.addListener((observable, oldValue, newValue) -> setEventHandler(M3DialogEvent.HIDDEN, newValue));
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

    /// Returns the mutable list of action nodes, in reading order. Prefer text [javafx.scene.control.Button] instances.
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

    /// Sets whether Escape requests dismissal.
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

    /// Sets whether primary scrim clicks request dismissal.
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

    /// Requests a cancellable dismissal. Repeated requests while hidden or closing have no effect.
    public void requestClose() {
        M3OverlayPane.checkThread();
        if (presentation != null)
            presentation.requestClose(null);
    }

    /// Immediately removes this dialog and releases listeners, even if a close request was rejected.
    /// Does nothing while hidden. Fires [M3DialogEvent#HIDDEN] after cleanup and permits reuse there.
    public void close() {
        M3OverlayPane.checkThread();
        if (presentation != null)
            presentation.hide();
    }

    /// Configures the content regions and installs them in reading order.
    private void initializeContent() {
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
        getChildren().addAll(graphicPane, header, scroll, actions);
    }

    /// Keeps optional content regions synchronized with their public properties.
    private void bindContent() {
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
    }

    /// Updates the host-owned presentation before notifying observers of its attachment state.
    void setPresentation(@Nullable M3DialogPresentation value) {
        presentation = value;
        showing.set(value != null);
    }
}
