// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.auth.authlibinjector;

import com.google.gson.JsonObject;
import org.glavo.uuid.UUIDs;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.AccountID;
import org.jackhuang.hmcl.auth.AccountFactory;
import org.jackhuang.hmcl.auth.AuthenticationException;
import org.jackhuang.hmcl.auth.CharacterSelector;
import org.jackhuang.hmcl.auth.NoCharacterException;
import org.jackhuang.hmcl.auth.yggdrasil.CompleteGameProfile;
import org.jackhuang.hmcl.auth.yggdrasil.GameProfile;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilSession;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.javafx.ObservableOptionalCache;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/// Creates or restores external accounts using a caller-supplied server lookup.
@NotNullByDefault
public class AuthlibInjectorAccountFactory extends AccountFactory<AuthlibInjectorAccount> {
    /// Resolves persisted server URLs to server objects.
    private final Function<String, AuthlibInjectorServer> serverLookup;

    /// Creates a factory that resolves persisted server URLs with the supplied lookup.
    public AuthlibInjectorAccountFactory(Function<String, AuthlibInjectorServer> serverLookup) {
        this.serverLookup = serverLookup;
    }

    @Override
    public AccountLoginType getLoginType() {
        return AccountLoginType.USERNAME_PASSWORD;
    }

    @Override
    public AuthlibInjectorAccount create(CharacterSelector selector, String username, String password, @Nullable ProgressCallback progressCallback, Object additionalData) throws AuthenticationException {
        Objects.requireNonNull(selector);
        Objects.requireNonNull(username);
        Objects.requireNonNull(password);

        AuthlibInjectorServer server = (AuthlibInjectorServer) additionalData;

        return authenticate(server, username, password, selector);
    }

    /// Authenticates a new account and selects a character when the server requires one.
    ///
    /// @throws NoCharacterException if authentication returns no selectable profiles
    /// @throws AuthenticationException if authentication or profile selection fails
    static AuthlibInjectorAccount authenticate(AuthlibInjectorServer server, String username, String password,
                                              CharacterSelector selector) throws AuthenticationException {
        var service = server.getYggdrasilService();
        YggdrasilSession session = service.authenticate(username, password, UUIDs.toCompactString(UUID.randomUUID()));
        if (session.getSelectedProfile() == null) {
            if (session.getAvailableProfiles() == null || session.getAvailableProfiles().isEmpty()) {
                throw new NoCharacterException();
            }
            GameProfile selected = selector.select(service, session.getAvailableProfiles());
            session = service.refresh(session.getAccessToken(), session.getClientToken(), selected);
        }
        return new AuthlibInjectorAccount(AccountID.generate(), server, username, session);
    }

    @Override
    public AuthlibInjectorAccount fromStorage(JsonObject metadata, JsonObject privateData) {
        Objects.requireNonNull(metadata);
        Objects.requireNonNull(privateData);

        @Nullable String apiRoot = JsonUtils.getString(metadata, "serverBaseURL");
        if (apiRoot == null) {
            throw new IllegalArgumentException("storage does not have API root.");
        }
        AuthlibInjectorServer server = serverLookup.apply(apiRoot);
        return fromStorage(metadata, privateData, server);
    }

    /// Restores an account and its cached profile properties for the supplied server.
    static AuthlibInjectorAccount fromStorage(
            JsonObject metadata,
            JsonObject privateData,
            AuthlibInjectorServer server) {
        AccountID accountID = Account.readAccountID(metadata);
        YggdrasilSession session = YggdrasilSession.fromStorage(metadata, privateData);

        @Nullable String loginName = JsonUtils.getString(metadata, "loginName");
        if (loginName == null) {
            throw new IllegalArgumentException("storage does not have loginName");
        }

        if (privateData.get("profileProperties") instanceof JsonObject profilePropertiesObject) {
            Map<String, String> properties = JsonUtils.GSON.fromJson(
                    profilePropertiesObject,
                    JsonUtils.mapTypeOf(String.class, String.class));
            GameProfile selected = session.getSelectedProfile();
            ObservableOptionalCache<UUID, CompleteGameProfile, AuthenticationException> profileRepository =
                    server.getYggdrasilService().getProfileRepository();
            profileRepository.put(selected.getId(), new CompleteGameProfile(selected, properties));
            profileRepository.invalidate(selected.getId());
        }

        return new AuthlibInjectorAccount(accountID, server, loginName, session);
    }
}
