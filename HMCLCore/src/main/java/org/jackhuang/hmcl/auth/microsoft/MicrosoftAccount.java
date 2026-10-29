// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.auth.microsoft;

import com.google.gson.JsonObject;
import org.jackhuang.hmcl.auth.*;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.UUID;

import static java.util.Objects.requireNonNull;

/// Stores a Microsoft identity and its current session.
@NotNullByDefault
public final class MicrosoftAccount extends OAuthAccount {

    /// The fixed Minecraft profile ID.
    protected final UUID profileID;

    /// Whether the current session has been authenticated in this process.
    private boolean authenticated;
    /// The current credentials and profile.
    private MicrosoftSession session;

    /// Creates an account from a session with a non-null profile ID.
    ///
    /// @param authenticated whether the caller has already authenticated this session
    protected MicrosoftAccount(AccountID accountID, MicrosoftSession session, boolean authenticated) {
        super(accountID);
        this.session = requireNonNull(session);
        this.authenticated = authenticated;
        this.profileID = requireNonNull(session.profile().id());
    }

    @Override
    public String getProfileName() {
        return session.profile().name();
    }

    @Override
    public UUID getProfileID() {
        return session.profile().id();
    }

    /// Returns the current session without validating or refreshing its credentials.
    public synchronized MicrosoftSession getSession() {
        return session;
    }

    /// Returns whether this session has been authenticated since loading or clearing the cache.
    public synchronized boolean isAuthenticated() {
        return authenticated;
    }

    /// Installs an authenticated session for this profile and schedules a change notification.
    /// Passing the current session marks it authenticated without scheduling a notification.
    ///
    /// @throws ServerResponseMalformedException if the profile differs or its name is missing;
    ///         the current session and authentication state are retained
    public synchronized void updateSession(MicrosoftSession session) throws ServerResponseMalformedException {
        if (session.profile() == null || !profileID.equals(session.profile().id())) {
            throw new ServerResponseMalformedException("Selected profile changed");
        }
        if (!session.hasProfileName()) {
            throw new ServerResponseMalformedException("Profile name is missing");
        }
        boolean changed = this.session != session;
        this.session = session;
        authenticated = true;
        if (changed) {
            invalidate();
        }
    }

    @Override
    public void writeMetadata(JsonObject metadata) {
        super.writeMetadata(metadata);
        metadata.addProperty("profileID", getProfileID().toString());
    }

    @Override
    public void writePrivateData(JsonObject privateData) {
        super.writePrivateData(privateData);
        session.writePrivateData(privateData);
    }

    /// Marks the current session as requiring validation without changing its stored credentials.
    public synchronized void invalidateAuthentication() {
        authenticated = false;
    }

    @Override
    public String toString() {
        return "MicrosoftAccount[accountID=" + getAccountID() + ", profileID=" + profileID
                + ", name=" + getProfileName() + "]";
    }
}
