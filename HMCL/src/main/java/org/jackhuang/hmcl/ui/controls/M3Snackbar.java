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
import javafx.geometry.Orientation;
import javafx.scene.AccessibleAttribute;
import javafx.scene.AccessibleRole;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.util.Duration;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Defines a non-modal Material Design 3 message presented by an [M3OverlayPane].
///
/// [M3OverlayPane#showSnackbar(M3Snackbar)] queues this instance after earlier messages. Only one snackbar per
/// host is visible. Plain messages disappear after [#getDuration()] (four seconds by default), starting
/// after entrance. Hovering pauses the remaining time. Messages with an action or close button remain
/// until dismissed, so keyboard and assistive-technology users can activate them without a deadline.
/// [Duration#INDEFINITE] also disables automatic dismissal. Changing duration or affordances restarts
/// the timeout for the current message. Modal overlays pause the remaining display time.
///
/// Activating the text action dismisses the message before running its callback; the close button
/// only dismisses it. [#dismiss()] removes either a current or queued message. Host detachment and window
/// hiding discard the entire queue and stop its timers. Dismissed instances can be reused. Showing an
/// already queued instance is an error, even in the same host.
///
/// The host supplies HMCL theme colors. This control loads its own stylesheet, does not steal focus,
/// and leaves the area outside its surface available to pointer input. Use brief supporting text and
/// one optional action; long action labels move to a separate row. All configuration and presentation
/// operations must run on the JavaFX application thread.
///
/// ```java
/// M3Snackbar snackbar = new M3Snackbar("Item deleted");
/// snackbar.setActionText("Undo");
/// snackbar.setAction(this::restoreItem);
/// overlayPane.showSnackbar(snackbar);
/// ```
@NotNullByDefault
public final class M3Snackbar extends Region {

    /// The wrapping supporting-text label.
    private final Label label = new Label();

    /// The optional text action.
    private final Button actionButton = new Button();

    /// The optional close affordance.
    private final Button closeButton = new Button("\u00d7");

    /// The overlay pane holding this message, or `null` when neither pending nor shown.
    private @Nullable M3OverlayPane host;

    /// Creates a message with required supporting text and no action or close button.
    ///
    /// @throws NullPointerException if `text` is `null`
    /// @throws IllegalArgumentException if `text` is blank
    public M3Snackbar(String text) {
        getStyleClass().add("m3-snackbar");
        getStylesheets().add(Objects.requireNonNull(M3Snackbar.class.getResource("m3-overlays.css")).toExternalForm());
        setAccessibleRole(AccessibleRole.PARENT);
        accessibleTextProperty().bind(label.textProperty());
        setMaxSize(560, USE_PREF_SIZE);
        setMinWidth(0);

        label.getStyleClass().add("m3-snackbar-text");
        label.setWrapText(true);
        label.setMinWidth(0);

        actionButton.getStyleClass().add("m3-snackbar-action");
        actionButton.visibleProperty().bind(javafx.beans.binding.Bindings.createBooleanBinding(() -> {
            @Nullable String value = actionButton.getText();
            return value != null && !value.isBlank();
        }, actionButton.textProperty()));
        actionButton.managedProperty().bind(actionButton.visibleProperty());

        closeButton.getStyleClass().add("m3-snackbar-close");
        closeButton.setAccessibleText(i18n("button.close"));
        closeButton.visibleProperty().bind(closeButtonVisible);
        closeButton.managedProperty().bind(closeButton.visibleProperty());

        actionButton.setOnAction(event -> {
            @Nullable Runnable callback = getAction();
            dismiss();
            if (callback != null)
                callback.run();
        });
        closeButton.setOnAction(event -> dismiss());

        getChildren().addAll(label, actionButton, closeButton);

        actionButton.textProperty().addListener(observable -> restartTimeout());
        closeButtonVisible.addListener(observable -> restartTimeout());
        duration.addListener(observable -> {
            requireDuration(duration.get());
            restartTimeout();
        });
        hoverProperty().addListener(observable -> {
            if (host != null)
                host.updateSnackbarTimer();
        });
        label.textProperty().addListener(observable -> {
            requireText(label.getText());
            notifyAccessibleAttributeChanged(AccessibleAttribute.TEXT);
        });
        setText(text);
    }

    /// Returns the non-blank supporting text.
    public String getText() {
        return Objects.requireNonNull(label.getText());
    }

