// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.auth.yggdrasil;

import com.google.gson.JsonObject;
import javafx.beans.binding.ObjectBinding;
import org.jackhuang.hmcl.auth.*;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.javafx.BindingMapping;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.*;

import static java.util.Objects.requireNonNull;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Stores a Yggdrasil identity, its current session, and cached profile properties.
@NotNullByDefault
public abstract class YggdrasilAccount extends ClassicAccount {

    /// The protocol service used for profile and skin operations.
    protected final YggdrasilService service;
    /// The fixed Minecraft profile ID.
    protected final UUID profileID;
    /// The login name accepted by the authentication server.
    protected final String loginName;

    /// The current credentials and selected profile.
    private YggdrasilSession session;

    /// Creates an account from a session with a selected profile and non-null profile ID.
    protected YggdrasilAccount(AccountID accountID, YggdrasilService service, String loginName, YggdrasilSession session) {
        super(accountID);
        this.service = requireNonNull(service);
        this.loginName = requireNonNull(loginName);
        this.profileID = requireNonNull(session.getSelectedProfile().getId());
        this.session = requireNonNull(session);

        addProfilePropertiesListener();
    }

    /// Keeps profile cache change notifications reachable.
    private ObjectBinding<Optional<CompleteGameProfile>> profilePropertiesBinding;

    /// Forwards profile cache changes to account observers.
    private void addProfilePropertiesListener() {
        // binding() is thread-safe
        // hold the binding so that it won't be garbage-collected
        profilePropertiesBinding = service.getProfileRepository().binding(profileID, true);
        // and it's safe to add a listener to an ObjectBinding which does not have any listener attached before (maybe tricky)
        profilePropertiesBinding.addListener((a, b, c) -> this.invalidate());
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
        service.getProfileRepository().getImmediately(profileID).ifPresent(profile ->
                privateData.add("profileProperties", JsonUtils.GSON.toJsonTree(profile.getProperties())));
    }

    /// Returns the protocol service associated with this account.
    public YggdrasilService getYggdrasilService() {
        return service;
    }

    @Override
    public void clearCache() {
        service.getProfileRepository().invalidate(profileID);
    }

    @Override
    public ObjectBinding<Optional<Map<TextureType, Texture>>> getTextures() {
        return BindingMapping.of(service.getProfileRepository().binding(getProfileID()))
                .map(profile -> profile.flatMap(it -> {
                    try {
                        return YggdrasilService.getTextures(it);
                    } catch (ServerResponseMalformedException e) {
                        LOG.warning("Failed to parse texture payload", e);
                        return Optional.empty();
                    }
                }));

    }

    @Override
    public String toString() {
        return "YggdrasilAccount[accountID=" + getAccountID() + ", profileID=" + profileID
                + ", loginName=" + loginName + "]";
    }
}
