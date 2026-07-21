/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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

import com.jfoenix.controls.JFXButton;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.collections.ListChangeListener;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.glavo.monetfx.ColorRole;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.theme.Themes;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.Motion;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.wizard.Navigation;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// Represents the clipped portion of a [Decorator] window inside its drop shadow.
///
/// This pane owns the themed background, page and floating-content layers, title bar, navigation
/// controls, and window buttons. It also manages moving, maximizing, and resizing the containing
/// stage; resize gestures are observed on the outer interaction pane supplied at construction.
@NotNullByDefault
final class DecoratorWindow extends StackPane {
    /// The decorator whose properties and actions drive this content.
    private final Decorator decorator;

    /// The outer pane that receives move and resize pointer events, including the shadow insets.
    private final StackPane interactionPane;

    /// The stage manipulated by window interactions.
    private final Stage primaryStage;

    /// The title container used to determine the minimum dimensions during window resizing.
    private final StackPane titleContainer;

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

    /// Creates and binds the non-shadow content of a decorator window.
    ///
    /// The constructor registers this pane as the snackbar container and assigns the internal
    /// wrapper used as the decorator's dialog container.
    ///
    /// @param decorator the decorator represented by this window
    /// @param interactionPane the outer pane that receives move and resize pointer events
    DecoratorWindow(Decorator decorator, StackPane interactionPane) {
        this.decorator = decorator;
        this.interactionPane = interactionPane;
        this.primaryStage = decorator.getPrimaryStage();

        EventHandler<MouseEvent> onMouseReleased = this::onMouseReleased;
        EventHandler<MouseEvent> onMouseDragged = this::onMouseDragged;
        EventHandler<MouseEvent> onMouseMoved = this::onMouseMoved;

        // https://github.com/HMCL-dev/HMCL/issues/4290
        if (OperatingSystem.CURRENT_OS != OperatingSystem.MACOS) {
            onWindowsStatusChange = observable -> {
                if (primaryStage.isIconified() || primaryStage.isFullScreen() || primaryStage.isMaximized()) {
                    interactionPane.removeEventFilter(MouseEvent.MOUSE_RELEASED, onMouseReleased);
                    interactionPane.removeEventFilter(MouseEvent.MOUSE_DRAGGED, onMouseDragged);
                    interactionPane.removeEventFilter(MouseEvent.MOUSE_MOVED, onMouseMoved);
                } else {
                    interactionPane.addEventFilter(MouseEvent.MOUSE_RELEASED, onMouseReleased);
                    interactionPane.addEventFilter(MouseEvent.MOUSE_DRAGGED, onMouseDragged);
                    interactionPane.addEventFilter(MouseEvent.MOUSE_MOVED, onMouseMoved);
                }
            };
            onTitleBarDoubleClick = event -> {
                if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                    primaryStage.setMaximized(!primaryStage.isMaximized());
                    event.consume();
                }
            };
            WeakInvalidationListener weakOnWindowsStatusChange =
                    new WeakInvalidationListener(onWindowsStatusChange);
            primaryStage.iconifiedProperty().addListener(weakOnWindowsStatusChange);
            primaryStage.maximizedProperty().addListener(weakOnWindowsStatusChange);
            primaryStage.fullScreenProperty().addListener(weakOnWindowsStatusChange);
            onWindowsStatusChange.invalidated(primaryStage.iconifiedProperty());
        } else {
            onWindowsStatusChange = null;
            onTitleBarDoubleClick = null;
            interactionPane.addEventFilter(MouseEvent.MOUSE_RELEASED, onMouseReleased);
            interactionPane.addEventFilter(MouseEvent.MOUSE_DRAGGED, onMouseDragged);
            interactionPane.addEventFilter(MouseEvent.MOUSE_MOVED, onMouseMoved);
        }

        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(widthProperty());
        clip.heightProperty().bind(heightProperty());
        clip.setArcWidth(8);
        clip.setArcHeight(8);
        setClip(clip);

