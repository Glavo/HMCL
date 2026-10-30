// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.account;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXDialogLayout;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.AccountType;
import org.jackhuang.hmcl.auth.NoSelectedCharacterException;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorAccount;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorServer;
import org.jackhuang.hmcl.auth.microsoft.MicrosoftAccount;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.*;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import static org.jackhuang.hmcl.setting.SettingsManager.settings;
import static org.jackhuang.hmcl.task.Schedulers.io;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Hosts account creation and reauthentication, including method selection and storage confirmation.
/// All methods must be called on the JavaFX application thread. Launch resources are prepared separately.
@NotNullByDefault
public final class AccountDialog extends JFXDialogLayout implements DialogAware {
    /// Contains optional method navigation followed by the current form.
    private final VBox content = new VBox(15);
    /// Submits the current form or performs the current Microsoft login step.
    private final JFXButton acceptButton = new JFXButton(i18n("account.login"));
    /// Indicates authentication or confirmation is pending.
    private final BooleanProperty busy = new SimpleBooleanProperty();
    /// Displays authentication or persistence failures.
    private final Label error = new Label();
    /// The original account when renewing credentials, or `null` for creation.
    private final @Nullable Account existing;
    /// Receives the original account after its new credentials have been accepted.
    private final @Nullable Consumer<Account> success;
    /// Notifies a waiting caller when this dialog closes without success.
    private final Runnable cancelled;
    /// The current method's form.
    private @Nullable VBox pane;
    /// The password authentication request, if any; Microsoft requests belong to their panel.
    private @Nullable Future<?> task;
    /// Invalidates results and confirmations from superseded requests or forms.
    private long generation;
    /// The account awaiting storage confirmation, or `null` when no result is pending.
    private @Nullable Account pending;
    /// Whether this dialog has been shown.
    private boolean shown;
    /// Whether this dialog has completed or been cancelled.
    private boolean closed;

    /// Creates the method chooser, honoring account restrictions and the last selected method.
    public AccountDialog() {
        this(null, () -> { }, null);
        setHeading(new Label(i18n("account.create")));
        if (AccountListPage.RESTRICTED.get()) {
            showPane(AccountType.MICROSOFT, null);
            return;
        }
        AccountType preferred;
        try {
            preferred = AccountType.fromId(settings().preferredLoginTypeProperty().get());
        } catch (IllegalArgumentException e) {
            preferred = AccountType.OFFLINE;
        }
        AccountType[] types = AccountType.values();
        TabItem<?>[] tabs = new TabItem<?>[types.length];
        Map<TabItem<?>, AccountType> methods = new IdentityHashMap<>();
        for (int i = 0; i < types.length; i++) {
            TabItem<Node> tab = new TabItem<>();
            tab.setText(Accounts.getLocalizedLoginTypeName(types[i]));
            tabs[i] = tab;
            methods.put(tab, types[i]);
        }
        TabHeader header = new TabHeader(tabs);
        header.getStyleClass().add("add-account-tab-header");
        header.setMinWidth(USE_PREF_SIZE);
        header.setMaxWidth(USE_PREF_SIZE);
        header.getController().selectTab(tabs[preferred.ordinal()]);
        content.getChildren().add(header);
        FXUtils.onChange(header.getController().selectedTabProperty(), selected -> {
            if (selected == null || closed) return;
            AccountType type = Objects.requireNonNull(methods.get(selected));
            settings().preferredLoginTypeProperty().set(type.id());
            showPane(type, null);
        });
        showPane(preferred, null);
    }

    /// Creates a dialog for one account type without method navigation.
    public AccountDialog(AccountType type) {
        this(null, () -> { }, null);
        setHeading(new Label(i18n("account.create." + type.id())));
        showPane(type, null);
    }

    /// Creates a dialog for a fixed external authentication server.
    public AccountDialog(AuthlibInjectorServer server) {
        this(null, () -> { }, null);
        setHeading(new Label(i18n("account.create.authlibInjector")));
        showPane(AccountType.AUTHLIB_INJECTOR, server);
    }

