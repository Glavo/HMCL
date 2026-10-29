// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.auth.yggdrasil;

import com.google.gson.JsonObject;
import org.jackhuang.hmcl.auth.*;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.UUID;

import static java.util.Objects.requireNonNull;

/// Stores a Yggdrasil identity and its current session.
@NotNullByDefault
public abstract class YggdrasilAccount extends ClassicAccount {

    /// The fixed Minecraft profile ID.
    protected final UUID profileID;
    /// The login name accepted by the authentication server.
    protected final String loginName;

    /// The current credentials and selected profile.
    private YggdrasilSession session;

    /// Creates an account from a session with a selected profile and non-null profile ID.
    protected YggdrasilAccount(AccountID accountID, String loginName, YggdrasilSession session) {
        super(accountID);
        this.loginName = requireNonNull(loginName);
        this.profileID = requireNonNull(session.getSelectedProfile().getId());
        this.session = requireNonNull(session);
    }

    @Override
    public String getLoginName() {
        return loginName;
    }

    @Override
    public String getProfileName() {
        return session.getSelectedProfile().getName();
    }

    @Override
    public UUID getProfileID() {
        return session.getSelectedProfile().getId();
    }

    /// Returns the current session without validating or refreshing its credentials.
    public synchronized YggdrasilSession getSession() {
        return session;
    }

    /// Installs a session for the same profile and schedules an account change notification.
    ///
    /// @throws ServerResponseMalformedException if the selected profile differs or its name is missing;
    ///         the current session is retained
    public synchronized void updateSession(YggdrasilSession session) throws ServerResponseMalformedException {
        if (session.getSelectedProfile() == null || !profileID.equals(session.getSelectedProfile().getId())) {
            throw new ServerResponseMalformedException("Selected profile changed");
        }
        if (!session.hasProfileName()) {
            throw new ServerResponseMalformedException("Profile name is missing");
        }
        this.session = session;
        invalidate();
    }

    @Override
    public void writeMetadata(JsonObject metadata) {
        super.writeMetadata(metadata);
        metadata.addProperty("loginName", loginName);
        metadata.addProperty("profileID", profileID.toString());
    }

    @Override
    public void writePrivateData(JsonObject privateData) {
        super.writePrivateData(privateData);
        session.writePrivateData(privateData);
    }

    @Override
    public String toString() {
        return "YggdrasilAccount[accountID=" + getAccountID() + ", profileID=" + profileID
                + ", loginName=" + loginName + "]";
    }
}
