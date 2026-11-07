// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.controls;

import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.event.Event;
import javafx.event.EventType;
import javafx.geometry.Point2D;
import javafx.scene.AccessibleAction;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ContentDisplay;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.controls.skin.M3ButtonSkin;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.charset.StandardCharsets;
import java.lang.reflect.Field;
import java.util.Base64;
import java.util.Objects;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/// Exercises button actions, scene lifecycle, labeled content, and rendered theme states.
@NotNullByDefault
@EnabledIf("org.jackhuang.hmcl.JavaFXLauncher#isStarted")
public final class M3ButtonTest {
    /// Ordinary setters and skin installation do not allocate optional properties or fallback key handlers.
    @Test
    void lazyPropertiesAndBoundRoles() throws Exception {
        onFxThread(() -> {
            M3Button button = new M3Button("Action", M3ButtonVariant.TONAL);
            VBox root = scene(button);
            M3ButtonSkin skin = (M3ButtonSkin) Objects.requireNonNull(button.getSkin());
            assertNull(field(skin, M3ButtonSkin.class, "sceneKeyHandler"));
            button.setDefaultButton(true);
            button.setCancelButton(true);
            button.setVariant(M3ButtonVariant.OUTLINED);
            assertNull(field(button, M3ButtonBase.class, "variant"));
            assertNull(field(button, M3ButtonBase.class, "defaultButton"));
            assertNull(field(button, M3ButtonBase.class, "cancelButton"));
            assertEquals(M3ButtonVariant.OUTLINED, button.variantProperty().get());
            assertTrue(button.defaultButtonProperty().get());
            assertTrue(button.cancelButtonProperty().get());

            AtomicInteger actions = new AtomicInteger();
            button.setOnAction(event -> actions.incrementAndGet());
            SimpleBooleanProperty defaultRole = new SimpleBooleanProperty(true);
            SimpleBooleanProperty cancelRole = new SimpleBooleanProperty(true);
            button.defaultButtonProperty().bind(defaultRole);
            button.cancelButtonProperty().bind(cancelRole);
            key(root, KeyEvent.KEY_PRESSED, KeyCode.ENTER);
            key(root, KeyEvent.KEY_PRESSED, KeyCode.ESCAPE);
            assertEquals(2, actions.get());
            defaultRole.set(false);
            cancelRole.set(false);
            key(root, KeyEvent.KEY_PRESSED, KeyCode.ENTER);
            key(root, KeyEvent.KEY_PRESSED, KeyCode.ESCAPE);
            assertEquals(2, actions.get());
            assertNull(field(skin, M3ButtonSkin.class, "actionScene"));
            defaultRole.set(true);
            cancelRole.set(true);
            button.setSkin(null);
            button.setSkin(new M3ButtonSkin(button));
            root.applyCss();
            key(root, KeyEvent.KEY_PRESSED, KeyCode.ENTER);
            key(root, KeyEvent.KEY_PRESSED, KeyCode.ESCAPE);
            assertEquals(4, actions.get());
            assertNull(field(skin, M3ButtonSkin.class, "actionScene"));
        });
    }

    /// Resolves all five variants through the live HMCL palette and supports nullable bindings.
    @Test
    void variantsAndThemeChanges() throws Exception {
        onFxThread(() -> {
            M3Button button = new M3Button("Action");
            VBox root = scene(button);
            assertEquals(Color.web("#4352a5"), background(button, "container"));
            assertEquals(Color.WHITE, button.getTextFill());
            button.setVariant(M3ButtonVariant.TONAL);
            root.applyCss();
            assertEquals(Color.web("#d0d5fd"), background(button, "container"));
            assertEquals(Color.web("#565b7d"), button.getTextFill());
            button.setVariant(M3ButtonVariant.OUTLINED);
            root.applyCss();
            assertEquals(Color.TRANSPARENT, background(button, "container"));
            assertEquals(Color.web("#767683"), part(button, "container").getBorder().getStrokes().get(0).getTopStroke());
            button.setVariant(M3ButtonVariant.TEXT);
            root.applyCss();
            assertEquals(Color.TRANSPARENT, background(button, "container"));
            button.setVariant(M3ButtonVariant.ELEVATED);
            root.applyCss();
            assertEquals(Color.web("#f5f2fa"), background(button, "container"));
            assertNotNull(part(button, "container").getEffect());
            SimpleObjectProperty<@Nullable M3ButtonVariant> value = new SimpleObjectProperty<>();
            button.variantProperty().bind(value);
            root.applyCss();
            assertEquals(M3ButtonVariant.FILLED, button.getVariant());
            setPrimaryColor(button, "#123456");
            root.applyCss();
            assertEquals(Color.web("#123456"), background(button, "container"));
            value.set(M3ButtonVariant.TEXT);
            root.applyCss();
            assertEquals(Color.web("#123456"), button.getTextFill());
        });
    }

