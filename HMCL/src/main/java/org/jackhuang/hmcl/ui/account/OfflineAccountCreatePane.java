// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.account;

import com.jfoenix.controls.JFXTextField;
import javafx.beans.binding.BooleanBinding;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import org.glavo.uuid.UUIDs;
import org.jackhuang.hmcl.auth.offline.OfflineAccount;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilService;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.javafx.BindingMapping;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.regex.Pattern;

import static javafx.beans.binding.Bindings.createBooleanBinding;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Collects an offline player identity and optional UUID override.
/// Methods must be called on the JavaFX application thread.
@NotNullByDefault
public final class OfflineAccountCreatePane extends VBox {
    /// The conventional Minecraft player-name character set.
    private static final Pattern NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]+$");
    /// The player name field.
    private final JFXTextField name = new JFXTextField();
    /// The optional UUID override field.
    private final JFXTextField uuid = new JFXTextField();
    /// Whether both identity fields can be submitted.
    private final BooleanBinding valid;

    /// Creates the identity fields, invoking the supplied action when Enter is pressed.
    public OfflineAccountCreatePane(Runnable submit) {
        GridPane form = new GridPane();
        form.setVgap(22);
        form.setHgap(15);
        form.setAlignment(Pos.CENTER);
        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setMinWidth(USE_PREF_SIZE);
        ColumnConstraints inputColumn = new ColumnConstraints();
        inputColumn.setHgrow(Priority.ALWAYS);
        form.getColumnConstraints().setAll(labelColumn, inputColumn);
        form.add(new Label(i18n("account.username")), 0, 0);
        name.setValidators(new RequiredValidator());
        name.setPromptText(i18n("account.methods.offline.name.special_characters"));
        FXUtils.installFastTooltip(name, i18n("account.methods.offline.name.special_characters"));
        FXUtils.setValidateWhileTextChanged(name, true);
        form.add(name, 1, 0);
        JFXHyperlink purchase = new JFXHyperlink(i18n("account.methods.microsoft.purchase"));
        purchase.setExternalLink(YggdrasilService.PURCHASE_URL);
        form.add(purchase, 0, 1, 2, 1);
        MenuUpDownButton advanced = new MenuUpDownButton();
        advanced.setText(i18n("settings.advanced"));
        form.add(advanced, 0, 2, 2, 1);
        Label uuidLabel = new Label(i18n("account.methods.offline.uuid"));
        GridPane.setHalignment(uuidLabel, HPos.LEFT);
        uuidLabel.visibleProperty().bind(advanced.selectedProperty());
        uuidLabel.managedProperty().bind(advanced.selectedProperty());
        uuid.visibleProperty().bind(advanced.selectedProperty());
        uuid.managedProperty().bind(advanced.selectedProperty());
        FXUtils.setValidateWhileTextChanged(uuid, true);
        uuid.setValidators(new Validator(i18n("account.methods.offline.uuid.malformed"), OfflineAccountCreatePane::validUUID));
        uuid.promptTextProperty().bind(BindingMapping.of(name.textProperty()).map(value -> OfflineAccount.getUUIDFromUserName(value).toString()));
        form.add(uuidLabel, 0, 3);
        form.add(uuid, 1, 3);
        HintPane hint = new HintPane(MessageDialogPane.MessageType.WARNING);
        hint.setText(i18n("account.methods.offline.uuid.hint"));
        hint.visibleProperty().bind(advanced.selectedProperty());
        hint.managedProperty().bind(advanced.selectedProperty());
        form.add(hint, 0, 4, 2, 1);
        getChildren().setAll(form);
        valid = createBooleanBinding(() -> StringUtils.isNotBlank(name.getText()) && validUUID(uuid.getText()),
                name.textProperty(), uuid.textProperty());
        name.setOnAction(event -> submit.run());
        uuid.setOnAction(event -> submit.run());
    }

    /// Returns whether the current fields contain a usable name and UUID.
    public BooleanBinding validProperty() {
        return valid;
    }

    /// Returns whether the player name requires a warning before account creation.
    public boolean requiresNameConfirmation() {
        return !NAME_PATTERN.matcher(name.getText()).matches() || name.getText().length() > 16;
    }

    /// Creates an account from the current fields without performing network operations.
    /// The fields must satisfy [#validProperty()].
    public OfflineAccount createAccount() {
        @Nullable UUID playerID = StringUtils.isBlank(uuid.getText()) ? null : UUIDs.parse(uuid.getText());
        return Accounts.createOfflineAccount(name.getText(), playerID, null);
    }

    /// Requests focus for the player name field.
    @Override
    public void requestFocus() {
        name.requestFocus();
    }

    /// Returns whether a UUID field is empty or parseable.
    private static boolean validUUID(String value) {
        if (StringUtils.isBlank(value)) return true;
        try {
            UUIDs.parse(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

}
