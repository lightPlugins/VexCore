package dev.vexsoft.core.common.service.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vexsoft.core.api.player.profile.PlayerDataScope;
import dev.vexsoft.core.api.player.profile.ProfileDomain;
import org.junit.jupiter.api.Test;

/** Verifies persisted scope declarations remain explicit and account defaults remain compatible. */
public final class PlayerDataScopeSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void accountAndExplicitDomainRoundTripWithoutDerivedProperties() throws Exception {
        var account = mapper.writeValueAsString(PlayerDataScope.ACCOUNT);
        assertEquals(PlayerDataScope.ACCOUNT, mapper.readValue(account, PlayerDataScope.class));
        var scoped = PlayerDataScope.profile(new ProfileDomain("example:adventure"));
        var serialized = mapper.writeValueAsString(scoped);
        assertFalse(serialized.contains("account"));
        assertEquals(scoped, mapper.readValue(serialized, PlayerDataScope.class));
    }

    @Test
    void malformedExplicitDomainFailsInsteadOfFallingBackToAccount() {
        assertThrows(Exception.class, () -> mapper.readValue("{\"domain\":{\"value\":\"invalid\"}}",
            PlayerDataScope.class));
    }
}