    /// Keeps artwork and state feedback intact when text, graphics, and content display change.
    @Test
    void contentAndStateLayer() throws Exception {
        onFxThread(() -> {
            M3Button button = new M3Button("Action");
            VBox root = scene(button);
            assertEquals(48, button.prefHeight(-1));
            assertEquals(40, part(button, "container").getHeight());
            Node text = Objects.requireNonNull(button.lookup(".text"));
            assertEquals(text.localToParent(0, text.getLayoutBounds().getMinY() + text.getBaselineOffset()).getY(),
                    button.getBaselineOffset(), 1);
            assertEquals(0, part(button, "state-layer").getOpacity());
            button.arm();
            root.applyCss();
            assertEquals(0.12, part(button, "state-layer").getOpacity());
            button.setText("Updated");
            button.setGraphic(new Rectangle(18, 18));
            button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            root.applyCss();
            root.layout();
            assertEquals(1, button.lookupAll(".button-state-layer").size());
            assertEquals(part(button, "container").getBoundsInParent(), part(button, "state-layer").getBoundsInParent());
            button.setDisable(true);
            root.applyCss();
            assertFalse(button.isArmed());
            assertEquals(0, part(button, "state-layer").getOpacity());
            assertEquals(0.12, part(button, "container").getOpacity());
            assertEquals(0.38, Objects.requireNonNull(button.getGraphic()).getOpacity());
        });
    }

    /// Application SVG graphics follow content color rather than the global surface icon color.
    @Test
    void themedGraphicColor() throws Exception {
        onFxThread(() -> {
            M3Button button = new M3Button("Action", SVG.FOLDER.createIcon(18));
            VBox root = scene(button);
            SVGPath icon = (SVGPath) Objects.requireNonNull(button.lookup(".svg"));
            assertEquals(button.getTextFill(), icon.getFill());
            button.setVariant(M3ButtonVariant.TONAL);
            root.applyCss();
            assertEquals(button.getTextFill(), icon.getFill());
            button.setDisable(true);
            root.applyCss();
            assertEquals(button.getTextFill(), icon.getFill());
        });
    }

    /// A new button subtype inherits the skin and styles when only a Monet palette is supplied.
    @Test
    void paletteOnlyStyling() throws Exception {
        onFxThread(() -> {
            M3ButtonBase button = new M3ButtonBase("Standalone") {
            };
            VBox root = new VBox(button);
            Scene scene = new Scene(root);
            scene.getStylesheets().add(Objects.requireNonNull(
                    M3ButtonTest.class.getResource("/assets/css/blue.css")).toExternalForm());
            root.applyCss();
            root.layout();
            assertEquals(Color.web("#4352a5"), background(button, "container"));
            assertEquals(48, button.prefHeight(-1));
        });
    }

