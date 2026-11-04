// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.controls.skin;

import javafx.scene.control.ButtonBase;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// Adds a CSS-sized selection indicator ahead of the inherited labeled content.
///
/// @param <C> the selection control type
@NotNullByDefault
abstract class M3SelectionControlSkinBase<C extends ButtonBase> extends M3LabeledButtonSkinBase<C> {
    /// Indicator container, absent during superclass construction.
    private @Nullable StackPane indicator;

    /// Inner check mark or radio dot, absent during superclass construction.
    private @Nullable Region mark;

    /// Selector used to position the circular feedback target.
    private final String indicatorSelector;

    /// Creates an indicator whose geometry and mark are supplied by the component stylesheet.
    M3SelectionControlSkinBase(C control, String indicatorClass, String markClass) {
        super(control);
        indicatorSelector = "." + indicatorClass;
        mark = new Region();
        mark.getStyleClass().setAll(markClass);
        indicator = new StackPane(mark);
        indicator.getStyleClass().setAll(indicatorClass);
        indicator.setManaged(false);
        indicator.setMouseTransparent(true);
        getChildren().add(indicator);
    }

    /// Restores the indicator after text or graphics change.
    @Override
    protected void updateChildren() {
        super.updateChildren();
        if (indicator != null)
            getChildren().add(indicator);
    }

    /// Returns the CSS-resolved indicator width, or zero during construction.
    private double indicatorWidth() {
        return indicator == null ? 0 : snapSizeX(indicator.prefWidth(-1));
    }

    /// Returns the CSS-resolved indicator height, or zero during construction.
    private double indicatorHeight() {
        return indicator == null ? 0 : snapSizeY(indicator.prefHeight(-1));
    }

    /// Includes the indicator before labeled content.
    @Override
    protected double computeMinWidth(double height, double top, double right, double bottom, double left) {
        return indicatorWidth() + super.computeMinWidth(height, top, right, bottom, left);
    }

    /// Includes the indicator before labeled content.
    @Override
    protected double computePrefWidth(double height, double top, double right, double bottom, double left) {
        return indicatorWidth() + super.computePrefWidth(height, top, right, bottom, left);
    }

    /// Reserves enough height for either the indicator or content.
    @Override
    protected double computeMinHeight(double width, double top, double right, double bottom, double left) {
        return Math.max(top + indicatorHeight() + bottom,
                super.computeMinHeight(Math.max(0, width - indicatorWidth()), top, right, bottom, left));
    }

    /// Reserves enough height for either the indicator or content.
    @Override
    protected double computePrefHeight(double width, double top, double right, double bottom, double left) {
        return Math.max(top + indicatorHeight() + bottom,
                super.computePrefHeight(Math.max(0, width - indicatorWidth()), top, right, bottom, left));
    }

    /// Positions the indicator in logical order and applies the current selection paints.
    @Override
    protected void layoutChildren(double x, double y, double width, double height) {
        double iw = indicatorWidth();
        double ih = indicatorHeight();
        super.layoutChildren(x + iw, y, Math.max(0, width - iw), height);
        if (indicator != null && mark != null) {
            indicator.resizeRelocate(x, y + (height - ih) / 2, iw, ih);
            updatePaints(indicator, mark);
        }
        layoutStateLayer(indicatorSelector);
    }

    /// Applies control-specific selection paints after CSS and layout.
    protected abstract void updatePaints(Region indicator, Region mark);
}
