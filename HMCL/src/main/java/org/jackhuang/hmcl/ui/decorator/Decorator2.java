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
package org.jackhuang.hmcl.ui.decorator;

import com.jfoenix.controls.JFXSnackbar;
import com.jfoenix.controls.JFXSnackbarLayout;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import org.jackhuang.hmcl.Launcher;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorDnD;
import org.jackhuang.hmcl.theme.Themes;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.DialogUtils;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.account.AccountListPage;
import org.jackhuang.hmcl.ui.account.AddAuthlibInjectorServerPane;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.Motion;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.JFXDialogPane;
import org.jackhuang.hmcl.ui.construct.Navigator;
import org.jackhuang.hmcl.ui.download.DownloadPage;
import org.jackhuang.hmcl.ui.main.LauncherSettingsPage;
import org.jackhuang.hmcl.ui.main.RootPage;
import org.jackhuang.hmcl.ui.terracotta.TerracottaPage;
import org.jackhuang.hmcl.ui.versions.GameListPage;
import org.jackhuang.hmcl.ui.versions.VersionPage;
import org.jackhuang.hmcl.ui.wizard.Navigation;
import org.jackhuang.hmcl.ui.wizard.Refreshable;
import org.jackhuang.hmcl.ui.wizard.WizardProvider;
import org.jackhuang.hmcl.util.FXThread;
import org.jackhuang.hmcl.util.javafx.NodeWindowProperty;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.ls.LSParser;

import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// @author Glavo
@NotNullByDefault
public final class Decorator2 extends Control {
    private @Nullable Stage stage;

    public Decorator2(Stage primaryStage) {
        this.stage = primaryStage;

        setBackground(new Background(new BackgroundFill(Color.TRANSPARENT, CornerRadii.EMPTY, Insets.EMPTY)));
    }

    // ==== Pages ====

    private @Nullable VersionPage versionPage;

    @FXThread
    public VersionPage getVersionPage() {
        if (versionPage == null) {
            versionPage = new VersionPage();
        }
        return versionPage;
    }

    @FXThread
    public void prepareVersionPage() {
        if (versionPage == null) {
            LOG.info("Prepare the version page");
            versionPage = FXUtils.prepareNode(new VersionPage());
        }
    }

    private @Nullable GameListPage gameListPage;

    @FXThread
    public GameListPage getGameListPage() {
        if (gameListPage == null) {
            gameListPage = new GameListPage();
        }
        return gameListPage;
    }

    private @Nullable RootPage rootPage;

    @FXThread
    public RootPage getRootPage() {
        if (rootPage == null) {
            rootPage = new RootPage();
        }
        return rootPage;
    }

    private @Nullable LauncherSettingsPage settingsPage;

    @FXThread
    public LauncherSettingsPage getSettingsPage() {
        if (settingsPage == null) {
            settingsPage = new LauncherSettingsPage();
        }
        return settingsPage;
    }

    @FXThread
    public void prepareSettingsPage() {
        if (settingsPage == null) {
            LOG.info("Prepare the settings page");
            settingsPage = FXUtils.prepareNode(new LauncherSettingsPage());
        }
    }

    private @Nullable AccountListPage accountListPage;

    @FXThread
    public AccountListPage getAccountListPage() {
        if (accountListPage == null) {
            accountListPage = new AccountListPage();
        }
        return accountListPage;
    }

    private @Nullable DownloadPage downloadPage;

    @FXThread
    public DownloadPage getDownloadPage() {
        if (downloadPage == null) {
            downloadPage = new DownloadPage();
        }
        return downloadPage;
    }

    @FXThread
    public void prepareDownloadPage() {
        if (downloadPage == null) {
            LOG.info("Prepare the download page");
            downloadPage = FXUtils.prepareNode(new DownloadPage());
        }
    }

    private @Nullable TerracottaPage terracottaPage;

    @FXThread
    public Node getTerracottaPage() {
        if (terracottaPage == null) {
            terracottaPage = new TerracottaPage();
        }
        return terracottaPage;
    }

}
