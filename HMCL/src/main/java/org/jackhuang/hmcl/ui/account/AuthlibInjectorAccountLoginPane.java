// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.account;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXComboBox;
import com.jfoenix.controls.JFXPasswordField;
import com.jfoenix.controls.JFXTextField;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorAccount;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorServer;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.upgrade.IntegrityChecker;
import org.jackhuang.hmcl.util.StringUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;

import static javafx.beans.binding.Bindings.*;
import static org.jackhuang.hmcl.setting.SettingsManager.getAuthlibInjectorServers;
import static org.jackhuang.hmcl.ui.FXUtils.stringConverter;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Collects external account credentials for a selectable or fixed server.
/// Methods must be called on the JavaFX application thread; the captured authentication operation runs in the background.
@NotNullByDefault
public final class AuthlibInjectorAccountLoginPane extends VBox implements AutoCloseable {
    /// The original account when renewing credentials, or `null` when creating an account.
    private final @Nullable AuthlibInjectorAccount existing;
    /// The selected authentication server.
    private final ObjectProperty<@Nullable AuthlibInjectorServer> server = new SimpleObjectProperty<>();
    /// The optional server selector, absent in fixed-server mode.
    private final @Nullable JFXComboBox<AuthlibInjectorServer> servers;
    /// The login name field.
    private final JFXTextField username = new JFXTextField();
    /// The password field.
    private final JFXPasswordField password = new JFXPasswordField();
    /// Links advertised by the selected server.
    private final HBox links = new HBox(8);
    /// Whether the username satisfies the selected server's current requirements.
    private final BooleanProperty validUsername = new SimpleBooleanProperty();
    /// Whether all required credentials are present and valid.
    private final BooleanBinding valid;
    /// Whether this pane has released its server listeners.
    private boolean closed;
    /// Updates links and username validation when server metadata changes.
    private final InvalidationListener metadataListener = observable -> updateServerDetails();

    /// Creates credential fields, optionally fixing their server or renewing an existing account.
    /// The supplied action is invoked when Enter is pressed.
    public AuthlibInjectorAccountLoginPane(@Nullable AuthlibInjectorServer fixedServer, @Nullable AuthlibInjectorAccount existing,
                                           Runnable submit) {
        setSpacing(15);
        this.existing = existing;
        GridPane form = new GridPane();
        form.setHgap(15);
        form.setVgap(22);
        form.setAlignment(Pos.CENTER);
        ColumnConstraints labels = new ColumnConstraints();
        labels.setMinWidth(USE_PREF_SIZE);
        ColumnConstraints inputs = new ColumnConstraints();
        inputs.setHgrow(Priority.ALWAYS);
        form.getColumnConstraints().setAll(labels, inputs);
        if (!IntegrityChecker.isOfficial()) {
            HintPane hint = new HintPane(MessageDialogPane.MessageType.WARNING);
            hint.setSegment(i18n("unofficial.hint"));
            getChildren().add(hint);
        }
        form.add(new Label(i18n("account.injector.server")), 0, 0);
        VBox serverDetails = new VBox(4);
        form.add(serverDetails, 1, 0);
        if (existing != null) fixedServer = existing.getServer();
        if (fixedServer != null) {
            servers = null;
            serverDetails.getChildren().add(new Label(fixedServer.getName()));
        } else {
            servers = new JFXComboBox<>();
            servers.setConverter(stringConverter(value -> String.format("%s (%s)", value.getName(), value.getDisplayHostUrl())));
            bindContent(servers.getItems(), getAuthlibInjectorServers());
            server.bind(servers.valueProperty());
            Label empty = new Label(i18n("account.injector.empty"));
            empty.visibleProperty().bind(isEmpty(servers.getItems()));
            empty.managedProperty().bind(empty.visibleProperty());
            servers.visibleProperty().bind(empty.visibleProperty().not());
            servers.managedProperty().bind(servers.visibleProperty());
            servers.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(servers, Priority.ALWAYS);
            JFXButton add = FXUtils.newToggleButton4(SVG.ADD, 20);
            add.setOnAction(event -> Controllers.dialog(new AddAuthlibInjectorServerPane()));
            HBox selection = new HBox(10, servers, empty, add);
            selection.setAlignment(Pos.CENTER_LEFT);
            serverDetails.getChildren().add(selection);
            servers.getItems().addListener((InvalidationListener) observable -> {
                Platform.runLater(() -> {
                    if (!closed && servers.getValue() == null) servers.getSelectionModel().selectFirst();
                });
            });
        }
        links.visibleProperty().bind(isNotEmpty(links.getChildren()));
        links.managedProperty().bind(links.visibleProperty());
        serverDetails.getChildren().add(links);
        form.add(new Label(i18n("account.username")), 0, 1);
        username.setValidators(new RequiredValidator(), new Validator(i18n("input.email"), value ->
                existing != null || server.get() == null || server.get().isNonEmailLogin() || value.contains("@")));
        FXUtils.setValidateWhileTextChanged(username, true);
        if (existing != null) {
            username.setText(existing.getLoginName());
            username.setEditable(false);
        }
        form.add(username, 1, 1);
        form.add(new Label(i18n("account.password")), 0, 2);
        password.setValidators(new RequiredValidator());
        FXUtils.setValidateWhileTextChanged(password, true);
        form.add(password, 1, 2);
        getChildren().add(form);
        server.addListener((observable, previous, selected) -> {
            if (previous != null) previous.removeListener(metadataListener);
            if (selected != null) selected.addListener(metadataListener);
            updateServerDetails();
        });
        if (servers != null) servers.getSelectionModel().selectFirst();
        else server.set(fixedServer);
        username.textProperty().addListener(observable -> validUsername.set(isUsernameValid()));
        validUsername.set(isUsernameValid());
        valid = createBooleanBinding(() -> server.get() != null && validUsername.get() && StringUtils.isNotBlank(password.getText()),
                server, validUsername, password.textProperty());
        username.setOnAction(event -> submit.run());
        password.setOnAction(event -> submit.run());
    }

