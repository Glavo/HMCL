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
package org.jackhuang.hmcl.announcement;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXDialogLayout;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.construct.DialogCloseEvent;
import org.jackhuang.hmcl.ui.construct.JFXHyperlink;
import org.jackhuang.hmcl.util.FXThread;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.HashSet;
import java.util.Set;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Shows popup announcements one at a time after the JavaFX UI is ready.
@NotNullByDefault
public final class AnnouncementPopupController {
    private static final Set<String> SHOWN_THIS_SESSION = new HashSet<>();
    private static boolean initialized;
    private static boolean showing;

    private AnnouncementPopupController() {
    }

    /// Installs listeners and attempts to display already loaded popup announcements.
    @FXThread
    public static void init() {
        if (initialized) {
            return;
        }

        initialized = true;
        AnnouncementManager.popupAnnouncementsProperty().addListener((ListChangeListener<? super Announcement>) change -> showNext());
        showNext();
    }

    private static void showNext() {
        if (showing) {
            return;
        }

        for (Announcement announcement : AnnouncementManager.popupAnnouncementsProperty()) {
            String id = announcement.getId();
            if (id == null || SHOWN_THIS_SESSION.contains(id)) {
                continue;
            }

            SHOWN_THIS_SESSION.add(id);
            showing = true;
            show(announcement);
            return;
        }
    }

    private static void show(Announcement announcement) {
        JFXDialogLayout layout = new JFXDialogLayout();
        layout.setHeading(new Label(announcement.getLocalizedTitle()));

        String content = announcement.getLocalizedContent();
        String link = announcement.getLocalizedLink();
        if (content != null) {
            ScrollPane scrollPane = new ScrollPane(AnnouncementUI.renderHtml(content));
            scrollPane.setFitToWidth(true);
            scrollPane.setMaxHeight(360);
            layout.setBody(scrollPane);
        } else if (link != null) {
            JFXHyperlink hyperlink = new JFXHyperlink(i18n("announcement.open"));
            hyperlink.setExternalLink(link);
            layout.setBody(hyperlink);
        }

        JFXButton okButton = new JFXButton(i18n("button.ok"));
        okButton.getStyleClass().add("dialog-accept");
        okButton.setOnAction(event -> {
            closeAnnouncement(announcement);
            layout.fireEvent(new DialogCloseEvent());
        });

        layout.setActions(okButton);

        layout.setMaxWidth(520);
        Controllers.dialog(layout);
    }

    private static void closeAnnouncement(Announcement announcement) {
        if (announcement.isShowOnce()) {
            AnnouncementManager.dismiss(announcement);
        }

        showing = false;
        Platform.runLater(AnnouncementPopupController::showNext);
    }
}
