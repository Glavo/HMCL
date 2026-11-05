// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.auth.offline;

import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.AccountID;
import org.jackhuang.hmcl.util.StringUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

import static java.nio.charset.StandardCharsets.UTF_8;

/// Stores an offline player identity and optional skin configuration.
@NotNullByDefault
public class OfflineAccount extends Account {

    /// The optional offline skin configuration.
    private volatile @Nullable Skin skin;

    /// Creates an offline identity with an optional skin.
    ///
    /// @throws IllegalArgumentException if the profile name is blank
    public OfflineAccount(
            AccountID accountID,
            String profileName,
            UUID profileID,
            @Nullable Skin skin) {
        super(accountID, profileID, profileName);
        this.skin = skin;

        if (StringUtils.isBlank(profileName)) {
            throw new IllegalArgumentException("Profile name cannot be blank");
        }
    }

    /// Derives the standard offline Minecraft UUID from the exact player name.
    public static UUID getUUIDFromUserName(String username) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(UTF_8));
    }

    /// Returns the skin configuration, or `null` for the default skin.
    public @Nullable Skin getSkin() {
        return skin;
    }

    /// Replaces the skin configuration and notifies the owner.
    public void setSkin(@Nullable Skin skin) {
        this.skin = skin;
        changed();
    }

}
