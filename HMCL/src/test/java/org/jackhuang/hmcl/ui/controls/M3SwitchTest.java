// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.controls;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.event.EventType;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Point2D;
import javafx.scene.AccessibleAction;
import javafx.scene.AccessibleAttribute;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.SkinBase;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Shape;
import org.jackhuang.hmcl.ui.construct.LineInheritableSwitch;
import org.jackhuang.hmcl.ui.construct.LineSwitch;
import org.jackhuang.hmcl.ui.controls.skin.M3SwitchSkin;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;


import static org.junit.jupiter.api.Assertions.*;

/// Exercises the switch through a live JavaFX scene, including CSS and inherited behavior.
@NotNullByDefault
@EnabledIf("org.jackhuang.hmcl.JavaFXLauncher#isStarted")
public final class M3SwitchTest {

    /// Subtree suppression settles transitions without interrupting pointer-driven dragging.
    @Test
    void draggingWhileAnimationsAreSuspended() throws Exception {
        onFxThread(() -> {
            M3Switch control = new M3Switch();
            VBox root = scene(control);
            AtomicInteger actions = new AtomicInteger();
            control.setOnAction(event -> actions.incrementAndGet());
            M3Animations.suspend(root);
            try {
                mouse(control, MouseEvent.MOUSE_PRESSED, 16, true);
                mouse(control, MouseEvent.MOUSE_DRAGGED, 36, true);
                assertEquals(36, part(control, "handle").getBoundsInParent().getCenterX());
                assertFalse(control.isSelected());
                M3Animations.suspend(control);
                M3Animations.resume(control);
                assertEquals(36, part(control, "handle").getBoundsInParent().getCenterX());
                mouse(control, MouseEvent.MOUSE_RELEASED, 36, false);
                assertTrue(control.isSelected());
                assertEquals(1, actions.get());
            } finally {
                M3Animations.resume(root);
            }
        });
    }

    /// Skin installation and input do not force selection, activation, size, or optional property allocation.
    @Test
    void lazyPropertiesAndSelectionBinding() throws Exception {
        onFxThread(() -> {
            M3Switch control = new M3Switch();
            control.setSize(32);
            control.setSelected(true);
            VBox root = scene(control);
            control.arm();
            assertEquals(28, part(control, "handle").getLayoutBounds().getWidth());
            control.disarm();
            control.fire();
            assertFalse(control.isSelected());
            for (String name : List.of("selected", "armed", "size", "showSelectedIcon", "showUnselectedIcon", "onAction"))
                assertNull(field(control, name), name + " must remain unallocated");
            control.arm();
            assertTrue(control.armedProperty().get());
            AtomicInteger armChanges = new AtomicInteger();
            control.armedProperty().addListener(observable -> armChanges.incrementAndGet());
            control.disarm();
            control.arm();
            assertEquals(2, armChanges.get());
            control.disarm();
            control.setSelected(true);
            assertTrue(control.selectedProperty().get());
            SimpleBooleanProperty selection = new SimpleBooleanProperty(true);
            control.selectedProperty().bind(selection);
            selection.set(false);
            selection.set(true);
            assertEquals(36, part(control, "handle").getBoundsInParent().getCenterX());
            control.fire();
            assertTrue(control.isSelected(), "Bound selection must still ignore activation");
            control.setSkin(null);
            control.setSkin(new M3SwitchSkin(control));
            layout(root);
            selection.set(false);
            assertEquals(16, part(control, "handle").getBoundsInParent().getCenterX());
        });
    }


    /// Loads the switch artwork styles without the application component stylesheet.
    @Test
    void paletteOnlyStyling() throws Exception {
        onFxThread(() -> {
            M3Switch control = new M3Switch();
            control.setSelected(true);
            VBox root = new VBox(control);
            Scene scene = new Scene(root);
            scene.getStylesheets().add(Objects.requireNonNull(
                    M3SwitchTest.class.getResource("/assets/css/blue.css")).toExternalForm());
            layout(root);
            assertEquals(Color.web("#4352a5"), part(control, "track").getFill());
            assertEquals(Color.WHITE, part(control, "handle").getFill());
            assertEquals(48, control.prefHeight(-1));
        });
    }

