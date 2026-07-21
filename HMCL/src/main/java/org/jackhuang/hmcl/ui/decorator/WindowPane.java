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
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
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
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// Represents the clipped portion of a [Decorator] window inside its drop shadow.
///
/// This pane owns the themed background, page and floating-content layers, title bar, navigation
/// controls, and window buttons. Window lifecycle and interaction state are managed by the
/// enclosing [Decorator].
@NotNullByDefault
final class WindowPane extends StackPane {
    /// The decorator whose properties and actions drive this pane.
    private final Decorator decorator;

    /// The pane spanning the window content and hosting dialogs above it.
    private final StackPane dialogContainer = new StackPane();

    /// The title container whose minimum dimensions constrain window resizing.
    private final StackPane titleContainer;

    /// Creates and binds the non-shadow portion of a custom window.
    ///
    /// The constructor registers this pane as the snackbar container and constructs the pane
    /// exposed by [#getDialogContainer()] for dialog presentation.
    ///
    /// @param decorator the decorator represented by this window
    WindowPane(Decorator decorator) {
        this.decorator = decorator;

        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(widthProperty());
        clip.heightProperty().bind(heightProperty());
        clip.setArcWidth(8);
        clip.setArcHeight(8);
        setClip(clip);

        decorator.getSnackbar().registerSnackbarContainer(this);

        dialogContainer.backgroundProperty().bind(Bindings.createObjectBinding(
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
        dialogContainer.getChildren().setAll(backgroundNode, frame);
        getChildren().add(dialogContainer);

        // The center clips regular page content to the window bounds.
        StackPane container = new StackPane();
        FXUtils.setOverflowHidden(container);

        StackPane contentPlaceHolder = new StackPane();
        contentPlaceHolder.getStyleClass().add("jfx-decorator-content-container");
        Bindings.bindContent(contentPlaceHolder.getChildren(), decorator.contentProperty());
        container.getChildren().add(contentPlaceHolder);

        frame.setCenter(container);

        titleContainer = new StackPane();
        titleContainer.setPickOnBounds(false);
        titleContainer.getStyleClass().add("jfx-tool-bar");
        backgroundNode.maxHeightProperty().bind(Bindings.createDoubleBinding(
                () -> Math.max(0.0, dialogContainer.getHeight()
                        - (decorator.isTitleTransparent() ? 0.0 : titleContainer.getHeight())),
                dialogContainer.heightProperty(),
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

    /// Returns the pane spanning the window content and hosting dialogs above it.
    ///
    /// @return the dialog container
    StackPane getDialogContainer() {
        return dialogContainer;
    }

    /// Returns the minimum width required by the title container.
    ///
    /// @return the title container's minimum width
    double getTitleContainerMinWidth() {
        return titleContainer.getMinWidth();
    }

    /// Returns the minimum height required by the title container.
    ///
    /// @return the title container's minimum height
    double getTitleContainerMinHeight() {
        return titleContainer.getMinHeight();
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
        center.setOnMouseClicked(decorator::onTitleBarClicked);
        center.setOnMouseDragged(decorator::onTitleBarDragged);
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
