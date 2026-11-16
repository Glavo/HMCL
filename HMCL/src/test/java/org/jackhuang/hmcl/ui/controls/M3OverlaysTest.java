// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.controls;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/// Exercises Material overlay lifetimes, input isolation, queueing and responsive layout in real scenes.
@NotNullByDefault
@EnabledIf("org.jackhuang.hmcl.JavaFXLauncher#isStarted")
final class M3OverlaysTest {

    /// Windows owned by the current test and closed on the JavaFX thread afterward.
    private final List<Stage> windows = new ArrayList<>();

    /// Creates a themed host in a showing test window.
    private StackPane host() {
        StackPane host = new StackPane();
        host.setStyle("-monet-surface-container-high: #ece6f0; -monet-on-surface: #1d1b20; "
                + "-monet-on-surface-variant: #49454f; -monet-secondary: #625b71; -monet-primary: #6750a4; "
                + "-monet-primary-container: #eaddff; -monet-secondary-container: #e8def8; "
                + "-monet-inverse-surface: #322f35; -monet-inverse-on-surface: #f5eff7; -monet-inverse-primary: #d0bcff;");
        Stage stage = new Stage();
        windows.add(stage);
        stage.setScene(new Scene(host, 800, 600));
        stage.show();
        return host;
    }

    /// Closes test windows even after a failed assertion.
    @AfterEach
    void closeWindows() throws Exception {
        onFx(() -> {
            windows.forEach(Stage::close);
            windows.clear();
            return true;
        });
    }

    /// Verifies cancellable action events, cancellable dismissal, result identity and focus restoration.
    @Test
    void dialogActionsAndFocus() throws Exception {
        onFx(() -> {
            StackPane host = host();
            Button background = new Button("Background");
            host.getChildren().add(background);
            background.requestFocus();
            M3Dialog dialog = new M3Dialog();
            dialog.setAnimated(false);
            dialog.setHeaderText("Confirm");
            Button confirm = new Button("Confirm");
            dialog.getActions().add(confirm);
            AtomicInteger hidden = new AtomicInteger();
            dialog.setOnHidden(event -> {
                assertSame(confirm, event.getAction());
                assertNull(dialog.getParent());
                hidden.incrementAndGet();
            });
            dialog.show(host);
            assertSame(dialog, host.getScene().getFocusOwner());
            background.requestFocus();
            assertSame(dialog, host.getScene().getFocusOwner());
            confirm.setOnAction(Event::consume);
            confirm.fire();
            assertTrue(dialog.isShowing());
            confirm.setOnAction(null);
            dialog.setOnCloseRequest(Event::consume);
            confirm.fire();
            assertTrue(dialog.isShowing());
            dialog.setOnCloseRequest(null);
            confirm.fire();
            assertFalse(dialog.isShowing());
            assertSame(background, host.getScene().getFocusOwner());
            dialog.close();
            assertEquals(1, hidden.get());
            return true;
        });
    }

    /// Prevents an old close request from dismissing a presentation opened by its callback.
    @Test
    void reentrantCloseRequestAndReuse() throws Exception {
        onFx(() -> {
            StackPane host = host();
            M3Dialog dialog = new M3Dialog();
            dialog.setAnimated(false);
            dialog.show(host);
            dialog.setOnCloseRequest(event -> {
                dialog.close();
                dialog.setOnCloseRequest(null);
                dialog.show(host);
            });
            dialog.requestClose();
            assertTrue(dialog.isShowing());
            dialog.close();
            dialog.setOnHidden(event -> dialog.show(host));
            dialog.show(host);
            dialog.close();
            assertTrue(dialog.isShowing());
            dialog.setOnHidden(null);
            return true;
        });
    }

    /// Retained nodes from a detached overlay cannot dismiss or activate a later presentation.
    @Test
    void detachedOverlayCannotAffectReopenedDialog() throws Exception {
        onFx(() -> {
            StackPane host = host();
            M3Dialog dialog = new M3Dialog();
            dialog.setAnimated(false);
            Button action = new Button("Confirm");
            action.setDefaultButton(true);
            AtomicInteger invocations = new AtomicInteger();
            action.setOnAction(event -> invocations.incrementAndGet());
            dialog.getActions().add(action);
            dialog.show(host);
            Node previousOverlay = dialog.getParent();
            dialog.close();
            dialog.show(host);
            click(previousOverlay, MouseButton.PRIMARY);
            key(previousOverlay, KeyCode.ENTER, false);
            assertTrue(dialog.isShowing());
            assertEquals(0, invocations.get());
            assertEquals(1, host.getChildren().size());
            return true;
        });
    }

