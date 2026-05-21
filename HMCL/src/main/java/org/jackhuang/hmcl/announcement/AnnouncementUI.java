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

import javafx.scene.text.TextFlow;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.HTMLRenderer;
import org.jetbrains.annotations.NotNullByDefault;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.net.URI;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Renders announcement content for JavaFX UI surfaces.
@NotNullByDefault
public final class AnnouncementUI {
    private AnnouncementUI() {
    }

    /// Converts announcement HTML into a JavaFX text flow.
    ///
    /// @param html Announcement HTML.
    /// @return A JavaFX representation.
    public static TextFlow renderHtml(String html) {
        Document document = Jsoup.parseBodyFragment(html);
        HTMLRenderer renderer = new HTMLRenderer(AnnouncementUI::openLink);
        renderer.appendNode(document);
        renderer.mergeLineBreaks();
        TextFlow flow = renderer.render();
        flow.setLineSpacing(4);
        return flow;
    }

    private static void openLink(URI uri) {
        Controllers.confirm(i18n("web.open_in_browser", uri), i18n("message.confirm"), () -> FXUtils.openLink(uri.toString()), null);
    }
}
