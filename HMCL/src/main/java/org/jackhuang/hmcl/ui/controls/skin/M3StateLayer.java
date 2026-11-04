// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.controls.skin;

import javafx.animation.Transition;
import javafx.beans.InvalidationListener;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.MenuButton;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.animation.Motion;
import org.jackhuang.hmcl.ui.controls.M3Control;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// Fades a stationary overlay without replacing the native skin's input behavior.
@NotNullByDefault
final class M3StateLayer extends Region {
    /// Control whose interaction state drives this layer.
    private final ButtonBase control;

    /// Appearance and animation options supplied by the control.
    private final M3Control options;

    /// Keyboard-focus indication, with a fallback for older JavaFX runtimes.
    private final ReadOnlyBooleanProperty focusVisible;

    /// Updates feedback when input, paint, or animation policy changes.
    private final InvalidationListener invalidation = observable -> update();

    /// Currently displayed paint.
    private Paint paint = Color.TRANSPARENT;

    /// Last requested paint.
    private @Nullable Paint targetPaint;

    /// Last requested opacity.
    private double targetOpacity = -1;

    /// Optional running transition.
    private @Nullable Transition transition;

    /// Corner geometry of the stationary overlay.
    private CornerRadii radii = CornerRadii.EMPTY;

    /// Whether the layer has been disposed.
    private boolean disposed;

    /// Creates a mouse-transparent overlay and observes the control until disposal.
    M3StateLayer(ButtonBase control) {
        this.control = control;
        options = (M3Control) control;
        @Nullable ReadOnlyBooleanProperty visible = FXUtils.focusVisibleProperty(control);
        focusVisible = visible == null ? control.focusedProperty() : visible;
        getStyleClass().setAll("m3-state-layer");
        setManaged(false);
        setMouseTransparent(true);
        setOpacity(0);
        control.hoverProperty().addListener(invalidation);
        control.armedProperty().addListener(invalidation);
        control.pressedProperty().addListener(invalidation);
        control.disabledProperty().addListener(invalidation);
        control.sceneProperty().addListener(invalidation);
        control.textFillProperty().addListener(invalidation);
        focusVisible.addListener(invalidation);
        options.stateLayerFillProperty().addListener(invalidation);
        options.disableAnimationProperty().addListener(invalidation);
        if (control instanceof MenuButton menu)
            menu.showingProperty().addListener(invalidation);
        update();
    }

    /// Fits the full button or a circular selection-indicator target after native layout.
    void layoutFeedback(@Nullable String indicatorSelector) {
        @Nullable Node indicator = indicatorSelector == null ? null : control.lookup(indicatorSelector);
        if (indicator != null) {
            Bounds bounds = indicator.getBoundsInParent();
            resizeRelocate(bounds.getCenterX() - 20, bounds.getCenterY() - 20, 40, 40);
            radii = new CornerRadii(20);
        } else {
            @Nullable Background background = control.getBackground();
            @Nullable BackgroundFill fill = background == null || background.getFills().isEmpty()
                    ? null : background.getFills().get(0);
            Insets insets = fill == null ? Insets.EMPTY : fill.getInsets();
            resizeRelocate(insets.getLeft(), insets.getTop(),
                    Math.max(0, control.getWidth() - insets.getLeft() - insets.getRight()),
                    Math.max(0, control.getHeight() - insets.getTop() - insets.getBottom()));
            radii = fill == null ? CornerRadii.EMPTY : fill.getRadii();
        }
        render();
    }

    /// Retargets from the visible state; detaching or disabling settles feedback immediately.
    private void update() {
        if (disposed)
            return;
        @Nullable Paint requested = options.getStateLayerFill();
        if (requested == null)
            requested = control.getTextFill();
        Paint nextPaint = requested == null ? Color.TRANSPARENT : requested;
        double nextOpacity = control.isDisabled() || control.getScene() == null ? 0
                : control.isArmed() || focusVisible.get() || control.isPressed() && control.isHover()
                || control instanceof MenuButton menu && menu.isShowing() ? 0.12 : control.isHover() ? 0.08 : 0;
        boolean immediate = options.isDisableAnimation() || control.isDisabled() || control.getScene() == null;
        if (!immediate && nextPaint.equals(targetPaint) && nextOpacity == targetOpacity)
            return;
        if (transition != null)
            transition.stop();
        targetPaint = nextPaint;
        targetOpacity = nextOpacity;
        if (immediate) {
            paint = nextPaint;
            setOpacity(nextOpacity);
            render();
            return;
        }
        Paint startPaint = paint;
        double startOpacity = getOpacity();
        transition = new Transition() {
            {
                setCycleDuration(Motion.SHORT3);
                setInterpolator(Motion.STANDARD);
            }

            /// Interpolates solid paints and the opacity of the entire stationary layer.
            @Override
            protected void interpolate(double fraction) {
                paint = startPaint instanceof Color start && nextPaint instanceof Color end
                        ? start.interpolate(end, fraction) : nextPaint;
                setOpacity(startOpacity + (nextOpacity - startOpacity) * fraction);
                render();
            }
        };
        transition.play();
    }

    /// Applies the visible paint while retaining the current CSS-derived corner geometry.
    private void render() {
        setBackground(new Background(new BackgroundFill(paint, radii, Insets.EMPTY)));
    }

    /// Stops transitions and releases every control listener.
    void dispose() {
        disposed = true;
        if (transition != null)
            transition.stop();
        control.hoverProperty().removeListener(invalidation);
        control.armedProperty().removeListener(invalidation);
        control.pressedProperty().removeListener(invalidation);
        control.disabledProperty().removeListener(invalidation);
        control.sceneProperty().removeListener(invalidation);
        control.textFillProperty().removeListener(invalidation);
        focusVisible.removeListener(invalidation);
        options.stateLayerFillProperty().removeListener(invalidation);
        options.disableAnimationProperty().removeListener(invalidation);
        if (control instanceof MenuButton menu)
            menu.showingProperty().removeListener(invalidation);
    }
}
