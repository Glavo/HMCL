// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.account;

import javafx.application.Platform;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.AccountID;
import org.jackhuang.hmcl.auth.AccountType;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorServer;
import org.jackhuang.hmcl.auth.microsoft.MicrosoftAccount;
import org.jackhuang.hmcl.auth.microsoft.MicrosoftSession;
import org.jackhuang.hmcl.auth.offline.OfflineAccount;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/// Verifies dialog cancellation without contacting authentication servers or changing stored accounts.
@NotNullByDefault
@EnabledIf("org.jackhuang.hmcl.JavaFXLauncher#isStarted")
public final class AccountDialogTest {
    /// Runs an assertion or setup operation on the JavaFX thread.
    private static <T> T onFx(Callable<T> operation) throws Exception {
        FutureTask<T> task = new FutureTask<>(operation);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }

    /// Starts a controlled background operation without adding a test-only production API.
    private static Future<?> authenticate(AccountDialog dialog, Callable<Account> operation) throws Exception {
        var method = AccountDialog.class.getDeclaredMethod("authenticate", Callable.class);
        method.setAccessible(true);
        method.invoke(dialog, operation);
        return (Future<?>) Objects.requireNonNull(readField(dialog, "task"));
    }

    /// Reads dialog state after queued JavaFX callbacks have been drained.
    private static @Nullable Object readField(AccountDialog dialog, String name) throws Exception {
        var field = AccountDialog.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(dialog);
    }

    /// Verifies a completed worker cannot submit its queued result after switching methods.
    @Test
    public void ignoresQueuedResultAfterSwitchingPanels() throws Exception {
        AccountDialog dialog = onFx(() -> {
            AccountDialog created = new AccountDialog(AccountType.OFFLINE);
            // Holding the JavaFX thread keeps result delivery queued until after the method switch.
            authenticate(created, () -> new OfflineAccount(AccountID.generate(), "Player", UUID.randomUUID(), null))
                    .get(5, TimeUnit.SECONDS);
            var switchMethod = AccountDialog.class.getDeclaredMethod("showPane", AccountType.class, AuthlibInjectorServer.class);
            switchMethod.setAccessible(true);
            switchMethod.invoke(created, AccountType.OFFLINE, null);
            return created;
        });
        onFx(() -> {
            assertNull(readField(dialog, "pending"));
            dialog.onDialogClosed();
            return true;
        });
    }

    /// Verifies closure interrupts the worker and reports cancellation only once.
    @Test
    public void closesPendingReauthenticationOnce() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        AtomicInteger cancelled = new AtomicInteger();
        onFx(() -> {
            MicrosoftAccount account = new MicrosoftAccount(AccountID.generate(),
                    new MicrosoftSession("Bearer", "token", Long.MAX_VALUE, "refresh", new MicrosoftSession.User("user"),
                            new MicrosoftSession.GameProfile(UUID.randomUUID(), "Player")), false);
            AccountDialog dialog = new AccountDialog(account, ignored -> fail("Cancelled account must not be accepted"), cancelled::incrementAndGet);
            authenticate(dialog, () -> {
                started.countDown();
                try {
                    new CountDownLatch(1).await();
                    throw new AssertionError("Worker must be interrupted");
                } catch (InterruptedException e) {
                    interrupted.countDown();
                    throw e;
                }
            });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            dialog.onDialogClosed();
            dialog.onDialogClosed();
            dialog.onDialogShown();
            return true;
        });
        assertTrue(interrupted.await(5, TimeUnit.SECONDS));
        onFx(() -> {
            assertEquals(1, cancelled.get());
            return true;
        });
    }
}
