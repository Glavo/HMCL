// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.auth.authlibinjector;

import com.google.gson.JsonObject;
import org.jackhuang.hmcl.auth.AccountFactory;
import org.jackhuang.hmcl.auth.AuthenticationException;
import org.jackhuang.hmcl.auth.CharacterSelector;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilService;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/// Creates or restores external accounts associated with one authentication server.
@NotNullByDefault
public class BoundAuthlibInjectorAccountFactory extends AccountFactory<AuthlibInjectorAccount> {
    /// The server used for all accounts created by this factory.
    private final AuthlibInjectorServer server;

    /// The launcher-managed protocol service used for authentication and cached profiles.
    private final YggdrasilService service;

    /// Creates a factory bound to the supplied server and protocol service.
    public BoundAuthlibInjectorAccountFactory(AuthlibInjectorServer server, YggdrasilService service) {
        this.server = Objects.requireNonNull(server);
        this.service = Objects.requireNonNull(service);
    }

    @Override
    public AccountLoginType getLoginType() {
        return AccountLoginType.USERNAME_PASSWORD;
    }

    /// Returns the server to which this factory is bound.
    public AuthlibInjectorServer getServer() {
        return server;
    }

    @Override
    public AuthlibInjectorAccount create(CharacterSelector selector, String username, String password, @Nullable ProgressCallback progressCallback, @Nullable Object additionalData) throws AuthenticationException {
        Objects.requireNonNull(selector);
        Objects.requireNonNull(username);
        Objects.requireNonNull(password);

        return AuthlibInjectorAccountFactory.authenticate(server, service, username, password, selector);
    }

    @Override
    public AuthlibInjectorAccount fromStorage(JsonObject metadata, JsonObject privateData) {
        return AuthlibInjectorAccountFactory.fromStorage(metadata, privateData, server, service);
    }
}
