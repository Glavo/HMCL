/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.ui.decorator;

import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.event.EventHandler;
import javafx.scene.Cursor;
import javafx.scene.control.SkinBase;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// Provides the shadow frame and native-window interaction for a [Decorator].
@NotNullByDefault
public class DecoratorSkin extends SkinBase<Decorator> {
    /// The outer pane that supplies resize insets around the window content.
    private final StackPane root;

    /// The clipped, non-shadow portion of the decorated window.
    private final DecoratorContent windowContent;

    /// The stage manipulated by window move, resize, minimize, and maximize interactions.
    private final Stage primaryStage;

    /// Retains the listener referenced weakly by the stage state properties.
    @SuppressWarnings("FieldCanBeLocal")
    private final @Nullable InvalidationListener onWindowsStatusChange;

    /// Handles title-bar double clicks on platforms with custom maximize behavior.
    private final @Nullable EventHandler<MouseEvent> onTitleBarDoubleClick;

    /// The initial horizontal screen coordinate of the active drag gesture.
    private double mouseInitX;

    /// The initial vertical screen coordinate of the active drag gesture.
    private double mouseInitY;

    /// The stage's initial horizontal screen position for the active drag gesture.
    private double stageInitX;

    /// The stage's initial vertical screen position for the active drag gesture.
    private double stageInitY;

    /// The stage's initial width for the active drag gesture.
    private double stageInitWidth;

    /// The stage's initial height for the active drag gesture.
    private double stageInitHeight;

    /// Creates a skin for the supplied decorator and installs window interaction handlers.
    ///
    /// @param control the decorator represented by this skin
    public DecoratorSkin(Decorator control) {
        super(control);

        primaryStage = control.getPrimaryStage();

        root = new StackPane();
        root.getStyleClass().add("window");

        StackPane shadowContainer = new StackPane();
        shadowContainer.getStyleClass().add("body");
        shadowContainer.setEffect(new DropShadow(
                BlurType.ONE_PASS_BOX, Color.rgb(0, 0, 0, 0.4), 10, 0.3, 0.0, 0.0));

        EventHandler<MouseEvent> onMouseReleased = this::onMouseReleased;
        EventHandler<MouseEvent> onMouseDragged = this::onMouseDragged;
        EventHandler<MouseEvent> onMouseMoved = this::onMouseMoved;

        // https://github.com/HMCL-dev/HMCL/issues/4290
        if (OperatingSystem.CURRENT_OS != OperatingSystem.MACOS) {
            onWindowsStatusChange = observable -> {
                if (primaryStage.isIconified() || primaryStage.isFullScreen() || primaryStage.isMaximized()) {
                    root.removeEventFilter(MouseEvent.MOUSE_RELEASED, onMouseReleased);
                    root.removeEventFilter(MouseEvent.MOUSE_DRAGGED, onMouseDragged);
                    root.removeEventFilter(MouseEvent.MOUSE_MOVED, onMouseMoved);
                } else {
                    root.addEventFilter(MouseEvent.MOUSE_RELEASED, onMouseReleased);
                    root.addEventFilter(MouseEvent.MOUSE_DRAGGED, onMouseDragged);
                    root.addEventFilter(MouseEvent.MOUSE_MOVED, onMouseMoved);
                }
            };
            onTitleBarDoubleClick = event -> {
                if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                    primaryStage.setMaximized(!primaryStage.isMaximized());
                    event.consume();
                }
            };
            WeakInvalidationListener weakOnWindowsStatusChange = new WeakInvalidationListener(onWindowsStatusChange);
            primaryStage.iconifiedProperty().addListener(weakOnWindowsStatusChange);
            primaryStage.maximizedProperty().addListener(weakOnWindowsStatusChange);
            primaryStage.fullScreenProperty().addListener(weakOnWindowsStatusChange);
            onWindowsStatusChange.invalidated(primaryStage.iconifiedProperty());
        } else {
            onWindowsStatusChange = null;
            onTitleBarDoubleClick = null;
            root.addEventFilter(MouseEvent.MOUSE_RELEASED, onMouseReleased);
            root.addEventFilter(MouseEvent.MOUSE_DRAGGED, onMouseDragged);
            root.addEventFilter(MouseEvent.MOUSE_MOVED, onMouseMoved);
        }