    /// An entrance interrupted by a showing listener cannot resume after that listener reopens the dialog.
    @Test
    void showingListenerCanReplacePresentation() throws Exception {
        onFx(() -> {
            StackPane host = host();
            M3Dialog dialog = new M3Dialog();
            dialog.setAnimated(false);
            AtomicInteger shown = new AtomicInteger();
            AtomicInteger openings = new AtomicInteger();
            dialog.setOnShown(event -> shown.incrementAndGet());
            dialog.showingProperty().addListener((observable, oldValue, newValue) -> {
                if (newValue && openings.incrementAndGet() == 1) {
                    dialog.close();
                    dialog.show(host);
                }
            });
            dialog.show(host);
            assertTrue(dialog.isShowing());
            assertEquals(2, openings.get());
            assertEquals(1, shown.get());
            assertEquals(1, host.getChildren().size());
            return true;
        });
    }

    /// Cleanup must not restore old focus over the focus selected by a replacement presentation.
    @Test
    void hiddenStateListenerPreservesReplacementFocus() throws Exception {
        onFx(() -> {
            StackPane host = host();
            Button background = new Button("Background");
            host.getChildren().add(background);
            background.requestFocus();
            M3Dialog dialog = new M3Dialog();
            dialog.setAnimated(false);
            TextField input = new TextField();
            dialog.setContent(input);
            dialog.setOnShown(event -> {
                // The ScrollPane skin must attach its content before it can receive focus.
                host.applyCss();
                host.layout();
                input.requestFocus();
            });
            dialog.show(host);
            AtomicInteger closures = new AtomicInteger();
            dialog.showingProperty().addListener((observable, oldValue, newValue) -> {
                if (!newValue && closures.incrementAndGet() == 1)
                    dialog.show(host);
            });
            dialog.close();
            assertTrue(dialog.isShowing());
            assertSame(input, host.getScene().getFocusOwner());
            return true;
        });
    }

    /// Keeps Tab and Enter inside the dialog and lets content consume Escape before dismissal.
    @Test
    void dialogKeyboardIsolation() throws Exception {
        onFx(() -> {
            StackPane host = host();
            AtomicInteger backgroundActions = new AtomicInteger();
            Button background = new Button("Background default");
            background.setDefaultButton(true);
            background.setOnAction(event -> backgroundActions.incrementAndGet());
            host.getChildren().add(background);
            M3Dialog dialog = new M3Dialog();
            dialog.setAnimated(false);
            Button first = new Button("First");
            Button last = new Button("Last");
            dialog.getActions().addAll(first, last);
            dialog.show(host);
            host.applyCss();
            host.layout();
            key(dialog, KeyCode.ENTER, false);
            assertEquals(0, backgroundActions.get());
            last.requestFocus();
            key(last, KeyCode.TAB, false);
            assertSame(first, host.getScene().getFocusOwner());
            key(first, KeyCode.TAB, true);
            assertSame(last, host.getScene().getFocusOwner());
            last.setOnKeyPressed(Event::consume);
            key(last, KeyCode.ESCAPE, false);
            assertTrue(dialog.isShowing());
            last.setOnKeyPressed(null);
            dialog.setCloseOnEscape(false);
            key(last, KeyCode.ESCAPE, false);
            assertTrue(dialog.isShowing());
            dialog.setCloseOnEscape(true);
            key(last, KeyCode.ESCAPE, false);
            assertFalse(dialog.isShowing());
            return true;
        });
    }

    /// Enforces one modal per scene and releases the reservation after external layer removal.
    @Test
    void dialogHostRemoval() throws Exception {
        onFx(() -> {
            StackPane host = host();
            M3Dialog first = new M3Dialog();
            M3Dialog second = new M3Dialog();
            first.setAnimated(false);
            second.setAnimated(false);
            first.show(host);
            assertThrows(IllegalStateException.class, () -> second.show(host));
            host.getChildren().clear();
            assertFalse(first.isShowing());
            assertNull(first.getParent());
            second.show(host);
            windows.get(0).hide();
            assertFalse(second.isShowing());
            return true;
        });
    }

