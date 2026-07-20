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

@NotNullByDefault
public final class NodeWindowProperty<W extends Window>
        extends ReadOnlyObjectPropertyBase<@Nullable W> {

    public static NodeWindowProperty<Stage> newStageProperty(Node node) {
        return new NodeWindowProperty<>(node, "stage", Stage.class);
    }

    private final ChangeListener<Window> sceneWindowChangedListener = (obs, old, current) -> updateValue();
    private final ChangeListener<Scene> nodeSceneChangedListener = (obs, old, current) -> sceneChanged(old, current);

    private final Node node;
    private final String name;
    private final Class<W> windowType;

    private boolean valid;
    private @Nullable W currentValue;

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

    @Override
    public Object getBean() {
        return node;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public @Nullable W get() {
        if (!valid) {
            updateValue();
            valid = true;
        }

        return currentValue;
    }

    private void invalidate() {
        if (valid) {
            valid = false;
            fireValueChangedEvent();
        }
    }

    private void sceneChanged(@Nullable Scene oldScene, @Nullable Scene newScene) {
        if (oldScene != null) {
            oldScene.windowProperty().removeListener(sceneWindowChangedListener);
        }
        if (newScene != null) {
            newScene.windowProperty().addListener(sceneWindowChangedListener);
        }

        updateValue();
    }


    private void updateValue() {
        Scene scene = node.getScene();
        W newValue;

        if (scene != null) {
            Window currentWindow = scene.getWindow();
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