        windowContent = new DecoratorContent(control, onTitleBarDoubleClick, this::onTitleBarDragged);
        shadowContainer.getChildren().setAll(windowContent);
        root.getChildren().setAll(shadowContainer);

        getChildren().add(root);
    }

    /// Restores a maximized stage and initializes movement when dragging begins in the title bar.
    ///
    /// @param mouseEvent the title-bar drag event
    private void onTitleBarDragged(MouseEvent mouseEvent) {
        if (!getSkinnable().isDragging() && primaryStage.isMaximized()) {
            getSkinnable().setDragging(true);
            mouseInitX = mouseEvent.getScreenX();
            mouseInitY = mouseEvent.getScreenY();
            primaryStage.setMaximized(false);
            stageInitWidth = primaryStage.getWidth();
            stageInitHeight = primaryStage.getHeight();
            primaryStage.setY(stageInitY = 0);
            primaryStage.setX(stageInitX = mouseInitX - stageInitWidth / 2);
        }
    }

    /// Returns whether the pointer is within the right resize inset.
    ///
    /// @param x the pointer's horizontal coordinate in the root pane
    /// @return `true` if the pointer is within the right resize inset
    private boolean isRightEdge(double x) {
        return x < root.getWidth() && x >= root.getWidth() - root.snappedLeftInset();
    }

    /// Returns whether the pointer is within the top resize inset.
    ///
    /// @param y the pointer's vertical coordinate in the root pane
    /// @return `true` if the pointer is within the top resize inset
    private boolean isTopEdge(double y) {
        return y >= 0 && y <= root.snappedTopInset();
    }

    /// Returns whether the pointer is within the bottom resize inset.
    ///
    /// @param y the pointer's vertical coordinate in the root pane
    /// @return `true` if the pointer is within the bottom resize inset
    private boolean isBottomEdge(double y) {
        return y < root.getHeight() && y >= root.getHeight() - root.snappedLeftInset();
    }

    /// Returns whether the pointer is within the left resize inset.
    ///
    /// @param x the pointer's horizontal coordinate in the root pane
    /// @return `true` if the pointer is within the left resize inset
    private boolean isLeftEdge(double x) {
        return x >= 0 && x <= root.snappedLeftInset();
    }

    /// Applies requested stage dimensions after enforcing stage and title-bar minimums.
    ///
    /// A negative dimension preserves the current value. Width and height are always written
    /// together to avoid JDK-8344372.
    ///
    /// @param newWidth the requested width, or a negative value to preserve the current width
    /// @param newHeight the requested height, or a negative value to preserve the current height
    private void resizeStage(double newWidth, double newHeight) {
        if (newWidth < 0)
            newWidth = primaryStage.getWidth();
        if (newWidth < primaryStage.getMinWidth())
            newWidth = primaryStage.getMinWidth();
        if (newWidth < windowContent.getTitleMinWidth())
            newWidth = windowContent.getTitleMinWidth();

        if (newHeight < 0)
            newHeight = primaryStage.getHeight();
        if (newHeight < primaryStage.getMinHeight())
            newHeight = primaryStage.getMinHeight();
        if (newHeight < windowContent.getTitleMinHeight())
            newHeight = windowContent.getTitleMinHeight();

        // Width and height must be set simultaneously to avoid JDK-8344372 (https://github.com/openjdk/jfx/pull/1654)
        primaryStage.setWidth(newWidth);
        primaryStage.setHeight(newHeight);
    }

    /// Selects the resize cursor for the pointer's current position in the outer pane.
    ///
    /// @param mouseEvent the pointer movement event
    private void onMouseMoved(MouseEvent mouseEvent) {
        if (!primaryStage.isFullScreen() && primaryStage.isResizable()) {
            double x = mouseEvent.getX();
            double y = mouseEvent.getY();
            double diagonalSize = root.snappedLeftInset() + 10;
            if (isRightEdge(x)) {
                if (y < diagonalSize) {
                    root.setCursor(Cursor.NE_RESIZE);
                } else if (y > root.getHeight() - diagonalSize) {
                    root.setCursor(Cursor.SE_RESIZE);
                } else {
                    root.setCursor(Cursor.E_RESIZE);
                }
            } else if (isLeftEdge(x)) {
                if (y < diagonalSize) {
                    root.setCursor(Cursor.NW_RESIZE);
                } else if (y > root.getHeight() - diagonalSize) {
                    root.setCursor(Cursor.SW_RESIZE);
                } else {
                    root.setCursor(Cursor.W_RESIZE);
                }
            } else if (isTopEdge(y)) {
                if (x < diagonalSize) {
                    root.setCursor(Cursor.NW_RESIZE);
                } else if (x > root.getWidth() - diagonalSize) {
                    root.setCursor(Cursor.NE_RESIZE);
                } else {
                    root.setCursor(Cursor.N_RESIZE);
                }
            } else if (isBottomEdge(y)) {
                if (x < diagonalSize) {
                    root.setCursor(Cursor.SW_RESIZE);
                } else if (x > root.getWidth() - diagonalSize) {
                    root.setCursor(Cursor.SE_RESIZE);
                } else {
                    root.setCursor(Cursor.S_RESIZE);
                }
            } else {
                root.setCursor(Cursor.DEFAULT);
            }
        } else {
            root.setCursor(Cursor.DEFAULT);
        }
    }

    /// Ends the active move or resize gesture.
    ///
    /// @param mouseEvent the mouse release event
    private void onMouseReleased(MouseEvent mouseEvent) {
        getSkinnable().setDragging(false);
    }

    /// Moves or resizes the stage according to the cursor selected at drag start.
    ///
    /// @param mouseEvent the active drag event
    private void onMouseDragged(MouseEvent mouseEvent) {
        if (!getSkinnable().isDragging()) {
            getSkinnable().setDragging(true);
            mouseInitX = mouseEvent.getScreenX();
            mouseInitY = mouseEvent.getScreenY();
            stageInitX = primaryStage.getX();
            stageInitY = primaryStage.getY();
            stageInitWidth = primaryStage.getWidth();
            stageInitHeight = primaryStage.getHeight();
        }

        if (primaryStage.isFullScreen() || !mouseEvent.isPrimaryButtonDown() || mouseEvent.isStillSincePress())
            return;

        double dx = mouseEvent.getScreenX() - mouseInitX;
        double dy = mouseEvent.getScreenY() - mouseInitY;

        Cursor cursor = root.getCursor();
        if (getSkinnable().isAllowMove()) {
            if (cursor == Cursor.DEFAULT) {
                primaryStage.setX(stageInitX + dx);
                primaryStage.setY(stageInitY + dy);
                mouseEvent.consume();
            }
        }

        if (getSkinnable().isResizable()) {
            if (cursor == Cursor.E_RESIZE) {
                resizeStage(stageInitWidth + dx, -1);
                mouseEvent.consume();

            } else if (cursor == Cursor.S_RESIZE) {
                resizeStage(-1, stageInitHeight + dy);
                mouseEvent.consume();

            } else if (cursor == Cursor.W_RESIZE) {
                resizeStage(stageInitWidth - dx, -1);
                primaryStage.setX(stageInitX + stageInitWidth - primaryStage.getWidth());
                mouseEvent.consume();

            } else if (cursor == Cursor.N_RESIZE) {
                resizeStage(-1, stageInitHeight - dy);
                primaryStage.setY(stageInitY + stageInitHeight - primaryStage.getHeight());
                mouseEvent.consume();

            } else if (cursor == Cursor.SE_RESIZE) {
                resizeStage(stageInitWidth + dx, stageInitHeight + dy);
                mouseEvent.consume();

            } else if (cursor == Cursor.SW_RESIZE) {
                resizeStage(stageInitWidth - dx, stageInitHeight + dy);
                primaryStage.setX(stageInitX + stageInitWidth - primaryStage.getWidth());
                mouseEvent.consume();

            } else if (cursor == Cursor.NW_RESIZE) {
                resizeStage(stageInitWidth - dx, stageInitHeight - dy);
                primaryStage.setX(stageInitX + stageInitWidth - primaryStage.getWidth());
                primaryStage.setY(stageInitY + stageInitHeight - primaryStage.getHeight());
                mouseEvent.consume();

            } else if (cursor == Cursor.NE_RESIZE) {
                resizeStage(stageInitWidth + dx, stageInitHeight - dy);
                primaryStage.setY(stageInitY + stageInitHeight - primaryStage.getHeight());
                mouseEvent.consume();
            }
        }
    }
}