    /// Preserves FIFO ordering, supports pending cancellation and dismisses before invoking an action.
    @Test
    void snackbarQueueAndAction() throws Exception {
        onFx(() -> {
            StackPane host = host();
            M3Snackbar first = new M3Snackbar("First");
            M3Snackbar pending = new M3Snackbar("Pending");
            M3Snackbar last = new M3Snackbar("Last");
            first.setAnimated(false);
            last.setAnimated(false);
            first.setActionText("Undo");
            first.show(host);
            pending.show(host);
            last.show(host);
            assertTrue(first.isShowing());
            assertFalse(last.isShowing());
            assertTrue(last.isQueued());
            assertThrows(IllegalStateException.class, () -> first.show(host));
            pending.dismiss();
            AtomicInteger actions = new AtomicInteger();
            first.setAction(() -> {
                assertFalse(first.isQueued());
                assertTrue(last.isShowing());
                actions.incrementAndGet();
            });
            host.applyCss();
            ((Button) first.lookup(".m3-snackbar-action")).fire();
            assertEquals(1, actions.get());
            assertFalse(pending.isQueued());
            last.dismiss();
            assertTrue(host.getChildren().isEmpty());
            first.show(host);
            assertTrue(first.isShowing());
            return true;
        });
    }

    /// Starts automatic dismissal after a persistent message loses its action affordance.
    @Test
    void snackbarTimeoutAndPersistentAction() throws Exception {
        CountDownLatch hidden = new CountDownLatch(1);
        CountDownLatch remained = new CountDownLatch(1);
        M3Snackbar snackbar = onFx(() -> {
            StackPane host = host();
            M3Snackbar message = new M3Snackbar("Saved");
            message.setAnimated(false);
            message.setDuration(Duration.millis(60));
            message.setActionText("Undo");
            message.showingProperty().addListener((observable, oldValue, newValue) -> {
                if (!newValue)
                    hidden.countDown();
            });
            message.show(host);
            PauseTransition pause = new PauseTransition(Duration.millis(180));
            pause.setOnFinished(event -> remained.countDown());
            pause.play();
            return message;
        });
        assertTrue(remained.await(5, TimeUnit.SECONDS));
        onFx(() -> {
            assertTrue(snackbar.isShowing());
            snackbar.setActionText("");
            return true;
        });
        assertTrue(hidden.await(5, TimeUnit.SECONDS));
        assertFalse(onFx(snackbar::isQueued));
    }

    /// Discards both current and pending entries on window hiding, including active entrance motion.
    @Test
    void snackbarWindowCleanupAndReuse() throws Exception {
        onFx(() -> {
            StackPane host = host();
            M3Snackbar current = new M3Snackbar("Current");
            M3Snackbar pending = new M3Snackbar("Pending");
            current.setAnimated(true);
            current.show(host);
            pending.show(host);
            windows.get(0).hide();
            assertFalse(current.isShowing());
            assertFalse(current.isQueued());
            assertFalse(pending.isQueued());
            assertTrue(host.getChildren().isEmpty());
            windows.get(0).show();
            pending.show(host);
            assertTrue(pending.isShowing());
            return true;
        });
    }

    /// Rejects invalid message and timer values before modifying valid state.
    @Test
    void snackbarValidation() throws Exception {
        onFx(() -> {
            assertThrows(IllegalArgumentException.class, () -> new M3Snackbar("  "));
            M3Snackbar message = new M3Snackbar("Valid");
            assertThrows(IllegalArgumentException.class, () -> message.setText(""));
            assertEquals("Valid", message.getText());
            assertThrows(IllegalArgumentException.class, () -> message.setDuration(Duration.UNKNOWN));
            assertThrows(IllegalArgumentException.class, () -> message.setDuration(Duration.ZERO));
            assertThrows(IllegalArgumentException.class, () -> message.setDuration(Duration.millis(-1)));
            assertEquals(Duration.seconds(4), message.getDuration());
            return true;
        });
    }

