# Controlled backend shutdown fallback

VexCore Paper and VexCore Velocity redirect players from a stopping backend to a configured lobby. This applies to every backend running VexCore, including servers without VexIsles. The lobby does not redirect to itself.

## Setup

1. Install the updated VexCore Paper JAR on the backends and the updated VexCore Velocity JAR on the proxy.
2. Start the proxy once to generate `plugins/VexSoft/VexCore/server-transfer.yml`.
3. Set `shutdown-fallback-server` to the lobby's exact backend name from `velocity.toml`:

   ```yaml
   shutdown-fallback-server: lobby
   ```

4. Restart the proxy after changing the file. The lobby must be registered and reachable.
5. Update VexIsles on Garden backends as well, so Garden shutdown leaves player disconnection to Core.

## Lifecycle

During a regular server stop, Core retires profile sessions, saves player data and waits for the network checkpoint before issuing the shutdown disconnect. The reason retains Minecraft's translated shutdown message and carries an invisible, versioned component insertion marker. The proxy recognizes this marker and sets Velocity's redirect result to the lobby.

The existing server-transfer service issues a single-use, short-lived fallback permit bound to that player connection, the stopped source and the configured target. Only this redirect skips another preparation request to the source, which has already drained and is stopping. Pending transfers are discarded without sending an abort that could reactivate the source profile. Ordinary server switches retain their preparation/admission checks.

Ordinary kicks, bans and kicks during connection attempts do not carry the shutdown marker. The lobby's own shutdown and an unavailable or unregistered lobby cannot produce a redirect loop. Disabling only the Core plugin does not trigger shutdown fallback. Failed persistence retains the existing recovery/export behavior and does not advertise a successfully drained source. Abrupt process termination cannot issue this controlled-shutdown marker.

Garden worlds remain frozen and save their final snapshot while players are still attached. Core initiates their lobby handover after player persistence; Garden's late native-world save protection remains in effect if player disconnection prevents an immediate world unload.

Automated tests cover actual scoped service registration and proxy configuration creation, redirects after the source connection is removed, ordinary kick handling, lobby loop prevention, connection-bound single-use permits, pending transfer cleanup, failed persistence, and Garden shutdown ordering. Live proxy/backend testing is still required after deployment.