    /// Preserves compact row height and overrides the inherited value once despite synchronous binder refresh.
    @Test
    void inheritableRowDragging() throws Exception {
        onFxThread(() -> {
            LineInheritableSwitch row = new LineInheritableSwitch();
            row.setTitle("Setting");
            row.overriddenProperty().addListener(observable -> row.setEffectiveValue(row.getRawValue()));
            row.rawValueProperty().addListener(observable -> row.setEffectiveValue(row.getRawValue()));
            AtomicInteger actions = new AtomicInteger();
            row.addEventHandler(javafx.event.ActionEvent.ACTION, event -> actions.incrementAndGet());
            VBox root = new VBox(row);
            applyStylesheets(new Scene(root, 300, 100));
            layout(root);
            M3Switch control = (M3Switch) Objects.requireNonNull(row.lookup(".m3-switch"));
            assertEquals(30, control.getHeight());
            assertEquals(50, row.getHeight());
            mouse(control, MouseEvent.MOUSE_PRESSED, 16, true);
            mouse(control, MouseEvent.MOUSE_DRAGGED, 36, true);
            mouse(control, MouseEvent.MOUSE_RELEASED, 36, false);
            mouse(control, MouseEvent.MOUSE_CLICKED, 36, false);
            assertTrue(control.isSelected());
            assertEquals(1, actions.get());
            assertTrue(row.isOverridden());
            assertTrue(row.getRawValue());
            assertTrue(row.isEffectiveValue());
        });
    }

    /// Preserves grab offsets, ignores secondary input, and commits only changed drag endpoints at either scale.
    @ParameterizedTest
    @CsvSource({"false, 32, 16", "true, 24, 4"})
    void handleDragging(boolean rtl, double size, double pressX) throws Exception {
        onFxThread(() -> {
            M3Switch control = new M3Switch();
            control.setStyle("-m3-size: " + size + ";");
            VBox root = scene(control);
            root.setNodeOrientation(rtl ? NodeOrientation.RIGHT_TO_LEFT : NodeOrientation.LEFT_TO_RIGHT);
            layout(root);
            AtomicInteger actions = new AtomicInteger();
            control.setOnAction(event -> actions.incrementAndGet());
            Shape handle = part(control, "handle");
            double initialX = handle.localToScene(handle.getLayoutBounds()).getCenterX();
            mouse(control, MouseEvent.MOUSE_PRESSED, pressX, true);
            mouse(control, MouseEvent.MOUSE_DRAGGED, pressX + 12, true);
            layout(root);
            assertFalse(control.isSelected());
            double draggedX = handle.localToScene(handle.getLayoutBounds()).getCenterX();
            assertEquals((rtl ? -1 : 1) * 12 * size / 32, draggedX - initialX, 0.001,
                    "The handle must follow the pointer without snapping to its position");
            mouse(control, MouseEvent.MOUSE_PRESSED, pressX + 12, MouseButton.SECONDARY, true);
            mouse(control, MouseEvent.MOUSE_RELEASED, pressX + 12, MouseButton.SECONDARY, true);
            mouse(control, MouseEvent.MOUSE_DRAGGED, pressX + 8, true);
            mouse(control, MouseEvent.MOUSE_RELEASED, pressX + 20, false);
            mouse(control, MouseEvent.MOUSE_CLICKED, pressX + 20, false);
            layout(root);
            assertTrue(control.isSelected(), "Commit the release position, preserving the original grab offset");
            assertEquals(1, actions.get());
            assertFalse(control.isArmed());

            mouse(control, MouseEvent.MOUSE_PRESSED, 36, true);
            mouse(control, MouseEvent.MOUSE_DRAGGED, 30, true);
            mouse(control, MouseEvent.MOUSE_RELEASED, 30, false);
            mouse(control, MouseEvent.MOUSE_CLICKED, 30, false);
            assertTrue(control.isSelected());
            assertEquals(1, actions.get(), "Dragging back to the selected endpoint must not fire an action");
            layout(root);
            mouse(control, MouseEvent.MOUSE_PRESSED, 36, true);
            mouse(control, MouseEvent.MOUSE_DRAGGED, 30, true);
            mouse(control, MouseEvent.MOUSE_RELEASED, -10, false);
            mouse(control, MouseEvent.MOUSE_CLICKED, -10, false);
            assertFalse(control.isSelected());
            assertEquals(2, actions.get());
        });
    }

