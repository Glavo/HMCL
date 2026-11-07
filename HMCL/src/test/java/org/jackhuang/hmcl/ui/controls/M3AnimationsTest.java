// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.controls;

import com.jfoenix.controls.JFXListView;
import javafx.animation.Animation;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Shape;
import org.jackhuang.hmcl.ui.construct.MDListCell;
import org.jackhuang.hmcl.ui.controls.skin.M3SwitchSkin;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.lang.reflect.Field;
import java.util.Objects;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/// Exercises nested subtree scopes, deferred CSS, local bindings, and reusable list-cell content.
@NotNullByDefault
@EnabledIf("org.jackhuang.hmcl.JavaFXLauncher#isStarted")
public final class M3AnimationsTest {

    /// Queries do not allocate node properties, and independent nested scopes remain balanced.
    @Test
    void nestingAndAllocation() throws Exception {
        onFxThread(() -> {
            Pane child = new Pane();
            Pane root = new Pane(child);
            assertFalse(M3Animations.isSuspended(child));
            assertFalse(root.hasProperties());
            assertFalse(child.hasProperties());
            assertThrows(IllegalStateException.class, () -> M3Animations.resume(child));
            assertFalse(child.hasProperties());

            M3Animations.suspend(root);
            M3Animations.suspend(root);
            M3Animations.suspend(child);
            M3Animations.resume(root);
            assertTrue(M3Animations.isSuspended(root));
            M3Animations.resume(root);
            assertFalse(M3Animations.isSuspended(root));
            assertTrue(M3Animations.isSuspended(child));
            M3Animations.resume(child);
            assertFalse(M3Animations.isSuspended(child));
        });
    }

    /// Suppressed changes remain immediate after CSS runs later, without changing a bound local policy.
    @Test
    void deferredCssAndLocalBinding() throws Exception {
        onFxThread(() -> {
            M3Switch control = new M3Switch();
            SimpleBooleanProperty localPolicy = new SimpleBooleanProperty(false);
            control.disableAnimationProperty().bind(localPolicy);
            VBox root = scene(control);
            AtomicInteger actions = new AtomicInteger();
            control.setOnAction(event -> actions.incrementAndGet());

            M3Animations.suspend(root);
            try {
                control.setSelected(true);
                assertEquals(36, center(control));
            } finally {
                M3Animations.resume(root);
            }
            layout(root);
            assertEquals(Color.web("#4352a5"), part(control, "track").getFill());
            assertNull(animation(control, "geometryTransition"));
            assertNull(animation(control, "colorTransition"));
            assertTrue(control.disableAnimationProperty().isBound());
            assertFalse(control.isDisableAnimation());
            assertEquals(0, actions.get());

            control.setSelected(false);
            layout(root);
            assertEquals(Animation.Status.RUNNING, Objects.requireNonNull(animation(control, "geometryTransition")).getStatus());
            assertEquals(Animation.Status.RUNNING, Objects.requireNonNull(animation(control, "colorTransition")).getStatus());
            M3Animations.suspend(root);
            M3Animations.resume(root);
            localPolicy.set(true);
            control.setSelected(true);
            assertEquals(36, center(control));
            assertStopped(control);
        });
    }

    /// Entry settles already running colors and geometry; subsequent changes are not replayed on exit.
    @Test
    void settlesActiveAndPendingTransitions() throws Exception {
        onFxThread(() -> {
            M3Switch control = new M3Switch();
            control.setDisableAnimation(false);
            VBox root = scene(control);
            control.setSelected(true);
            layout(root);
            assertEquals(Animation.Status.RUNNING, Objects.requireNonNull(animation(control, "colorTransition")).getStatus());
            M3Animations.suspend(root);
            try {
                assertStopped(control);
                assertEquals(36, center(control));
                assertEquals(Color.web("#4352a5"), part(control, "track").getFill());
                control.setSelected(false);
            } finally {
                M3Animations.resume(root);
            }
            layout(root);
            assertEquals(16, center(control));
            assertEquals(Color.web("#e3e1e9"), part(control, "track").getFill());
            assertStopped(control);

            control.setSelected(true);
            // Enter and leave before CSS has resolved the new selected colors.
            M3Animations.suspend(root);
            M3Animations.resume(root);
            layout(root);
            assertEquals(Color.web("#4352a5"), part(control, "track").getFill());
            assertStopped(control);
        });
    }

    /// New children and replacement skins observe the current ancestor scope without tree listeners.
    @Test
    void newChildrenAndReplacementSkins() throws Exception {
        onFxThread(() -> {
            VBox root = scene();
            M3Switch control = new M3Switch();
            control.setDisableAnimation(false);
            M3Animations.suspend(root);
            try {
                root.getChildren().add(control);
                assertNull(control.getSkin());
                control.setSelected(true);
                layout(root);
                assertEquals(36, center(control));
                M3SwitchSkin previous = (M3SwitchSkin) Objects.requireNonNull(control.getSkin());
                control.setSkin(null);
                control.setSkin(new M3SwitchSkin(control));
                control.setSelected(false);
                previous.finishAnimations();
            } finally {
                M3Animations.resume(root);
            }
            layout(root);
            assertEquals(16, center(control));
            assertNull(animation(control, "geometryTransition"));
            assertNull(animation(control, "colorTransition"));
        });
    }