    /// Pointer release fires once inside the target, with exit cancellation and re-entry support.
    @Test
    void pointerActivation() throws Exception {
        onFxThread(() -> {
            M3Button button = new M3Button("Action");
            scene(button);
            AtomicInteger actions = new AtomicInteger();
            button.setOnAction(event -> actions.incrementAndGet());
            mouse(button, MouseEvent.MOUSE_PRESSED, 10, MouseButton.PRIMARY);
            assertTrue(button.isArmed());
            mouse(button, MouseEvent.MOUSE_RELEASED, 10, MouseButton.PRIMARY);
            mouse(button, MouseEvent.MOUSE_CLICKED, 10, MouseButton.PRIMARY);
            assertEquals(1, actions.get());
            mouse(button, MouseEvent.MOUSE_PRESSED, 10, MouseButton.PRIMARY);
            mouse(button, MouseEvent.MOUSE_EXITED, -5, MouseButton.PRIMARY);
            assertFalse(button.isArmed());
            mouse(button, MouseEvent.MOUSE_RELEASED, -5, MouseButton.PRIMARY);
            assertEquals(1, actions.get());
            mouse(button, MouseEvent.MOUSE_PRESSED, 10, MouseButton.PRIMARY);
            mouse(button, MouseEvent.MOUSE_EXITED, -5, MouseButton.PRIMARY);
            mouse(button, MouseEvent.MOUSE_ENTERED, 10, MouseButton.PRIMARY);
            mouse(button, MouseEvent.MOUSE_RELEASED, 10, MouseButton.PRIMARY);
            assertEquals(2, actions.get());
            mouse(button, MouseEvent.MOUSE_PRESSED, 10, MouseButton.SECONDARY);
            mouse(button, MouseEvent.MOUSE_RELEASED, 10, MouseButton.SECONDARY);
            assertEquals(2, actions.get());
        });
    }

    /// Space and Enter activate once per press; Escape and disablement cancel pending input.
    @Test
    void keyboardAndAccessibleActivation() throws Exception {
        onFxThread(() -> {
            M3Button button = new M3Button("Action");
            scene(button);
            AtomicInteger actions = new AtomicInteger();
            button.setOnAction(event -> actions.incrementAndGet());
            key(button, KeyEvent.KEY_PRESSED, KeyCode.SPACE);
            key(button, KeyEvent.KEY_PRESSED, KeyCode.SPACE);
            assertTrue(button.isArmed());
            assertEquals(0, actions.get());
            key(button, KeyEvent.KEY_RELEASED, KeyCode.SPACE);
            assertEquals(1, actions.get());
            key(button, KeyEvent.KEY_PRESSED, KeyCode.SPACE);
            key(button, KeyEvent.KEY_PRESSED, KeyCode.ESCAPE);
            key(button, KeyEvent.KEY_PRESSED, KeyCode.SPACE);
            key(button, KeyEvent.KEY_RELEASED, KeyCode.SPACE);
            assertEquals(1, actions.get());
            key(button, KeyEvent.KEY_PRESSED, KeyCode.ENTER);
            key(button, KeyEvent.KEY_RELEASED, KeyCode.ENTER);
            assertEquals(2, actions.get());
            button.executeAccessibleAction(AccessibleAction.FIRE);
            assertEquals(3, actions.get());
            key(button, KeyEvent.KEY_PRESSED, KeyCode.SPACE);
            button.setDisable(true);
            key(button, KeyEvent.KEY_RELEASED, KeyCode.SPACE);
            button.fire();
            button.executeAccessibleAction(AccessibleAction.FIRE);
            assertEquals(3, actions.get());
        });
    }

