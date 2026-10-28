// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.auth.offline;

import com.google.gson.JsonObject;
import org.glavo.uuid.UUIDs;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.AccountFactory;
import org.jackhuang.hmcl.auth.AccountID;
import org.jackhuang.hmcl.auth.CharacterSelector;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

import static java.nio.charset.StandardCharsets.UTF_8;

/// Creates or restores offline player identities without preparing launch resources.
@NotNullByDefault
public final class OfflineAccountFactory extends AccountFactory<OfflineAccount> {
    /// Creates a factory for offline identities.
    public OfflineAccountFactory() {
    }

    @Override
    public AccountLoginType getLoginType() {
        return AccountLoginType.USERNAME;
    }

    /// Creates an offline account with the supplied identity and no custom skin.
    public OfflineAccount create(String username, UUID uuid) {
        return new OfflineAccount(AccountID.generate(), username, uuid, null);
    }

    @Override
    public OfflineAccount create(@Nullable CharacterSelector selector, String username, @Nullable String password, @Nullable ProgressCallback progressCallback, @Nullable Object additionalData) {
        AdditionalData data;
        UUID uuid;
        @Nullable Skin skin;
        if (additionalData != null) {
            data = (AdditionalData) additionalData;
            uuid = data.uuid == null ? getUUIDFromUserName(username) : data.uuid;
            skin = data.skin;
        } else {
            uuid = getUUIDFromUserName(username);
            skin = null;
        }
        return new OfflineAccount(AccountID.generate(), username, uuid, skin);
    }

    @Override
    public OfflineAccount fromStorage(JsonObject metadata, JsonObject privateData) {
        AccountID accountID = Account.readAccountID(metadata);
        @Nullable String profileName = JsonUtils.getString(metadata, "profileName");
        if (profileName == null) {
            throw new IllegalStateException("Offline account configuration malformed.");
        }
        @Nullable String profileIDText = JsonUtils.getString(metadata, "profileID");
        UUID profileID = profileIDText != null
                ? UUIDs.parse(profileIDText)
                : getUUIDFromUserName(profileName);
        @Nullable Skin skin = Skin.fromStorage(metadata.get("skin") instanceof JsonObject skinObject ? skinObject : null);

        return new OfflineAccount(accountID, profileName, profileID, skin);
    }

    /// Derives the standard offline Minecraft UUID from the exact player name.
    public static UUID getUUIDFromUserName(String username) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(UTF_8));
    }

    /// Optional identity and skin overrides for offline account creation.
    @NotNullByDefault
    public static class AdditionalData {
        /// An explicit UUID, or `null` to derive it from the player name.
        private final @Nullable UUID uuid;
        /// The optional skin configuration.
        private final @Nullable Skin skin;

        /// Creates optional overrides; either value may be `null`.
        public AdditionalData(@Nullable UUID uuid, @Nullable Skin skin) {
            this.uuid = uuid;
            this.skin = skin;
        }
    }

}
