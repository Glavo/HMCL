// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import com.jfoenix.controls.JFXListView;
import javafx.beans.binding.DoubleBinding;
import javafx.css.PseudoClass;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.controls.M3Animations;
import org.jetbrains.annotations.Nullable;

public abstract class MDListCell<T> extends ListCell<T> {
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");
    private static final PseudoClass LAST = PseudoClass.getPseudoClass("last");

    private final StackPane container = new StackPane();
    private final StackPane root = new StackPane();
    private final RipplerContainer ripplerContainer = new RipplerContainer(container);

    public MDListCell(JFXListView<T> listView) {

        setText(null);
        setGraphic(null);

        root.getStyleClass().add("md-list-cell");
        root.getChildren().setAll(ripplerContainer);

        Region clippedContainer = (Region) listView.lookup(".clipped-container");
        setPrefWidth(0);
        if (clippedContainer != null) {
            DoubleBinding converted = clippedContainer.widthProperty().subtract(1);

            maxWidthProperty().bind(converted);
            prefWidthProperty().bind(converted);
            minWidthProperty().bind(converted);
        }
    }

    /// Refreshes reusable content without animating item changes, including while its graphic is detached.
    @Override
    protected void updateItem(@Nullable T item, boolean empty) {
        if (getItem() == item && isEmpty() == empty) {
            super.updateItem(item, empty);
            updateLastPseudoClass(empty);
            return;
        }

        M3Animations.suspend(root);
        try {
            super.updateItem(item, empty);
            ripplerContainer.resetFeedback();
            updateControl(item, empty);
            if (empty || item == null) {
                setGraphic(null);
            } else {
                setGraphic(root);
            }
            updateLastPseudoClass(empty || item == null);
        } finally {
            M3Animations.resume(root);
        }
    }

    /// Updates the `:last` pseudo-class so the trailing divider can be suppressed in CSS.
    ///
    /// @param empty whether this cell is empty
    private void updateLastPseudoClass(boolean empty) {
        ListView<T> listView = getListView();
        boolean last = !empty
                && listView != null
                && getIndex() >= 0
                && getIndex() == listView.getItems().size() - 1;
        root.pseudoClassStateChanged(LAST, last);
    }

    protected StackPane getContainer() {
        return container;
    }

    protected void setSelectable() {
        FXUtils.onChangeAndOperate(selectedProperty(), selected -> {
            root.pseudoClassStateChanged(SELECTED, selected);
        });
    }

    protected void onClicked(Runnable action) {
        FXUtils.onClicked(ripplerContainer, action);
    }

    protected abstract void updateControl(T item, boolean empty);
}