    /// A canceled drag must not become a click after re-enabling and re-entering the control.
    @Test
    void canceledDragRelease() throws Exception {
        onFxThread(() -> {
            M3Switch control = new M3Switch();
            scene(control);
            AtomicInteger actions = new AtomicInteger();
            control.setOnAction(event -> actions.incrementAndGet());
            mouse(control, MouseEvent.MOUSE_PRESSED, 16, true);
            mouse(control, MouseEvent.MOUSE_DRAGGED, 36, true);
            control.setDisable(true);
            control.setDisable(false);
            mouse(control, MouseEvent.MOUSE_EXITED, 60, true);
            mouse(control, MouseEvent.MOUSE_ENTERED, 36, true);
            mouse(control, MouseEvent.MOUSE_RELEASED, 36, false);
            mouse(control, MouseEvent.MOUSE_CLICKED, 36, false);
            assertFalse(control.isSelected());
            assertFalse(control.isArmed());
            assertEquals(0, actions.get());
        });
    }

    /// Scales artwork and its target through CSS, respects bindings, and falls back for invalid sizes.
    @Test
    void styleableSize() throws Exception {
        onFxThread(() -> {
            M3Switch control = new M3Switch();
            VBox root = scene(control);
            assertEquals(52, control.prefWidth(-1));
            assertEquals(48, control.prefHeight(-1));
            control.setStyle("-m3-size: 24px;");
            layout(root);
            assertEquals(39, control.prefWidth(-1));
            assertEquals(36, control.prefHeight(-1));
            Shape track = part(control, "track");
            assertEquals(39, track.localToScene(track.getLayoutBounds()).getWidth(), 0.001);
            assertEquals(24, track.localToScene(track.getLayoutBounds()).getHeight(), 0.001);

            SimpleDoubleProperty boundSize = new SimpleDoubleProperty(40);
            control.sizeProperty().bind(boundSize);
            control.setStyle("-m3-size: 28px;");
            layout(root);
            assertEquals(65, control.prefWidth(-1));
            boundSize.set(Double.NaN);
            layout(root);
            assertEquals(52, control.prefWidth(-1));
            assertEquals(48, control.prefHeight(-1));
        });
    }

    /// Routes Space and accessibility to the switch while leaving Enter to the scene's default button.
    @Test
    void keyboardAndAccessibility() throws Exception {
        onFxThread(() -> {
            M3Switch control = new M3Switch();
            control.setAccessibleText("Wi-Fi");
            VBox root = scene(control);
            Button confirm = new Button("Confirm");
            confirm.setDefaultButton(true);
            AtomicInteger confirmations = new AtomicInteger();
            confirm.setOnAction(event -> confirmations.incrementAndGet());
            root.getChildren().add(confirm);
            layout(root);
            AtomicInteger actions = new AtomicInteger();
            control.setOnAction(event -> actions.incrementAndGet());
            key(control, KeyEvent.KEY_PRESSED, KeyCode.SPACE);
            key(control, KeyEvent.KEY_RELEASED, KeyCode.SPACE);
            assertTrue(control.isSelected());
            assertEquals(1, actions.get());
            assertEquals(AccessibleRole.TOGGLE_BUTTON, control.getAccessibleRole());
            assertEquals(true, control.queryAccessibleAttribute(AccessibleAttribute.SELECTED));
            assertEquals("Wi-Fi", control.queryAccessibleAttribute(AccessibleAttribute.TEXT));
            control.executeAccessibleAction(AccessibleAction.FIRE);
            assertFalse(control.isSelected());
            assertEquals(2, actions.get());
            key(control, KeyEvent.KEY_PRESSED, KeyCode.ENTER);
            key(control, KeyEvent.KEY_RELEASED, KeyCode.ENTER);
            assertEquals(1, confirmations.get(), "Enter must reach the default button");
            assertFalse(control.isSelected());
            assertEquals(2, actions.get());

            control.selectedProperty().bind(new SimpleBooleanProperty(false));
            key(control, KeyEvent.KEY_PRESSED, KeyCode.ENTER);
            key(control, KeyEvent.KEY_RELEASED, KeyCode.ENTER);
            assertEquals(2, confirmations.get(), "Bound selection must not swallow Enter");
            control.selectedProperty().unbind();
            control.setDisable(true);
            control.fire();
            control.executeAccessibleAction(AccessibleAction.FIRE);
            key(control, KeyEvent.KEY_PRESSED, KeyCode.ENTER);
            key(control, KeyEvent.KEY_RELEASED, KeyCode.ENTER);
            assertEquals(3, confirmations.get(), "A disabled switch must not swallow Enter");
            assertFalse(control.isSelected());
            assertEquals(2, actions.get());
        });
    }

