package dev.vexsoft.core.paper.reward.service;

import dev.vexsoft.core.paper.screenui.ScreenUi;
import dev.vexsoft.core.paper.screenui.reward.RewardCard;
import dev.vexsoft.core.paper.screenui.reward.RewardCardLayout;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import net.kyori.adventure.text.Component;

/** Entity-thread queue; only content changes and stack transitions produce screen updates. */
public final class RewardToastQueue implements AutoCloseable {

    private final ScreenUi screen;
    private final Component subtitle;
    private final List<Entry> visible = new ArrayList<>();
    private final LinkedHashMap<MergeKey, RewardCard> waiting = new LinkedHashMap<>();
    private long sequence;
    private long changed = Long.MIN_VALUE / 2;

    public RewardToastQueue(ScreenUi screen, Component subtitle) {
        this.screen = screen;
        this.subtitle = subtitle;
    }

    /** Returns false only when the bounded queue cannot accept another distinct entry. */
    public boolean push(RewardCard card, long now) {
        if (screen.isClosed()) {
            return false;
        }
        MergeKey key = key(card);
        if (card.reward().amount() != null) {
            for (Entry entry : visible) {
                if (key.equals(key(entry.card)) && now - entry.started < 55) {
                    entry.card = merge(entry.card, card);
                    entry.dirty = true;
                    if (now - entry.moved >= RewardCardLayout.REFLOW + 2) {
                        entry.previousSlot = visible.indexOf(entry);
                        entry.moved = now + 2;
                        entry.pulse = true;
                    }
                    return true;
                }
            }
        }
        if (!waiting.containsKey(key) && waiting.size() >= 64) {
            return false;
        }
        waiting.merge(key, card, RewardToastQueue::merge);
        return true;
    }

    /** Advances expiry and admission; animation between these updates is entirely client-side. */
    public void tick(long now) {
        if (screen.isClosed()) {
            waiting.clear();
            visible.clear();
            return;
        }
        if (now - changed >= RewardCardLayout.REFLOW + 2) {
            var previous = List.copyOf(visible);
            boolean removed = visible.removeIf(entry -> {
                if (now - entry.started >= RewardCardLayout.LIFETIME || now < entry.started - 4) {
                    screen.remove(entry.id);
                    return true;
                }
                return false;
            });
            boolean admitted = !waiting.isEmpty() && visible.size() < RewardCardLayout.CAPACITY;
            if (admitted) {
                var next = waiting.entrySet().iterator().next();
                visible.addFirst(new Entry("reward-" + sequence++, next.getValue(), now + 2));
                waiting.remove(next.getKey());
            }
            if (removed || admitted) {
                changed = now;
                for (int slot = 0; slot < visible.size(); slot++) {
                    Entry entry = visible.get(slot);
                    int old = previous.indexOf(entry);
                    if (old >= 0 && old != slot) {
                        entry.previousSlot = old;
                        entry.moved = now + 2;
                        entry.pulse = false;
                        entry.dirty = true;
                    }
                }
            }
        }
        for (int slot = 0; slot < visible.size(); slot++) {
            Entry entry = visible.get(slot);
            if (entry.dirty) {
                screen.rewardCard(entry.id, entry.card, subtitle, new RewardCardLayout(
                    slot, entry.previousSlot, entry.started, entry.moved, entry.pulse
                ));
                entry.dirty = false;
            }
        }
    }

    public boolean isEmpty() {
        return visible.isEmpty() && waiting.isEmpty();
    }

    @Override
    public void close() {
        waiting.clear();
        visible.clear();
        screen.close();
    }

    private MergeKey key(RewardCard card) {
        return new MergeKey(card.reward().identity(), card.reward().name(), card.icon().asString(),
            card.reward().amount() == null ? sequence++ : -1);
    }

    private static RewardCard merge(RewardCard first, RewardCard second) {
        return new RewardCard(first.reward().merge(second.reward()), first.icon());
    }

    private record MergeKey(String identity, Component name, String icon, long customSequence) {
    }

    private static final class Entry {

        private final String id;
        private final long started;
        private RewardCard card;
        private int previousSlot;
        private long moved;
        private boolean pulse;
        private boolean dirty = true;

        private Entry(String id, RewardCard card, long started) {
            this.id = id;
            this.card = card;
            this.started = started;
            moved = started;
        }
    }
}
