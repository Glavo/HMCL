// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.account;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXSpinner;
import io.nayuki.qrcodegen.QrCode;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.shape.SVGPath;
import org.jackhuang.hmcl.auth.OAuth;
import org.jackhuang.hmcl.auth.microsoft.MicrosoftAccount;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilService;
import org.jackhuang.hmcl.game.OAuthServer;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.theme.Themes;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.WeakListenerHolder;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.upgrade.IntegrityChecker;
import org.jackhuang.hmcl.util.QrCodeUtils;
import org.jackhuang.hmcl.util.StringUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import static org.jackhuang.hmcl.setting.SettingsManager.settings;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Presents browser and device-code authentication using a separate callback channel for each attempt.
@NotNullByDefault
public final class MicrosoftAccountLoginPane extends VBox implements AutoCloseable {
    /// The original identity when renewing credentials, or `null` for a new account.
    private final @Nullable MicrosoftAccount existing;
    /// Holds the weak progress listeners for the current attempt.
    private final WeakListenerHolder holder = new WeakListenerHolder();
    /// The rendered OAuth step.
    private final ObjectProperty<Step> step = new SimpleObjectProperty<>(new Step.Init());
    /// The operation-specific callback whose events may update this pane.
    private @Nullable OAuthServer.Factory activeCallback;

    /// The dialog's primary action, whose behavior follows the current OAuth step.
    private final JFXButton acceptButton;
    /// Controls the dialog's login spinner.
    private final BooleanProperty busy;
    /// Receives authenticated account state on the JavaFX thread.
    private final Consumer<MicrosoftAccount> success;
    /// The current background OAuth request, if any.
    private @Nullable Future<?> task;
    /// Whether this pane has been closed.
    private boolean closed;

    /// Creates a Microsoft panel without starting OAuth. Call [#start()] when the dialog is shown.
    /// The button and busy property belong to the containing dialog.
    public MicrosoftAccountLoginPane(@Nullable MicrosoftAccount existing, JFXButton acceptButton,
                                      BooleanProperty busy, Consumer<MicrosoftAccount> success) {
        this.existing = existing;
        this.acceptButton = acceptButton;
        this.busy = busy;
        this.success = success;
        getStyleClass().add("microsoft-login-dialog");
        setMaxWidth(650);
        boolean supported = !new OAuthServer.Factory().getClientId().isEmpty();
        acceptButton.disableProperty().bind(Bindings.createBooleanBinding(() -> busy.get() || !supported, busy));
        FXUtils.onChangeAndOperate(step, this::onStep);
    }

    /// Starts browser authentication when a client ID is configured and this pane is still open.
    /// All methods on this pane must be called on the JavaFX application thread.
    public void start() {
        if (!closed && activeCallback == null) {
            step.set(new OAuthServer.Factory().getClientId().isEmpty() ? new Step.Init() : new Step.StartAuthorizationCodeLogin());
        }
    }

    /// Cancels unfinished OAuth work and ignores queued progress and results. Repeated calls have no effect.
    @Override
    public void close() {
        closed = true;
        activeCallback = null;
        holder.clear();
        if (task != null) task.cancel(true);
        task = null;
    }

    /// Starts an isolated OAuth attempt and ignores progress from superseded attempts.
    private void startAuthentication(OAuth.GrantFlow flow) {
        if (closed) return;
        if (task != null) task.cancel(true);
        holder.clear();
        OAuthServer.Factory callback = new OAuthServer.Factory();
        activeCallback = callback;
        holder.registerWeak(callback.onOpenBrowserAuthorizationCode, event -> Platform.runLater(() -> {
            if (activeCallback == callback) step.set(new Step.WaitForOpenBrowser(event.getUrl()));
        }));
        holder.registerWeak(callback.onGrantDeviceCode, event -> Platform.runLater(() -> {
            if (activeCallback == callback) step.set(new Step.WaitForScanQrCode(event.getUserCode(), event.getVerificationUri()));
        }));
        holder.registerWeak(callback.onLoginCompletedDeviceCode, event -> Platform.runLater(() -> {
            if (activeCallback == callback) step.set(new Step.DeviceLoginCompleted());
        }));
        busy.set(true);
        task = Schedulers.io().submit(() -> {
            try {
                MicrosoftAccount account = existing == null ? Accounts.createMicrosoftAccount(flow, callback)
                        : Accounts.reauthenticateMicrosoftAccount(existing, flow, callback);
                Platform.runLater(() -> {
                    if (activeCallback != callback) return;
                    activeCallback = null;
                    task = null;
                    holder.clear();
                    step.set(new Step.Init());
                    success.accept(account);
                });
            } catch (Exception exception) {
                Platform.runLater(() -> {
                    if (activeCallback != callback) return;
                    activeCallback = null;
                    task = null;
                    holder.clear();
                    step.set(new Step.LoginFailed(Accounts.localizeErrorMessage(exception)));
                });
            }
        });
    }

