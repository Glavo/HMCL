// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.controls.skin;

import javafx.geometry.Insets;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// Applies selection paint properties while retaining CSS-defined indicator geometry.
@NotNullByDefault
final class M3SelectionPaints {
    /// Prevents instantiation.
    private M3SelectionPaints() {
    }

    /// Replaces the indicator's fill without changing its CSS corner radii or insets.
    static void fill(Region region, @Nullable Paint paint) {
        @Nullable Background background = region.getBackground();
        @Nullable BackgroundFill fill = background == null || background.getFills().isEmpty()
                ? null : background.getFills().get(0);
        region.setBackground(new Background(new BackgroundFill(paint == null ? Color.TRANSPARENT : paint,
                fill == null ? CornerRadii.EMPTY : fill.getRadii(), fill == null ? Insets.EMPTY : fill.getInsets())));
    }

    /// Replaces the outline paint without changing CSS widths, radii, or insets.
    static void outline(Region region, @Nullable Paint paint) {
        @Nullable Border border = region.getBorder();
        @Nullable BorderStroke stroke = border == null || border.getStrokes().isEmpty() ? null : border.getStrokes().get(0);
        region.setBorder(new Border(new BorderStroke(paint == null ? Color.TRANSPARENT : paint, BorderStrokeStyle.SOLID,
                stroke == null ? CornerRadii.EMPTY : stroke.getRadii(),
                stroke == null ? BorderWidths.DEFAULT : stroke.getWidths(), stroke == null ? Insets.EMPTY : stroke.getInsets())));
    }
}