    /// Sets supporting text; rejects null or blank values before changing the message.
    public void setText(String value) {
        label.setText(requireText(value));
    }

    /// Returns the bindable text property. Callers and bindings must supply non-blank, non-null text.
    public StringProperty textProperty() {
        return label.textProperty();
    }

    /// Returns the optional action label; null or blank text hides the action.
    public @Nullable String getActionText() {
        return actionButton.getText();
    }

    /// Sets the action label; null or blank text hides the action and enables the plain-message timeout.
    public void setActionText(@Nullable String value) {
        actionButton.setText(value);
    }

    /// Returns the bindable action label, initially empty. Prefer a short, non-blank label.
    public StringProperty actionTextProperty() {
        return actionButton.textProperty();
    }

    /// The callback executed after action dismissal.
    private final ObjectProperty<@Nullable Runnable> action = new SimpleObjectProperty<>(this, "action");

    /// Returns the action callback, or null when activation only dismisses the snackbar.
    public @Nullable Runnable getAction() {
        return action.get();
    }

    /// Sets the callback invoked after action dismissal; null performs no callback.
    public void setAction(@Nullable Runnable value) {
        action.set(value);
    }

    /// Returns the bindable action callback, initially null. It does not control button visibility.
    public ObjectProperty<@Nullable Runnable> actionProperty() {
        return action;
    }

    /// Whether the close affordance is shown.
    private final BooleanProperty closeButtonVisible = new SimpleBooleanProperty(this, "closeButtonVisible");

    /// Returns whether the close affordance is visible.
    public boolean isCloseButtonVisible() {
        return closeButtonVisible.get();
    }

    /// Sets whether the close affordance is visible.
    public void setCloseButtonVisible(boolean value) {
        closeButtonVisible.set(value);
    }

    /// Returns the bindable property controlling whether the close affordance is visible; initially false.
    public BooleanProperty closeButtonVisibleProperty() {
        return closeButtonVisible;
    }

    /// The timeout for plain messages; must remain positive or indefinite.
    private final ObjectProperty<Duration> duration = new SimpleObjectProperty<>(this, "duration", Duration.seconds(4));

    /// Returns the display duration for plain messages, initially four seconds.
    public Duration getDuration() {
        return duration.get();
    }

    /// Sets a positive display duration or Duration.INDEFINITE; rejects null, zero, negative and unknown values.
    public void setDuration(Duration value) {
        duration.set(requireDuration(value));
    }

    /// Returns the bindable duration. Callers and bindings must supply a positive or indefinite value.
    public ObjectProperty<Duration> durationProperty() {
        return duration;
    }

    /// Whether entrance motion is enabled; initially follows HMCL's preference.
    private final BooleanProperty animated = new SimpleBooleanProperty(this, "animated", AnimationUtils.isAnimationEnabled());

    /// Returns whether entrance motion is enabled.
    public boolean isAnimated() {
        return animated.get();
    }

    /// Sets whether entrance motion is enabled.
    public void setAnimated(boolean value) {
        animated.set(value);
    }

    /// Returns the bindable property controlling whether entrance motion is enabled; initially the HMCL animation preference.
    public BooleanProperty animatedProperty() {
        return animated;
    }

    /// Whether this message is currently attached, including entrance motion.
    private final ReadOnlyBooleanWrapper showing = new ReadOnlyBooleanWrapper(this, "showing");

    /// Returns whether this message is currently attached, including entrance motion.
    public boolean isShowing() {
        return showing.get();
    }

    /// Returns the read-only current-message state, initially `false`.
    public ReadOnlyBooleanProperty showingProperty() {
        return showing.getReadOnlyProperty();
    }

    /// Immediately removes this current or pending message. Repeated calls have no effect.
    /// The next queued message starts its entrance after the current message has been removed.
    public void dismiss() {
        M3OverlayPane.checkThread();
        if (host != null)
            host.dismissSnackbar(this);
    }

    /// Returns whether this message is pending or currently shown.
    public boolean isQueued() {
        return host != null;
    }

    /// Makes parent layouts calculate the wrapped height from the allocated width.
    @Override
    public Orientation getContentBias() {
        return Orientation.HORIZONTAL;
    }

    /// Returns a preferred width between 288px and 560px, including padding.
    @Override
    protected double computePrefWidth(double height) {
        return Math.min(560, Math.max(288, snappedLeftInset() + snappedRightInset() + label.prefWidth(-1)
                + closeWidth() + (actionButton.isVisible() ? actionButton.prefWidth(-1) + 16 : 0)));
    }