    /// Scene fallbacks respect consumed keys, hidden ancestors, skin replacement, and detachment.
    @Test
    void sceneActionsAndSkinLifecycle() throws Exception {
        onFxThread(() -> {
            M3Button button = new M3Button("Action");
            VBox root = scene(button);
            Scene oldScene = Objects.requireNonNull(root.getScene());
            AtomicInteger actions = new AtomicInteger();
            button.setOnAction(event -> actions.incrementAndGet());
            button.setDefaultButton(true);
            button.setCancelButton(true);
            key(root, KeyEvent.KEY_PRESSED, KeyCode.ENTER);
            assertEquals(1, actions.get());
            button.setSkin(null);
            button.setSkin(new M3ButtonSkin(button));
            root.applyCss();
            key(root, KeyEvent.KEY_PRESSED, KeyCode.ESCAPE);
            assertEquals(2, actions.get());
            root.setVisible(false);
            key(root, KeyEvent.KEY_PRESSED, KeyCode.ENTER);
            assertEquals(2, actions.get());
            root.setVisible(true);
            root.getChildren().clear();
            Event.fireEvent(oldScene, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER, false, false, false, false));
            assertEquals(2, actions.get());
            root.getChildren().add(button);
            root.addEventFilter(KeyEvent.KEY_PRESSED, Event::consume);
            key(root, KeyEvent.KEY_PRESSED, KeyCode.ENTER);
            assertEquals(2, actions.get());
        });
    }

    /// Pending presses cannot survive skin replacement or scene removal.
    @Test
    void interruptedGestures() throws Exception {
        onFxThread(() -> {
            M3Button button = new M3Button("Action");
            VBox root = scene(button);
            AtomicInteger actions = new AtomicInteger();
            button.setOnAction(event -> actions.incrementAndGet());
            mouse(button, MouseEvent.MOUSE_PRESSED, 10, MouseButton.PRIMARY);
            button.setSkin(null);
            button.setSkin(new M3ButtonSkin(button));
            root.applyCss();
            mouse(button, MouseEvent.MOUSE_RELEASED, 10, MouseButton.PRIMARY);
            assertEquals(0, actions.get());
            key(button, KeyEvent.KEY_PRESSED, KeyCode.SPACE);
            root.getChildren().clear();
            root.getChildren().add(button);
            key(button, KeyEvent.KEY_RELEASED, KeyCode.SPACE);
            assertEquals(0, actions.get());
            button.fire();
            assertEquals(1, actions.get());
        });
    }

    /// Attaches a button to the same palette and component stylesheet used by HMCL.
    private static VBox scene(M3Button button) {
        VBox root = new VBox(button);
        Scene scene = new Scene(root, 300, 120);
        scene.getStylesheets().addAll(
                Objects.requireNonNull(M3ButtonTest.class.getResource("/assets/css/blue.css")).toExternalForm(),
                Objects.requireNonNull(M3ButtonTest.class.getResource("/assets/css/root.css")).toExternalForm());
        root.applyCss();
        root.layout();
        return root;
    }

    /// Finds a required decoration in the installed skin.
    private static Region part(M3ButtonBase button, String name) {
        return (Region) Objects.requireNonNull(button.lookup(".button-" + name));
    }

    /// Returns the resolved background color of a decoration.
    private static Color background(M3ButtonBase button, String name) {
        return (Color) part(button, name).getBackground().getFills().get(0).getFill();
    }

    /// Dispatches a key through the real scene event chain.
    private static void key(Node target, EventType<KeyEvent> type, KeyCode code) {
        target.fireEvent(new KeyEvent(type, "", "", code, false, false, false, false));
    }

    /// Dispatches a pointer event at the requested local target coordinate.
    private static void mouse(M3Button button, EventType<MouseEvent> type, double x, MouseButton mouseButton) {
        Point2D point = button.localToScene(x, 24);
        button.fireEvent(new MouseEvent(type, point.getX(), point.getY(), point.getX(), point.getY(), mouseButton, 1,
                false, false, false, false, type == MouseEvent.MOUSE_PRESSED && mouseButton == MouseButton.PRIMARY,
                false, type == MouseEvent.MOUSE_PRESSED && mouseButton == MouseButton.SECONDARY, false, false, false,
                new PickResult(button, point.getX(), point.getY())));
    }

    /// Overrides the theme token on the control and its descendants, matching the palette's scope.
    private static void setPrimaryColor(M3ButtonBase control, String color) {
        String css = "* { -monet-primary: " + color + "; }";
        control.getStylesheets().setAll("data:text/css;base64,"
                + Base64.getEncoder().encodeToString(css.getBytes(StandardCharsets.UTF_8)));
    }

    /// Inspects allocation and registration state without invoking a property accessor.
    private static @Nullable Object field(Object target, Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    /// Runs assertions on the shared JavaFX thread and propagates failures to JUnit.
    private static void onFxThread(Runnable action) throws Exception {
        FutureTask<@Nullable Void> task = new FutureTask<>(action, null);
        Platform.runLater(task);
        task.get(10, TimeUnit.SECONDS);
    }
}
