// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.auth.offline;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import javafx.beans.binding.ObjectBinding;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.AccountID;
import org.jackhuang.hmcl.auth.yggdrasil.Texture;
import org.jackhuang.hmcl.auth.yggdrasil.TextureType;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.ToStringBuilder;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static java.util.Objects.requireNonNull;

/// Stores an offline player identity and optional skin configuration.
@NotNullByDefault
public class OfflineAccount extends Account {

    /// The nonblank player name.
    private final String profileName;
    /// The player UUID used for launching.
    private final UUID profileID;
    /// The optional offline skin configuration.
    private @Nullable Skin skin;

    /// Creates an offline identity with an optional skin.
    ///
    /// @throws IllegalArgumentException if the profile name is blank
    protected OfflineAccount(
            AccountID accountID,
            String profileName,
            UUID profileID,
            @Nullable Skin skin) {
        super(accountID);
        this.profileName = requireNonNull(profileName);
        this.profileID = requireNonNull(profileID);
        this.skin = skin;

        if (StringUtils.isBlank(profileName)) {
            throw new IllegalArgumentException("Profile name cannot be blank");
        }
    }

    @Override
    public UUID getProfileID() {
        return profileID;
    }

    @Override
    public String getProfileName() {
        return profileName;
    }

    /// Returns the skin configuration, or `null` for the default skin.
    public @Nullable Skin getSkin() {
        return skin;
    }

    /// Replaces the skin configuration and schedules an account change notification.
    public void setSkin(@Nullable Skin skin) {
        this.skin = skin;
        invalidate();
    }

    @Override
    public void writeMetadata(JsonObject metadata) {
        super.writeMetadata(metadata);
        metadata.addProperty("profileID", profileID.toString());
        metadata.addProperty("profileName", profileName);
        if (skin == null) {
            metadata.add("skin", JsonNull.INSTANCE);
        } else {
            JsonObject skinStorage = new JsonObject();
            skin.writeStorage(skinStorage);
            metadata.add("skin", skinStorage);
        }
    }

    @Override
    public ObjectBinding<Optional<Map<TextureType, Texture>>> getTextures() {
        return super.getTextures();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("accountID", getAccountID())
                .append("profileName", profileName)
                .append("profileID", profileID)
                .toString();
    }
}