    /// Creates a reauthentication dialog that preserves the account's identity and storage settings.
    /// Exactly one of the completion callbacks is invoked when the dialog completes or closes.
    ///
    /// @throws IllegalArgumentException if the account does not support interactive authentication
    public AccountDialog(Account existing, Consumer<Account> success, Runnable cancelled) {
        this(existing, cancelled, success);
        if (!(existing instanceof MicrosoftAccount || existing instanceof AuthlibInjectorAccount)) {
            throw new IllegalArgumentException("Account does not support interactive authentication");
        }
        setHeading(new Label(i18n("account.login.refresh")));
        showPane(AccountType.of(existing), existing instanceof AuthlibInjectorAccount injector ? injector.getServer() : null);
    }

    /// Initializes the shared dialog controls and completion callbacks.
    private AccountDialog(@Nullable Account existing, Runnable cancelled, @Nullable Consumer<Account> success) {
        this.existing = existing;
        this.success = success;
        this.cancelled = cancelled;
        error.setWrapText(true);
        error.setMaxWidth(500);
        error.getStyleClass().add("dialog-error");
        error.visibleProperty().bind(error.textProperty().isNotEmpty());
        error.managedProperty().bind(error.visibleProperty());
        acceptButton.getStyleClass().add("dialog-accept");
        SpinnerPane spinner = new SpinnerPane();
        spinner.getStyleClass().add("small-spinner-pane");
        spinner.setContent(acceptButton);
        busy.addListener((observable, oldValue, value) -> spinner.setLoading(value));
        JFXButton cancel = new JFXButton(i18n("button.cancel"));
        cancel.getStyleClass().add("dialog-cancel");
        cancel.setOnAction(event -> cancel());
        setBody(content);
        setActions(error, spinner, cancel);
        FXUtils.onEscPressed(this, this::cancel);
    }

    /// Replaces the form and cancels requests and confirmations belonging to its predecessor.
    private void showPane(AccountType type, @Nullable AuthlibInjectorServer server) {
        closePane();
        content.getChildren().remove(pane);
        acceptButton.disableProperty().unbind();
        acceptButton.setOnAction(null);
        busy.set(false);
        content.setDisable(false);
        error.setText("");
        long selection = generation;
        pane = switch (type) {
            case OFFLINE -> {
                OfflineAccountCreatePane offline = new OfflineAccountCreatePane(acceptButton::fire);
                acceptButton.disableProperty().bind(busy.or(offline.validProperty().not()));
                acceptButton.setOnAction(event -> createOfflineAccount(offline));
                offline.disableProperty().bind(busy);
                yield offline;
            }
            case AUTHLIB_INJECTOR -> {
                AuthlibInjectorAccountLoginPane injector = new AuthlibInjectorAccountLoginPane(server,
                        (AuthlibInjectorAccount) existing, acceptButton::fire);
                acceptButton.disableProperty().bind(busy.or(injector.validProperty().not()));
                acceptButton.setOnAction(event -> authenticate(injector.authentication()));
                injector.disableProperty().bind(busy);
                yield injector;
            }
            case MICROSOFT -> new MicrosoftAccountLoginPane((MicrosoftAccount) existing, acceptButton, busy, account -> {
                if (!closed && generation == selection) onCompleted(account);
            });
        };
        content.getChildren().add(pane);
        setPrefWidth(type == AccountType.MICROSOFT ? 650 : 560);
        if (shown) {
            if (pane instanceof MicrosoftAccountLoginPane microsoft) microsoft.start();
            else pane.requestFocus();
        }
    }

    /// Creates an offline identity after any required player-name warning has been accepted.
    private void createOfflineAccount(OfflineAccountCreatePane offline) {
        if (closed || busy.get() || !offline.validProperty().get()) return;
        Account account = offline.createAccount();
        if (offline.requiresNameConfirmation()) {
            long attempt = generation;
            busy.set(true);
            Controllers.confirmWithCountdown(i18n("account.methods.offline.name.invalid"), i18n("message.warning"), 10,
                    MessageDialogPane.MessageType.WARNING, () -> {
                        if (!closed && generation == attempt) onCompleted(account);
                    }, () -> {
                        if (!closed && generation == attempt) busy.set(false);
                    });
        } else {
            onCompleted(account);
        }
    }