        decorator.getSnackbar().registerSnackbarContainer(this);

        StackPane wrapper = new StackPane();
        wrapper.backgroundProperty().bind(Bindings.createObjectBinding(
                () -> Themes.windowTransparentProperty().get()
                        ? null
                        : new Background(new BackgroundFill(
                                Themes.getColorScheme().getColor(ColorRole.SURFACE_CONTAINER),
                                CornerRadii.EMPTY,
                                Insets.EMPTY)),
                Themes.windowTransparentProperty(),
                Themes.colorSchemeProperty()));

        Region backgroundNode = new Region();
        backgroundNode.setMouseTransparent(true);
        backgroundNode.backgroundProperty().bind(Bindings.createObjectBinding(
                () -> decorator.getContentBackground() == null
                        ? null
                        : decorator.getContentBackground().background(),
                decorator.contentBackgroundProperty()));
        backgroundNode.opacityProperty().bind(Bindings.createDoubleBinding(
                () -> decorator.getContentBackground() == null
                        ? 1.0
                        : decorator.getContentBackground().opacity(),
                decorator.contentBackgroundProperty()));
        StackPane.setAlignment(backgroundNode, Pos.BOTTOM_CENTER);

        BorderPane frame = new BorderPane();
        frame.getStyleClass().add("jfx-decorator");
        wrapper.getChildren().setAll(backgroundNode, frame);
        decorator.setDrawerWrapper(wrapper);
        getChildren().add(wrapper);

        // The center stacks regular page content below transient welcome and hint content.
        StackPane container = new StackPane();
        FXUtils.setOverflowHidden(container);

        StackPane contentPlaceHolder = new StackPane();
        contentPlaceHolder.getStyleClass().add("jfx-decorator-content-container");
        Bindings.bindContent(contentPlaceHolder.getChildren(), decorator.contentProperty());
        container.getChildren().add(contentPlaceHolder);

        StackPane floatLayer = new StackPane();
        Bindings.bindContent(floatLayer.getChildren(), decorator.containerProperty());
        ListChangeListener<Node> containerListener = change -> updateFloatLayer(floatLayer);
        decorator.containerProperty().addListener(containerListener);
        updateFloatLayer(floatLayer);
        container.getChildren().add(floatLayer);

        frame.setCenter(container);

        titleContainer = new StackPane();
        titleContainer.setPickOnBounds(false);
        titleContainer.getStyleClass().add("jfx-tool-bar");
        backgroundNode.maxHeightProperty().bind(Bindings.createDoubleBinding(
                () -> Math.max(0.0, wrapper.getHeight()
                        - (decorator.isTitleTransparent() ? 0.0 : titleContainer.getHeight())),
                wrapper.heightProperty(),
                decorator.titleTransparentProperty(),
                titleContainer.heightProperty()));

        // A future implementation may select the title foreground from the image below it.
        FXUtils.onChangeAndOperate(decorator.titleTransparentProperty(), titleTransparent -> {
            if (titleTransparent) {
                titleContainer.getStyleClass().remove("background");
                titleContainer.getStyleClass().add("gray-background");
            } else {
                titleContainer.getStyleClass().add("background");
                titleContainer.getStyleClass().remove("gray-background");
            }
        });

        decorator.capableDraggingWindow(titleContainer);

        BorderPane titleBar = new BorderPane();
        titleContainer.getChildren().add(titleBar);