    /// Keeps the settings row compact and emits one action for row, pointer, and keyboard activation.
    @Test
    void rowActionDispatch() throws Exception {
        onFxThread(() -> {
            LineSwitch row = new LineSwitch();
            row.setTitle("Setting");
            VBox root = new VBox(row);
            applyStylesheets(new Scene(root, 300, 100));
            layout(root);
            M3Switch control = (M3Switch) Objects.requireNonNull(row.lookup(".m3-switch"));
            assertEquals(30, control.getHeight());
            assertEquals(50, row.getHeight());
            AtomicInteger rowActions = new AtomicInteger();
            AtomicInteger parentActions = new AtomicInteger();
            row.addEventHandler(javafx.event.ActionEvent.ACTION, event -> rowActions.incrementAndGet());
            root.addEventHandler(javafx.event.ActionEvent.ACTION, event -> parentActions.incrementAndGet());

            row.fire();
            assertTrue(row.isSelected());
            assertEquals(1, rowActions.get());
            assertEquals(1, parentActions.get());

            mouse(control, MouseEvent.MOUSE_PRESSED, 36, true);
            mouse(control, MouseEvent.MOUSE_DRAGGED, 37, true);
            mouse(control, MouseEvent.MOUSE_RELEASED, 37, false);
            mouse(control, MouseEvent.MOUSE_CLICKED, 37, false);
            assertFalse(row.isSelected());
            assertEquals(2, rowActions.get());
            assertEquals(2, parentActions.get());

            key(control, KeyEvent.KEY_PRESSED, KeyCode.SPACE);
            key(control, KeyEvent.KEY_RELEASED, KeyCode.SPACE);
            assertTrue(row.isSelected());
            assertEquals(3, rowActions.get());
            assertEquals(3, parentActions.get());
            root.setDisable(true);
            row.fire();
            assertTrue(row.isSelected(), "A disabled row must not write the bound setting");
            assertEquals(3, rowActions.get());
            assertEquals(3, parentActions.get());
        });
    }

