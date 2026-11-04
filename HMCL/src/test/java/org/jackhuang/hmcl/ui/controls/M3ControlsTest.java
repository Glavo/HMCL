// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.controls;

import javafx.application.Platform;
import javafx.animation.AnimationTimer;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.PseudoClass;
import javafx.event.Event;
import javafx.geometry.Insets;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.jackhuang.hmcl.ui.controls.skin.M3ButtonGroupSkin;
import org.jackhuang.hmcl.ui.controls.skin.M3ButtonSkin;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.FutureTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/// Exercises Material controls in real JavaFX scenes, including CSS, native behavior, and skin replacement.
@NotNullByDefault
@EnabledIf("org.jackhuang.hmcl.JavaFXLauncher#isStarted")
public final class M3ControlsTest {
    /// Ensures every control renders without application styles and exports a themed visual fixture.
    @Test
    void standaloneAndThemedLayout() throws Exception {
        onFx(() -> {
            VBox root = gallery();
            Scene scene = new Scene(root, 920, 620);
            layout(root);
            for (Node node : root.lookupAll(".m3-button-base")) {
                assertTrue(node.getLayoutBounds().getWidth() > 0);
                assertTrue(node.getLayoutBounds().getHeight() >= 32);
                assertNotNull(node.lookup(".m3-state-layer"));
            }
            assertNotNull(root.lookup(".switch-track"));
            theme(scene);
            layout(root);
            M3Button button = (M3Button) Objects.requireNonNull(root.lookup("#filled"));
            assertEquals(Color.web("#4352a5"), button.getBackground().getFills().get(0).getFill());
            saveSnapshot(root, "m3-controls-light.png");
            String darkPalette = "-monet-surface: #141318; -monet-on-surface: #e6e1e9;"
                    + "-monet-primary: #bcc2ff; -monet-on-primary: #242d73;"
                    + "-monet-secondary-container: #424765; -monet-on-secondary-container: #dfe1ff;"
                    + "-monet-primary-container: #3b438b; -monet-on-primary-container: #e0e1ff;"
                    + "-monet-surface-container-low: #1c1b20; -monet-outline: #90909c;"
                    + "-monet-on-surface-variant: #c6c5d0;";
            // HMCL theme palettes use a universal selector, so replace local tokens at every node.
            try {
                Path darkStylesheet = Path.of("build", "reports", "m3-test-dark.css").toAbsolutePath();
                Files.writeString(darkStylesheet, "* { " + darkPalette + " }");
                scene.getStylesheets().add(darkStylesheet.toUri().toString());
            } catch (IOException exception) {
                throw new AssertionError(exception);
            }
            root.setStyle("-fx-background-color: -monet-surface;");
            layout(root);
            assertEquals(Color.web("#bcc2ff"), button.getBackground().getFills().get(0).getFill());
            M3MenuButton menu = (M3MenuButton) Objects.requireNonNull(root.lookup(".m3-menu-button"));
            assertEquals(menu.getTextFill(), ((Label) Objects.requireNonNull(menu.lookup(".label"))).getTextFill());
            saveSnapshot(root, "m3-controls-dark.png");
        });
    }

    /// Preserves native command activation and restores the overlay after labeled-content or skin changes.
    @Test
    void commandInputAndSkinReplacement() throws Exception {
        onFx(() -> {
            M3Button button = new M3Button("Run");
            button.setDisableAnimation(true);
            VBox root = scene(button);
            AtomicInteger actions = new AtomicInteger();
            button.setOnAction(event -> actions.incrementAndGet());
            key(button, KeyEvent.KEY_PRESSED, KeyCode.SPACE);
            assertTrue(button.isArmed());
            key(button, KeyEvent.KEY_RELEASED, KeyCode.SPACE);
            assertEquals(1, actions.get());
            mouse(button, MouseEvent.MOUSE_PRESSED, true);
            assertTrue(button.isArmed());
            mouse(button, MouseEvent.MOUSE_RELEASED, false);
            assertEquals(2, actions.get());
            button.setText("A different label");
            button.setGraphic(new Label("+"));
            layout(root);
            assertEquals(1, button.lookupAll(".m3-state-layer").size());
            Node oldLayer = Objects.requireNonNull(button.lookup(".m3-state-layer"));
            button.setSkin(null);
            button.setSkin(new M3ButtonSkin(button));
            layout(root);
            assertNotSame(oldLayer, button.lookup(".m3-state-layer"));
            key(button, KeyEvent.KEY_PRESSED, KeyCode.SPACE);
            key(button, KeyEvent.KEY_RELEASED, KeyCode.SPACE);
            assertEquals(3, actions.get(), "Replacing a skin must not leave duplicate activation handlers");
            button.setDisable(true);
            button.fire();
            assertEquals(3, actions.get());
            assertEquals(0, Objects.requireNonNull(button.lookup(".m3-state-layer")).getOpacity());
        });
    }

