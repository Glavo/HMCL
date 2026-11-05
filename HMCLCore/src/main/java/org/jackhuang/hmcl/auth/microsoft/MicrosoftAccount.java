// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.auth.microsoft;

import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.AccountID;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jackhuang.hmcl.auth.ServerResponseMalformedException;
import org.jackhuang.hmcl.auth.CredentialExpiredException;
import java.util.UUID;

/// Holds a Microsoft game identity and its accepted credentials.
@NotNullByDefault
public final class MicrosoftAccount extends Account {
    /// The accepted credentials, or null when reauthentication is required.
    private @Nullable MicrosoftSession session;
    /// Whether the current credentials have been validated in this process.
    private boolean authenticated;

    /// Creates a saved identity with its last known profile name.
    public MicrosoftAccount(AccountID accountID, UUID profileID, String profileName) {
        super(accountID, profileID, profileName);
    }

    /// Creates an identity with previously accepted credentials; no network validation is performed.
    public MicrosoftAccount(AccountID accountID, UUID profileID, String profileName, @Nullable MicrosoftSession session) {
        this(accountID, profileID, profileName);
        if (session != null && !(session.profile() != null && getProfileID().equals(session.profile().id()))) throw new IllegalArgumentException("Session profile mismatch");
        this.session = session;
    }

    /// Returns stored credentials without validating them, or null when unavailable.
    public synchronized @Nullable MicrosoftSession getSession() {
        return session;
    }

    /// Returns stored credentials or requests interactive authentication when unavailable.
    public synchronized MicrosoftSession requireSession() throws CredentialExpiredException {
        if (session == null) throw new CredentialExpiredException("Account credentials are missing");
        return session;
    }

    /// Accepts credentials for the same profile and notifies the owner.
    public synchronized void updateSession(MicrosoftSession session) throws ServerResponseMalformedException {
        if (!(session.profile() != null && getProfileID().equals(session.profile().id())) || !session.hasProfileName())
            throw new ServerResponseMalformedException("Session profile mismatch or missing profile name");
        this.session = session;
        updateProfileName(session.profile().name());
        authenticated = true;
        sessionChanged();
    }

    /// Returns whether these credentials have been validated in this process.
    public synchronized boolean isAuthenticated() {
        return authenticated;
    }

    /// Requires validation on the next login without discarding stored credentials.
    public synchronized void invalidateAuthentication() {
        authenticated = false;
        invalidateAuthenticationResults();
    }
}