    /// Rejects modified input and keeps activation state consistent through cancellation.
    @Test
    void inputLifecycle() throws Exception {
        onFxThread(() -> {
            var control = new M3Switch() {
                /// Exercises focus cancellation without depending on native window activation.
                void changeFocus(boolean focused) {
                    setFocused(focused);
                }
            };
            VBox root = scene(control);
            AtomicInteger actions = new AtomicInteger();
            control.setOnAction(event -> actions.incrementAndGet());
            AtomicInteger passedKeys = new AtomicInteger();
            root.addEventHandler(KeyEvent.ANY, event -> passedKeys.incrementAndGet());
            for (KeyCode modifier : List.of(KeyCode.SHIFT, KeyCode.CONTROL, KeyCode.ALT, KeyCode.META)) {
                mouse(control, MouseEvent.MOUSE_PRESSED, 16, MouseButton.PRIMARY, true, modifier);
                mouse(control, MouseEvent.MOUSE_RELEASED, 16, false);
                mouse(control, MouseEvent.MOUSE_CLICKED, 16, false);
                assertFalse(control.isSelected(), "Modified clicks must not change selection: " + modifier);
                key(control, KeyEvent.KEY_PRESSED, KeyCode.SPACE, modifier);
                key(control, KeyEvent.KEY_RELEASED, KeyCode.SPACE, modifier);
                assertFalse(control.isSelected(), "Modified Space must not change selection: " + modifier);
            }
            assertEquals(8, passedKeys.get(), "Unaccepted key combinations must propagate");
            assertEquals(0, actions.get());

            Point2D point = control.localToScene(16, 24);
            for (MouseButton other : List.of(MouseButton.MIDDLE, MouseButton.SECONDARY)) {
                control.fireEvent(new MouseEvent(MouseEvent.MOUSE_PRESSED, point.getX(), point.getY(),
                        point.getX(), point.getY(), MouseButton.PRIMARY, 1,
                        false, false, false, false, true, other == MouseButton.MIDDLE,
                        other == MouseButton.SECONDARY, false, false, false,
                        new PickResult(control, point.getX(), point.getY())));
                mouse(control, MouseEvent.MOUSE_RELEASED, 16, false);
                assertEquals(0, actions.get(), "Chorded mouse presses must not activate the switch");
            }

            key(control, KeyEvent.KEY_PRESSED, KeyCode.SPACE);
            key(control, KeyEvent.KEY_PRESSED, KeyCode.SPACE);
            mouse(control, MouseEvent.MOUSE_PRESSED, 16, true);
            mouse(control, MouseEvent.MOUSE_RELEASED, 16, false);
            assertTrue(control.isArmed());
            assertEquals(0, actions.get());
            key(control, KeyEvent.KEY_RELEASED, KeyCode.SPACE);
            key(control, KeyEvent.KEY_RELEASED, KeyCode.SPACE);
            assertEquals(1, actions.get());
            assertFalse(control.isArmed());

            key(control, KeyEvent.KEY_PRESSED, KeyCode.SPACE);
            control.setDisable(true);
            control.setDisable(false);
            key(control, KeyEvent.KEY_RELEASED, KeyCode.SPACE);
            key(control, KeyEvent.KEY_PRESSED, KeyCode.SPACE);
            key(control, KeyEvent.KEY_PRESSED, KeyCode.ESCAPE);
            key(control, KeyEvent.KEY_PRESSED, KeyCode.SPACE);
            key(control, KeyEvent.KEY_PRESSED, KeyCode.SPACE);
            key(control, KeyEvent.KEY_RELEASED, KeyCode.SPACE);
            assertEquals(1, actions.get(), "Auto-repeat must not reactivate a canceled key gesture");
            control.changeFocus(true);
            key(control, KeyEvent.KEY_PRESSED, KeyCode.SPACE);
            assertTrue(control.isArmed(), "Space must work again after the canceled key is released");
            control.changeFocus(false);
            key(control, KeyEvent.KEY_RELEASED, KeyCode.SPACE);
            assertFalse(control.isArmed());
            assertEquals(1, actions.get());

            mouse(control, MouseEvent.MOUSE_PRESSED, 16, true);
            mouse(control, MouseEvent.MOUSE_EXITED, 60, true);
            assertFalse(control.isArmed());
            mouse(control, MouseEvent.MOUSE_RELEASED, 60, false);
            assertEquals(1, actions.get());
            mouse(control, MouseEvent.MOUSE_PRESSED, 16, true);
            mouse(control, MouseEvent.MOUSE_EXITED, 60, true);
            mouse(control, MouseEvent.MOUSE_ENTERED, 16, true);
            assertTrue(control.isArmed());
            mouse(control, MouseEvent.MOUSE_RELEASED, 16, false);
            assertEquals(2, actions.get());

            key(control, KeyEvent.KEY_PRESSED, KeyCode.SPACE);
            control.setSkin(null);
            control.setSkin(new M3SwitchSkin(control));
            key(control, KeyEvent.KEY_RELEASED, KeyCode.SPACE);
            assertEquals(2, actions.get());
            assertFalse(control.isArmed());
            key(control, KeyEvent.KEY_PRESSED, KeyCode.SPACE);
            key(control, KeyEvent.KEY_RELEASED, KeyCode.SPACE);
            assertEquals(3, actions.get(), "The replacement skin must handle new input exactly once");

            SimpleBooleanProperty selected = new SimpleBooleanProperty(true);
            control.selectedProperty().bind(selected);
            control.fire();
            control.executeAccessibleAction(AccessibleAction.FIRE);
            key(control, KeyEvent.KEY_PRESSED, KeyCode.SPACE);
            key(control, KeyEvent.KEY_RELEASED, KeyCode.SPACE);
            mouse(control, MouseEvent.MOUSE_PRESSED, 16, true);
            mouse(control, MouseEvent.MOUSE_DRAGGED, 36, true);
            mouse(control, MouseEvent.MOUSE_RELEASED, 36, false);
            assertTrue(control.isSelected());
            assertFalse(control.isArmed());
            assertEquals(3, actions.get(), "Bound selection must ignore every activation path");
            selected.set(false);
            assertFalse(control.isSelected());
            assertEquals(false, control.queryAccessibleAttribute(AccessibleAttribute.SELECTED));
        });
    }

