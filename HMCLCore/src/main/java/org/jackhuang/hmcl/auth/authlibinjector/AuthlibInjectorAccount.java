// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.auth.authlibinjector;

import com.google.gson.JsonObject;
import org.jackhuang.hmcl.auth.*;
import org.jackhuang.hmcl.auth.yggdrasil.CompleteGameProfile;
import org.jackhuang.hmcl.auth.yggdrasil.TextureType;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilAccount;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilSession;
import org.jackhuang.hmcl.util.ToStringBuilder;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;

import static java.util.Collections.emptySet;
import static java.util.Collections.unmodifiableSet;

/// Stores a Yggdrasil account associated with an authlib-injector server.
@NotNullByDefault
public class AuthlibInjectorAccount extends YggdrasilAccount {
    /// The authentication server associated with this account.
    private final AuthlibInjectorServer server;

    /// Creates an account from a session with a selected profile.
    public AuthlibInjectorAccount(
            AccountID accountID,
            AuthlibInjectorServer server,
            String username,
            YggdrasilSession session) {
        super(accountID, server.getYggdrasilService(), username, session);
        this.server = server;
    }

    @Override
    public void writeMetadata(JsonObject metadata) {
        metadata.addProperty("serverBaseURL", server.getUrl());
        super.writeMetadata(metadata);
    }

    @Override
    public void clearCache() {
        super.clearCache();
        server.invalidateMetadataCache();
    }

    /// Returns the authentication server associated with this account.
    public AuthlibInjectorServer getServer() {
        return server;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("accountID", getAccountID())
                .append("profileID", profileID)
                .append("loginName", getLoginName())
                .append("server", getServer().getUrl())
                .toString();
    }

    /// Returns an immutable set of recognized texture types advertised as uploadable by the profile.
    public static @Unmodifiable Set<TextureType> getUploadableTextures(CompleteGameProfile profile) {
        @Nullable String prop = profile.getProperties().get("uploadableTextures");
        if (prop == null)
            return emptySet();
        Set<TextureType> result = EnumSet.noneOf(TextureType.class);
        for (String val : prop.split(",")) {
            val = val.toUpperCase(Locale.ROOT);
            TextureType parsed;
            try {
                parsed = TextureType.valueOf(val);
            } catch (IllegalArgumentException e) {
                continue;
            }
            result.add(parsed);
        }
        return unmodifiableSet(result);
    }
}
