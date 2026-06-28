package hu.bence.aibuilder;

import java.util.ArrayList;
import java.util.List;

public class BuildPlan {
    public String originMode = "player";
    public List<BlockInstruction> blocks = new ArrayList<>();

    public static class BlockInstruction {
        public int dx;
        public int dy;
        public int dz;
        public String block;
    }
}
