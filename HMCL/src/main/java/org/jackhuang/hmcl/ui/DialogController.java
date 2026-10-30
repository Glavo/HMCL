// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui;

import org.jackhuang.hmcl.auth.*;
import org.jackhuang.hmcl.auth.microsoft.MicrosoftAccount;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorAccount;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.ui.account.AccountDialog;
import org.jackhuang.hmcl.ui.construct.DialogCloseEvent;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.jackhuang.hmcl.ui.FXUtils.runInFX;

/// Bridges background launch authentication to JavaFX account dialogs.
@NotNullByDefault
public final class DialogController {
    /// Prevents instantiation of this utility.
    private DialogController() {
    }

    /// Reauthenticates an online account interactively, preserving its identity and storage settings.
    /// Other account types use stored-credential login directly. This method must not run on the JavaFX thread.
    /// Launch resources are prepared after accepting the credentials; a preparation failure retains the new credentials.
    /// The caller owns the returned launch resources and must close them after use.
    ///
    /// @throws CancellationException if the dialog closes without accepting a result
    /// @throws AuthenticationException if stored-credential login or launch-resource preparation fails
    /// @throws InterruptedException if the waiting thread is interrupted; the dialog is closed asynchronously
    public static AuthInfo logIn(Account account) throws CancellationException, AuthenticationException, InterruptedException {
        if (account instanceof MicrosoftAccount
                || account instanceof AuthlibInjectorAccount) {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicBoolean accepted = new AtomicBoolean();
            AtomicReference<@Nullable AccountDialog> dialog = new AtomicReference<>();
            AtomicBoolean cancelled = new AtomicBoolean();
            runInFX(() -> {
                if (cancelled.get()) return;
                AccountDialog pane = new AccountDialog(account, authenticated -> {
                    accepted.set(true);
                    latch.countDown();
                }, latch::countDown);
                dialog.set(pane);
                Controllers.dialog(pane);
            });
            try {
                latch.await();
                if (!accepted.get()) throw new CancellationException();
            } catch (InterruptedException e) {
                cancelled.set(true);
                runInFX(() -> {
                    @Nullable AccountDialog pane = dialog.get();
                    if (pane != null) {
                        pane.onDialogClosed();
                        pane.fireEvent(new DialogCloseEvent());
                    }
                });
                throw e;
            }
        }
        return Accounts.logIn(account);
    }
}