    /// Exit settles an active node attached while suspended even when no pulse ran within the scope.
    @Test
    void incomingAnimationsBeforeNextPulse() throws Exception {
        onFxThread(() -> {
            M3Switch control = new M3Switch();
            control.setDisableAnimation(false);
            Pane source = new Pane(control);
            Pane target = new Pane();
            VBox root = scene(source, target);
            control.setSelected(true);
            layout(root);
            M3Animations.suspend(target);
            try {
                source.getChildren().remove(control);
                target.getChildren().add(control);
                assertTrue(M3Animations.isSuspended(control));
            } finally {
                M3Animations.resume(target);
            }
            layout(root);
            assertEquals(36, center(control));
            assertStopped(control);
        });
    }

    /// The shared cell update path suppresses detached content and unwinds its scope after exceptions.
    @Test
    void reusableCellAndExceptionalRefresh() throws Exception {
        onFxThread(() -> {
            SwitchCell cell = new SwitchCell();
            VBox root = scene(cell);
            cell.refresh(false);
            layout(root);
            cell.refresh(true);
            layout(root);
            assertEquals(36, center(cell.control));
            assertNull(animation(cell.control, "geometryTransition"));
            assertFalse(M3Animations.isSuspended(cell.control));
            assertFalse(cell.control.isDisableAnimation());
            cell.refresh(null);
            assertNull(cell.getGraphic());
            cell.refresh(false);
            layout(root);
            assertEquals(16, center(cell.control));
            assertNull(animation(cell.control, "geometryTransition"));

            cell.failUpdate = true;
            assertThrows(IllegalArgumentException.class, () -> cell.refresh(true));
            assertFalse(M3Animations.isSuspended(cell.control));
            cell.failUpdate = false;
            cell.refresh(false);
            layout(root);
            cell.control.fire();
            assertTrue(cell.control.isSelected());
            assertEquals(Animation.Status.RUNNING,
                    Objects.requireNonNull(animation(cell.control, "geometryTransition")).getStatus());
            M3Animations.suspend(root);
            M3Animations.resume(root);
        });
    }

    /// Installs the application palette and component styles on a test subtree.
    private static VBox scene(javafx.scene.Node... children) {
        VBox root = new VBox(children);
        Scene scene = new Scene(root, 300, 180);
        scene.getStylesheets().addAll(
                Objects.requireNonNull(M3AnimationsTest.class.getResource("/assets/css/blue.css")).toExternalForm(),
                Objects.requireNonNull(M3AnimationsTest.class.getResource("/assets/css/root.css")).toExternalForm());
        layout(root);
        return root;
    }

    /// Resolves deferred CSS and lays out the subtree synchronously.
    private static void layout(Region root) {
        root.applyCss();
        root.layout();
    }

    /// Returns a required rendered switch part.
    private static Shape part(M3Switch control, String name) {
        return (Shape) Objects.requireNonNull(control.lookup(".switch-" + name));
    }

    /// Returns the logical handle center in unscaled artwork coordinates.
    private static double center(M3Switch control) {
        return part(control, "handle").getBoundsInParent().getCenterX();
    }

    /// Checks that neither transition remains running after a scope has settled the switch.
    private static void assertStopped(M3Switch control) {
        for (String name : new String[]{"geometryTransition", "colorTransition"}) {
            @Nullable Animation animation = animation(control, name);
            if (animation != null)
                assertEquals(Animation.Status.STOPPED, animation.getStatus(), name);
        }
    }

    /// Reads existing transitions without allocating them or advancing the animation clock.
    private static @Nullable Animation animation(M3Switch control, String name) {
        try {
            Field field = M3SwitchSkin.class.getDeclaredField(name);
            field.setAccessible(true);
            return (Animation) field.get(control.getSkin());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    /// Executes assertions on the shared application thread and propagates failures to JUnit.
    private static void onFxThread(Runnable action) throws Exception {
        FutureTask<@Nullable Void> task = new FutureTask<>(action, null);
        Platform.runLater(task);
        task.get(10, TimeUnit.SECONDS);
    }

    /// A reusable cell that exposes its refresh path without virtual-flow timing dependencies.
    @NotNullByDefault
    private static final class SwitchCell extends MDListCell<Boolean> {

        /// Creates persistent content with local animation explicitly enabled for this test.
        private SwitchCell() {
            super(new JFXListView<>());
            control.setDisableAnimation(false);
            getContainer().getChildren().setAll(control);
        }

        /// The persistent control reused across items.
        private final M3Switch control = new M3Switch();

        /// Whether the next content update should fail.
        private boolean failUpdate;

        /// Invokes the real cell update path, treating null as an empty row.
        private void refresh(@Nullable Boolean item) {
            updateItem(item, item == null);
        }

        /// Applies new state under the content-root scope, optionally simulating a failed refresh.
        @Override
        protected void updateControl(@Nullable Boolean item, boolean empty) {
            assertTrue(M3Animations.isSuspended(control));
            control.setSelected(Boolean.TRUE.equals(item));
            if (failUpdate)
                throw new IllegalArgumentException("Simulated cell refresh failure");
        }
    }
}
