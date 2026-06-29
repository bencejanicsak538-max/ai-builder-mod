package hu.bence.aibuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Epitmenyek elforgatasara szolgalo segédosztaly.
 * Tamogatott szogek: 90, 180, 270 (fok, orajarat iranyaban Y tengely korul).
 */
public class RotateUtil {

    /**
     * Elforgat egy BuildPlan-t a megadott fokkal (90/180/270).
     * Y tengely korul forgat, az origen marad a jatekos pozicioja.
     */
    public static BuildPlan rotate(BuildPlan original, int degrees) {
        int norm = ((degrees % 360) + 360) % 360;
        if (norm == 0) return original;

        BuildPlan rotated = new BuildPlan();
        rotated.originMode = original.originMode;
        rotated.blocks = new ArrayList<>();

        for (BuildPlan.BlockEntry b : original.blocks) {
            BuildPlan.BlockEntry nb = new BuildPlan.BlockEntry();
            nb.dy = b.dy;
            nb.block = b.block;
            nb.state = rotateState(b.state, norm);

            switch (norm) {
                case 90  -> { nb.dx = -b.dz; nb.dz =  b.dx; }
                case 180 -> { nb.dx = -b.dx; nb.dz = -b.dz; }
                case 270 -> { nb.dx =  b.dz; nb.dz = -b.dx; }
                default  -> { nb.dx =  b.dx; nb.dz =  b.dz; }
            }
            rotated.blocks.add(nb);
        }
        return rotated;
    }

    /**
     * BlockState irany tulajdonsagait is elforgat (facing, half, stb)
     */
    private static String rotateState(String state, int degrees) {
        if (state == null || state.isBlank()) return state;
        try {
            String[] parts = state.split(",");
            StringBuilder sb = new StringBuilder();
            for (String part : parts) {
                String[] kv = part.trim().split("=", 2);
                if (kv.length == 2 && kv[0].trim().equals("facing")) {
                    sb.append("facing=").append(rotateFacing(kv[1].trim(), degrees));
                } else {
                    sb.append(part);
                }
                sb.append(",");
            }
            if (sb.length() > 0) sb.deleteCharAt(sb.length() - 1);
            return sb.toString();
        } catch (Exception e) {
            return state;
        }
    }

    private static String rotateFacing(String facing, int degrees) {
        String[] order = {"north", "east", "south", "west"};
        int steps = degrees / 90;
        for (int i = 0; i < order.length; i++) {
            if (order[i].equals(facing)) {
                return order[(i + steps) % 4];
            }
        }
        return facing; // up/down valtozatlan marad
    }
}
