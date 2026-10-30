// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.account;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXDialogLayout;
import javafx.application.Platform;
import javafx.scene.canvas.Canvas;
import org.jackhuang.hmcl.auth.CharacterSelector;
import org.jackhuang.hmcl.auth.NoSelectedCharacterException;
import org.jackhuang.hmcl.auth.yggdrasil.GameProfile;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilService;
import org.jackhuang.hmcl.game.TexturesLoader;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.*;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Bridges background authentication to a cancellable character-selection dialog.
@NotNullByDefault
final class DialogCharacterSelector implements CharacterSelector {
    /// Creates a selector without creating JavaFX controls or starting authentication.
    DialogCharacterSelector() {
    }

    /// Waits for a selection off the JavaFX thread and closes the dialog when interrupted.
    @Override
    public GameProfile select(YggdrasilService service, List<GameProfile> profiles) throws NoSelectedCharacterException {
        CountDownLatch selected = new CountDownLatch(1);
        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicReference<@Nullable GameProfile> result = new AtomicReference<>();
        AtomicReference<@Nullable SelectionDialog> dialog = new AtomicReference<>();
        Platform.runLater(() -> {
            if (cancelled.get()) return;
            SelectionDialog pane = new SelectionDialog(service, profiles, result, selected);
            dialog.set(pane);
            Controllers.dialog(pane);
        });
        try {
            selected.await();
            @Nullable GameProfile profile = result.get();
            if (profile == null) throw new NoSelectedCharacterException();
            return profile;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NoSelectedCharacterException();
        } finally {
            cancelled.set(true);
            Platform.runLater(() -> {
                @Nullable SelectionDialog pane = dialog.get();
                if (pane != null) pane.fireEvent(new DialogCloseEvent());
            });
        }
    }

    /// Releases the waiting authentication thread on selection or any dialog closure.
    @NotNullByDefault
    private static final class SelectionDialog extends JFXDialogLayout implements DialogAware {
        /// Signals selection or cancellation to the worker.
        private final CountDownLatch selected;

        /// Builds the character list on the JavaFX application thread.
        private SelectionDialog(YggdrasilService service, List<GameProfile> profiles,
                                AtomicReference<@Nullable GameProfile> result, CountDownLatch selected) {
            this.selected = selected;
            AdvancedListBox list = new AdvancedListBox();
            list.startCategory(i18n("account.choose").toUpperCase(Locale.ROOT));
            for (GameProfile profile : profiles) {
                Canvas portrait = new Canvas(32, 32);
                TexturesLoader.bindAvatar(portrait, service, profile.getId());
                IconedItem item = new IconedItem(portrait, profile.getName());
                FXUtils.onClicked(item, () -> {
                    result.set(profile);
                    selected.countDown();
                });
                list.add(item);
            }
            JFXButton cancel = new JFXButton(i18n("button.cancel"));
            cancel.getStyleClass().add("dialog-cancel");
            cancel.setOnAction(event -> selected.countDown());
            setBody(list);
            setActions(cancel);
            FXUtils.onEscPressed(this, cancel::fire);
        }

        @Override
        public void onDialogClosed() {
            selected.countDown();
        }
    }
}
