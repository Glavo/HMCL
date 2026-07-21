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

import com.jfoenix.controls.JFXSnackbar;
import com.jfoenix.controls.JFXSnackbarLayout;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.SkinBase;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import org.jackhuang.hmcl.Launcher;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorDnD;
import org.jackhuang.hmcl.theme.LauncherBackground;
import org.jackhuang.hmcl.theme.Themes;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.DialogUtils;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.account.AddAuthlibInjectorServerPane;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.Motion;
import org.jackhuang.hmcl.ui.animation.TransitionPane.AnimationProducer;
import org.jackhuang.hmcl.ui.construct.JFXDialogPane;
import org.jackhuang.hmcl.ui.construct.Navigator;
import org.jackhuang.hmcl.ui.wizard.Navigation;
import org.jackhuang.hmcl.ui.wizard.Refreshable;
import org.jackhuang.hmcl.ui.wizard.WizardProvider;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;

/// Provides the launcher window content, navigation, dialogs, and custom window decoration.
@NotNullByDefault
public class Decorator extends Control {
    /// The nodes displayed in the primary page-content layer.
    private final ListProperty<Node> content = new SimpleListProperty<>(FXCollections.observableArrayList());

    /// The nodes displayed in the floating overlay layer.
    private final ListProperty<Node> container = new SimpleListProperty<>(FXCollections.observableArrayList());

    /// The launcher background and opacity rendered by this decorator's skin.
    private final ObjectProperty<@Nullable LauncherBackground> contentBackground = new SimpleObjectProperty<>();

    /// The state currently rendered by the navigation bar.
    private final ObjectProperty<DecoratorPage.@Nullable State> state = new SimpleObjectProperty<>();

    /// The action invoked by the page close button, or `null` when no action is installed.
    private final ObjectProperty<@Nullable EventHandler<ActionEvent>> onCloseNavButtonAction =
            new SimpleObjectProperty<>();

    /// The action invoked by the page back button, or `null` when no action is installed.
    private final ObjectProperty<@Nullable EventHandler<ActionEvent>> onBackNavButtonAction =
            new SimpleObjectProperty<>();

    /// The action invoked by the page refresh button, or `null` when no action is installed.
    private final ObjectProperty<@Nullable EventHandler<ActionEvent>> onRefreshNavButtonAction =
            new SimpleObjectProperty<>();

    /// Whether the current page permits refreshing.
    private final BooleanProperty canRefresh = new SimpleBooleanProperty(false);

    /// Whether the navigation state permits returning to the previous page.
    private final BooleanProperty canBack = new SimpleBooleanProperty(false);

    /// Whether the current page exposes the page close button.
    private final BooleanProperty canClose = new SimpleBooleanProperty(false);

    /// Whether the page close button uses the home icon.
    private final BooleanProperty showCloseAsHome = new SimpleBooleanProperty(false);

    /// Whether the page background extends beneath the title bar.
    private final BooleanProperty titleTransparent = new SimpleBooleanProperty(false);

    /// The stage initially configured and controlled by this decorator.
    private final Stage primaryStage;

    /// The page navigator displayed in the primary content layer.
    private final Navigator navigator;

    /// The direction used for the next navigation-bar transition.
    private Navigation.NavigationDirection navigationDirection = Navigation.NavigationDirection.START;

    /// The pane used as the dialog container after the skin creates it.
    private @Nullable StackPane drawerWrapper;

    /// The snackbar displayed over this decorator.
    private final JFXSnackbar snackbar = new JFXSnackbar();

    /// Whether the current pointer location permits moving the stage.
    private final ReadOnlyBooleanWrapper allowMove = new ReadOnlyBooleanWrapper();

    /// Whether a stage move or resize gesture is active.
    private final ReadOnlyBooleanWrapper dragging = new ReadOnlyBooleanWrapper();

    /// Whether restoring from an iconified state should play the restore animation.
    private boolean playRestoreMinimizeAnimation = false;

