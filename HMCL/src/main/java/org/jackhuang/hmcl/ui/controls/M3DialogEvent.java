// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.controls;

import javafx.event.Event;
import javafx.event.EventType;
import javafx.scene.control.Button;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// Reports dialog presentation and dismissal, including the initiating action when present.
@NotNullByDefault
public final class M3DialogEvent extends Event {

    /// The parent of all Material dialog events.
    public static final EventType<M3DialogEvent> ANY = new EventType<>(Event.ANY, "M3_DIALOG");
    /// Fired after the entrance transition completes.
    public static final EventType<M3DialogEvent> SHOWN = new EventType<>(ANY, "M3_DIALOG_SHOWN");
    /// Fired before dismissal; consuming this event keeps the dialog open.
    public static final EventType<M3DialogEvent> CLOSE_REQUEST = new EventType<>(ANY, "M3_DIALOG_CLOSE_REQUEST");
    /// Fired after removal, including forced removal when the host disappears.
    public static final EventType<M3DialogEvent> HIDDEN = new EventType<>(ANY, "M3_DIALOG_HIDDEN");

    /// The action initiating dismissal, or `null` for other dismissal paths.
    private final @Nullable Button action;

    /// Creates an event whose source and target are the dialog.
    M3DialogEvent(M3Dialog dialog, EventType<M3DialogEvent> type, @Nullable Button action) {
        super(dialog, dialog, type);
        this.action = action;
    }

    /// Returns the initiating action, or `null` for Escape, scrim, programmatic or forced dismissal.
    public @Nullable Button getAction() {
        return action;
    }
}