    /// Keeps bound icon options effective before skin creation and after replacement.
    @Test
    void optionUpdatesAcrossSkinReplacement() throws Exception {
        onFxThread(() -> {
            M3Switch control = new M3Switch();
            SimpleBooleanProperty selectedIcon = new SimpleBooleanProperty(false);
            control.showSelectedIconProperty().bind(selectedIcon);
            selectedIcon.set(true);
            control.setSelected(true);
            VBox root = scene(control);
            assertTrue(part(control, "icon").isVisible());
            selectedIcon.set(false);
            assertFalse(part(control, "icon").isVisible());

            Node oldArtwork = Objects.requireNonNull(control.lookup(".switch-artwork"));
            // JavaFX 17 ignores same-class replacements; another SkinBase exercises disposal on both versions.
            control.setSkin(new SkinBase<>(control) {});
            assertNull(oldArtwork.getParent(), "Replacing the skin must detach its artwork");
            selectedIcon.set(true);
            control.setSkin(new M3SwitchSkin(control));
            layout(root);
            assertEquals(1, control.lookupAll(".switch-artwork").size());
            assertTrue(part(control, "icon").isVisible());
            selectedIcon.set(false);
            assertFalse(part(control, "icon").isVisible());
            selectedIcon.set(true);
            assertTrue(part(control, "icon").isVisible());
            control.setSelected(false);
            assertEquals(16, part(control, "handle").getLayoutBounds().getWidth());
            assertEquals(16, part(control, "handle").getBoundsInParent().getCenterX());
        });
    }

    /// Dispatches a primary pointer event at a position in the unscaled switch artwork.
    private static void mouse(M3Switch control, EventType<MouseEvent> type, double x, boolean down) {
        mouse(control, type, x, MouseButton.PRIMARY, down);
    }

    /// Dispatches a pointer event while preserving the primary button's independent state.
    private static void mouse(M3Switch control, EventType<MouseEvent> type, double x, MouseButton button, boolean down) {
        mouse(control, type, x, button, down, null);
    }

    /// Dispatches a pointer event with an optional keyboard modifier.
    private static void mouse(M3Switch control, EventType<MouseEvent> type, double x, MouseButton button,
                              boolean down, @Nullable KeyCode modifier) {
        Point2D scenePoint = Objects.requireNonNull(control.lookup(".switch-artwork")).localToScene(x, 24);
        control.fireEvent(new MouseEvent(type, scenePoint.getX(), scenePoint.getY(),
                scenePoint.getX(), scenePoint.getY(), button, 1,
                modifier == KeyCode.SHIFT, modifier == KeyCode.CONTROL,
                modifier == KeyCode.ALT, modifier == KeyCode.META, down, false,
                button == MouseButton.SECONDARY && type == MouseEvent.MOUSE_PRESSED, false, false, false,
                new PickResult(control, scenePoint.getX(), scenePoint.getY())));
    }

    /// Dispatches an unmodified key event to the switch.
    private static void key(M3Switch control, EventType<KeyEvent> type, KeyCode code) {
        key(control, type, code, null);
    }

    /// Dispatches a key event with an optional modifier.
    private static void key(M3Switch control, EventType<KeyEvent> type, KeyCode code, @Nullable KeyCode modifier) {
        control.fireEvent(new KeyEvent(type, "", "", code, modifier == KeyCode.SHIFT,
                modifier == KeyCode.CONTROL, modifier == KeyCode.ALT, modifier == KeyCode.META));
    }

    /// Attaches a switch to a scene with the application theme and component stylesheet.
    private static VBox scene(M3Switch control) {
        VBox root = new VBox(control);
        applyStylesheets(new Scene(root, 300, 100));
        layout(root);
        return root;
    }

    /// Loads the same shared component styles and color palette used by the application.
    private static void applyStylesheets(Scene scene) {
        scene.getStylesheets().addAll(
                Objects.requireNonNull(M3SwitchTest.class.getResource("/assets/css/blue.css")).toExternalForm(),
                Objects.requireNonNull(M3SwitchTest.class.getResource("/assets/css/root.css")).toExternalForm());
    }

    /// Applies pending CSS changes and synchronously lays out the given subtree.
    private static void layout(Region root) {
        root.applyCss();
        root.layout();
    }

    /// Returns a required shape from the default skin.
    private static Shape part(M3Switch control, String name) {
        return (Shape) Objects.requireNonNull(control.lookup(".switch-" + name));
    }

    /// Reads optional-property storage without forcing its allocation.
    private static @Nullable Object field(M3Switch control, String name) {
        try {
            Field field = M3Switch.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(control);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    /// Runs assertions on the application thread and propagates failures to JUnit.
    private static void onFxThread(Runnable action) throws Exception {
        callOnFxThread(() -> {
            action.run();
            return null;
        });
    }

    /// Returns a value from the application thread without sharing mutable test holders.
    private static <T extends @Nullable Object> T callOnFxThread(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }
}
