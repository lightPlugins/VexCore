package dev.vexsoft.core.paper.reward.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.paper.screenui.ScreenUi;
import dev.vexsoft.core.paper.screenui.UiTexture;
import dev.vexsoft.core.paper.screenui.layout.TextBlockLayout;
import dev.vexsoft.core.paper.screenui.layout.TextureLayout;
import dev.vexsoft.core.paper.screenui.reward.RewardCard;
import dev.vexsoft.core.paper.screenui.reward.RewardCardLayout;
import dev.vexsoft.core.reward.notification.RewardNotification;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

/** Exercises burst admission, finite aggregation, smooth reflow metadata and idle cleanup. */
public final class RewardToastQueueTest {

    @Test
    void newestAppearsAtBottomAndExistingCardsReceiveOneSharedReflowClock() {
        var screen = new RecordingScreen();
        var queue = new RewardToastQueue(screen, Component.text("Erhalten"));
        assertTrue(queue.push(card("stone", "2"), 23998));
        queue.tick(23998);
        long born = screen.layouts.get("reward-0").started();
        assertTrue(queue.push(card("wheat", "8"), 23999));
        queue.tick(24006);
        assertEquals(0, screen.layouts.get("reward-1").slot());
        assertEquals(1, screen.layouts.get("reward-0").slot());
        assertEquals(0, screen.layouts.get("reward-0").previousSlot());
        assertEquals(born, screen.layouts.get("reward-0").started());
        assertEquals(screen.layouts.get("reward-1").moved(), screen.layouts.get("reward-0").moved());
        int writes = screen.writes;
        queue.tick(24007);
        assertEquals(writes, screen.writes);
    }

    @Test
    void mergesExactHugeQuantitiesButDoesNotExtendLifetimeOrMergeUpgradeVariants() {
        var screen = new RecordingScreen();
        var queue = new RewardToastQueue(screen, Component.empty());
        queue.push(card("ore", "52000000000000000"), 0);
        queue.tick(0);
        queue.push(card("ore", "3"), 10);
        queue.tick(10);
        assertEquals(new BigDecimal("52000000000000003"), screen.cards.get("reward-0").reward().amount());
        assertTrue(screen.layouts.get("reward-0").pulse());
        assertEquals(2, screen.layouts.get("reward-0").started());
        queue.push(card("ore:upgrade1", "1"), 11);
        queue.tick(11);
        assertEquals(2, screen.cards.size());
        for (int tick = 12; tick < 200; tick++) {
            queue.tick(tick);
        }
        assertTrue(queue.isEmpty());
        assertTrue(screen.cards.isEmpty());
    }

    @Test
    void boundedBurstNeverExceedsFourCardsAndDrainsWithoutIdleWrites() {
        var screen = new RecordingScreen();
        var queue = new RewardToastQueue(screen, Component.empty());
        for (int index = 0; index < 64; index++) {
            assertTrue(queue.push(card("item" + index, "1"), 0));
        }
        assertFalse(queue.push(card("overflow", "1"), 0));
        for (int tick = 0; tick < 1600; tick++) {
            queue.tick(tick);
            assertTrue(screen.cards.size() <= 4);
        }
        assertTrue(queue.isEmpty());
        int writes = screen.writes;
        queue.tick(1601);
        assertEquals(writes, screen.writes);
        queue.close();
        assertFalse(queue.push(card("late", "1"), 1602));
    }

    private static RewardCard card(String id, String amount) {
        return new RewardCard(new RewardNotification(id, Component.text(id), "minecraft:paper", new BigDecimal(amount)),
            Key.key("vexcore:reward/icon/vanilla/paper"));
    }

    private static final class RecordingScreen implements ScreenUi {

        private final Map<String, RewardCard> cards = new HashMap<>();
        private final Map<String, RewardCardLayout> layouts = new HashMap<>();
        private int writes;
        @Getter(onMethod_ = @Override)
        private boolean closed;

        @Override
        public void rewardCard(String id, RewardCard card, Component subtitle, RewardCardLayout layout) {
            cards.put(id, card);
            layouts.put(id, layout);
            writes++;
        }

        @Override
        public void textBlock(String id, List<Component> lines, TextBlockLayout layout) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void textureBlock(String id, UiTexture texture, TextureLayout layout) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setLines(String id, List<Component> lines) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void remove(String id) {
            cards.remove(id);
            layouts.remove(id);
        }

        @Override
        public void close() {
            cards.clear();
            layouts.clear();
            closed = true;
        }
    }
}
