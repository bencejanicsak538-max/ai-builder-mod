package hu.bence.aibuilder;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Epitesi elozmenyek - utolso MAX_HISTORY epitest tarolja jatekosokenkent.
 * /aihistory -> listazas
 * /aihistory <id> -> ujraepites
 */
public class BuildHistory {

    public static final int MAX_HISTORY = 10;

    public static class Entry {
        public final int id;
        public final String prompt;
        public final String style;
        public final long timestamp;
        public final int blockCount;
        public final BuildPlan plan;

        public Entry(int id, String prompt, String style, int blockCount, BuildPlan plan) {
            this.id = id;
            this.prompt = prompt;
            this.style = style;
            this.timestamp = System.currentTimeMillis();
            this.blockCount = blockCount;
            this.plan = plan;
        }

        public String timeAgo() {
            long sec = (System.currentTimeMillis() - timestamp) / 1000;
            if (sec < 60) return sec + "mp";
            if (sec < 3600) return (sec / 60) + "perc";
            return (sec / 3600) + "ora";
        }
    }

    private static final Map<String, Deque<Entry>> HISTORY = new ConcurrentHashMap<>();
    private static final Map<String, Integer> COUNTERS = new ConcurrentHashMap<>();

    public static void add(String playerUuid, String prompt, String style, int blockCount, BuildPlan plan) {
        HISTORY.computeIfAbsent(playerUuid, k -> new ArrayDeque<>());
        int id = COUNTERS.merge(playerUuid, 1, Integer::sum);
        Deque<Entry> dq = HISTORY.get(playerUuid);
        dq.addFirst(new Entry(id, prompt, style, blockCount, plan));
        while (dq.size() > MAX_HISTORY) dq.removeLast();
    }

    public static List<Entry> get(String playerUuid) {
        Deque<Entry> dq = HISTORY.get(playerUuid);
        if (dq == null) return Collections.emptyList();
        return new ArrayList<>(dq);
    }

    public static Entry getById(String playerUuid, int id) {
        Deque<Entry> dq = HISTORY.get(playerUuid);
        if (dq == null) return null;
        for (Entry e : dq) if (e.id == id) return e;
        return null;
    }

    public static boolean hasAny(String playerUuid) {
        Deque<Entry> dq = HISTORY.get(playerUuid);
        return dq != null && !dq.isEmpty();
    }
}
