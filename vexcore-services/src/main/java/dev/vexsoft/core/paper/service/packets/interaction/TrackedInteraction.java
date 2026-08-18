package dev.vexsoft.core.paper.service.packets.interaction;

import dev.vexsoft.core.paper.packets.display.DisplayLifecycle;
import dev.vexsoft.core.paper.packets.interaction.FakeInteractHandler;
import dev.vexsoft.core.paper.packets.interaction.FakeInteractionHandle;
import java.util.Set;
import lombok.Value;

/** Internal association between a virtual interaction entity and its callback policy. */
@Value
public class TrackedInteraction {
  FakeInteractionHandle handle;
  FakeInteractHandler interactHandler;
  Set<DisplayLifecycle> lifecycle;
}
