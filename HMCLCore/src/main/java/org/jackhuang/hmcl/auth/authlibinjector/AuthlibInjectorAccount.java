// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.auth.authlibinjector;

import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilSession;
import org.jackhuang.hmcl.auth.AccountID;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jackhuang.hmcl.auth.ServerResponseMalformedException;
import org.jackhuang.hmcl.auth.CredentialExpiredException;

import java.util.Objects;
import java.util.UUID;

/// Stores a Yggdrasil account associated with an authlib-injector server.
@NotNullByDefault
public class AuthlibInjectorAccount extends Account {
    /// The accepted credentials, or null when reauthentication is required.
    private @Nullable YggdrasilSession session;

    /// The login name used for reauthentication.
    private final String loginName;

    /// The authentication server associated with this account.
    private final AuthlibInjectorServer server;

    /// Creates an external identity independently of its credentials.
    public AuthlibInjectorAccount(
            AccountID accountID,
            AuthlibInjectorServer server,
            String username,
            UUID profileID, String profileName) {
        super(accountID, profileID, profileName);
        this.loginName = Objects.requireNonNull(username);
        this.server = Objects.requireNonNull(server);
    }

    /// Returns the login name used by the authentication provider.
    public String getLoginName() {
        return loginName;
    }

    /// Returns the authentication server associated with this account.
    public AuthlibInjectorServer getServer() {
        return server;
    }

    /// Creates an identity with previously accepted credentials; no network validation is performed.
    public AuthlibInjectorAccount(AccountID accountID, AuthlibInjectorServer server, String username,
                                  UUID profileID, String profileName, @Nullable YggdrasilSession session) {
        this(accountID, server, username, profileID, profileName);
        if (session != null && !(session.getSelectedProfile() != null && getProfileID().equals(session.getSelectedProfile().getId()))) throw new IllegalArgumentException("Session profile mismatch");
        this.session = session;
    }

    /// Returns stored credentials without validating them, or null when unavailable.
    public synchronized @Nullable YggdrasilSession getSession() {
        return session;
    }

    /// Returns stored credentials or requests interactive authentication when unavailable.
    public synchronized YggdrasilSession requireSession() throws CredentialExpiredException {
        if (session == null) throw new CredentialExpiredException("Account credentials are missing");
        return session;
    }

    /// Accepts credentials for the same profile and notifies the owner.
    public synchronized void updateSession(YggdrasilSession session) throws ServerResponseMalformedException {
        if (!(session.getSelectedProfile() != null && getProfileID().equals(session.getSelectedProfile().getId())) || !session.hasProfileName())
            throw new ServerResponseMalformedException("Session profile mismatch or missing profile name");
        this.session = session;
        updateProfileName(session.getSelectedProfile().getName());
        sessionChanged();
    }
}
