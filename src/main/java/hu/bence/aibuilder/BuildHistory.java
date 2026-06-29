package hu.bence.aibuilder;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Epitesi elozmenyek - jatekosokenkent tarolja az utolso MAX_HISTORY epitest.
 * Lehetove teszi az /aihistory paranccsal valo ujraepiteset.
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
            this.st