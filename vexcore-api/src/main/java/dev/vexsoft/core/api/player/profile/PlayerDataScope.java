package dev.vexsoft.core.api.player.profile;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;
import java.util.Optional;

/** Selects account persistence by default or an explicitly participating profile domain. */
public final class PlayerDataScope {

    public static final PlayerDataScope ACCOUNT = new PlayerDataScope(null);

    @JsonProperty("domain")
    private final ProfileDomain domain;
    private final Optional<ProfileDomain> participatingDomain;

    @JsonCreator
    private PlayerDataScope(@JsonProperty("domain") final ProfileDomain domain) {
        this.domain = domain;
        participatingDomain = Optional.ofNullable(domain);
    }

    /** Creates a personal-profile scope; it never falls back to account storage. */
    public static PlayerDataScope profile(final ProfileDomain domain) {
        return new PlayerDataScope(Objects.requireNonNull(domain, "domain"));
    }

    /** Returns the participating domain, or empty for account data. */
    public Optional<ProfileDomain> domain() {
        return participatingDomain;
    }

    /** Whether this key uses the unchanged account address. */
    @JsonIgnore
    public boolean isAccount() {
        return domain == null;
    }

    @Override
    public boolean equals(final Object other) {
        return other instanceof PlayerDataScope scope && Objects.equals(domain, scope.domain);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(domain);
    }
}