    /// Allows the host to shrink the surface below its desktop preferred width.
    @Override
    protected double computeMinWidth(double height) {
        return 0;
    }

    /// Returns the wrapped content height, retaining 48px and 68px single- and multi-line minima.
    @Override
    protected double computePrefHeight(double width) {
        double inner = Math.max(1, (width < 0 ? prefWidth(-1) : width) - snappedLeftInset() - snappedRightInset());
        double textHeight = label.prefHeight(textWidth(inner));
        double rowHeight = Math.max(textHeight, Math.max(closeButton.isVisible() ? closeButton.prefHeight(-1) : 0,
                actionButton.isVisible() && !stackAction(inner) ? actionButton.prefHeight(-1) : 0));
        double height = snappedTopInset() + rowHeight + snappedBottomInset();
        if (stackAction(inner))
            height += 8 + actionButton.prefHeight(-1);
        return Math.max(textHeight > label.prefHeight(-1) + 1 ? 68 : 48, height);
    }

    /// Keeps the complete message and its actions visible when the host is short.
    @Override
    protected double computeMinHeight(double width) {
        return computePrefHeight(width);
    }

    /// Lays out inline actions or a trailing action row for long labels, respecting RTL orientation.
    @Override
    protected void layoutChildren() {
        double x = snappedLeftInset();
        double y = snappedTopInset();
        double width = Math.max(1, getWidth() - x - snappedRightInset());
        boolean stacked = stackAction(width);
        double actionHeight = actionButton.isVisible() ? actionButton.prefHeight(-1) : 0;
        double rowHeight = getHeight() - y - snappedBottomInset() - (stacked ? actionHeight + 8 : 0);
        label.resizeRelocate(x, y, textWidth(width), rowHeight);
        if (closeButton.isVisible()) {
            double w = closeButton.prefWidth(-1);
            double h = closeButton.prefHeight(-1);
            closeButton.resizeRelocate(x + width - w, y + (rowHeight - h) / 2, w, h);
        }
        if (actionButton.isVisible()) {
            double w = Math.min(width, actionButton.prefWidth(-1));
            actionButton.resizeRelocate(x + width - w - (stacked ? 0 : closeWidth()),
                    stacked ? y + rowHeight + 8 : y + (rowHeight - actionHeight) / 2, w, actionHeight);
        }
    }

    /// Restarts the active message's timeout after a configuration change.
    private void restartTimeout() {
        requestLayout();
        if (host != null)
            host.snackbarChanged(this);
    }

    /// Returns whether the text action requires its own row at the given inner width.
    private boolean stackAction(double width) {
        return actionButton.isVisible() && actionButton.prefWidth(-1) > width * 0.4;
    }

    /// Returns the horizontal space reserved for a visible close button.
    private double closeWidth() {
        return closeButton.isVisible() ? closeButton.prefWidth(-1) + 8 : 0;
    }

    /// Returns the supporting-text width after reserving space for inline affordances.
    private double textWidth(double width) {
        return Math.max(1, width - closeWidth()
                - (actionButton.isVisible() && !stackAction(width) ? actionButton.prefWidth(-1) + 16 : 0));
    }

    /// Validates required supporting text before assigning it.
    private static String requireText(@Nullable String value) {
        Objects.requireNonNull(value, "text");
        if (value.isBlank())
            throw new IllegalArgumentException("Snackbar text must not be blank");
        return value;
    }

    /// Validates a positive timeout or the indefinite sentinel.
    private static Duration requireDuration(@Nullable Duration value) {
        Objects.requireNonNull(value, "duration");
        if (value.isUnknown() || value.lessThanOrEqualTo(Duration.ZERO))
            throw new IllegalArgumentException("Snackbar duration must be positive or indefinite");
        return value;
    }

    /// Validates message state before a host claims this instance.
    void validate() {
        requireText(getText());
        requireDuration(getDuration());
    }

    /// Assigns or releases queue ownership before changing the observable showing state.
    void setHost(@Nullable M3OverlayPane value) {
        host = value;
    }

    /// Reports whether the host has attached this message's surface.
    void setShowing(boolean value) {
        showing.set(value);
    }

    /// Returns whether a visible action requires persistent presentation.
    boolean hasAction() {
        return actionButton.isVisible();
    }
}
