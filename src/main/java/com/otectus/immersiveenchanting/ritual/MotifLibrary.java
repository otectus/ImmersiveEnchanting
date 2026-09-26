package com.otectus.immersiveenchanting.ritual;

import com.otectus.immersiveenchanting.ritual.Motif.Family;
import com.otectus.immersiveenchanting.ritual.Motif.Proto;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The built-in motif grammar. Ids are stable protocol: pattern sets and profiles reference them, and the client
 * resolves them from this same table. Changing a builder changes generated patterns, which requires bumping
 * {@link PatternGenerator#VERSION}.
 *
 * <p>Lanes are numbered in key order (Bind Rune I..IV). "Ascending" means I toward IV.
 */
public final class MotifLibrary {
    public static final String NAMESPACE = "immersive_enchanting";
    /** Always eligible; the generator falls back to it when a pattern set leaves nothing else to choose. */
    public static final String SCATTER = NAMESPACE + ":scatter";

    private static final Map<String, Motif> MOTIFS = new LinkedHashMap<>();

    static {
        add("scatter", 0, 3, false, false, 4, 1.0, Family.FLOW, 0, MotifLibrary::scatter);
        add("alternating_pair", 0, 3, false, false, 4, 1.0, Family.FLOW, 0, MotifLibrary::alternatingPair);
        add("ascending_cascade", 0, 3, false, false, 4, 1.0, Family.FLOW, 0, (r, a, t, u) -> cascade(r, a, u, false));
        add("descending_cascade", 1, 3, false, false, 4, 1.0, Family.FLOW, 0, (r, a, t, u) -> cascade(r, a, u, true));
        add("pulse", 1, 3, false, false, 4, 1.0, Family.PULSE, 99, MotifLibrary::pulse);
        add("echo", 1, 3, false, false, 5, 0.8, Family.FLOW, 0, MotifLibrary::echo);
        add("palindrome", 1, 3, false, false, 5, 1.0, Family.FLOW, 0, MotifLibrary::palindrome);
        add("mirrored_cascade", 2, 3, false, false, 6, 1.0, Family.FLOW, 0, MotifLibrary::mirroredCascade);
        add("left_right_split", 2, 4, false, false, 5, 0.8, Family.FLOW, 0, MotifLibrary::leftRightSplit);
        add("center_cross", 2, 4, false, false, 4, 1.0, Family.FLOW, 0, MotifLibrary::centerCross);
        add("simple_hold", 2, 3, true, false, 4, 0.5, Family.HOLD, 5, MotifLibrary::simpleHold);
        add("hold_anchor", 3, 3, true, false, 4, 1.0, Family.HOLD, 5, MotifLibrary::holdAnchor);
        add("chord_pulse", 3, 3, false, true, 3, 1.0, Family.CHORD, 5, MotifLibrary::chordPulse);
        add("syncopated_echo", 3, 3, false, false, 2.5, 1.6, Family.FLOW, 0, MotifLibrary::syncopatedEcho);
        add("chord_cascade", 4, 3, false, true, 4, 1.0, Family.CHORD, 5, MotifLibrary::chordCascade);
    }

    private static void add(String path, int minTier, int lanes, boolean holds, boolean chords, double lengthBeats,
                            double density, Family family, int repeatTier, Motif.Builder builder) {
        String id = NAMESPACE + ":" + path;
        MOTIFS.put(id, new Motif(id, minTier, lanes, holds, chords, lengthBeats, density, family, repeatTier, builder));
    }

    public static Motif get(String id) {
        return MOTIFS.get(id);
    }

    public static Collection<Motif> all() {
        return Collections.unmodifiableCollection(MOTIFS.values());
    }

    // ---- builders -------------------------------------------------------------------------------------------------

    /** Four taps on random lanes, never the same lane twice in a row. */
    private static List<Proto> scatter(RitualRandom rng, int anchors, int tier, int unit) {
        List<Proto> out = new ArrayList<>();
        int last = -1;
        for (int i = 0; i < 4; i++) {
            int lane = otherLane(rng, anchors, last);
            out.add(Proto.tap(lane, i * unit));
            last = lane;
        }
        return out;
    }

    /** A B A B, lengthened to six notes from tier 3. */
    private static List<Proto> alternatingPair(RitualRandom rng, int anchors, int tier, int unit) {
        int a = rng.nextInt(anchors);
        int b = otherLane(rng, anchors, a);
        int count = tier >= 3 ? 6 : 4;
        List<Proto> out = new ArrayList<>();
        for (int i = 0; i < count; i++) out.add(Proto.tap(i % 2 == 0 ? a : b, i * unit));
        return out;
    }

    /** An ordered sweep across three or four neighbouring anchors. */
    private static List<Proto> cascade(RitualRandom rng, int anchors, int unit, boolean descending) {
        int length = anchors == 4 && rng.chance(0.35) ? 3 : anchors;
        int start = length < anchors ? rng.nextInt(anchors - length + 1) : 0;
        List<Proto> out = new ArrayList<>();
        for (int i = 0; i < length; i++) {
            int lane = descending ? start + length - 1 - i : start + i;
            out.add(Proto.tap(lane, i * unit));
        }
        return out;
    }

    /** One anchor struck three times, then answered on another. Repeats are a full beat apart at most speeds. */
    private static List<Proto> pulse(RitualRandom rng, int anchors, int tier, int unit) {
        int a = rng.nextInt(anchors);
        int b = otherLane(rng, anchors, a);
        int spacing = Math.max(unit, 2);
        return List.of(Proto.tap(a, 0), Proto.tap(a, spacing), Proto.tap(a, spacing * 2), Proto.tap(b, spacing * 2 + unit));
    }

    /** A short phrase played twice with a breath between: A B . A B (or A B C . A B C from tier 3). */
    private static List<Proto> echo(RitualRandom rng, int anchors, int tier, int unit) {
        int length = tier >= 3 ? 3 : 2;
        int[] phrase = distinctLanes(rng, anchors, length);
        List<Proto> out = new ArrayList<>();
        for (int i = 0; i < length; i++) out.add(Proto.tap(phrase[i], i * unit));
        int restart = (length + 1) * unit;
        for (int i = 0; i < length; i++) out.add(Proto.tap(phrase[i], restart + i * unit));
        return out;
    }

    /** A B C B A. */
    private static List<Proto> palindrome(RitualRandom rng, int anchors, int tier, int unit) {
        int[] l = distinctLanes(rng, anchors, 3);
        int[] order = {l[0], l[1], l[2], l[1], l[0]};
        List<Proto> out = new ArrayList<>();
        for (int i = 0; i < order.length; i++) out.add(Proto.tap(order[i], i * unit));
        return out;
    }

    /** Up and back down again without repeating the turning anchor: I II III IV III II. */
    private static List<Proto> mirroredCascade(RitualRandom rng, int anchors, int tier, int unit) {
        List<Integer> lanes = new ArrayList<>();
        for (int i = 0; i < anchors; i++) lanes.add(i);
        for (int i = anchors - 2; i >= 1; i--) lanes.add(i);
        if (rng.chance(0.5)) {
            lanes.replaceAll(l -> anchors - 1 - l);
        }
        List<Proto> out = new ArrayList<>();
        for (int i = 0; i < lanes.size(); i++) out.add(Proto.tap(lanes.get(i), i * unit));
        return out;
    }

    /** Both left anchors, a breath, both right anchors (or the mirror). */
    private static List<Proto> leftRightSplit(RitualRandom rng, int anchors, int tier, int unit) {
        boolean leftFirst = rng.chance(0.5);
        int[] left = rng.chance(0.5) ? new int[]{0, 1} : new int[]{1, 0};
        int[] right = rng.chance(0.5) ? new int[]{2, 3} : new int[]{3, 2};
        int[] first = leftFirst ? left : right;
        int[] second = leftFirst ? right : left;
        return List.of(Proto.tap(first[0], 0), Proto.tap(first[1], unit),
                Proto.tap(second[0], unit * 3), Proto.tap(second[1], unit * 4));
    }

    /** The inner pair then the outer pair (II III I IV), or outer then inner. */
    private static List<Proto> centerCross(RitualRandom rng, int anchors, int tier, int unit) {
        int[] order = rng.chance(0.5) ? new int[]{1, 2, 0, 3} : new int[]{0, 3, 1, 2};
        if (rng.chance(0.5)) {
            int t = order[0]; order[0] = order[1]; order[1] = t;
            t = order[2]; order[2] = order[3]; order[3] = t;
        }
        List<Proto> out = new ArrayList<>();
        for (int i = 0; i < 4; i++) out.add(Proto.tap(order[i], i * unit));
        return out;
    }

    /** One held rune, then a tap on another anchor after the release. */
    private static List<Proto> simpleHold(RitualRandom rng, int anchors, int tier, int unit) {
        int a = rng.nextInt(anchors);
        int b = otherLane(rng, anchors, a);
        int length = unit == 1 ? 3 : 4;
        return List.of(Proto.hold(a, 0, length), Proto.tap(b, length + unit));
    }

    /** A rune held on one anchor while the free anchors are tapped. */
    private static List<Proto> holdAnchor(RitualRandom rng, int anchors, int tier, int unit) {
        int held = rng.nextInt(anchors);
        List<Proto> out = new ArrayList<>();
        out.add(Proto.hold(held, 0, unit * 4));
        int last = held;
        for (int i = 1; i <= 3; i++) {
            int lane = laneExcluding(rng, anchors, held, last);
            out.add(Proto.tap(lane, i * unit));
            last = lane;
        }
        return out;
    }

    /** Chord, a single rune on a third anchor, the same chord again. */
    private static List<Proto> chordPulse(RitualRandom rng, int anchors, int tier, int unit) {
        int[] l = distinctLanes(rng, anchors, 3);
        return List.of(Proto.chord(l[0], l[1], 0), Proto.tap(l[2], unit), Proto.chord(l[0], l[1], unit * 2));
    }

    /** Off-beat doubles: A A . B B on consecutive half beats. */
    private static List<Proto> syncopatedEcho(RitualRandom rng, int anchors, int tier, int unit) {
        int a = rng.nextInt(anchors);
        int b = otherLane(rng, anchors, a);
        return List.of(Proto.tap(a, 0), Proto.tap(a, 2), Proto.tap(b, 3), Proto.tap(b, 5));
    }

    /** Chord, two single runes, a chord on the two anchors just played. */
    private static List<Proto> chordCascade(RitualRandom rng, int anchors, int tier, int unit) {
        int[] l = distinctLanes(rng, anchors, anchors);
        int a = l[0], b = l[1], c = l[2];
        int d = anchors == 4 ? l[3] : (rng.chance(0.5) ? a : b);
        return List.of(Proto.chord(a, b, 0), Proto.tap(c, unit), Proto.tap(d, unit * 2), Proto.chord(c, d, unit * 3));
    }

    // ---- lane helpers ---------------------------------------------------------------------------------------------

    private static int otherLane(RitualRandom rng, int anchors, int exclude) {
        if (exclude < 0) return rng.nextInt(anchors);
        int lane = rng.nextInt(anchors - 1);
        return lane >= exclude ? lane + 1 : lane;
    }

    private static int laneExcluding(RitualRandom rng, int anchors, int a, int b) {
        int[] options = new int[anchors];
        int n = 0;
        for (int i = 0; i < anchors; i++) if (i != a && i != b) options[n++] = i;
        if (n == 0) return otherLane(rng, anchors, a);
        return options[rng.nextInt(n)];
    }

    /** {@code count} distinct lanes in random order (a partial Fisher-Yates shuffle). */
    private static int[] distinctLanes(RitualRandom rng, int anchors, int count) {
        int[] lanes = new int[anchors];
        for (int i = 0; i < anchors; i++) lanes[i] = i;
        for (int i = 0; i < count; i++) {
            int j = i + rng.nextInt(anchors - i);
            int t = lanes[i]; lanes[i] = lanes[j]; lanes[j] = t;
        }
        int[] out = new int[count];
        System.arraycopy(lanes, 0, out, 0, count);
        return out;
    }

    private MotifLibrary() {}
}