    /// Keeps JavaFX scene-wide default and cancel accelerators available.
    @Test
    void defaultAndCancelActions() throws Exception {
        onFx(() -> {
            M3Button confirm = new M3Button("Confirm");
            M3Button cancel = new M3Button("Cancel");
            confirm.setDefaultButton(true);
            cancel.setCancelButton(true);
            VBox root = scene(confirm, cancel);
            AtomicInteger confirmations = new AtomicInteger();
            AtomicInteger cancellations = new AtomicInteger();
            confirm.setOnAction(event -> confirmations.incrementAndGet());
            cancel.setOnAction(event -> cancellations.incrementAndGet());
            Event.fireEvent(root.getScene(), new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER, false, false, false, false));
            Event.fireEvent(root.getScene(), new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE, false, false, false, false));
            assertEquals(1, confirmations.get());
            assertEquals(1, cancellations.get());
        });
    }

    /// Applies CSS enum properties and JFoenix-style indicator paints, including bound values.
    @Test
    void stylePropertiesAndSelectionPaints() throws Exception {
        onFx(() -> {
            M3Button button = new M3Button("Styled");
            button.setStyle("-m3-variant: outlined; -m3-size: large; -m3-shape: square; -m3-state-layer-fill: #123456;");
            M3CheckBox check = new M3CheckBox("Check");
            M3RadioButton radio = new M3RadioButton("Radio");
            VBox root = scene(button, check, radio);
            assertEquals(M3ButtonVariant.OUTLINED, button.getVariant());
            assertEquals(M3ButtonSize.LARGE, button.getSize());
            assertEquals(M3ButtonShape.SQUARE, button.getButtonShape());
            assertEquals(Color.web("#123456"), button.getStateLayerFill());
            SimpleObjectProperty<@Nullable M3ButtonVariant> variant = new SimpleObjectProperty<>(M3ButtonVariant.TEXT);
            button.variantProperty().bind(variant);
            layout(root);
            assertEquals(M3ButtonVariant.TEXT, button.getVariant());
            variant.set(null);
            assertEquals(M3ButtonVariant.FILLED, button.getVariant());
            check.setCheckedColor(Color.ORANGE);
            check.setSelected(true);
            radio.setSelectedColor(Color.GREEN);
            radio.setSelected(true);
            layout(root);
            assertEquals(Color.ORANGE, ((Region) Objects.requireNonNull(check.lookup(".box"))).getBackground().getFills().get(0).getFill());
            assertEquals(Color.GREEN, ((Region) Objects.requireNonNull(radio.lookup(".dot"))).getBackground().getFills().get(0).getFill());
            check.setCheckedColor(null);
            layout(root);
            assertEquals(Color.TRANSPARENT, ((Region) Objects.requireNonNull(check.lookup(".box"))).getBackground().getFills().get(0).getFill());
        });
    }

    /// Preserves ToggleGroup, radio-button, and tri-state semantics while guarding bound state.
    @Test
    void selectionAndBindingContracts() throws Exception {
        onFx(() -> {
            M3IconToggleButton first = new M3IconToggleButton();
            M3IconToggleButton second = new M3IconToggleButton();
            ToggleGroup toggles = new ToggleGroup();
            first.setToggleGroup(toggles);
            second.setToggleGroup(toggles);
            first.fire();
            second.fire();
            assertFalse(first.isSelected());
            assertTrue(second.isSelected());
            second.fire();
            assertNull(toggles.getSelectedToggle());
            first.selectedProperty().bind(new SimpleBooleanProperty(true));
            assertDoesNotThrow(first::fire);
            M3RadioButton radio = new M3RadioButton();
            radio.setToggleGroup(new ToggleGroup());
            AtomicInteger actions = new AtomicInteger();
            radio.setOnAction(event -> actions.incrementAndGet());
            radio.fire();
            radio.fire();
            assertEquals(1, actions.get());
            assertTrue(radio.isSelected());
            M3CheckBox check = new M3CheckBox();
            check.setAllowIndeterminate(true);
            check.fire();
            assertTrue(check.isIndeterminate());
            check.fire();
            assertTrue(check.isSelected());
            assertFalse(check.isIndeterminate());
            check.fire();
            assertFalse(check.isSelected());
            check.selectedProperty().bind(new SimpleBooleanProperty(true));
            check.fire();
            assertTrue(check.isSelected());
        });
    }

    /// Normalizes selection, releases removed listeners, and supports atomic reordering.
    @Test
    void groupSelectionAndOwnership() throws Exception {
        onFx(() -> {
            M3SegmentedButton first = new M3SegmentedButton("First");
            M3SegmentedButton second = new M3SegmentedButton("Second");
            M3SegmentedButton third = new M3SegmentedButton("Third");
            M3SegmentedButtonGroup group = new M3SegmentedButtonGroup(first, second, third);
            VBox root = scene(group);
            assertSame(first, group.getSelectedItem());
            second.fire();
            assertSame(second, group.getSelectedItem());
            second.fire();
            assertTrue(second.isSelected());
            group.setSelectionMode(SelectionMode.MULTIPLE);
            third.fire();
            assertEquals(List.of(second, third), group.getSelectedItems());
            group.setSelectionMode(SelectionMode.SINGLE);
            assertEquals(List.of(second), group.getSelectedItems());
            group.getItems().setAll(third, second, first);
            layout(root);
            assertTrue(third.getPseudoClassStates().contains(PseudoClass.getPseudoClass("group-first")));
            assertThrows(IllegalArgumentException.class, () -> group.getItems().setAll(first, first));
            assertEquals(List.of(third, second, first), group.getItems());
            assertThrows(IllegalArgumentException.class, () -> new M3SegmentedButtonGroup(first));
            group.getItems().remove(second);
            assertSame(third, group.getSelectedItem());
            second.setSelected(true);
            assertSame(third, group.getSelectedItem());
            assertNull(second.getParent());
            assertThrows(UnsupportedOperationException.class, () -> group.getSelectedItems().clear());
            group.setSkin(null);
            group.setSkin(new M3ButtonGroupSkin<>(group));
            layout(root);
            assertNotNull(first.getParent());
            group.getItems().clear();
            assertNull(group.getSelectedItem());
        });
    }

    /// Navigates groups with disabled items, orientation changes, and RTL mirroring.
    @Test
    void groupKeyboardNavigation() throws Exception {
        onFx(() -> {
            M3SegmentedButton first = new M3SegmentedButton("First");
            M3SegmentedButton disabled = new M3SegmentedButton("Disabled");
            disabled.setDisable(true);
            M3SegmentedButton last = new M3SegmentedButton("Last");
            M3SegmentedButtonGroup group = new M3SegmentedButtonGroup(first, disabled, last);
            VBox root = scene(group);
            Stage stage = new Stage();
            stage.setScene(root.getScene());
            try {
                stage.show();
                first.requestFocus();
                key(first, KeyEvent.KEY_PRESSED, KeyCode.RIGHT);
                assertSame(last, root.getScene().getFocusOwner());
                assertSame(last, group.getSelectedItem());
                group.setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);
                key(last, KeyEvent.KEY_PRESSED, KeyCode.RIGHT);
                assertSame(first, root.getScene().getFocusOwner());
                group.setOrientation(Orientation.VERTICAL);
                layout(root);
                key(first, KeyEvent.KEY_PRESSED, KeyCode.DOWN);
                assertSame(last, root.getScene().getFocusOwner());
            } finally {
                stage.hide();
            }
        });
    }

    /// Reconciles selection changes made synchronously by application listeners.
    @Test
    void reentrantGroupSelection() throws Exception {
        onFx(() -> {
            M3SegmentedButton first = new M3SegmentedButton("First");
            M3SegmentedButton second = new M3SegmentedButton("Second");
            M3SegmentedButtonGroup group = new M3SegmentedButtonGroup(first, second);
            group.selectedItemProperty().addListener((observable, previous, selected) -> {
                if (selected == second)
                    first.setSelected(true);
            });
            second.fire();
            assertSame(first, group.getSelectedItem());
            assertEquals(List.of(first), group.getSelectedItems());
            assertFalse(second.isSelected());
        });
    }

    /// Keeps menu items and split-button primary actions independent.
    @Test
    void menuAndSplitActions() throws Exception {
        onFx(() -> {
            MenuItem item = new MenuItem("Choice");
            M3MenuButton menu = new M3MenuButton("Menu", null, item);
            M3SplitButton split = new M3SplitButton("Split", null, new MenuItem("Other"));
            AtomicInteger actions = new AtomicInteger();
            split.setOnAction(event -> actions.incrementAndGet());
            VBox root = scene(menu, split);
            Stage stage = new Stage();
            stage.setScene(root.getScene());
            try {
                stage.show();
                menu.show();
                assertTrue(menu.isShowing());
                menu.hide();
                split.fire();
                assertEquals(1, actions.get());
                assertFalse(split.isShowing());
                split.show();
                assertTrue(split.isShowing());
                assertEquals(1, actions.get());
                split.hide();
            } finally {
                menu.hide();
                split.hide();
                stage.hide();
            }
        });
    }

    /// Fades the whole stationary layer and settles it when disabled or detached.
    @Test
    void stateLayerAnimationLifecycle() throws Exception {
        AtomicReference<@Nullable M3Button> buttonRef = new AtomicReference<>();
        AtomicReference<@Nullable Region> layerRef = new AtomicReference<>();
        AtomicReference<@Nullable VBox> rootRef = new AtomicReference<>();
        onFx(() -> {
            M3Button button = new M3Button("Animate");
            button.setFocusTraversable(false);
            button.setDisableAnimation(false);
            VBox root = scene(button);
            Region layer = (Region) Objects.requireNonNull(button.lookup(".m3-state-layer"));
            buttonRef.set(button);
            layerRef.set(layer);
            rootRef.set(root);
            assertEquals(0, layer.getOpacity());
            button.arm();
            assertEquals(0, layer.getOpacity(), "Feedback must animate instead of jumping to its target");
        });
        awaitFx(() -> Objects.requireNonNull(layerRef.get()).getOpacity() >= 0.119);
        onFx(() -> {
            M3Button button = Objects.requireNonNull(buttonRef.get());
            Region layer = Objects.requireNonNull(layerRef.get());
            double width = layer.getWidth();
            double height = layer.getHeight();
            button.disarm();
            assertEquals(0.12, layer.getOpacity(), 0.001);
            button.arm();
            assertEquals(width, layer.getWidth());
            assertEquals(height, layer.getHeight());
            button.setDisableAnimation(true);
            assertEquals(0.12, layer.getOpacity(), 0.001);
            button.setDisable(true);
            assertEquals(0, layer.getOpacity());
            button.setDisable(false);
            button.arm();
            Objects.requireNonNull(rootRef.get()).getChildren().clear();
            assertEquals(0, layer.getOpacity());
        });
    }

    /// Builds a compact visual fixture covering each family and representative states.
    private static VBox gallery() {
        VBox root = new VBox(18);
        root.setPadding(new Insets(24));
        root.setStyle("-fx-background-color: #fbf8ff;");
        HBox variants = new HBox(12);
        for (M3ButtonVariant variant : M3ButtonVariant.values()) {
            M3Button button = new M3Button(variant.name());
            button.setId(variant.name().toLowerCase(java.util.Locale.ROOT));
            button.setVariant(variant);
            variants.getChildren().add(button);
        }
        M3Button disabled = new M3Button("Disabled");
        disabled.setDisable(true);
        variants.getChildren().add(disabled);
        M3IconButton icon = new M3IconButton(new Label("+"));
        M3IconToggleButton toggle = new M3IconToggleButton(new Label("★"));
        toggle.setSelected(true);
        M3FloatingActionButton fab = new M3FloatingActionButton("Create", new Label("+"));
        M3CheckBox check = new M3CheckBox("Selected");
        check.setSelected(true);
        M3CheckBox mixed = new M3CheckBox("Indeterminate");
        mixed.setIndeterminate(true);
        M3RadioButton radio = new M3RadioButton("Selected");
        radio.setSelected(true);
        M3Switch off = new M3Switch();
        M3Switch on = new M3Switch();
        on.setSelected(true);
        on.setShowSelectedIcon(true);
        M3ButtonGroup commands = new M3ButtonGroup(new M3Button("First"), new M3Button("Second"), new M3Button("Third"));
        commands.setVariant(M3ButtonGroupVariant.CONNECTED);
        commands.setSpacing(2);
        M3SegmentedButtonGroup segments = new M3SegmentedButtonGroup(new M3SegmentedButton("Day"), new M3SegmentedButton("Week"), new M3SegmentedButton("Month"));
        M3IconToggleButtonGroup icons = new M3IconToggleButtonGroup(new M3IconToggleButton(new Label("A")), new M3IconToggleButton(new Label("B")), new M3IconToggleButton(new Label("C")));
        icons.getItems().get(1).setSelected(true);
        root.getChildren().addAll(new Label("Material buttons"), variants,
                new HBox(16, icon, toggle, fab, new M3MenuButton("Menu", null, new MenuItem("Choice")), new M3SplitButton("Split", null, new MenuItem("Choice"))),
                new HBox(16, new M3CheckBox("Unchecked"), check, mixed),
                new HBox(16, new M3RadioButton("Unselected"), radio, off, on),
                commands, segments, icons);
        return root;
    }

    /// Creates a scene with Material controls using only their standalone stylesheets.
    private static VBox scene(Node... nodes) {
        VBox root = new VBox(8, nodes);
        new Scene(root, 800, 400);
        layout(root);
        return root;
    }

    /// Adds the existing HMCL palette and component-theme mapping.
    private static void theme(Scene scene) {
        scene.getStylesheets().addAll(
                Objects.requireNonNull(M3ControlsTest.class.getResource("/assets/css/blue.css")).toExternalForm(),
                Objects.requireNonNull(M3ControlsTest.class.getResource("/assets/css/root.css")).toExternalForm());
    }

    /// Applies pending CSS and layout synchronously.
    private static void layout(Region root) {
        root.applyCss();
        root.layout();
    }

    /// Dispatches an unmodified keyboard event to the control.
    private static void key(Node node, javafx.event.EventType<KeyEvent> type, KeyCode code) {
        node.fireEvent(new KeyEvent(type, "", "", code, false, false, false, false));
    }

    /// Dispatches a primary press or release inside a button.
    private static void mouse(ButtonBase node, javafx.event.EventType<MouseEvent> type, boolean pressed) {
        node.fireEvent(new MouseEvent(type, 10, 10, 10, 10, MouseButton.PRIMARY, 1,
                false, false, false, false, pressed, false, false, false, false, false,
                new PickResult(node, 10, 10)));
    }

    /// Writes a PNG for visual inspection without requiring the JavaFX Swing module.
    private static void saveSnapshot(Region root, String name) {
        WritableImage image = root.snapshot(null, null);
        BufferedImage pixels = new BufferedImage((int) image.getWidth(), (int) image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < pixels.getHeight(); y++) {
            for (int x = 0; x < pixels.getWidth(); x++)
                pixels.setRGB(x, y, image.getPixelReader().getArgb(x, y));
        }
        try {
            Path path = Path.of("build", "reports", name);
            Files.createDirectories(path.getParent());
            ImageIO.write(pixels, "png", path.toFile());
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }

    /// Runs assertions on the FX thread and propagates failures to JUnit.
    private static void onFx(Runnable action) throws Exception {
        FutureTask<@Nullable Void> task = new FutureTask<>(() -> {
            Thread thread = Thread.currentThread();
            @Nullable Thread.UncaughtExceptionHandler previous = thread.getUncaughtExceptionHandler();
            AtomicReference<@Nullable Throwable> failure = new AtomicReference<>();
            thread.setUncaughtExceptionHandler((source, exception) -> failure.compareAndSet(null, exception));
            try {
                action.run();
                if (failure.get() != null)
                    throw new AssertionError("Uncaught JavaFX listener failure", failure.get());
            } finally {
                thread.setUncaughtExceptionHandler(previous);
            }
        }, null);
        Platform.runLater(task);
        task.get(10, TimeUnit.SECONDS);
    }

    /// Waits for a visual condition on the FX thread with a bounded timeout.
    private static void awaitFx(BooleanSupplier condition) throws Exception {
        CompletableFuture<@Nullable Void> complete = new CompletableFuture<>();
        AnimationTimer timer = new AnimationTimer() {
            /// Samples the condition once per JavaFX pulse.
            @Override
            public void handle(long now) {
                try {
                    if (condition.getAsBoolean()) {
                        stop();
                        complete.complete(null);
                    }
                } catch (Throwable failure) {
                    stop();
                    complete.completeExceptionally(failure);
                }
            }
        };
        try {
            onFx(timer::start);
            complete.get(10, TimeUnit.SECONDS);
        } finally {
            onFx(timer::stop);
        }
    }
}