        Rectangle buttonsContainerPlaceHolder = new Rectangle();
        TransitionPane navBarPane = new TransitionPane();
        navBarPane.setId("decoratorTitleTransitionPane");
        FXUtils.onChangeAndOperate(
                decorator.stateProperty(), (@Nullable DecoratorPage.State state) -> {
                    if (state == null) {
                        return;
                    }

                    Node node = createNavBar(
                            state.leftPaneWidth(),
                            state.backable(),
                            decorator.canCloseProperty().get(),
                            decorator.showCloseAsHomeProperty().get(),
                            state.refreshable(),
                            state.title(),
                            state.titleNode());
                    if (state.animate()) {
                        TransitionPane.AnimationProducer animation =
                                switch (decorator.getNavigationDirection()) {
                                    case NEXT -> NavBarAnimations.NEXT;
                                    case PREVIOUS -> NavBarAnimations.PREVIOUS;
                                    default -> ContainerAnimations.FADE;
                                };
                        decorator.setNavigationDirection(Navigation.NavigationDirection.START);
                        navBarPane.setContent(node, animation, Motion.SHORT4);
                    } else {
                        navBarPane.getChildren().setAll(node);
                    }
                });
        titleBar.setCenter(navBarPane);
        titleBar.setRight(buttonsContainerPlaceHolder);
        frame.setTop(titleContainer);

        HBox buttonsContainer = new HBox();
        buttonsContainer.setAlignment(Pos.TOP_RIGHT);
        buttonsContainer.setMaxHeight(40);

        JFXButton helpButton = new JFXButton();
        helpButton.setFocusTraversable(false);
        helpButton.setGraphic(SVG.HELP.createIcon(Themes.titleFillProperty()));
        helpButton.getStyleClass().add("jfx-decorator-button");
        helpButton.setOnAction(event -> FXUtils.openLink(Metadata.CONTACT_URL));

        JFXButton minimizeButton = new JFXButton();
        minimizeButton.setFocusTraversable(false);
        minimizeButton.setGraphic(SVG.MINIMIZE_CENTER.createIcon(Themes.titleFillProperty()));
        minimizeButton.getStyleClass().add("jfx-decorator-button");
        minimizeButton.setOnAction(event -> decorator.minimize());

        JFXButton closeButton = new JFXButton();
        closeButton.setFocusTraversable(false);
        closeButton.setGraphic(SVG.CLOSE.createIcon(Themes.titleFillProperty()));
        closeButton.getStyleClass().add("jfx-decorator-button");
        closeButton.setOnAction(event -> decorator.close());

        buttonsContainer.getChildren().setAll(helpButton, minimizeButton, closeButton);

