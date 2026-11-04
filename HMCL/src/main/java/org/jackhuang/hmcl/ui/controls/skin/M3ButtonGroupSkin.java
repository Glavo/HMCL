// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.controls.skin;

import javafx.collections.ListChangeListener;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.SkinBase;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.ui.controls.M3ButtonGroupBase;
import org.jetbrains.annotations.NotNullByDefault;

/// Arranges group items in a horizontal or vertical native layout pane.
///
/// @param <T> the group's button type
@NotNullByDefault
public final class M3ButtonGroupSkin<T extends ButtonBase> extends SkinBase<M3ButtonGroupBase<T>> {
    /// Current orientation-specific layout pane.
    private Pane container = new HBox();

    /// Mirrors item changes into the scene graph.
    private final ListChangeListener<T> itemsChanged = change -> synchronizeItems();

    /// Creates a skin and observes items and geometry options.
    public M3ButtonGroupSkin(M3ButtonGroupBase<T> control) {
        super(control);
        control.getItems().addListener(itemsChanged);
        registerChangeListener(control.orientationProperty(), value -> rebuild());
        registerChangeListener(control.spacingProperty(), value -> updateSpacing());
        rebuild();
    }

    /// Replaces the orientation-specific pane after releasing its old children.
    private void rebuild() {
        container.getChildren().clear();
        container = getSkinnable().getOrientation() == Orientation.VERTICAL ? new VBox() : new HBox();
        container.getStyleClass().setAll("m3-group-container");
        if (container instanceof HBox box)
            box.setAlignment(Pos.CENTER_LEFT);
        else if (container instanceof VBox box)
            box.setAlignment(Pos.TOP_LEFT);
        getChildren().setAll(container);
        synchronizeItems();
        updateSpacing();
    }

    /// Copies the live item order without retaining removed nodes.
    private void synchronizeItems() {
        container.getChildren().setAll(getSkinnable().getItems());
        getSkinnable().requestLayout();
    }

    /// Applies a finite nonnegative gap.
    private void updateSpacing() {
        double value = getSkinnable().getSpacing();
        double gap = Double.isFinite(value) ? Math.max(0, value) : 0;
        if (container instanceof HBox box)
            box.setSpacing(gap);
        else if (container instanceof VBox box)
            box.setSpacing(gap);
    }

    /// Fits the orientation-specific pane within the control's insets.
    @Override
    protected void layoutChildren(double x, double y, double width, double height) {
        container.resizeRelocate(x, y, width, height);
    }

    /// Includes minimum child widths and group insets.
    @Override
    protected double computeMinWidth(double height, double top, double right, double bottom, double left) {
        return left + container.minWidth(height) + right;
    }

    /// Includes minimum child heights and group insets.
    @Override
    protected double computeMinHeight(double width, double top, double right, double bottom, double left) {
        return top + container.minHeight(width) + bottom;
    }

    /// Includes preferred child widths and group insets.
    @Override
    protected double computePrefWidth(double height, double top, double right, double bottom, double left) {
        return left + container.prefWidth(height) + right;
    }

    /// Includes preferred child heights and group insets.
    @Override
    protected double computePrefHeight(double width, double top, double right, double bottom, double left) {
        return top + container.prefHeight(width) + bottom;
    }

    /// Releases item listeners and nodes so another skin can immediately acquire them.
    @Override
    public void dispose() {
        getSkinnable().getItems().removeListener(itemsChanged);
        container.getChildren().clear();
        super.dispose();
    }
}