    /// Checks Material colors, wrapping actions and scrollable dialog content at constrained sizes.
    @Test
    void responsiveLayoutAndTheme() throws Exception {
        onFx(() -> {
            StackPane host = host();
            M3Snackbar snackbar = new M3Snackbar("A supporting message that wraps on a narrow window");
            snackbar.setActionText("A longer action label");
            snackbar.setCloseButtonVisible(true);
            snackbar.setAnimated(false);
            snackbar.show(host);
            host.applyCss();
            snackbar.resize(240, snackbar.prefHeight(240));
            snackbar.layout();
            Node text = snackbar.lookup(".m3-snackbar-text");
            Node action = snackbar.lookup(".m3-snackbar-action");
            assertTrue(action.getBoundsInParent().getMinY() >= text.getBoundsInParent().getMaxY());
            assertTrue(action.getBoundsInParent().getMaxX() <= snackbar.getWidth());
            assertEquals(Color.web("#322f35"), snackbar.getBackground().getFills().get(0).getFill());
            host.resize(280, 400);
            host.layout();
            assertEquals(snackbar.prefHeight(snackbar.getWidth()), snackbar.getHeight(), 1);
            M3Dialog dialog = new M3Dialog();
            dialog.setAnimated(false);
            dialog.setHeaderText("A headline");
            dialog.setContent(new Label("Long content\n".repeat(100)));
            dialog.getActions().add(new Button("Confirm"));
            dialog.show(host);
            host.applyCss();
            dialog.resize(300, 220);
            dialog.layout();
            ScrollPane scroll = (ScrollPane) dialog.lookup(".m3-dialog-content");
            assertTrue(scroll.getHeight() < scroll.getContent().prefHeight(-1));
            assertEquals(28, dialog.getBackground().getFills().get(0).getRadii().getTopLeftHorizontalRadius());
            assertTrue(dialog.lookup(".m3-dialog-actions").getBoundsInParent().getMaxY() <= 220);
            return true;
        });
    }

    /// Delivers Enter to custom inputs before considering default dialog actions.
    @Test
    void customContentHandlesEnter() throws Exception {
        onFx(() -> {
            StackPane host = host();
            M3Dialog dialog = new M3Dialog();
            dialog.setAnimated(false);
            TextField input = new TextField();
            AtomicInteger submitted = new AtomicInteger();
            input.setOnAction(event -> {
                submitted.incrementAndGet();
                event.consume();
            });
            dialog.setContent(input);
            dialog.show(host);
            host.applyCss();
            input.requestFocus();
            key(input, KeyCode.ENTER, false);
            assertEquals(1, submitted.get());
            assertTrue(dialog.isShowing());
            return true;
        });
    }

    /// Only primary scrim activation dismisses, and the dismissal option remains live while showing.
    @Test
    void scrimDismissal() throws Exception {
        onFx(() -> {
            StackPane host = host();
            M3Dialog dialog = new M3Dialog();
            dialog.setAnimated(false);
            dialog.show(host);
            Node overlay = dialog.getParent();
            click(overlay, MouseButton.SECONDARY);
            assertTrue(dialog.isShowing());
            dialog.setDismissOnScrimClick(false);
            click(overlay, MouseButton.PRIMARY);
            assertTrue(dialog.isShowing());
            dialog.setDismissOnScrimClick(true);
            click(dialog, MouseButton.PRIMARY);
            assertTrue(dialog.isShowing());
            click(overlay, MouseButton.PRIMARY);
            assertFalse(dialog.isShowing());
            return true;
        });
    }

    /// Blocks repeated button callbacks while an accepted animated dismissal is in progress.
    @Test
    void animatedExitBlocksRepeatedActions() throws Exception {
        onFx(() -> {
            StackPane host = host();
            M3Dialog dialog = new M3Dialog();
            dialog.setAnimated(false);
            Button action = new Button("Apply");
            AtomicInteger invocations = new AtomicInteger();
            action.setOnAction(event -> invocations.incrementAndGet());
            dialog.getActions().add(action);
            dialog.show(host);
            dialog.setAnimated(true);
            action.fire();
            assertTrue(dialog.isShowing());
            action.fire();
            assertEquals(1, invocations.get());
            dialog.close();
            assertFalse(dialog.isShowing());
            return true;
        });
    }

    /// Sends a pointer activation through the target's event chain.
    private static void click(Node target, MouseButton button) {
        Event.fireEvent(target, new MouseEvent(MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, button, 1,
                false, false, false, false, false, false, false, false, false, true, null));
    }

    /// Sends one keyboard press through the real scene dispatch chain.
    private static void key(Node target, KeyCode code, boolean shift) {
        Event.fireEvent(target, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, shift, false, false, false));
    }

    /// Executes an operation on the toolkit thread and propagates assertion failures to JUnit.
    private static <T> T onFx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }
}