    /// Authenticates captured password credentials off the JavaFX thread and ignores obsolete results.
    private void authenticate(Callable<? extends Account> operation) {
        if (closed || busy.get()) return;
        long attempt = ++generation;
        busy.set(true);
        error.setText("");
        task = io().submit(() -> {
            try {
                Account account = operation.call();
                Platform.runLater(() -> {
                    if (closed || generation != attempt) return;
                    task = null;
                    onCompleted(account);
                });
            } catch (Exception exception) {
                Platform.runLater(() -> {
                    if (closed || generation != attempt) return;
                    task = null;
                    busy.set(false);
                    if (exception instanceof NoSelectedCharacterException || exception instanceof CancellationException) cancel();
                    else error.setText(Accounts.localizeErrorMessage(exception));
                });
            }
        });
    }

    /// Confirms any required storage overwrite before applying authenticated account state.
    private void onCompleted(Account account) {
        if (closed) return;
        pending = account;
        busy.set(true);
        content.setDisable(true);
        Account target = existing != null ? existing : account;
        if (Accounts.isAccountFilesReadOnly(target)) {
            Controllers.dialog(new MessageDialogPane.Builder(
                    i18n("account.storage.read_only") + "\n\n" + i18n("settings.file.force_write.confirm"),
                    i18n("message.warning"), MessageDialogPane.MessageType.WARNING)
                    .addAction(i18n("settings.file.force_write"), () -> {
                        if (closed || pending != account) return;
                        try {
                            Accounts.forceOverwriteAccountFiles(target);
                            commit(account);
                        } catch (Exception e) {
                            reject(account, e);
                        }
                    })
                    .addCancel(() -> reject(account, null)).build());
        } else {
            commit(account);
        }
    }

    /// Stores an accepted account or applies its credentials to the original account, then closes the dialog.
    private void commit(Account account) {
        if (closed || pending != account) return;
        Account selected = existing != null ? existing : account;
        try {
            if (existing == null) {
                Accounts.getAccounts().add(account);
            } else {
                if (Accounts.getAccounts().stream().noneMatch(value -> value == existing)) {
                    throw new IllegalStateException("Account was removed during authentication");
                }
                Accounts.acceptReauthentication(existing, account);
            }
            Accounts.setSelectedAccount(selected);
        } catch (Exception e) {
            reject(account, e);
            return;
        }
        closed = true;
        closePane();
        try {
            if (success != null) success.accept(selected);
        } finally {
            fireEvent(new DialogCloseEvent());
        }
    }

    /// Discards a declined result and allows the current form to be submitted again.
    private void reject(Account account, @Nullable Exception exception) {
        if (closed || pending != account) return;
        pending = null;
        content.setDisable(false);
        busy.set(false);
        if (exception != null) error.setText(Accounts.localizeErrorMessage(exception));
    }

    /// Cancels current work, invalidates queued callbacks, and releases panel-specific listeners.
    private void closePane() {
        generation++;
        pending = null;
        if (task != null) task.cancel(true);
        task = null;
        if (pane instanceof MicrosoftAccountLoginPane microsoft) microsoft.close();
        else if (pane instanceof AuthlibInjectorAccountLoginPane injector) injector.close();
    }

    /// Cancels the dialog and requests its removal from the dialog stack.
    private void cancel() {
        if (closed) return;
        onDialogClosed();
        fireEvent(new DialogCloseEvent());
    }

    @Override
    public void onDialogShown() {
        if (closed || shown) return;
        shown = true;
        if (pane instanceof MicrosoftAccountLoginPane microsoft) microsoft.start();
        else if (pane != null) pane.requestFocus();
    }

    @Override
    public void onDialogClosed() {
        if (closed) return;
        closed = true;
        closePane();
        cancelled.run();
    }
}
