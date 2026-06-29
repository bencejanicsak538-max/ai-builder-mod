package hu.bence.aibuilder;

import java.util.ArrayList;
import java.util.List;

public class BuildPlan {
    public String originMode = "player";
    public List<BlockEntry> blocks = new ArrayList<>();

    public static class BlockEntry {
        public int dx, dy, dz;
        public String block;
    }
}
