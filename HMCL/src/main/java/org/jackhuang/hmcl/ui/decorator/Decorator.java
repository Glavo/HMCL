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
import javafx.beans.InvalidationListener;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
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
import org.jackhuang.hmcl.util.javafx.NodeWindowProperty;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;

/// Provides the launcher window content, navigation, dialogs, and custom window decoration.
@NotNullByDefault
public class Decorator extends StackPane {
    /// The nodes displayed in the primary page-content layer.
    private final ListProperty<Node> content = new SimpleListProperty<>(FXCollections.observableArrayList());

    /// The nodes displayed in the floating overlay layer.
    private final ListProperty<Node> container = new SimpleListProperty<>(FXCollections.observableArrayList());

    /// The launcher background and opacity rendered behind this decorator's content.
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

    /// Tracks the stage currently containing this decorator.
    private final NodeWindowProperty<Stage> stage = NodeWindowProperty.newStageProperty(this);

    /// The page navigator displayed in the primary content layer.
    private final Navigator navigator;

    /// The view that renders the non-shadow portion of the window.
    private final WindowPane windowPane;

    /// The direction used for the next navigation-bar transition.
    private Navigation.NavigationDirection navigationDirection = Navigation.NavigationDirection.START;

    /// The pane spanning the window content and hosting dialogs above it.
    private final StackPane dialogContainer;

    /// The snackbar displayed over this decorator.
    private final JFXSnackbar snackbar = new JFXSnackbar();

    /// Ends an active move or resize gesture.
    private final EventHandler<MouseEvent> onMouseReleased = this::onMouseReleased;

    /// Processes an active move or resize gesture.
    private final EventHandler<MouseEvent> onMouseDragged = this::onMouseDragged;

    /// Updates the resize cursor for the current pointer location.
    private final EventHandler<MouseEvent> onMouseMoved = this::onMouseMoved;

    /// Updates interaction filters when the attached stage changes state.
    private final InvalidationListener windowStateChangedListener = observable -> updateInteractionFilters();

    /// Whether restoring from an iconified state should play the restore animation.
    private boolean playRestoreMinimizeAnimation;

    /// Transfers window-state observation when this decorator moves between stages.
    private final ChangeListener<@Nullable Stage> stageChangedListener =
            (observable, oldStage, newStage) -> stageChanged(oldStage, newStage);

    /// Handles iconification changes and plays the restoration animation when requested.
    private final ChangeListener<Boolean> iconifiedChangedListener = (observable, oldValue, iconified) -> {
        updateInteractionFilters();
        if (playRestoreMinimizeAnimation && !iconified) {
            playRestoreAnimation();
        }
    };

    /// Whether move and resize filters are currently installed on this decorator.
    private boolean interactionFiltersInstalled;

    /// Whether the current pointer location permits moving the attached stage.
    private boolean allowMove;

    /// Whether a stage move or resize gesture is active.
    private boolean dragging;

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

