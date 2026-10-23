package com.tabletopgames.game.ddz;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 牌型识别与比较（addon {@code Ia/Ga/Wa} 的移植）。
 * <p>
 * 牌型一览（kind 与 addon 对齐）：
 * <pre>
 * single            单张
 * pair              对子
 * triple            三张
 * triple_with_single 三带一（4 张）
 * triple_with_pair  三带二（5 张）
 * straight          顺子（≥5 张连续单，≤A 不含 2/王）
 * consecutive_pairs 连对（≥3 对连续）
 * airplane          飞机不带（≥2 组连续三张）
 * airplane_with_singles 飞机带单（每组三张带 1 单，总长 4n）
 * airplane_with_pairs   飞机带对（每组三张带 1 对，总长 5n）
 * four_with_two_singles 四带二单（6 张，含四张）
 * four_with_two_pairs   四带两对（8 张）
 * bomb              炸弹（四张同点）
 * rocket             王炸（双王）
 * </pre>
 * 比较规则：rocket > bomb > 同 kind 且同结构（cardCount + chainLength）比 mainRank。
 */
public final class DdzPattern {

    public enum Kind {
        SINGLE, PAIR, TRIPLE, TRIPLE_WITH_SINGLE, TRIPLE_WITH_PAIR,
        STRAIGHT, CONSECUTIVE_PAIRS,
        AIRPLANE, AIRPLANE_WITH_SINGLES, AIRPLANE_WITH_PAIRS,
        FOUR_WITH_TWO_SINGLES, FOUR_WITH_TWO_PAIRS,
        BOMB, ROCKET
    }

    private final Kind kind;
    /** 参与牌型的牌总数。 */
    private final int cardCount;
    /** 顺子/连对/飞机的链长（连续组数）；其余为 1。 */
    private final int chainLength;
    /** 主 rank（顺子=最大 rank，带牌=三张/四张的 rank，单对=其 rank）。 */
    private final int mainRank;

    private DdzPattern(Kind kind, int cardCount, int chainLength, int mainRank) {
        this.kind = kind;
        this.cardCount = cardCount;
        this.chainLength = chainLength;
        this.mainRank = mainRank;
    }

    public Kind getKind() {
        return kind;
    }

    public int getCardCount() {
        return cardCount;
    }

    public int getChainLength() {
        return chainLength;
    }

    public int getMainRank() {
        return mainRank;
    }

    public boolean isBomb() {
        return kind == Kind.BOMB;
    }

    public boolean isRocket() {
        return kind == Kind.ROCKET;
    }

    /** 炸弹类（炸弹或王炸）：可压一切非炸弹牌型。 */
    public boolean isBombLike() {
        return kind == Kind.BOMB || kind == Kind.ROCKET;
    }

    // ------------------------------------------------------------------
    // rank 统计辅助
    // ------------------------------------------------------------------