        AnchorPane buttonLayer = new AnchorPane();
        buttonLayer.setPickOnBounds(false);
        buttonLayer.getChildren().add(buttonsContainer);
        AnchorPane.setTopAnchor(buttonsContainer, 0.0);
        AnchorPane.setRightAnchor(buttonsContainer, 0.0);
        buttonsContainerPlaceHolder.widthProperty().bind(buttonsContainer.widthProperty());
        getChildren().add(buttonLayer);
    }

    /// Restores a maximized stage and initializes movement when dragging begins in the title bar.
    ///
    /// @param mouseEvent the title-bar drag event
    private void onTitleBarDragged(MouseEvent mouseEvent) {
        if (!decorator.isDragging() && primaryStage.isMaximized()) {
            decorator.setDragging(true);
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
    /// @param x the pointer's horizontal coordinate in the interaction pane
    /// @return `true` if the pointer is within the right resize inset
    private boolean isRightEdge(double x) {
        return x < interactionPane.getWidth()
                && x >= interactionPane.getWidth() - interactionPane.snappedLeftInset();
    }

    /// Returns whether the pointer is within the top resize inset.
    ///
    /// @param y the pointer's vertical coordinate in the interaction pane
    /// @return `true` if the pointer is within the top resize inset
    private boolean isTopEdge(double y) {
        return y >= 0 && y <= interactionPane.snappedTopInset();
    }

    /// Returns whether the pointer is within the bottom resize inset.
    ///
    /// @param y the pointer's vertical coordinate in the interaction pane
    /// @return `true` if the pointer is within the bottom resize inset
    private boolean isBottomEdge(double y) {
        return y < interactionPane.getHeight()
                && y >= interactionPane.getHeight() - interactionPane.snappedLeftInset();
    }

    /// Returns whether the pointer is within the left resize inset.
    ///
    /// @param x the pointer's horizontal coordinate in the interaction pane
    /// @return `true` if the pointer is within the left resize inset
    private boolean isLeftEdge(double x) {
        return x >= 0 && x <= interactionPane.snappedLeftInset();
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
        if (newWidth < titleContainer.getMinWidth())
            newWidth = titleContainer.getMinWidth();

        if (newHeight < 0)
            newHeight = primaryStage.getHeight();
        if (newHeight < primaryStage.getMinHeight())
            newHeight = primaryStage.getMinHeight();
        if (newHeight < titleContainer.getMinHeight())
            newHeight = titleContainer.getMinHeight();

        // Width and height must be set simultaneously to avoid JDK-8344372 (https://github.com/openjdk/jfx/pull/1654)
        primaryStage.setWidth(newWidth);
        primaryStage.setHeight(newHeight);
    }

    /// Selects the resize cursor for the pointer's current position in the interaction pane.
    ///
    /// @param mouseEvent the pointer movement event
    private void onMouseMoved(MouseEvent mouseEvent) {
        if (!primaryStage.isFullScreen() && primaryStage.isResizable()) {
            double x = mouseEvent.getX();
            double y = mouseEvent.getY();
            double diagonalSize = interactionPane.snappedLeftInset() + 10;
            if (isRightEdge(x)) {
                if (y < diagonalSize) {
                    interactionPane.setCursor(Cursor.NE_RESIZE);
                } else if (y > interactionPane.getHeight() - diagonalSize) {
                    interactionPane.setCursor(Cursor.SE_RESIZE);
                } else {
                    interactionPane.setCursor(Cursor.E_RESIZE);
                }
            } else if (isLeftEdge(x)) {
                if (y < diagonalSize) {
                    interactionPane.setCursor(Cursor.NW_RESIZE);
                } else if (y > interactionPane.getHeight() - diagonalSize) {
                    interactionPane.setCursor(Cursor.SW_RESIZE);
                } else {
                    interactionPane.setCursor(Cursor.W_RESIZE);
                }
            } else if (isTopEdge(y)) {
                if (x < diagonalSize) {
                    interactionPane.setCursor(Cursor.NW_RESIZE);
                } else if (x > interactionPane.getWidth() - diagonalSize) {
                    interactionPane.setCursor(Cursor.NE_RESIZE);
                } else {
                    interactionPane.setCursor(Cursor.N_RESIZE);
                }
            } else if (isBottomEdge(y)) {
                if (x < diagonalSize) {
                    interactionPane.setCursor(Cursor.SW_RESIZE);
                } else if (x > interactionPane.getWidth() - diagonalSize) {
                    interactionPane.setCursor(Cursor.SE_RESIZE);
                } else {
                    interactionPane.setCursor(Cursor.S_RESIZE);
                }
            } else {
                interactionPane.setCursor(Cursor.DEFAULT);
            }
        } else {
            interactionPane.setCursor(Cursor.DEFAULT);
        }
    }

    /// Ends the active move or resize gesture.
    ///
    /// @param mouseEvent the mouse release event
    private void onMouseReleased(MouseEvent mouseEvent) {
        decorator.setDragging(false);
    }

    /// Moves or resizes the stage according to the cursor selected at drag start.
    ///
    /// @param mouseEvent the active drag event
    private void onMouseDragged(MouseEvent mouseEvent) {
        if (!decorator.isDragging()) {
            decorator.setDragging(true);
            mouseInitX = mouseEvent.getScreenX();
            mouseInitY = mouseEvent.getScreenY();
            stageInitX = primaryStage.getX();
            stageInitY = primaryStage.getY();
            stageInitWidth = primaryStage.getWidth();
            stageInitHeight = primaryStage.getHeight();
        }

        if (primaryStage.isFullScreen()
                || !mouseEvent.isPrimaryButtonDown()
                || mouseEvent.isStillSincePress())
            return;

        double dx = mouseEvent.getScreenX() - mouseInitX;
        double dy = mouseEvent.getScreenY() - mouseInitY;

        Cursor cursor = interactionPane.getCursor();
        if (decorator.isAllowMove() && cursor == Cursor.DEFAULT) {
            primaryStage.setX(stageInitX + dx);
            primaryStage.setY(stageInitY + dy);
            mouseEvent.consume();
        }

        if (decorator.isResizable()) {
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

    /// Updates whether the floating layer participates in layout visibility and mouse picking.
    ///
    /// @param floatLayer the floating layer bound to the decorator's transient containers
    private void updateFloatLayer(StackPane floatLayer) {
        boolean empty = decorator.getContainer().isEmpty();
        floatLayer.setMouseTransparent(empty);
        floatLayer.setVisible(!empty);
    }

    /// Creates the navigation bar for the current decorator state.
    ///
    /// @param leftPaneWidth the width reserved for the left page pane
    /// @param canBack whether to show the back button
    /// @param canClose whether to show the page close button
    /// @param showCloseAsHome whether to use the home icon for the page close button
    /// @param canRefresh whether to show the refresh button
    /// @param title the title text, or `null` to omit it
    /// @param titleNode the additional title node, or `null` to omit it
    /// @return the configured navigation bar
    private Node createNavBar(
            double leftPaneWidth,
            boolean canBack,
            boolean canClose,
            boolean showCloseAsHome,
            boolean canRefresh,
            @Nullable String title,
            @Nullable Node titleNode) {
        BorderPane navBar = new BorderPane();
        navBar.getStyleClass().add("navigation-bar");

        HBox navLeft = new HBox();
        navLeft.setAlignment(Pos.CENTER_LEFT);
        navLeft.setPadding(new Insets(0, 5, 0, 5));

        if (canBack) {
            JFXButton backNavButton = new JFXButton();
            decorator.forbidDraggingWindow(backNavButton);
            backNavButton.setFocusTraversable(false);
            backNavButton.setGraphic(SVG.ARROW_BACK.createIcon(Themes.titleFillProperty()));
            backNavButton.getStyleClass().add("jfx-decorator-button");
            backNavButton.onActionProperty().bind(decorator.onBackNavButtonActionProperty());
            backNavButton.setVisible(true);
            navLeft.getChildren().add(backNavButton);
        }

        if (canClose) {
            JFXButton closeNavButton = new JFXButton();
            decorator.forbidDraggingWindow(closeNavButton);
            closeNavButton.setFocusTraversable(false);
            closeNavButton.setGraphic((showCloseAsHome ? SVG.HOME : SVG.CLOSE)
                    .createIcon(Themes.titleFillProperty()));
            closeNavButton.getStyleClass().add("jfx-decorator-button");
            closeNavButton.onActionProperty().bind(decorator.onCloseNavButtonActionProperty());
            navLeft.getChildren().add(closeNavButton);
        }

        if (canBack || canClose) {
            navBar.setLeft(navLeft);
        }

        BorderPane center = new BorderPane();
        if (title != null) {
            Label titleLabel = new Label();
            titleLabel.textFillProperty().bind(Themes.titleFillProperty());
            titleLabel.getStyleClass().add("jfx-decorator-title");
            if (titleNode == null) {
                titleLabel.maxWidthProperty().bind(Bindings.createDoubleBinding(
                        () -> decorator.getWidth() - 150 - navLeft.getWidth(),
                        decorator.widthProperty(),
                        navLeft.widthProperty()));
            } else {
                titleLabel.prefWidthProperty().bind(Bindings.createDoubleBinding(
                        () -> leftPaneWidth - 8 - navLeft.getWidth(),
                        navLeft.widthProperty()));
            }
            titleLabel.setText(title);
            center.setLeft(titleLabel);
            BorderPane.setAlignment(titleLabel, Pos.CENTER_LEFT);
        }
        if (titleNode != null) {
            center.setCenter(titleNode);
            BorderPane.setAlignment(titleNode, Pos.CENTER_LEFT);
            BorderPane.setMargin(titleNode, new Insets(0, 0, 0, 8));
        }
        if (onTitleBarDoubleClick != null) {
            center.setOnMouseClicked(onTitleBarDoubleClick);
        }
        center.setOnMouseDragged(this::onTitleBarDragged);
        navBar.setCenter(center);

        if (canRefresh) {
            HBox navRight = new HBox();
            navRight.setAlignment(Pos.CENTER_RIGHT);
            JFXButton refreshNavButton = new JFXButton();
            refreshNavButton.setGraphic(SVG.REFRESH.createIcon(Themes.titleFillProperty()));
            refreshNavButton.getStyleClass().add("jfx-decorator-button");
            refreshNavButton.onActionProperty().bind(decorator.onRefreshNavButtonActionProperty());
            decorator.forbidDraggingWindow(refreshNavButton);

            navRight.getChildren().setAll(refreshNavButton);
            navBar.setRight(navRight);
        }
        return navBar;
    }

    /// Provides the navigation transitions used when switching decorator pages.
    private enum NavBarAnimations implements TransitionPane.AnimationProducer {
        /// Moves the new navigation bar in from the right.
        NEXT {
            /// Initializes the next navigation bar outside the right edge of its container.
            @Override
            public void init(TransitionPane container, Node previousNode, Node nextNode) {
                super.init(container, previousNode, nextNode);
                nextNode.setTranslateX(container.getWidth());
            }

            /// Creates the forward navigation timeline.
            @Override
            public Timeline animate(
                    Pane container,
                    Node previousNode,
                    Node nextNode,
                    Duration duration,
                    Interpolator interpolator) {
                return new Timeline(
                        new KeyFrame(Duration.ZERO,
                                new KeyValue(nextNode.translateXProperty(), 50, interpolator),
                                new KeyValue(previousNode.translateXProperty(), 0, interpolator),
                                new KeyValue(nextNode.opacityProperty(), 0, interpolator),
                                new KeyValue(previousNode.opacityProperty(), 1, interpolator)),
                        new KeyFrame(duration,
                                new KeyValue(nextNode.translateXProperty(), 0, interpolator),
                                new KeyValue(previousNode.translateXProperty(), -50, interpolator),
                                new KeyValue(nextNode.opacityProperty(), 1, interpolator),
                                new KeyValue(previousNode.opacityProperty(), 0, interpolator)));
            }

            /// Returns this transition for an opposite animation request.
            @Override
            public TransitionPane.AnimationProducer opposite() {
                return NEXT;
            }
        },

        /// Moves the new navigation bar in from the left.
        PREVIOUS {
            /// Initializes the next navigation bar outside the right edge of its container.
            @Override
            public void init(TransitionPane container, Node previousNode, Node nextNode) {
                super.init(container, previousNode, nextNode);
                nextNode.setTranslateX(container.getWidth());
            }

            /// Creates the backward navigation timeline.
            @Override
            public Timeline animate(
                    Pane container,
                    Node previousNode,
                    Node nextNode,
                    Duration duration,
                    Interpolator interpolator) {
                return new Timeline(
                        new KeyFrame(Duration.ZERO,
                                new KeyValue(nextNode.translateXProperty(), -50, interpolator),
                                new KeyValue(previousNode.translateXProperty(), 0, interpolator),
                                new KeyValue(nextNode.opacityProperty(), 0, interpolator),
                                new KeyValue(previousNode.opacityProperty(), 1, interpolator)),
                        new KeyFrame(duration,
                                new KeyValue(nextNode.translateXProperty(), 0, interpolator),
                                new KeyValue(previousNode.translateXProperty(), 50, interpolator),
                                new KeyValue(nextNode.opacityProperty(), 1, interpolator),
                                new KeyValue(previousNode.opacityProperty(), 0, interpolator)));
            }

            /// Returns this transition for an opposite animation request.
            @Override
            public TransitionPane.AnimationProducer opposite() {
                return PREVIOUS;
            }
        }
    }
}
