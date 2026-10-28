// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.auth;

import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import org.jackhuang.hmcl.auth.yggdrasil.Texture;
import org.jackhuang.hmcl.auth.yggdrasil.TextureType;
import org.jackhuang.hmcl.util.ToStringBuilder;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.javafx.ObservableHelper;
import org.jetbrains.annotations.MustBeInvokedByOverriders;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/// Stores an account identity and exposes observable profile and persistence state.
@NotNullByDefault
public abstract class Account implements Observable {
    /// The serialized account ID property name.
    public static final String PROPERTY_ACCOUNT_ID = "accountID";

    /// The stable ID of this account entry.
    private final AccountID accountID;

    /// Creates an account.
    ///
    /// @param accountID the stable account entry ID
    protected Account(AccountID accountID) {
        this.accountID = Objects.requireNonNull(accountID);
    }

    /// Returns the stable ID of this account entry.
    public AccountID getAccountID() {
        return accountID;
    }

    /// Returns the cached player profile name.
    public abstract String getProfileName();

    /// Returns the player profile UUID.
    public abstract UUID getProfileID();

    /// Writes public account metadata into the target JSON object.
    ///
    /// Metadata is stored in `accounts.json` and must not contain credentials or cached private profile data.
    ///
    /// The target object is owned by the caller. Implementations may only mutate it during this method call and must
    /// not retain a reference to it.
    @MustBeInvokedByOverriders
    public void writeMetadata(JsonObject metadata) {
        metadata.addProperty(PROPERTY_ACCOUNT_ID, accountID.toString());
    }

    /// Writes private account data into the target JSON object.
    ///
    /// Private data is stored outside `accounts.json` and may contain credentials or cached profile data.
    ///
    /// The target object is owned by the caller. Implementations may only mutate it during this method call and must
    /// not retain a reference to it.
    @MustBeInvokedByOverriders
    public void writePrivateData(JsonObject privateData) {
    }

    /// Invalidates cached authentication or profile data maintained by this account.
    public void clearCache() {
    }

    /// Whether this account is stored with the local launcher configuration.
    private final BooleanProperty portable = new SimpleBooleanProperty(false);

    /// Returns the mutable property selecting local account storage.
    public BooleanProperty portableProperty() {
        return portable;
    }

    /// Returns whether this account is stored with the local launcher configuration.
    public boolean isPortable() {
        return portable.get();
    }

    /// Selects local or shared account storage.
    public void setPortable(boolean value) {
        this.portable.set(value);
    }

    /// Returns the stable account ID for the given serialized account record.
    ///
    /// @param storage the serialized account record
    /// @return the stable account ID, or `null` if the account record has no valid account ID
    public static @Nullable AccountID getAccountID(JsonObject storage) {
        @Nullable String accountID = JsonUtils.getString(storage, PROPERTY_ACCOUNT_ID);
        if (accountID == null) {
            return null;
        }

        try {
            return AccountID.parse(accountID);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /// Reads an account ID from serialized account storage.
    ///
    /// @param storage the account storage object
    /// @return the parsed account ID
    /// @throws IllegalArgumentException if the storage has no valid account ID
    public static AccountID readAccountID(JsonObject storage) {
        @Nullable String accountID = JsonUtils.getString(storage, PROPERTY_ACCOUNT_ID);
        if (accountID == null) {
            throw new IllegalArgumentException("accountID is missing");
        }

        return AccountID.parse(accountID);
    }

    /// Dispatches account invalidation notifications.
    private final ObservableHelper helper = new ObservableHelper(this);

    @Override
    public void addListener(InvalidationListener listener) {
        helper.addListener(listener);
    }

    @Override
    public void removeListener(InvalidationListener listener) {
        helper.removeListener(listener);
    }

    /// Schedules an account change notification on the JavaFX application thread.
    /// This method may be called from any thread after the JavaFX runtime has started.
    protected void invalidate() {
        Platform.runLater(helper::invalidate);
    }

    /// Returns an observable binding for cached profile textures, or an empty optional when unavailable.
    public ObjectBinding<Optional<Map<TextureType, Texture>>> getTextures() {
        return Bindings.createObjectBinding(Optional::empty);
    }

    @Override
    public final int hashCode() {
        return Objects.hash(portable.get(), accountID);
    }

    @Override
    public final boolean equals(@Nullable Object obj) {
        if (this == obj)
            return true;
        if (!(obj instanceof Account))
            return false;

        Account another = (Account) obj;
        return isPortable() == another.isPortable() && accountID.equals(another.accountID);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("accountID", accountID)
                .append("profileName", getProfileName())
                .append("profileID", getProfileID())
                .append("portable", isPortable())
                .toString();
    }
}