    /// Renders the current OAuth step and starts work only for explicit start states.
    private void onStep(Step currentStep) {
        VBox rootContainer = new VBox(10);
        getChildren().setAll(rootContainer);
        rootContainer.setAlignment(Pos.TOP_CENTER);

        StackPane warningPane = new StackPane();
        rootContainer.getChildren().add(warningPane);

        if (!IntegrityChecker.isOfficial()) {
            var unofficialHintPane = new HintPane(MessageDialogPane.MessageType.WARNING);
            unofficialHintPane.setSegment(i18n("unofficial.hint"));
            warningPane.getChildren().setAll(unofficialHintPane);
        }

        if (new OAuthServer.Factory().getClientId().isEmpty()) {
            HintPane snapshotHint = new HintPane(MessageDialogPane.MessageType.ERROR);
            snapshotHint.setSegment(i18n("account.methods.microsoft.snapshot"));
            warningPane.getChildren().setAll(snapshotHint);
            busy.set(false);
        } else if (currentStep instanceof Step.Init) {
            acceptButton.setOnAction(e -> this.step.set(new Step.StartAuthorizationCodeLogin()));
            busy.set(false);

            var hintPane = new HintPane(MessageDialogPane.MessageType.INFO);
            hintPane.setText(i18n("account.methods.microsoft.hint"));
            rootContainer.getChildren().add(hintPane);
        } else if (currentStep instanceof Step.StartAuthorizationCodeLogin) {
            busy.set(false);

            rootContainer.getChildren().add(new JFXSpinner());

            startAuthentication(OAuth.GrantFlow.AUTHORIZATION_CODE);
        } else if (currentStep instanceof Step.StartDeviceCodeLogin) {
            busy.set(true);

            rootContainer.getChildren().add(new JFXSpinner());

            startAuthentication(OAuth.GrantFlow.DEVICE);
        } else if (currentStep instanceof Step.WaitForOpenBrowser wait) {
            acceptButton.setOnAction(e -> {
                FXUtils.openLink(wait.url());
                busy.set(true);
            });
            busy.set(false);

            HintPane hintPane = new HintPane(MessageDialogPane.MessageType.INFO);
            hintPane.setSegment(
                    i18n("account.methods.microsoft.methods.browser.hint", StringUtils.escapeXmlAttribute(wait.url()), wait.url()),
                    FXUtils::copyText
            );

            rootContainer.getChildren().add(hintPane);
        } else if (currentStep instanceof Step.WaitForScanQrCode wait) {
            busy.set(true);

            VBox leftVBox = new VBox(6);
            leftVBox.setAlignment(Pos.CENTER);
            VBox rightVBox = new VBox(6);

            HBox hbox = new HBox(leftVBox, rightVBox);

            leftVBox.prefWidthProperty().bind(hbox.widthProperty().divide(2));
            rightVBox.prefWidthProperty().bind(hbox.widthProperty().divide(2));
            leftVBox.setMaxWidth(Double.MAX_VALUE);
            rightVBox.setMaxWidth(Double.MAX_VALUE);

            HBox.setHgrow(leftVBox, Priority.ALWAYS);
            HBox.setHgrow(rightVBox, Priority.ALWAYS);

            String scanUri = "https://www.microsoft.com/link".equals(wait.verificationUri())
                    ? "https://www.microsoft.com/link?otc=" + wait.userCode()
                    : wait.verificationUri();

            var deviceHint = new HintPane(MessageDialogPane.MessageType.INFO);
            deviceHint.setSegment(i18n("account.methods.microsoft.methods.device.hint",
                    StringUtils.escapeXmlAttribute(scanUri),
                    wait.verificationUri(),
                    wait.userCode()
            ));

            var qrCode = new SVGPath();
            qrCode.fillProperty().bind(Themes.colorSchemeProperty().getPrimary());
            qrCode.setContent(QrCodeUtils.toSVGPath(QrCode.encodeText(scanUri, QrCode.Ecc.MEDIUM)));
            qrCode.setScaleX(3);
            qrCode.setScaleY(3);

            var lblCode = new Label(wait.userCode());
            lblCode.getStyleClass().add("code-label");
            lblCode.setStyle("-fx-font-family: \"" + Objects.requireNonNullElse(settings().logFontFamilyProperty().get(), FXUtils.DEFAULT_MONOSPACE_FONT) + "\";");

            var codeBox = new StackPane(lblCode);
            codeBox.getStyleClass().add("code-box");
            codeBox.setCursor(Cursor.HAND);
            FXUtils.onClicked(codeBox, () -> FXUtils.copyText(wait.userCode()));
            codeBox.setMaxWidth(USE_PREF_SIZE);

            leftVBox.getChildren().setAll(new Group(qrCode), codeBox);
            rightVBox.getChildren().setAll(warningPane, deviceHint);

            rootContainer.getChildren().setAll(hbox);
        } else if (currentStep instanceof Step.DeviceLoginCompleted) {
            busy.set(true);

            var hintPane = new HintPane(MessageDialogPane.MessageType.INFO);
            hintPane.setSegment(i18n("account.methods.microsoft.methods.device.hint.completed"));

            rootContainer.getChildren().addAll(hintPane);
        } else if (currentStep instanceof Step.LoginFailed failed) {
            acceptButton.setOnAction(e -> this.step.set(new Step.StartAuthorizationCodeLogin()));
            busy.set(false);

            HintPane errHintPane = new HintPane(MessageDialogPane.MessageType.ERROR);
            errHintPane.setSegment(failed.message());
            rootContainer.getChildren().add(errHintPane);
        }

        var linkBox = new FlowPane(8, 8);
        linkBox.setAlignment(Pos.CENTER_LEFT);
        linkBox.setPrefWrapLength(500);

        if (currentStep instanceof Step.Init || currentStep instanceof Step.StartAuthorizationCodeLogin || currentStep instanceof Step.WaitForOpenBrowser) {
            JFXHyperlink useQrCode = new JFXHyperlink(i18n("account.methods.microsoft.methods.device"));
            useQrCode.setOnAction(e -> this.step.set(new Step.StartDeviceCodeLogin()));
            linkBox.getChildren().add(useQrCode);
        } else if (currentStep instanceof Step.StartDeviceCodeLogin || currentStep instanceof Step.WaitForScanQrCode) {
            JFXHyperlink userBrowser = new JFXHyperlink(i18n("account.methods.microsoft.methods.browser"));
            userBrowser.setOnAction(e -> this.step.set(new Step.StartAuthorizationCodeLogin()));
            linkBox.getChildren().add(userBrowser);
        }

        JFXHyperlink profileLink = new JFXHyperlink(i18n("account.methods.microsoft.profile"));
        profileLink.setExternalLink("https://account.live.com/editprof.aspx");
        JFXHyperlink purchaseLink = new JFXHyperlink(i18n("account.methods.microsoft.purchase"));
        purchaseLink.setExternalLink(YggdrasilService.PURCHASE_URL);

        linkBox.getChildren().addAll(profileLink, purchaseLink);
        rootContainer.getChildren().add(linkBox);

        getChildren().setAll(rootContainer);
    }

    /// The visible state of the current Microsoft authentication attempt.
    @NotNullByDefault
    private sealed interface Step {
        final class Init implements Step {
        }

        /// Starting authorization-code authentication.
        @NotNullByDefault
        record StartAuthorizationCodeLogin() implements Step { }

        /// Displays the authorization URL returned by this attempt.
        ///
        /// @param url the browser authorization URL
        @NotNullByDefault
        record WaitForOpenBrowser(String url) implements Step { }

        /// Starting device-code authentication.
        @NotNullByDefault
        record StartDeviceCodeLogin() implements Step { }

        /// Displays the device code and verification URL returned by this attempt.
        ///
        /// @param userCode the code to enter in the browser
        /// @param verificationUri the verification page
        @NotNullByDefault
        record WaitForScanQrCode(String userCode, String verificationUri) implements Step { }

        /// OAuth authorization completed and Minecraft authentication is continuing.
        @NotNullByDefault
        record DeviceLoginCompleted() implements Step { }

        /// Displays a failed attempt and permits retrying.
        ///
        /// @param message the localized failure message
        @NotNullByDefault
        record LoginFailed(String message) implements Step { }
    }
}
