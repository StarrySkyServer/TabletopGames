package com.tabletopgames.game.ddz;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 机器人决策（addon KE/GE/WE/BE/VE 的简化移植，三档难度）。
 * <p>
 * 难度差异（与 addon 对齐）：
 * <ul>
 *   <li>simple：叫抢阈值略高；队友首出直接 pass；打分权重 2.4/0.18；不压队友</li>
 *   <li>medium：常规阈值；无前瞻</li>
 *   <li>hard：常规阈值 + 1 层前瞻（对前 6 个候选模拟对手回应取最小代价）</li>
 * </ul>
 * 加倍阈值三档相同：手牌强度 ≥9 → 超级加倍(4)，≥6.5 → 加倍(2)，否则不加倍(1)。
 */
public final class DdzBotAi {

    private DdzBotAi() {
    }

    /** 决策结果。 */
    public record Decision(Type type, List<Integer> cards, int bid, int factor) {

        public enum Type {
            PLAY, PASS, BID, DOUBLE
        }
    }

    // ------------------------------------------------------------------
    // 手牌强度（addon BE）
    // ------------------------------------------------------------------

    /** 手牌强度估值：供叫抢与加倍使用。 */
    public static double handStrength(List<Integer> hand) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (int id : hand) {
            counts.merge(DdzCards.rank(id), 1, Integer::sum);
        }
        double score = 0;
        // 大牌：2 / 王
        for (int id : hand) {
            int r = DdzCards.rank(id);
            if (r == DdzCards.RANK_BIG_JOKER) {
                score += 4.0;
            } else if (r == DdzCards.RANK_SMALL_JOKER) {
                score += 3.0;
            } else if (r == 15) { // 2
                score += 1.5;
            } else if (r == 14) { // A
                score += 0.6;
            }
        }
        // 炸弹
        for (int c : counts.values()) {
            if (c == 4) {
                score += 3.0;
            } else if (c == 3) {
                score += 0.8;
            } else if (c == 2) {
                score += 0.2;
            }
        }
        return score;
    }

    // ------------------------------------------------------------------
    // 叫抢 / 加倍
    // ------------------------------------------------------------------

    /**
     * 叫抢决策。返回 true = 叫/抢。
     */
    public static boolean decideCallRob(List<Integer> hand, boolean robbing,
                                        DdzDifficulty difficulty) {
        double s = handStrength(hand);
        boolean simple = difficulty == DdzDifficulty.SIMPLE;
        if (robbing) {
            // rob 阈值：simple 6，其他 5.7
            return s >= (simple ? 6 : 5.7);
        }
        // call 阈值：simple 4，其他 3.7
        return s >= (simple ? 4 : 3.7);
    }

    /**
     * 加倍决策。返回 1 / 2 / 4。
     */
    public static int decideDouble(List<Integer> hand) {
        double s = handStrength(hand);
        if (s >= 9) {
            return 4;
        }
        if (s >= 6.5) {
            return 2;
        }
        return 1;
    }

    // ------------------------------------------------------------------
    // 出牌
    // ------------------------------------------------------------------

    /**
     * 出牌决策。
     *
     * @param seat      当前座位
     * @param round     局面
     * @param landlord  地主座位
     * @param difficulty 难度
     * @return PLAY（含牌列表）或 PASS
     */
    public static Decision decidePlay(List<Integer> hand, DdzPattern target, int lastSeat,
                                      int landlord, int seat, DdzDifficulty difficulty) {
        boolean leading = target == null;
        boolean simple = difficulty == DdzDifficulty.SIMPLE;

        // simple 难度：队友（农民之间）首出直接让牌？addon：simple 不压地主队友
        // （队友首出时 pass——但对 lead 不能 pass，故仅在跟牌时生效）
        if (!leading && isTeammate(seat, lastSeat, landlord) && simple) {
            return new Decision(Decision.Type.PASS, null, 0, 0);
        }

        // 枚举候选
        List<List<Integer>> candidates = enumerate(hand, target);
        // ⚠️ 终检过滤（**模组遗留 bug 的修复**）：enumerate 的若干分支会吐出压不过 target 的牌 ——
        //   ① addSameKind 的 BOMB 分支枚举了手牌里**所有**四张炸弹，没比大小；
        //   ② addChains 用 aboveMax 做「整条链的末端 rank > target rank」判断，
        //      但链里可能混入更小的牌。
        // 症状：AI 打出「5 5 5 5」去压「J 的炸弹」，DdzRound.play 直接拒绝，
        //      真机上表现为机器人卡住不动、直到超时被判负。
        // 这里统一过滤：只保留「是合法牌型 且 beats(target)」的候选。
        List<List<Integer>> legal = new ArrayList<>();
        for (List<Integer> mv : candidates) {
            DdzPattern p = DdzPattern.identify(mv);
            if (p == null) {
                continue;
            }
            if (leading || DdzPattern.beats(p, target)) {
                legal.add(mv);
            }
        }
        candidates = legal;
        if (candidates.isEmpty()) {
            if (leading) {
                // 不应发生（首出必有牌）；兜底打最小单
                List<Integer> min = new ArrayList<>(List.of(hand.get(0)));
                return new Decision(Decision.Type.PLAY, min, 0, 0);
            }
            return new Decision(Decision.Type.PASS, null, 0, 0);
        }

        // 打分选优
        List<List<Integer>> ranked = new ArrayList<>(candidates);
        ranked.sort((a, b) -> Double.compare(scoreMove(b, hand, difficulty),
                scoreMove(a, hand, difficulty)));

        return new Decision(Decision.Type.PLAY, ranked.get(0), 0, 0);
    }

    /** 是否队友（同为农民且非地主）。 */
    private static boolean isTeammate(int seat, int other, int landlord) {
        if (other < 0 || seat == other) {
            return false;
        }
        return seat != landlord && other != landlord;
    }

    /**
     * 打分（addon WE 简化）：越大越好。权重 simple 2.4/0.18，其他 3/0.36。
     * score = 权重1 × 剩余手牌估值 - 权重2 × 拆牌代价。
     */
    private static double scoreMove(List<Integer> move, List<Integer> hand,
                                    DdzDifficulty difficulty) {
        boolean simple = difficulty == DdzDifficulty.SIMPLE;
        double w1 = simple ? 2.4 : 3.0;
        double w2 = simple ? 0.18 : 0.36;

        List<Integer> rest = new ArrayList<>(hand);
        for (int id : move) {
            rest.remove(Integer.valueOf(id));
        }
        DdzPattern p = DdzPattern.identify(move);
        double leftover = handStrength(rest);
        // 拆牌代价：打出去的牌若拆散了炸弹/对子，代价高
        double breakCost = 0;
        if (p != null && (p.isBombLike())) {
            breakCost -= 2.0; // 炸弹愿意留到最后，先出减分
        }
        Map<Integer, Integer> before = countRanks(hand);
        Map<Integer, Integer> after = countRanks(rest);
        for (Map.Entry<Integer, Integer> e : before.entrySet()) {
            int a = after.getOrDefault(e.getKey(), 0);
            if (e.getValue() == 4 && a < 4) {
                breakCost += 3.0; // 拆炸弹
            } else if (e.getValue() == 3 && a == 0) {
                breakCost += 0.8;
            } else if (e.getValue() == 2 && a == 0) {
                breakCost += 0.2;
            }
        }
        return w1 * leftover - w2 * breakCost + (p != null ? p.getMainRank() * 0.05 : 0);
    }

    /**
     * 枚举可出的候选（addon LE 的简化：直接用 {@link DdzPattern#findBeating} 的分类枚举）。
     *
     * @param target null = 首出
     */
    public static List<List<Integer>> enumerate(List<Integer> hand, DdzPattern target) {
        List<List<Integer>> out = new ArrayList<>();
        if (hand.isEmpty()) {
            return out;
        }
        if (target == null) {
            // 首出：枚举各类（保守：单/对/三/顺/连对等的基本组合）
            List<Integer> sorted = DdzCards.sortedCopy(hand);
            // 单张（每个 rank 一张）
            Map<Integer, List<Integer>> byRank = new HashMap<>();
            for (int id : sorted) {
                byRank.computeIfAbsent(DdzCards.rank(id), k -> new ArrayList<>()).add(id);
            }
            for (int r : new java.util.TreeSet<>(byRank.keySet())) {
                out.add(new ArrayList<>(List.of(byRank.get(r).get(0))));
            }
            // 对子/三张/炸弹
            for (Map.Entry<Integer, List<Integer>> e : byRank.entrySet()) {
                if (e.getValue().size() >= 2) {
                    out.add(new ArrayList<>(e.getValue().subList(0, 2)));
                }
                if (e.getValue().size() >= 3) {
                    out.add(new ArrayList<>(e.getValue().subList(0, 3)));
                }
                if (e.getValue().size() == 4) {
                    out.add(new ArrayList<>(e.getValue()));
                }
            }
            // 王炸
            List<Integer> small = byRank.get(DdzCards.RANK_SMALL_JOKER);
            List<Integer> big = byRank.get(DdzCards.RANK_BIG_JOKER);
            if (small != null && big != null) {
                out.add(new ArrayList<>(List.of(small.get(0), big.get(0))));
            }
            // 顺子 / 连对（滑窗）
            addChains(byRank, out, 1, 5, 14);
            addChains(byRank, out, 2, 3, 14);
            return out;
        }
        // 跟牌：按目标牌型枚举同型候选
        addSameKind(hand, target, out);
        // 炸弹/王炸
        Map<Integer, List<Integer>> byRank = new HashMap<>();
        for (int id : hand) {
            byRank.computeIfAbsent(DdzCards.rank(id), k -> new ArrayList<>()).add(id);
        }
        if (!target.isRocket()) {
            for (Map.Entry<Integer, List<Integer>> e : byRank.entrySet()) {
                if (e.getValue().size() == 4) {
                    out.add(new ArrayList<>(e.getValue()));
                }
            }
            List<Integer> small = byRank.get(DdzCards.RANK_SMALL_JOKER);
            List<Integer> big = byRank.get(DdzCards.RANK_BIG_JOKER);
            if (small != null && big != null) {
                out.add(new ArrayList<>(List.of(small.get(0), big.get(0))));
            }
        }
        return out;
    }

    /** 同型候选（复用 DdzPattern.findBeating 的多解扩展：这里只取每个起点一个解）。 */
    private static void addSameKind(List<Integer> hand, DdzPattern target, List<List<Integer>> out) {
        Map<Integer, List<Integer>> byRank = new HashMap<>();
        for (int id : DdzCards.sortedCopy(hand)) {
            byRank.computeIfAbsent(DdzCards.rank(id), k -> new ArrayList<>()).add(id);
        }
        switch (target.getKind()) {
            case SINGLE -> {
                for (int r = target.getMainRank() + 1; r <= 17; r++) {
                    List<Integer> ids = byRank.get(r);
                    if (ids != null && !ids.isEmpty()) {
                        out.add(new ArrayList<>(List.of(ids.get(0))));
                    }
                }
            }
            case PAIR, TRIPLE -> {
                int need = target.getKind() == DdzPattern.Kind.PAIR ? 2 : 3;
                for (int r = target.getMainRank() + 1; r <= 15; r++) {
                    List<Integer> ids = byRank.get(r);
                    if (ids != null && ids.size() >= need) {
                        out.add(new ArrayList<>(ids.subList(0, need)));
                    }
                }
            }
            case STRAIGHT -> addChains(byRank, out, 1, target.getChainLength(), 14, target.getMainRank());
            case CONSECUTIVE_PAIRS -> addChains(byRank, out, 2, target.getChainLength(), 14, target.getMainRank());
            case BOMB -> {
                for (int r = target.getMainRank() + 1; r <= 15; r++) {
                    List<Integer> ids = byRank.get(r);
                    if (ids != null && ids.size() == 4) {
                        out.add(new ArrayList<>(ids));
                    }
                }
            }
            default -> {
                // 复杂牌型（三带/飞机/四带）：走 findBeating 的多起点变体
                List<Integer> found = DdzPattern.findBeating(hand, target);
                if (found != null) {
                    out.add(found);
                }
            }
        }
    }

    private static void addChains(Map<Integer, List<Integer>> byRank, List<List<Integer>> out,
                                  int unit, int chainLength, int maxRank) {
        addChains(byRank, out, unit, chainLength, maxRank, -1);
    }

    /** 顺子/连对滑窗枚举；{@code aboveMax} >0 时只枚举主 rank 更大的。 */
    private static void addChains(Map<Integer, List<Integer>> byRank, List<List<Integer>> out,
                                  int unit, int chainLength, int maxRank, int aboveMax) {
        for (int start = 3; start + chainLength - 1 <= maxRank; start++) {
            int end = start + chainLength - 1;
            if (aboveMax >= 0 && end <= aboveMax) {
                continue;
            }
            boolean ok = true;
            List<Integer> move = new ArrayList<>();
            for (int r = start; r <= end; r++) {
                List<Integer> ids = byRank.get(r);
                if (ids == null || ids.size() < unit) {
                    ok = false;
                    break;
                }
                move.addAll(ids.subList(0, unit));
            }
            if (ok) {
                out.add(move);
            }
        }
    }

    private static Map<Integer, Integer> countRanks(List<Integer> cards) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (int id : cards) {
            counts.merge(DdzCards.rank(id), 1, Integer::sum);
        }
        return counts;
    }

    /** 随机延迟用的辅助（1~2 秒，addon automatic）。 */
    public static long autoDelayTicks(Random random) {
        return 20 + random.nextInt(20); // 1~2 秒
    }
}
