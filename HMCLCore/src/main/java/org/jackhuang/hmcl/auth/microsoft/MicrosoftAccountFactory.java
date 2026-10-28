// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.auth.microsoft;

import com.google.gson.JsonObject;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.AccountFactory;
import org.jackhuang.hmcl.auth.AccountID;
import org.jackhuang.hmcl.auth.AuthenticationException;
import org.jackhuang.hmcl.auth.CharacterSelector;
import org.jackhuang.hmcl.auth.OAuth;
import org.jackhuang.hmcl.auth.yggdrasil.CompleteGameProfile;
import org.jackhuang.hmcl.auth.yggdrasil.GameProfile;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

/// Creates or restores Microsoft accounts using a caller-configured protocol service.
@NotNullByDefault
public class MicrosoftAccountFactory extends AccountFactory<MicrosoftAccount> {

    /// The service used for initial authentication and restored accounts.
    private final MicrosoftService service;

    /// Creates a factory using the supplied Microsoft authentication service.
    public MicrosoftAccountFactory(MicrosoftService service) {
        this.service = service;
    }

    @Override
    public AccountLoginType getLoginType() {
        return AccountLoginType.NONE;
    }

    @Override
    public MicrosoftAccount create(@Nullable CharacterSelector selector, @Nullable String username, @Nullable String password, @Nullable ProgressCallback progressCallback, Object additionalData) throws AuthenticationException {
        MicrosoftSession session = service.authenticate((OAuth.GrantFlow) additionalData);
        if (session.profile() == null) {
            session = service.refresh(session);
        }
        return new MicrosoftAccount(AccountID.generate(), service, session, true);
    }

    @Override
    public MicrosoftAccount fromStorage(JsonObject metadata, JsonObject privateData) {
        Objects.requireNonNull(metadata);
        Objects.requireNonNull(privateData);
        AccountID accountID = Account.readAccountID(metadata);
        MicrosoftSession session = MicrosoftSession.fromStorage(metadata, privateData);

        if (privateData.get("profileProperties") instanceof JsonObject profilePropertiesObject) {
            Map<String, String> properties = JsonUtils.GSON.fromJson(
                    profilePropertiesObject,
                    JsonUtils.mapTypeOf(String.class, String.class));
            MicrosoftSession.GameProfile profile = session.profile();
            var profileRepository = service.getProfileRepository();
            profileRepository.put(profile.id(), new CompleteGameProfile(new GameProfile(profile.id(), profile.name()), properties));
            profileRepository.invalidate(profile.id());
        }

        return new MicrosoftAccount(accountID, service, session, false);
    }
}
