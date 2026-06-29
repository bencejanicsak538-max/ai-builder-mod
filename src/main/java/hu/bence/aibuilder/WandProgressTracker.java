package hu.bence.aibuilder;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Nyilvantartja az epites haladasat jatekosok szerint.
 * Az AIBuilderClient olvassa es jeleníti meg.
 */
public class WandProgressTracker {

    public static class Progress {
        public final int placed;
        public final int total;
        public final boolean done;
        public Progress(int placed, int total, boolean done) {
            this.placed = placed;
            this.total = total;
            this.done = done;
        }
    }

    // UUID -> Progress
    private static final ConcurrentHashMap<String, Progress> MAP = new ConcurrentHashMap<>();

    public static void update(String uuid, int placed, int total) {
        MAP.put(uuid, new Progress(placed, total, false));
    }

    public static void finish(String uuid) {
        Progress current = MAP.get(uuid);
        int total = current != null ? current.total : 0;
        int placed = current != null ? current.placed : 0;
        MAP.put(uuid, new Progress(placed, total, true));
    }

    public static void clear(String uuid) {
        MAP.remove(uuid);
    }

    public static Progress get(String uuid) {
        return MAP.get(uuid);
    }

    public static boolean isActive(String uuid) {
        Progress p = MAP.get(uuid);
        return p != null && !p.done;
    }
}