    /// Creates a decorator, initializes its navigation root, and configures its stage.
    ///
    /// @param primaryStage the stage initially hosting this decorator
    /// @param mainPage the root page of the navigation stack
    public Decorator(Stage primaryStage, Node mainPage) {
        this.primaryStage = primaryStage;

        setBackground(new Background(new BackgroundFill(Color.TRANSPARENT, CornerRadii.EMPTY, Insets.EMPTY)));

        primaryStage.initStyle(StageStyle.UNDECORATED);

        if (AnimationUtils.playWindowAnimation()) {
            FXUtils.onChange(primaryStage.iconifiedProperty(), iconified -> {
                if (playRestoreMinimizeAnimation && !iconified) {
                    playRestoreMinimizeAnimation = false;
                    Timeline timeline = new Timeline(
                            new KeyFrame(Duration.ZERO,
                                    new KeyValue(this.opacityProperty(), 0, Motion.EASE),
                                    new KeyValue(this.translateYProperty(), 200, Motion.EASE),
                                    new KeyValue(this.scaleXProperty(), 0.4, Motion.EASE),
                                    new KeyValue(this.scaleYProperty(), 0.4, Motion.EASE),
                                    new KeyValue(this.scaleZProperty(), 0.4, Motion.EASE)
                            ),
                            new KeyFrame(Motion.SHORT4,
                                    new KeyValue(this.opacityProperty(), 1, Motion.EASE),
                                    new KeyValue(this.translateYProperty(), 0, Motion.EASE),
                                    new KeyValue(this.scaleXProperty(), 1, Motion.EASE),
                                    new KeyValue(this.scaleYProperty(), 1, Motion.EASE),
                                    new KeyValue(this.scaleZProperty(), 1, Motion.EASE)
                            )
                    );
                    timeline.play();
                }
            });
        }

        titleTransparentProperty().bind(Themes.titleBarTransparentProperty());

        navigator = new Navigator();
        navigator.setOnNavigated(this::onNavigated);
        navigator.init(mainPage);

        getContent().setAll(navigator);
        onCloseNavButtonActionProperty().set(event -> closePage());
        onBackNavButtonActionProperty().set(event -> back());
        onRefreshNavButtonActionProperty().set(event -> refresh());

        setupAuthlibInjectorDnD();

        contentBackgroundProperty().bind(Themes.backgroundProperty());

        // Pass key events to the current dialog or page.
        addEventFilter(KeyEvent.ANY, event -> {
            if (!(event.getTarget() instanceof Node target)) {
                return;
            }

            Node newTarget;
            @Nullable JFXDialogPane currentDialogPane = null;
            if (getDrawerWrapper() != null) {
                currentDialogPane = (JFXDialogPane) getDrawerWrapper().getProperties()
                        .get(DialogUtils.PROPERTY_DIALOG_PANE_INSTANCE);
            }

            if (currentDialogPane != null && currentDialogPane.peek().isPresent()) {
                newTarget = currentDialogPane.peek().get();
            } else {
                newTarget = navigator.getCurrentPage();
            }

            boolean needsRedirect = true;
            for (@Nullable Node current = target; current != null; current = current.getParent()) {
                if (current == newTarget) {
                    needsRedirect = false;
                    break;
                }
            }
            if (!needsRedirect) {
                return;
            }

            event.consume();
            newTarget.fireEvent(event.copyFor(event.getSource(), newTarget));
        });

        onEscPressed(navigator, this::back);

        // https://github.com/HMCL-dev/HMCL/issues/4290
        if (OperatingSystem.CURRENT_OS != OperatingSystem.MACOS) {
            navigator.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
                if (event.getCode() == KeyCode.F11) {
                    primaryStage.setFullScreen(!primaryStage.isFullScreen());
                    event.consume();
                }
            });
        }

        navigator.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
            if (event.getButton() == MouseButton.BACK) {
                back();
                event.consume();
            }
        });
    }

    /// Returns the stage initially configured by this decorator.
    ///
    /// @return the initially configured stage
    public Stage getPrimaryStage() {
        return primaryStage;
    }

    /// Returns the pane used as the dialog container after skin creation.
    ///
    /// @return the dialog container, or `null` before the skin creates it
    public @Nullable StackPane getDrawerWrapper() {
        return drawerWrapper;
    }

    /// Sets the pane used as the dialog container.
    ///
    /// @param drawerWrapper the dialog container created by the skin
    public void setDrawerWrapper(StackPane drawerWrapper) {
        this.drawerWrapper = drawerWrapper;
    }

    /// Returns the mutable list displayed in the primary content layer.
    ///
    /// @return the primary content list
    public ObservableList<Node> getContent() {
        return content.get();
    }

    /// Returns the primary content list property.
    ///
    /// @return the primary content list property
    public ListProperty<Node> contentProperty() {
        return content;
    }

    /// Replaces the list displayed in the primary content layer.
    ///
    /// @param content the new primary content list
    public void setContent(ObservableList<Node> content) {
        this.content.set(content);
    }

    /// Returns the state currently rendered by the navigation bar.
    ///
    /// @return the current navigation state, or `null` if none has been established
    public DecoratorPage.@Nullable State getState() {
        return state.get();
    }

    /// Returns the navigation-bar state property.
    ///
    /// @return the navigation-bar state property
    public ObjectProperty<DecoratorPage.@Nullable State> stateProperty() {
        return state;
    }

    /// Sets the state rendered by the navigation bar.
    ///
    /// @param state the new navigation state, or `null` to clear it
    public void setState(DecoratorPage.@Nullable State state) {
        this.state.set(state);
    }

    /// Returns the mutable list displayed in the floating overlay layer.
    ///
    /// @return the floating overlay list
    public ObservableList<Node> getContainer() {
        return container.get();
    }

    /// Returns the floating overlay list property.
    ///
    /// @return the floating overlay list property
    public ListProperty<Node> containerProperty() {
        return container;
    }

    /// Replaces the list displayed in the floating overlay layer.
    ///
    /// @param container the new floating overlay list
    public void setContainer(ObservableList<Node> container) {
        this.container.set(container);
    }

    /// Returns the launcher background rendered behind the content.
    ///
    /// @return the launcher background, or `null` when no image background is configured
    public @Nullable LauncherBackground getContentBackground() {
        return contentBackground.get();
    }

    /// Returns the launcher background property rendered by this decorator's skin.
    ///
    /// @return the launcher background property
    public ObjectProperty<@Nullable LauncherBackground> contentBackgroundProperty() {
        return contentBackground;
    }

    /// Sets the launcher background rendered behind the content.
    ///
    /// @param contentBackground the launcher background, or `null` to clear it
    public void setContentBackground(@Nullable LauncherBackground contentBackground) {
        this.contentBackground.set(contentBackground);
    }

    /// Returns the property indicating whether the current page may be refreshed.
    ///
    /// @return the refresh availability property
    public BooleanProperty canRefreshProperty() {
        return canRefresh;
    }

    /// Returns the property indicating whether backward navigation is available.
    ///
    /// @return the backward navigation availability property
    public BooleanProperty canBackProperty() {
        return canBack;
    }

    /// Returns the property controlling visibility of the page close button.
    ///
    /// @return the page close availability property
    public BooleanProperty canCloseProperty() {
        return canClose;
    }

    /// Returns the property selecting the home icon for the page close button.
    ///
    /// @return the close-as-home property
    public BooleanProperty showCloseAsHomeProperty() {
        return showCloseAsHome;
    }

    /// Returns whether the current pointer location permits moving the stage.
    ///
    /// @return `true` if a drag may move the stage
    public boolean isAllowMove() {
        return allowMove.get();
    }

    /// Returns the read-only stage movement permission property.
    ///
    /// @return the stage movement permission property
    public ReadOnlyBooleanProperty allowMoveProperty() {
        return allowMove.getReadOnlyProperty();
    }

    /// Updates whether the current pointer location permits moving the stage.
    ///
    /// @param allowMove whether a drag may move the stage
    void setAllowMove(boolean allowMove) {
        this.allowMove.set(allowMove);
    }

    /// Returns whether a stage move or resize gesture is active.
    ///
    /// @return `true` while a move or resize gesture is active
    public boolean isDragging() {
        return dragging.get();
    }

    /// Returns the read-only active drag property.
    ///
    /// @return the active drag property
    public ReadOnlyBooleanProperty draggingProperty() {
        return dragging.getReadOnlyProperty();
    }

    /// Updates whether a stage move or resize gesture is active.
    ///
    /// @param dragging whether a move or resize gesture is active
    void setDragging(boolean dragging) {
        this.dragging.set(dragging);
    }

    /// Returns whether page content extends beneath the title bar.
    ///
    /// @return `true` if the title bar is transparent over page content
    public boolean isTitleTransparent() {
        return titleTransparent.get();
    }

    /// Returns the transparent-title property.
    ///
    /// @return the transparent-title property
    public BooleanProperty titleTransparentProperty() {
        return titleTransparent;
    }

    /// Sets whether page content extends beneath the title bar.
    ///
    /// @param titleTransparent whether the title bar is transparent over page content
    public void setTitleTransparent(boolean titleTransparent) {
        this.titleTransparent.set(titleTransparent);
    }

    /// Returns the action property for the page back button.
    ///
    /// @return the page back-button action property
    public ObjectProperty<@Nullable EventHandler<ActionEvent>> onBackNavButtonActionProperty() {
        return onBackNavButtonAction;
    }

    /// Returns the action property for the page close button.
    ///
    /// @return the page close-button action property
    public ObjectProperty<@Nullable EventHandler<ActionEvent>> onCloseNavButtonActionProperty() {
        return onCloseNavButtonAction;
    }

    /// Returns the action property for the page refresh button.
    ///
    /// @return the page refresh-button action property
    public ObjectProperty<@Nullable EventHandler<ActionEvent>> onRefreshNavButtonActionProperty() {
        return onRefreshNavButtonAction;
    }

    /// Returns the snackbar displayed over this decorator.
    ///
    /// @return the snackbar
    public JFXSnackbar getSnackbar() {
        return snackbar;
    }

    /// Navigates to a node using the supplied transition.
    ///
    /// @param node the destination node
    /// @param animationProducer the transition producer
    /// @param duration the transition duration
    /// @param interpolator the transition interpolator
    public void navigate(
            Node node,
            AnimationProducer animationProducer,
            Duration duration,
            Interpolator interpolator) {
        navigator.navigate(node, animationProducer, duration, interpolator);
    }

    /// Returns the property indicating whether navigation can return to a previous page.
    ///
    /// @return the navigator's backable property
    public BooleanProperty backableProperty() {
        return navigator.backableProperty();
    }

    /// Closes the current page when possible, or clears the navigation stack.
    private void closePage() {
        if (navigator.getCurrentPage() instanceof DecoratorPage page && page.isPageCloseable()) {
            page.closePage();
            return;
        }
        navigator.clear();
    }

    /// Requests backward navigation from the current page.
    private void back() {
        if (navigator.getCurrentPage() instanceof DecoratorPage page) {
            if (page.back() && navigator.canGoBack()) {
                navigator.close();
            }
        } else if (navigator.canGoBack()) {
            navigator.close();
        }
    }

    /// Refreshes the current page when it currently permits refreshing.
    private void refresh() {
        if (navigator.getCurrentPage() instanceof Refreshable refreshable
                && refreshable.refreshableProperty().get()) {
            refreshable.refresh();
        }
    }

    /// Synchronizes decorator state with the page selected by the navigator.
    ///
    /// @param event the completed navigation event
    private void onNavigated(Navigator.NavigationEvent event) {
        if (event.getSource() != navigator) {
            return;
        }

        Node destination = event.getNode();
        if (destination instanceof Refreshable refreshable) {
            canRefreshProperty().bind(refreshable.refreshableProperty());
        } else {
            canRefreshProperty().unbind();
            canRefreshProperty().set(false);
        }

        canCloseProperty().set(navigator.size() > 2);
        if (destination instanceof DecoratorPage page) {
            showCloseAsHomeProperty().set(!page.isPageCloseable());
        } else {
            showCloseAsHomeProperty().set(true);
        }

        setNavigationDirection(event.getDirection());

        // The state property must be updated after the other navigation properties.
        if (destination instanceof DecoratorPage page) {
            stateProperty().bind(page.stateProperty());
        } else {
            stateProperty().unbind();
            stateProperty().set(new DecoratorPage.State(
                    "", null, navigator.canGoBack(), false, true));
        }

        if (destination instanceof Region region) {
            StackPane parent = (StackPane) region.getParent();
            region.prefWidthProperty().bind(parent.widthProperty());
            region.prefHeightProperty().bind(parent.heightProperty());
        }
    }

    /// Shows a dialog over this decorator.
    ///
    /// @param node the dialog content
    public void showDialog(Node node) {
        DialogUtils.show(this, node);
    }

    /// Schedules a dialog to be shown over this decorator.
    ///
    /// @param node the dialog content
    public void showDialogLater(Node node) {
        DialogUtils.showLater(this, node);
    }

    /// Shows a snackbar containing the supplied text.
    ///
    /// @param content the snackbar text
    public void showToast(String content) {
        snackbar.fireEvent(new JFXSnackbar.SnackbarEvent(new JFXSnackbarLayout(content)));
    }

    /// Starts a wizard without selecting an initial category.
    ///
    /// @param wizardProvider the wizard provider
    public void startWizard(WizardProvider wizardProvider) {
        startWizard(wizardProvider, null);
    }

    /// Starts a wizard and optionally selects an initial category.
    ///
    /// @param wizardProvider the wizard provider
    /// @param category the initial category, or `null` to use the wizard default
    public void startWizard(WizardProvider wizardProvider, @Nullable String category) {
        FXUtils.checkFxUserThread();
        navigator.navigate(
                new DecoratorWizardDisplayer(wizardProvider, category),
                ContainerAnimations.FORWARD,
                Motion.SHORT4,
                Motion.EASE);
    }

    /// Installs drag-and-drop handlers for adding authlib-injector authentication servers.
    private void setupAuthlibInjectorDnD() {
        addEventFilter(DragEvent.DRAG_OVER, AuthlibInjectorDnD.dragOverHandler());
        addEventFilter(DragEvent.DRAG_DROPPED, AuthlibInjectorDnD.dragDroppedHandler(
                url -> Controllers.dialog(new AddAuthlibInjectorServerPane(url))));
    }

    /// Creates the default skin containing the window pane and its drop shadow.
    ///
    /// @return a new skin for this decorator
    @Override
    protected SkinBase<?> createDefaultSkin() {
        return new Skin(this);
    }

    /// Iconifies the configured stage, playing the minimize animation when enabled.
    public void minimize() {
        if (AnimationUtils.playWindowAnimation() && OperatingSystem.CURRENT_OS != OperatingSystem.MACOS) {
            playRestoreMinimizeAnimation = true;
            Timeline timeline = new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(this.opacityProperty(), 1, Motion.EASE),
                            new KeyValue(this.translateYProperty(), 0, Motion.EASE),
                            new KeyValue(this.scaleXProperty(), 1, Motion.EASE),
                            new KeyValue(this.scaleYProperty(), 1, Motion.EASE),
                            new KeyValue(this.scaleZProperty(), 1, Motion.EASE)
                    ),
                    new KeyFrame(Motion.SHORT4,
                            new KeyValue(this.opacityProperty(), 0, Motion.EASE),
                            new KeyValue(this.translateYProperty(), 200, Motion.EASE),
                            new KeyValue(this.scaleXProperty(), 0.4, Motion.EASE),
                            new KeyValue(this.scaleYProperty(), 0.4, Motion.EASE),
                            new KeyValue(this.scaleZProperty(), 0.4, Motion.EASE)
                    )
            );
            timeline.setOnFinished(event -> primaryStage.setIconified(true));
            timeline.play();
        } else {
            primaryStage.setIconified(true);
        }
    }

    /// Stops the application, playing the window close animation when enabled.
    public void close() {
        if (AnimationUtils.playWindowAnimation()) {
            Timeline timeline = new Timeline(
                    new KeyFrame(Duration.millis(0),
                            new KeyValue(opacityProperty(), 1, Motion.EASE),
                            new KeyValue(scaleXProperty(), 1, Motion.EASE),
                            new KeyValue(scaleYProperty(), 1, Motion.EASE),
                            new KeyValue(scaleZProperty(), 0.3, Motion.EASE)
                    ),
                    new KeyFrame(Duration.millis(200),
                            new KeyValue(opacityProperty(), 0, Motion.EASE),
                            new KeyValue(scaleXProperty(), 0.8, Motion.EASE),
                            new KeyValue(scaleYProperty(), 0.8, Motion.EASE),
                            new KeyValue(scaleZProperty(), 0.8, Motion.EASE)
                    )
            );
            timeline.setOnFinished(event -> Launcher.stopApplication());
            timeline.play();
        } else {
            Launcher.stopApplication();
        }
    }

    /// Marks a node as an area from which a pointer drag may move the stage.
    ///
    /// @param node the draggable node
    public void capableDraggingWindow(Node node) {
        node.addEventHandler(MouseEvent.MOUSE_MOVED, e -> allowMove.set(true));
        node.addEventHandler(MouseEvent.MOUSE_EXITED, e -> {
            if (!isDragging()) allowMove.set(false);
        });
    }

    /// Marks a node as an area whose pointer events must not move the stage.
    ///
    /// @param node the non-draggable node
    public void forbidDraggingWindow(Node node) {
        node.addEventHandler(MouseEvent.MOUSE_MOVED, e -> {
            allowMove.set(false);
            e.consume();
        });
    }

    /// Returns the direction used for the next navigation-bar transition.
    ///
    /// @return the pending navigation direction
    public Navigation.NavigationDirection getNavigationDirection() {
        return navigationDirection;
    }

    /// Sets the direction used for the next navigation-bar transition.
    ///
    /// @param navigationDirection the pending navigation direction
    public void setNavigationDirection(Navigation.NavigationDirection navigationDirection) {
        this.navigationDirection = navigationDirection;
    }

    /// Provides the outer spacing and drop shadow for a decorator.
    private static final class Skin extends SkinBase<Decorator> {
        /// Creates a skin that wraps the window pane in its drop shadow.
        ///
        /// @param control the decorator represented by this skin
        private Skin(Decorator control) {
            super(control);

            StackPane root = new StackPane();
            root.getStyleClass().add("window");

            StackPane shadowContainer = new StackPane();
            shadowContainer.getStyleClass().add("body");
            shadowContainer.setEffect(new DropShadow(
                    BlurType.ONE_PASS_BOX,
                    Color.rgb(0, 0, 0, 0.4),
                    10,
                    0.3,
                    0.0,
                    0.0));

            WindowPane windowPane = new WindowPane(control, root);
            shadowContainer.getChildren().setAll(windowPane);
            root.getChildren().setAll(shadowContainer);

            getChildren().add(root);
        }
    }
}