    /// Creates a decorator and initializes its navigation root and window content.
    ///
    /// @param mainPage the root page of the navigation stack
    public Decorator(Node mainPage) {
        setBackground(new Background(new BackgroundFill(Color.TRANSPARENT, CornerRadii.EMPTY, Insets.EMPTY)));

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

        getStyleClass().add("window");

        StackPane shadowContainer = new StackPane();
        shadowContainer.getStyleClass().add("body");
        shadowContainer.setEffect(new DropShadow(
                BlurType.ONE_PASS_BOX,
                Color.rgb(0, 0, 0, 0.4),
                10,
                0.3,
                0.0,
                0.0));

        windowPane = new WindowPane(this);
        dialogContainer = windowPane.getDialogContainer();
        shadowContainer.getChildren().setAll(windowPane);
        getChildren().setAll(shadowContainer);

        stage.addListener(stageChangedListener);
        stageChanged(null, stage.get());

        // Pass key events to the current dialog or page.
        addEventFilter(KeyEvent.ANY, event -> {
            if (!(event.getTarget() instanceof Node target)) {
                return;
            }

            Node newTarget;
            @Nullable JFXDialogPane currentDialogPane = (JFXDialogPane) dialogContainer.getProperties()
                    .get(DialogUtils.PROPERTY_DIALOG_PANE_INSTANCE);

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
                    @Nullable Stage currentStage = stage.get();
                    if (currentStage != null) {
                        currentStage.setFullScreen(!currentStage.isFullScreen());
                        event.consume();
                    }
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

    /// Returns the pane spanning the window content and hosting dialogs above it.
    ///
    /// @return the dialog container
    public StackPane getDialogContainer() {
        return dialogContainer;
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

    /// Returns the launcher background property rendered behind this decorator's content.
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

    /// Transfers window-state listeners between the previous and current stages.
    ///
    /// @param oldStage the stage that previously contained this decorator, or `null` if there was none
    /// @param newStage the stage that now contains this decorator, or `null` if there is none
    private void stageChanged(@Nullable Stage oldStage, @Nullable Stage newStage) {
        if (oldStage != null) {
            oldStage.iconifiedProperty().removeListener(iconifiedChangedListener);
            oldStage.maximizedProperty().removeListener(windowStateChangedListener);
            oldStage.fullScreenProperty().removeListener(windowStateChangedListener);
        }
        if (newStage != null) {
            newStage.iconifiedProperty().addListener(iconifiedChangedListener);
            newStage.maximizedProperty().addListener(windowStateChangedListener);
            newStage.fullScreenProperty().addListener(windowStateChangedListener);
        }

        allowMove = false;
        dragging = false;
        playRestoreMinimizeAnimation = false;
        updateInteractionFilters();
    }

    /// Installs window interaction filters while the attached stage accepts custom gestures.
    private void updateInteractionFilters() {
        @Nullable Stage currentStage = stage.get();
        boolean shouldInstall = currentStage != null
                && (OperatingSystem.CURRENT_OS == OperatingSystem.MACOS
                    || !(currentStage.isIconified()
                        || currentStage.isFullScreen()
                        || currentStage.isMaximized()));

        if (shouldInstall == interactionFiltersInstalled) {
            return;
        }

        interactionFiltersInstalled = shouldInstall;
        if (shouldInstall) {
            addEventFilter(MouseEvent.MOUSE_RELEASED, onMouseReleased);
            addEventFilter(MouseEvent.MOUSE_DRAGGED, onMouseDragged);
            addEventFilter(MouseEvent.MOUSE_MOVED, onMouseMoved);
        } else {
            removeEventFilter(MouseEvent.MOUSE_RELEASED, onMouseReleased);
            removeEventFilter(MouseEvent.MOUSE_DRAGGED, onMouseDragged);
            removeEventFilter(MouseEvent.MOUSE_MOVED, onMouseMoved);
            setCursor(Cursor.DEFAULT);
        }
    }

    /// Handles a title-bar click that may toggle the attached stage's maximized state.
    ///
    /// @param event the title-bar mouse event
    void onTitleBarClicked(MouseEvent event) {
        @Nullable Stage currentStage = stage.get();
        if (currentStage != null
                && OperatingSystem.CURRENT_OS != OperatingSystem.MACOS
                && event.getButton() == MouseButton.PRIMARY
                && event.getClickCount() == 2) {
            currentStage.setMaximized(!currentStage.isMaximized());
            event.consume();
        }
    }

    /// Restores a maximized stage and initializes movement when its title bar is dragged.
    ///
    /// @param event the title-bar drag event
    void onTitleBarDragged(MouseEvent event) {
        @Nullable Stage currentStage = stage.get();
        if (currentStage != null && !dragging && currentStage.isMaximized()) {
            dragging = true;
            mouseInitX = event.getScreenX();
            mouseInitY = event.getScreenY();
            currentStage.setMaximized(false);
            stageInitWidth = currentStage.getWidth();
            stageInitHeight = currentStage.getHeight();
            currentStage.setY(stageInitY = 0);
            currentStage.setX(stageInitX = mouseInitX - stageInitWidth / 2);
        }
    }

    /// Returns whether the pointer is within the right resize inset.
    ///
    /// @param x the pointer's horizontal coordinate in this decorator
    /// @return `true` if the pointer is within the right resize inset
    private boolean isRightEdge(double x) {
        return x < getWidth() && x >= getWidth() - snappedLeftInset();
    }

    /// Returns whether the pointer is within the top resize inset.
    ///
    /// @param y the pointer's vertical coordinate in this decorator
    /// @return `true` if the pointer is within the top resize inset
    private boolean isTopEdge(double y) {
        return y >= 0 && y <= snappedTopInset();
    }

    /// Returns whether the pointer is within the bottom resize inset.
    ///
    /// @param y the pointer's vertical coordinate in this decorator
    /// @return `true` if the pointer is within the bottom resize inset
    private boolean isBottomEdge(double y) {
        return y < getHeight() && y >= getHeight() - snappedLeftInset();
    }

    /// Returns whether the pointer is within the left resize inset.
    ///
    /// @param x the pointer's horizontal coordinate in this decorator
    /// @return `true` if the pointer is within the left resize inset
    private boolean isLeftEdge(double x) {
        return x >= 0 && x <= snappedLeftInset();
    }

    /// Applies requested stage dimensions after enforcing stage and title-bar minimums.
    ///
    /// A negative dimension preserves the current value. Width and height are always written
    /// together to avoid JDK-8344372.
    ///
    /// @param currentStage the stage being resized
    /// @param newWidth the requested width, or a negative value to preserve the current width
    /// @param newHeight the requested height, or a negative value to preserve the current height
    private void resizeStage(Stage currentStage, double newWidth, double newHeight) {
        if (newWidth < 0)
            newWidth = currentStage.getWidth();
        if (newWidth < currentStage.getMinWidth())
            newWidth = currentStage.getMinWidth();
        if (newWidth < windowPane.getTitleContainerMinWidth())
            newWidth = windowPane.getTitleContainerMinWidth();

        if (newHeight < 0)
            newHeight = currentStage.getHeight();
        if (newHeight < currentStage.getMinHeight())
            newHeight = currentStage.getMinHeight();
        if (newHeight < windowPane.getTitleContainerMinHeight())
            newHeight = windowPane.getTitleContainerMinHeight();

        // Width and height must be set simultaneously to avoid JDK-8344372.
        currentStage.setWidth(newWidth);
        currentStage.setHeight(newHeight);
    }

    /// Selects the resize cursor for the pointer's current position.
    ///
    /// @param event the pointer movement event
    private void onMouseMoved(MouseEvent event) {
        @Nullable Stage currentStage = stage.get();
        if (currentStage != null && !currentStage.isFullScreen() && currentStage.isResizable()) {
            double x = event.getX();
            double y = event.getY();
            double diagonalSize = snappedLeftInset() + 10;
            if (isRightEdge(x)) {
                if (y < diagonalSize) {
                    setCursor(Cursor.NE_RESIZE);
                } else if (y > getHeight() - diagonalSize) {
                    setCursor(Cursor.SE_RESIZE);
                } else {
                    setCursor(Cursor.E_RESIZE);
                }
            } else if (isLeftEdge(x)) {
                if (y < diagonalSize) {
                    setCursor(Cursor.NW_RESIZE);
                } else if (y > getHeight() - diagonalSize) {
                    setCursor(Cursor.SW_RESIZE);
                } else {
                    setCursor(Cursor.W_RESIZE);
                }
            } else if (isTopEdge(y)) {
                if (x < diagonalSize) {
                    setCursor(Cursor.NW_RESIZE);
                } else if (x > getWidth() - diagonalSize) {
                    setCursor(Cursor.NE_RESIZE);
                } else {
                    setCursor(Cursor.N_RESIZE);
                }
            } else if (isBottomEdge(y)) {
                if (x < diagonalSize) {
                    setCursor(Cursor.SW_RESIZE);
                } else if (x > getWidth() - diagonalSize) {
                    setCursor(Cursor.SE_RESIZE);
                } else {
                    setCursor(Cursor.S_RESIZE);
                }
            } else {
                setCursor(Cursor.DEFAULT);
            }
        } else {
            setCursor(Cursor.DEFAULT);
        }
    }

    /// Ends the active move or resize gesture.
    ///
    /// @param event the mouse release event
    private void onMouseReleased(MouseEvent event) {
        dragging = false;
    }

    /// Moves or resizes the attached stage according to the cursor selected at drag start.
    ///
    /// @param event the active drag event
    private void onMouseDragged(MouseEvent event) {
        @Nullable Stage currentStage = stage.get();
        if (currentStage == null) {
            dragging = false;
            return;
        }

        if (!dragging) {
            dragging = true;
            mouseInitX = event.getScreenX();
            mouseInitY = event.getScreenY();
            stageInitX = currentStage.getX();
            stageInitY = currentStage.getY();
            stageInitWidth = currentStage.getWidth();
            stageInitHeight = currentStage.getHeight();
        }

        if (currentStage.isFullScreen()
                || !event.isPrimaryButtonDown()
                || event.isStillSincePress())
            return;

        double dx = event.getScreenX() - mouseInitX;
        double dy = event.getScreenY() - mouseInitY;

        Cursor cursor = getCursor();
        if (allowMove && cursor == Cursor.DEFAULT) {
            currentStage.setX(stageInitX + dx);
            currentStage.setY(stageInitY + dy);
            event.consume();
        }

        if (currentStage.isResizable()) {
            if (cursor == Cursor.E_RESIZE) {
                resizeStage(currentStage, stageInitWidth + dx, -1);
                event.consume();
            } else if (cursor == Cursor.S_RESIZE) {
                resizeStage(currentStage, -1, stageInitHeight + dy);
                event.consume();
            } else if (cursor == Cursor.W_RESIZE) {
                resizeStage(currentStage, stageInitWidth - dx, -1);
                currentStage.setX(stageInitX + stageInitWidth - currentStage.getWidth());
                event.consume();
            } else if (cursor == Cursor.N_RESIZE) {
                resizeStage(currentStage, -1, stageInitHeight - dy);
                currentStage.setY(stageInitY + stageInitHeight - currentStage.getHeight());
                event.consume();
            } else if (cursor == Cursor.SE_RESIZE) {
                resizeStage(currentStage, stageInitWidth + dx, stageInitHeight + dy);
                event.consume();
            } else if (cursor == Cursor.SW_RESIZE) {
                resizeStage(currentStage, stageInitWidth - dx, stageInitHeight + dy);
                currentStage.setX(stageInitX + stageInitWidth - currentStage.getWidth());
                event.consume();
            } else if (cursor == Cursor.NW_RESIZE) {
                resizeStage(currentStage, stageInitWidth - dx, stageInitHeight - dy);
                currentStage.setX(stageInitX + stageInitWidth - currentStage.getWidth());
                currentStage.setY(stageInitY + stageInitHeight - currentStage.getHeight());
                event.consume();
            } else if (cursor == Cursor.NE_RESIZE) {
                resizeStage(currentStage, stageInitWidth + dx, stageInitHeight - dy);
                currentStage.setY(stageInitY + stageInitHeight - currentStage.getHeight());
                event.consume();
            }
        }
    }

    /// Plays the restoration animation after the attached stage leaves the iconified state.
    private void playRestoreAnimation() {
        playRestoreMinimizeAnimation = false;
        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(opacityProperty(), 0, Motion.EASE),
                        new KeyValue(translateYProperty(), 200, Motion.EASE),
                        new KeyValue(scaleXProperty(), 0.4, Motion.EASE),
                        new KeyValue(scaleYProperty(), 0.4, Motion.EASE),
                        new KeyValue(scaleZProperty(), 0.4, Motion.EASE)),
                new KeyFrame(Motion.SHORT4,
                        new KeyValue(opacityProperty(), 1, Motion.EASE),
                        new KeyValue(translateYProperty(), 0, Motion.EASE),
                        new KeyValue(scaleXProperty(), 1, Motion.EASE),
                        new KeyValue(scaleYProperty(), 1, Motion.EASE),
                        new KeyValue(scaleZProperty(), 1, Motion.EASE)));
        timeline.play();
    }

    /// Iconifies the attached stage, playing the minimize animation when enabled.
    ///
    /// This method has no effect while this decorator is detached from a stage.
    public void minimize() {
        @Nullable Stage currentStage = stage.get();
        if (currentStage == null) {
            return;
        }

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
            timeline.setOnFinished(event -> {
                if (stage.get() == currentStage) {
                    currentStage.setIconified(true);
                }
            });
            timeline.play();
        } else {
            currentStage.setIconified(true);
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
        node.addEventHandler(MouseEvent.MOUSE_MOVED, event -> allowMove = true);
        node.addEventHandler(MouseEvent.MOUSE_EXITED, event -> {
            if (!dragging) allowMove = false;
        });
    }

    /// Marks a node as an area whose pointer events must not move the stage.
    ///
    /// @param node the non-draggable node
    public void forbidDraggingWindow(Node node) {
        node.addEventHandler(MouseEvent.MOUSE_MOVED, event -> {
            allowMove = false;
            event.consume();
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

}
