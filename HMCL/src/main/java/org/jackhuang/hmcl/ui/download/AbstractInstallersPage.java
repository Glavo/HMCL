/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui.download;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXTextField;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.LibraryAnalyzer;
import org.jackhuang.hmcl.ui.Controllers;
import javafx.scene.layout.BorderPane;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.InstallerItem;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.wizard.WizardController;
import org.jackhuang.hmcl.ui.wizard.WizardPage;

import java.util.Map;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public abstract class AbstractInstallersPage extends Control implements WizardPage {
    protected final WizardController controller;

    protected InstallerItem.InstallerItemGroup group;
    protected JFXTextField txtName = new JFXTextField();

    protected BooleanProperty installable = new SimpleBooleanProperty();

    public AbstractInstallersPage(WizardController controller, String gameVersion, DownloadProvider downloadProvider) {
        this.controller = controller;
        this.group = new InstallerItem.InstallerItemGroup(gameVersion, getInstallerItemStyle());

        for (InstallerItem library : group.getLibraries()) {
            String libraryId = library.getLibraryId();
            if (libraryId.equals(LibraryAnalyzer.LibraryType.MINECRAFT.getPatchId())) continue;
            library.setOnInstall(() -> {
                if (LibraryAnalyzer.LibraryType.FABRIC_API.getPatchId().equals(libraryId)) {
                    Controllers.dialog(i18n("install.installer.fabric-api.warning"), i18n("message.warning"), MessageDialogPane.MessageType.WARNING);
                }

                if (!(library.resolvedStateProperty().get() instanceof InstallerItem.IncompatibleState))
                    controller.onNext(new VersionsPage(controller, i18n("install.installer.choose", i18n("install.installer." + libraryId)), gameVersion, downloadProvider, libraryId, () -> controller.onPrev(false)));
            });
            library.setOnRemove(() -> {
                controller.getSettings().remove(libraryId);
                reload();
            });
        }
    }

    protected InstallerItem.Style getInstallerItemStyle() {
        return InstallerItem.Style.CARD;
    }

    @Override
    public abstract String getTitle();

    protected abstract void reload();

    @Override
    public void onNavigate(Map<String, Object> settings) {
        reload();
    }

    @Override
    public abstract void cleanup(Map<String, Object> settings);

    protected abstract void onInstall();

    @Override
    protected Skin<?> createDefaultSkin() {
        return new InstallersPageSkin(this);
    }

    protected static class InstallersPageSkin extends SkinBase<AbstractInstallersPage> {
        /**
         * Constructor for all SkinBase instances.
         *
         * @param control The control for which this Skin should attach to.
         */
        protected InstallersPageSkin(AbstractInstallersPage control) {
            super(control);

            BorderPane root = new BorderPane();
            root.setPadding(new Insets(16));

            {
                HBox versionNamePane = new HBox(8);
                versionNamePane.getStyleClass().add("card-non-transparent");
                versionNamePane.setStyle("-fx-padding: 20 8 20 16");
                versionNamePane.setAlignment(Pos.CENTER_LEFT);

                control.txtName.setMaxWidth(300);
                versionNamePane.getChildren().setAll(new Label(i18n("version.name")), control.txtName);
                root.setTop(versionNamePane);
            }

            {
                InstallerItem[] libraries = control.group.getLibraries();

                FlowPane libraryPane = new FlowPane(libraries);
                libraryPane.setVgap(8);
                libraryPane.setHgap(16);

                ScrollPane scrollPane = new ScrollPane(libraryPane);
                scrollPane.setFitToWidth(true);
                scrollPane.setFitToHeight(true);
                BorderPane.setMargin(scrollPane, new Insets(16, 0, 16, 0));
                root.setCenter(scrollPane);
            }

            {
                JFXButton installButton = FXUtils.newRaisedButton(i18n("button.install"));
                installButton.disableProperty().bind(control.installable.not());
                installButton.setPrefWidth(100);
                installButton.setPrefHeight(40);
                installButton.setOnAction(e -> control.onInstall());
                BorderPane.setAlignment(installButton, Pos.CENTER_RIGHT);
                root.setBottom(installButton);
            }

            getChildren().setAll(root);
        }
    }
}