    /** 统计每个 rank 的张数。 */
    private static Map<Integer, Integer> countRanks(List<Integer> cards) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (int id : cards) {
            counts.merge(DdzCards.rank(id), 1, Integer::sum);
        }
        return counts;
    }

    /** 统计值列表（各 rank 张数）按升序排列。 */
    private static List<Integer> sortedCounts(Map<Integer, Integer> counts) {
        List<Integer> list = new ArrayList<>(counts.values());
        list.sort(Integer::compareTo);
        return list;
    }

    /** rank 集合是否为从 fromStart 开始、长度 len 的连续段（不含 2/王）。 */
    private static boolean isChain(Map<Integer, Integer> counts, int expectedCount,
                                   int chainLength, int maxAllowedRank) {
        List<Integer> ranks = new ArrayList<>(counts.keySet());
        ranks.sort(Integer::compareTo);
        if (ranks.size() != chainLength) {
            return false;
        }
        int start = ranks.get(0);
        if (start + chainLength - 1 > maxAllowedRank) {
            return false;
        }
        for (int i = 0; i < chainLength; i++) {
            Integer c = counts.get(start + i);
            if (c == null || c != expectedCount) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------
    // 识别
    // ------------------------------------------------------------------

    /**
     * 识别一组牌的牌型；非法组合返回 {@code null}。
     *
     * @param cards 牌 id 列表（任意顺序）
     */
    public static DdzPattern identify(List<Integer> cards) {
        if (cards == null || cards.isEmpty() || cards.size() > DdzCards.COUNT) {
            return null;
        }
        // 去重校验：同 id 不允许出现两次
        if (cards.stream().distinct().count() != cards.size()) {
            return null;
        }
        for (int id : cards) {
            if (!DdzCards.isValid(id)) {
                return null;
            }
        }
        int n = cards.size();
        Map<Integer, Integer> counts = countRanks(cards);

        // 王炸：仅大小王两张
        if (n == 2 && counts.containsKey(DdzCards.RANK_SMALL_JOKER)
                && counts.containsKey(DdzCards.RANK_BIG_JOKER)) {
            return new DdzPattern(Kind.ROCKET, 2, 1, DdzCards.RANK_BIG_JOKER);
        }

        // 炸弹：单一 rank 四张
        if (n == 4 && counts.size() == 1) {
            return new DdzPattern(Kind.BOMB, 4, 1, counts.keySet().iterator().next());
        }

        // 单/对/三张
        if (n == 1) {
            return new DdzPattern(Kind.SINGLE, 1, 1, DdzCards.rank(cards.get(0)));
        }
        if (n == 2 && counts.size() == 1) {
            return new DdzPattern(Kind.PAIR, 2, 1, counts.keySet().iterator().next());
        }
        if (n == 3 && counts.size() == 1) {
            return new DdzPattern(Kind.TRIPLE, 3, 1, counts.keySet().iterator().next());
        }

        // 三带一 / 三带二
        if (n == 4) {
            // 含三张（排除已判定的炸弹）
            Integer triple = findRankWith(counts, 3);
            if (triple != null) {
                return new DdzPattern(Kind.TRIPLE_WITH_SINGLE, 4, 1, triple);
            }
        }
        if (n == 5) {
            Integer triple = findRankWith(counts, 3);
            if (triple != null) {
                // 剩余须为对子
                Map<Integer, Integer> rest = new HashMap<>(counts);
                rest.remove(triple);
                if (rest.size() == 1 && rest.values().iterator().next() == 2) {
                    return new DdzPattern(Kind.TRIPLE_WITH_PAIR, 5, 1, triple);
                }
            }
        }

        // 顺子：≥5 全单连续，最大 ≤ A(14)
        if (n >= 5 && counts.size() == n) {
            if (isChain(counts, 1, n, 14)) {
                int max = counts.keySet().stream().mapToInt(Integer::intValue).max().getAsInt();
                return new DdzPattern(Kind.STRAIGHT, n, n, max);
            }
        }

        // 连对：≥6 偶数、全对、≥3 对连续，最大 ≤ A
        if (n >= 6 && n % 2 == 0) {
            int pairs = n / 2;
            if (counts.size() == pairs && allEqual(counts, 2)
                    && isChain(counts, 2, pairs, 14)) {
                int max = counts.keySet().stream().mapToInt(Integer::intValue).max().getAsInt();
                return new DdzPattern(Kind.CONSECUTIVE_PAIRS, n, pairs, max);
            }
        }

        // 飞机（不带）：≥6 %3==0 全三张连续
        if (n >= 6 && n % 3 == 0) {
            int groups = n / 3;
            if (counts.size() == groups && allEqual(counts, 3)
                    && isChain(counts, 3, groups, 14)) {
                int max = counts.keySet().stream().mapToInt(Integer::intValue).max().getAsInt();
                return new DdzPattern(Kind.AIRPLANE, n, groups, max);
            }
        }

        // 飞机带单：总长 4n（n≥2 组）
        if (n >= 8 && n % 4 == 0) {
            DdzPattern p = tryAirplane(counts, n, 4, Kind.AIRPLANE_WITH_SINGLES);
            if (p != null) {
                return p;
            }
        }
        // 飞机带对：总长 5n（n≥2 组）
        if (n >= 10 && n % 5 == 0) {
            DdzPattern p = tryAirplane(counts, n, 5, Kind.AIRPLANE_WITH_PAIRS);
            if (p != null) {
                return p;
            }
        }

        // 四带二单：6 张含四张
        if (n == 6) {
            Integer quad = findRankWith(counts, 4);
            if (quad != null) {
                return new DdzPattern(Kind.FOUR_WITH_TWO_SINGLES, 6, 1, quad);
            }
        }
        // 四带两对：8 张含四张 + 两对
        if (n == 8) {
            Integer quad = findRankWith(counts, 4);
            if (quad != null) {
                Map<Integer, Integer> rest = new HashMap<>(counts);
                rest.remove(quad);
                if (rest.size() == 2 && allEqual(rest, 2)) {
                    return new DdzPattern(Kind.FOUR_WITH_TWO_PAIRS, 8, 1, quad);
                }
            }
        }

        return null;
    }

    /**
     * 飞机带牌尝试：main 三张组必须连续，翅膀匹配指定形态。
     *
     * @param unitSize 4 = 带单，5 = 带对
     */
    private static DdzPattern tryAirplane(Map<Integer, Integer> counts, int total,
                                       int unitSize, Kind kind) {
        int groups = total / unitSize;
        if (groups < 2) {
            return null;
        }
        // 枚举作为「机身」的三张 rank（允许四张也算作三张参与？addon：全 count3 判不带；
        // 带牌模式机身要求恰好三张或四张中取三张。为兼容四张拆带，机身 rank 允许 count>=3）
        List<Integer> tripleRanks = new ArrayList<>();
        for (Map.Entry<Integer, Integer> e : counts.entrySet()) {
            if (e.getValue() >= 3 && e.getKey() <= 14) {
                tripleRanks.add(e.getKey());
            }
        }
        tripleRanks.sort(Integer::compareTo);
        // 找连续 groups 长的三张段
        for (int i = 0; i + groups <= tripleRanks.size(); i++) {
            int start = tripleRanks.get(i);
            if (start + groups - 1 > 14) {
                continue;
            }
            boolean contiguous = true;
            for (int g = 0; g < groups; g++) {
                if (!tripleRanks.contains(start + g)) {
                    contiguous = false;
                    break;
                }
            }
            if (!contiguous) {
                continue;
            }
            // 剩余牌构成翅膀校验
            Map<Integer, Integer> rest = new HashMap<>(counts);
            boolean ok = true;
            for (int g = 0; g < groups; g++) {
                int r = start + g;
                int c = rest.getOrDefault(r, 0);
                if (c == 3) {
                    rest.remove(r);
                } else if (c > 3) {
                    rest.put(r, c - 3); // 四张拆出三张，剩 1 张当翅膀
                } else {
                    ok = false;
                    break;
                }
            }
            if (!ok) {
                continue;
            }
            int restCount = rest.values().stream().mapToInt(Integer::intValue).sum();
            if (unitSize == 4) {
                // 翅膀 = groups 张单（任意牌，含对子拆开？标准斗地主：带单可带任意单，
                // addon airplane_with_singles：t=8/12/16…，n=t/4，机身 n 组 + n 张任意单）
                if (restCount == groups) {
                    return new DdzPattern(kind, total, groups, start + groups - 1);
                }
            } else {
                // 翅膀 = groups 对
                if (restCount == groups * 2 && allEqual(rest, 2)) {
                    return new DdzPattern(kind, total, groups, start + groups - 1);
                }
            }
        }
        return null;
    }

    private static Integer findRankWith(Map<Integer, Integer> counts, int n) {
        for (Map.Entry<Integer, Integer> e : counts.entrySet()) {
            if (e.getValue() == n) {
                return e.getKey();
            }
        }
        return null;
    }

    private static boolean allEqual(Map<Integer, Integer> counts, int value) {
        for (int v : counts.values()) {
            if (v != value) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------
    // 比较
    // ------------------------------------------------------------------

    /**
     * a 能否压过 b（addon {@code Ga}/{@code Wa}）。a 为 null 表示首出（任何合法牌型均可）。
     *
     * @return true = a 可以打出
     */
    public static boolean beats(DdzPattern a, DdzPattern b) {
        if (a == null) {
            return false;
        }
        if (b == null) {
            return true; // 首出
        }
        if (a.isRocket()) {
            return true;
        }
        if (b.isRocket()) {
            return false;
        }
        if (a.isBomb() && !b.isBomb()) {
            return true;
        }
        if (b.isBomb() && !a.isBomb()) {
            return false;
        }
        // 同为炸弹或均为普通牌型：同结构比主 rank
        if (a.kind != b.kind || a.cardCount != b.cardCount || a.chainLength != b.chainLength) {
            return false;
        }
        return a.mainRank > b.mainRank;
    }

    /**
     * 手牌中是否存在能压过 {@code target} 的牌（供超时判定「压不过」用）。
     */
    public static boolean canBeatAny(List<Integer> hand, DdzPattern target) {
        // 枚举手牌所有子集不现实；这里用轻量代理：
        // target 为 null（首出）→ 有任意牌即可
        if (target == null) {
            return !hand.isEmpty();
        }
        // 王炸在手：压一切
        boolean hasSmall = false;
        boolean hasBig = false;
        for (int id : hand) {
            int r = DdzCards.rank(id);
            if (r == DdzCards.RANK_SMALL_JOKER) {
                hasSmall = true;
            }
            if (r == DdzCards.RANK_BIG_JOKER) {
                hasBig = true;
            }
        }
        if (hasSmall && hasBig) {
            return true;
        }
        // 炸弹在手且 target 非王炸
        if (!target.isRocket()) {
            Map<Integer, Integer> counts = countRanks(hand);
            for (int c : counts.values()) {
                if (c == 4) {
                    return true;
                }
            }
        }
        if (target.isBombLike()) {
            return false; // 只有王炸能压炸弹，前面已查
        }
        // 同型压制：按 target 结构在手牌中找
        return findBeating(hand, target) != null;
    }

    /**
     * 在手牌中找一手能压过 target 的牌（保守枚举：按牌型逐类构造）。
     * 供超时「压不过」判定与提示使用；找不到返回 null。
     */
    public static List<Integer> findBeating(List<Integer> hand, DdzPattern target) {
        if (target == null) {
            return hand.isEmpty() ? null : new ArrayList<>(hand.subList(0, 1));
        }
        List<Integer> sorted = DdzCards.sortedCopy(hand);
        Map<Integer, List<Integer>> byRank = new HashMap<>();
        for (int id : sorted) {
            byRank.computeIfAbsent(DdzCards.rank(id), k -> new ArrayList<>()).add(id);
        }

        // 王炸
        if (!target.isRocket()) {
            List<Integer> small = byRank.get(DdzCards.RANK_SMALL_JOKER);
            List<Integer> big = byRank.get(DdzCards.RANK_BIG_JOKER);
            if (small != null && big != null && target.isBombLike()) {
                // 只有炸弹需要王炸压
                return new ArrayList<>(List.of(small.get(0), big.get(0)));
            }
            // 炸弹压非炸弹
            if (!target.isBombLike()) {
                for (Map.Entry<Integer, List<Integer>> e : byRank.entrySet()) {
                    if (e.getValue().size() == 4) {
                        return new ArrayList<>(e.getValue());
                    }
                }
                if (small != null && big != null) {
                    return new ArrayList<>(List.of(small.get(0), big.get(0)));
                }
            }
        }

        List<Integer> result = switch (target.getKind()) {
            case SINGLE -> findSingle(byRank, target.getMainRank());
            case PAIR -> findN(byRank, target.getMainRank(), 2, 2);
            case TRIPLE -> findN(byRank, target.getMainRank(), 3, 3);
            case TRIPLE_WITH_SINGLE -> findTripleWith(byRank, target.getMainRank(), false);
            case TRIPLE_WITH_PAIR -> findTripleWith(byRank, target.getMainRank(), true);
            case STRAIGHT -> findStraight(byRank, target.getChainLength(), target.getMainRank(), 1);
            case CONSECUTIVE_PAIRS -> findStraight(byRank, target.getChainLength(), target.getMainRank(), 2);
            case AIRPLANE -> findAirplane(byRank, target.getChainLength(), target.getMainRank(), 0);
            case AIRPLANE_WITH_SINGLES -> findAirplane(byRank, target.getChainLength(), target.getMainRank(), 1);
            case AIRPLANE_WITH_PAIRS -> findAirplane(byRank, target.getChainLength(), target.getMainRank(), 2);
            case FOUR_WITH_TWO_SINGLES -> findFourWith(byRank, target.getMainRank(), false);
            case FOUR_WITH_TWO_PAIRS -> findFourWith(byRank, target.getMainRank(), true);
            default -> null;
        };
        return result;
    }

    private static List<Integer> findSingle(Map<Integer, List<Integer>> byRank, int above) {
        for (int r = above + 1; r <= DdzCards.RANK_BIG_JOKER; r++) {
            List<Integer> ids = byRank.get(r);
            if (ids != null && !ids.isEmpty()) {
                return new ArrayList<>(List.of(ids.get(0)));
            }
        }
        return null;
    }

    /** 找恰含 n 张 rank r 的组（可从更多张中取 n 张）。 */
    private static List<Integer> findN(Map<Integer, List<Integer>> byRank, int r, int need, int take) {
        List<Integer> ids = byRank.get(r);
        if (ids == null || ids.size() < need) {
            return null;
        }
        return new ArrayList<>(ids.subList(0, take));
    }

    private static List<Integer> findTripleWith(Map<Integer, List<Integer>> byRank,
                                                int tripleRank, boolean pairKicker) {
        // 找 > tripleRank 的三张（含四张拆）
        for (int r = tripleRank + 1; r <= 15; r++) {
            List<Integer> ids = byRank.get(r);
            if (ids == null || ids.size() < 3) {
                continue;
            }
            List<Integer> result = new ArrayList<>(ids.subList(0, 3));
            // 找翅膀
            if (pairKicker) {
                for (int kr = 3; kr <= 17; kr++) {
                    if (kr == r) {
                        continue;
                    }
                    List<Integer> k = byRank.get(kr);
                    if (k != null && k.size() >= 2) {
                        result.add(k.get(0));
                        result.add(k.get(1));
                        return result;
                    }
                }
            } else {
                for (int kr = 3; kr <= 17; kr++) {
                    if (kr == r) {
                        continue;
                    }
                    List<Integer> k = byRank.get(kr);
                    if (k != null && !k.isEmpty()) {
                        result.add(k.get(0));
                        return result;
                    }
                }
            }
        }
        return null;
    }

    /**
     * 找顺子/连对。{@code unitSize}=1 顺子 / 2 连对；同长度、起点高于 {@code aboveMax}。
     */
    private static List<Integer> findStraight(Map<Integer, List<Integer>> byRank,
                                              int chainLength, int aboveMax, int unitSize) {
        for (int start = aboveMax - chainLength + 2; start + chainLength - 1 <= 14; start++) {
            if (start < 3) {
                continue;
            }
            int end = start + chainLength - 1;
            if (end > 14) {
                continue;
            }
            if (end <= aboveMax) {
                continue;
            }
            boolean ok = true;
            List<Integer> result = new ArrayList<>();
            for (int r = start; r <= end; r++) {
                List<Integer> ids = byRank.get(r);
                if (ids == null || ids.size() < unitSize) {
                    ok = false;
                    break;
                }
                result.addAll(ids.subList(0, unitSize));
            }
            if (ok) {
                return result;
            }
        }
        return null;
    }

    /**
     * 找飞机（含带翅膀）。{@code wings}=0 不带 / 1 带单 / 2 带对。
     * 同 chainLength、机身最高 rank > aboveMax。
     */
    private static List<Integer> findAirplane(Map<Integer, List<Integer>> byRank,
                                              int chainLength, int aboveMax, int wings) {
        for (int start = aboveMax - chainLength + 2; start + chainLength - 1 <= 14; start++) {
            if (start < 3) {
                continue;
            }
            int end = start + chainLength - 1;
            if (end > 14 || end <= aboveMax) {
                continue;
            }
            boolean ok = true;
            List<Integer> result = new ArrayList<>();
            for (int r = start; r <= end; r++) {
                List<Integer> ids = byRank.get(r);
                if (ids == null || ids.size() < 3) {
                    ok = false;
                    break;
                }
                result.addAll(ids.subList(0, 3));
            }
            if (!ok) {
                continue;
            }
            if (wings == 0) {
                return result;
            }
            // 收集翅膀候选（不重复使用机身牌）
            List<Integer> kickerPool = new ArrayList<>();
            for (Map.Entry<Integer, List<Integer>> e : byRank.entrySet()) {
                int r = e.getKey();
                if (r >= start && r <= end) {
                    // 机身 rank 多余的牌（如四张）也可当单翅膀
                    for (int i = 3; i < e.getValue().size(); i++) {
                        kickerPool.add(e.getValue().get(i));
                    }
                } else {
                    kickerPool.addAll(e.getValue());
                }
            }
            if (wings == 1) {
                if (kickerPool.size() >= chainLength) {
                    // 优先小牌当翅膀
                    kickerPool.sort((a, b) -> Integer.compare(DdzCards.rank(a), DdzCards.rank(b)));
                    result.addAll(kickerPool.subList(0, chainLength));
                    return result;
                }
            } else {
                // 带对：需要 chainLength 对
                Map<Integer, Integer> poolCounts = new HashMap<>();
                for (int id : kickerPool) {
                    poolCounts.merge(DdzCards.rank(id), 1, Integer::sum);
                }
                List<Integer> pairRanks = new ArrayList<>();
                for (Map.Entry<Integer, Integer> e : poolCounts.entrySet()) {
                    if (e.getValue() >= 2) {
                        pairRanks.add(e.getKey());
                    }
                }
                if (pairRanks.size() >= chainLength) {
                    pairRanks.sort(Integer::compareTo);
                    for (int i = 0; i < chainLength; i++) {
                        List<Integer> ids = byRank.get(pairRanks.get(i));
                        result.add(ids.get(0));
                        result.add(ids.get(1));
                    }
                    return result;
                }
            }
        }
        return null;
    }

    private static List<Integer> findFourWith(Map<Integer, List<Integer>> byRank,
                                              int quadRank, boolean pairKickers) {
        for (int r = quadRank + 1; r <= 15; r++) {
            List<Integer> ids = byRank.get(r);
            if (ids == null || ids.size() < 4) {
                continue;
            }
            List<Integer> result = new ArrayList<>(ids.subList(0, 4));
            if (pairKickers) {
                int pairsFound = 0;
                for (int kr = 3; kr <= 17 && pairsFound < 2; kr++) {
                    if (kr == r) {
                        continue;
                    }
                    List<Integer> k = byRank.get(kr);
                    if (k != null && k.size() >= 2) {
                        result.add(k.get(0));
                        result.add(k.get(1));
                        pairsFound++;
                    }
                }
                if (pairsFound == 2) {
                    return result;
                }
            } else {
                int singles = 0;
                for (int kr = 3; kr <= 17 && singles < 2; kr++) {
                    if (kr == r) {
                        continue;
                    }
                    List<Integer> k = byRank.get(kr);
                    if (k != null && !k.isEmpty()) {
                        result.add(k.get(0));
                        singles++;
                        if (k.size() >= 2) {
                            // 一张牌的对子贡献第二张单
                            result.add(k.get(1));
                            singles++;
                        }
                    }
                }
                if (singles >= 2) {
                    return result;
                }
            }
        }
        return null;
    }

    /**
     * 牌型的中文名（播报用）。
     * <p>
     * 带牌/链式牌型会带上张数（如「飞机带翅膀」8 张），单张/对子等直接给名字 ——
     * 播报里出现「STRAIGHT(5, chain=5, rank=8)」对玩家毫无意义。
     */
    public String label() {
        return switch (kind) {
            case SINGLE -> "单张";
            case PAIR -> "对子";
            case TRIPLE -> "三张";
            case TRIPLE_WITH_SINGLE -> "三带一";
            case TRIPLE_WITH_PAIR -> "三带二";
            case STRAIGHT -> "顺子" + cardCount + "张";
            case CONSECUTIVE_PAIRS -> "连对" + cardCount + "张";
            case AIRPLANE -> "飞机" + chainLength + "组";
            case AIRPLANE_WITH_SINGLES -> "飞机带单" + cardCount + "张";
            case AIRPLANE_WITH_PAIRS -> "飞机带对" + cardCount + "张";
            case FOUR_WITH_TWO_SINGLES -> "四带二";
            case FOUR_WITH_TWO_PAIRS -> "四带两对";
            case BOMB -> "炸弹";
            case ROCKET -> "王炸";
        };
    }

    @Override
    public String toString() {
        return kind + "(" + cardCount + ", chain=" + chainLength + ", rank=" + mainRank + ")";
    }
}