    /// Returns whether the current server and credentials can be submitted.
    public BooleanBinding validProperty() {
        return valid;
    }

    /// Updates links and validation from the selected server's current metadata.
    private void updateServerDetails() {
        links.getChildren().clear();
        @Nullable AuthlibInjectorServer selected = server.get();
        if (selected != null) {
            for (String key : List.of("homepage", "register")) {
                @Nullable String url = selected.getLinks().get(key);
                if (url != null) {
                    JFXHyperlink link = new JFXHyperlink(i18n("account.injector.link." + key));
                    link.setExternalLink(url);
                    FXUtils.installSlowTooltip(link, url);
                    links.getChildren().add(link);
                }
            }
        }
        validUsername.set(isUsernameValid());
        if (!username.getText().isEmpty()) username.validate();
    }

    /// Checks the current username without displaying validation messages for untouched fields.
    private boolean isUsernameValid() {
        @Nullable AuthlibInjectorServer selected = server.get();
        return StringUtils.isNotBlank(username.getText())
                && (existing != null || selected == null || selected.isNonEmailLogin() || username.getText().contains("@"));
    }

    /// Captures the current credentials on the JavaFX thread for authentication on a background thread.
    /// The fields must satisfy [#validProperty()]. The operation returns detached account state.
    public Callable<AuthlibInjectorAccount> authentication() {
        AuthlibInjectorServer selected = Objects.requireNonNull(server.get());
        String loginName = username.getText();
        String secret = password.getText();
        return () -> existing == null
                ? Accounts.createAuthlibInjectorAccount(selected, loginName, secret, new DialogCharacterSelector())
                : Accounts.reauthenticateAuthlibInjectorAccount(existing, secret);
    }

    /// Focuses the login name for creation, or the password for reauthentication.
    @Override
    public void requestFocus() {
        if (existing == null) username.requestFocus();
        else password.requestFocus();
    }

    /// Clears the password and releases server listeners on the JavaFX thread. Repeated calls have no effect.
    @Override
    public void close() {
        if (closed) return;
        closed = true;
        password.clear();
        @Nullable AuthlibInjectorServer selected = server.get();
        if (selected != null) selected.removeListener(metadataListener);
        if (servers != null) {
            server.unbind();
            unbindContent(servers.getItems(), getAuthlibInjectorServers());
        }
    }
}
