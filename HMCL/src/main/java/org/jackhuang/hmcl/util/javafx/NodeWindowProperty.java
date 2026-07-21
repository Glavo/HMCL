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
package org.jackhuang.hmcl.util.javafx;

import javafx.beans.property.ReadOnlyObjectPropertyBase;
import javafx.beans.value.ChangeListener;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/// A read-only property that tracks the window containing a JavaFX node.
///
/// The value is the window associated with the node's current scene when that window is an
/// instance of `W`; otherwise, the value is `null`. The property follows changes to both the
/// node's scene and the scene's window. Changes that occur while the property is invalid are
/// coalesced until its value is read again.
///
/// The property registers listeners on the node and its current scene. It automatically moves
/// the scene listener when the node enters another scene, but it does not provide an operation
/// for detaching the listener from the node.
///
/// @param <W> the type of window exposed by this property
@NotNullByDefault
public final class NodeWindowProperty<W extends Window>
        extends ReadOnlyObjectPropertyBase<@Nullable W> {

    /// Creates a property that tracks the stage containing `node`.
    ///
    /// The returned property has `node` as its bean and `stage` as its name. Its value is
    /// `null` while the node has no scene, the scene has no window, or the scene's window is not
    /// a [Stage].
    ///
    /// @param node the node whose stage is tracked
    /// @return a read-only property that follows the node's stage
    /// @throws NullPointerException if `node` is `null`
    public static NodeWindowProperty<Stage> newStageProperty(Node node) {
        return new NodeWindowProperty<>(node, "stage", Stage.class);
    }

    /// Recomputes the value when the current scene is attached to or detached from a window.
    private final ChangeListener<@Nullable Window> sceneWindowChangedListener = (obs, old, current) -> updateValue();

    /// Transfers window observation when the node enters or leaves a scene.
    private final ChangeListener<@Nullable Scene> nodeSceneChangedListener =
            (obs, old, current) -> sceneChanged(old, current);

    /// The node whose containing window is tracked and which is exposed as the property bean.
    private final Node node;

    /// The name exposed by this property.
    private final String name;

    /// The runtime window type accepted as a property value.
    private final Class<W> windowType;

    /// Whether the cached value has been observed since its most recent change.
    private boolean valid;

    /// The most recently computed window that matches [#windowType], or `null` if none does.
    private @Nullable W currentValue;

    /// Creates a window property and begins observing the node's scene.
    ///
    /// @param node the node whose containing window is tracked
    /// @param name the property name
    /// @param windowType the runtime type required of values exposed by the property
    private NodeWindowProperty(Node node, String name, Class<W> windowType) {
        Objects.requireNonNull(node);
        Objects.requireNonNull(name);
        Objects.requireNonNull(windowType);

        this.node = node;
        this.name = name;
        this.windowType = windowType;

        node.sceneProperty().addListener(nodeSceneChangedListener);

        sceneChanged(null, node.getScene());
    }

    /// Returns the node whose window is tracked.
    ///
    /// @return the tracked node
    @Override
    public Object getBean() {
        return node;
    }

    /// Returns the name assigned when this property was created.
    ///
    /// @return the property name
    @Override
    public String getName() {
        return name;
    }

    /// Returns the current matching window containing the node.
    ///
    /// The value is recomputed if the property has been invalidated. It is `null` if the node
    /// has no scene, the scene has no window, or the window is not an instance of `W`.
    ///
    /// @return the current matching window, or `null` if no matching window contains the node
    @Override
    public @Nullable W get() {
        if (!valid) {
            updateValue();
            valid = true;
        }

        return currentValue;
    }

    /// Marks the property invalid and notifies listeners if it was previously valid.
    private void invalidate() {
        if (valid) {
            valid = false;
            fireValueChangedEvent();
        }
    }

    /// Moves the window listener from the previous scene to the new scene and updates the value.
    ///
    /// @param oldScene the scene previously containing the node, or `null` if there was none
    /// @param newScene the scene now containing the node, or `null` if there is none
    private void sceneChanged(@Nullable Scene oldScene, @Nullable Scene newScene) {
        if (oldScene != null) {
            oldScene.windowProperty().removeListener(sceneWindowChangedListener);
        }
        if (newScene != null) {
            newScene.windowProperty().addListener(sceneWindowChangedListener);
        }

        updateValue();
    }

    /// Recomputes the cached value and invalidates the property when its window identity changes.
    private void updateValue() {
        Scene scene = node.getScene();
        @Nullable W newValue;

        if (scene != null) {
            @Nullable Window currentWindow = scene.getWindow();
            if (windowType.isInstance(currentWindow)) {
                newValue = windowType.cast(currentWindow);
            } else {
                newValue = null;
            }
        } else
            newValue = null;

        if (newValue != currentValue) {
            currentValue = newValue;
            invalidate();
        }
    }
}
