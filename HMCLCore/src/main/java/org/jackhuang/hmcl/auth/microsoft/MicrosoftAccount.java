// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.auth.microsoft;

import com.google.gson.JsonObject;
import javafx.beans.binding.ObjectBinding;
import org.jackhuang.hmcl.auth.*;
import org.jackhuang.hmcl.auth.yggdrasil.CompleteGameProfile;
import org.jackhuang.hmcl.auth.yggdrasil.Texture;
import org.jackhuang.hmcl.auth.yggdrasil.TextureType;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilService;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.javafx.BindingMapping;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static java.util.Objects.requireNonNull;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Stores a Microsoft identity, its current session, and cached profile properties.
@NotNullByDefault
public final class MicrosoftAccount extends OAuthAccount {

    /// The protocol service used for profile and skin operations.
    protected final MicrosoftService service;
    /// The fixed Minecraft profile ID.
    protected final UUID profileID;

    /// Whether the current session has been authenticated in this process.
    private boolean authenticated;
    /// The current credentials and profile.
    private MicrosoftSession session;

    /// Creates an account from a session with a non-null profile ID.
    ///
    /// @param authenticated whether the caller has already authenticated this session
    protected MicrosoftAccount(AccountID accountID, MicrosoftService service, MicrosoftSession session, boolean authenticated) {
        super(accountID);
        this.service = requireNonNull(service);
        this.session = requireNonNull(session);
        this.authenticated = authenticated;
        this.profileID = requireNonNull(session.profile().id());

        addProfilePropertiesListener();
    }

    /// Keeps profile cache change notifications reachable.
    @SuppressWarnings("FieldCanBeLocal")
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
        service.getProfileRepository().getImmediately(profileID).ifPresent(profile ->
                privateData.add("profileProperties", JsonUtils.GSON.toJsonTree(profile.getProperties())));
    }

    /// Returns the protocol service associated with this account.
    public MicrosoftService getService() {
        return service;
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
    public synchronized void clearCache() {
        authenticated = false;
        service.getProfileRepository().invalidate(profileID);
    }

    @Override
    public String toString() {
        return "MicrosoftAccount[accountID=" + getAccountID() + ", profileID=" + profileID
                + ", name=" + getProfileName() + "]";
    }
}
