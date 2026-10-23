package com.tabletopgames.game.ddz;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 斗地主「高级」档：**蒙特卡洛推演**（公平版本）。
 *
 * <h2>思路</h2>
 * 对每一个候选出法，按对手剩余张数<b>随机采样</b>若干套对手手牌，各打一条"贪心线"到终局，
 * 统计我方（地主 / 农民阵营）胜率，取胜率最高的那一手。
 *
 * <p>这与 ratel 那套「试打推演」是同一思想，但有两处关键差别：</p>
 * <ol>
 *   <li><b>不偷看对手手牌</b>：只拿「未知牌集合」（= 全部 54 张减去我的手牌，等价于
 *       "54 − 自己 − 已打出"这份公开信息）随机分配，按各自剩余张数切分；</li>
 *   <li><b>单线推演而不是全树展开</b>：每个采样点只走一条贪心线（各自出最小可压的牌），
 *       所以代价是 {@code 候选数 × 采样数 × 回合数}，比 ratel 的深搜便宜 1~2 个数量级，
 *       实测同一量级下可以从"900 ms/次"降到"十几毫秒/次"，于是采样数可以给得更足。</li>
 * </ol>
 *
 * <h2>纯函数</h2>
 * 本类<b>不引用任何 Nukkit 类型、也不碰 {@code DdzRound}</b>，入参全是传值快照，
 * 因此可以直接在后台线程跑（见 {@code AiThinkDispatcher.heavy}），也可脱机回归。
 */
public final class DdzRolloutAi {

    /** 根候选上限：首出时枚举可能上百个，先按"最便宜"截断，避免把时间全花在明显烂的牌上。 */
    private static final int MAX_ROOT = 16;

    /** 单条推演的最大回合数（正常一局几十手，超过就是死循环保护）。 */
    private static final int MAX_PLIES = 120;

    private DdzRolloutAi() {
    }

    /**
     * 选出这一手要出的牌。
     *
     * @param myHand     我的手牌（不会被修改）
     * @param last       上一手牌型；null = 我首出
     * @param lastSeat   上一手是谁出的（首出传 -1）
     * @param mySeat     我的座位
     * @param landlord   地主座位
     * @param unknown    未知牌集合（= 其它两家的牌并集；调用方保证不含我的手牌）
     * @param handSizes  三家当前手牌张数（下标 = 座位）
     * @param deadlineNanos {@link System#nanoTime()} 的截止时刻，超了就带着当前最优返回
     * @param samples    每个候选采样几套对手手牌（越大越准越慢）
     * @return 要出的牌；<b>空列表 = 要不起（过）</b>
     */
    public static List<Integer> bestPlay(List<Integer> myHand, DdzPattern last, int lastSeat,
                                         int mySeat, int landlord, List<Integer> unknown,
                                         int[] handSizes, long deadlineNanos, int samples,
                                         Random rnd) {
        if (myHand == null || myHand.isEmpty()) {
            return List.of();
        }
        List<List<Integer>> cands = candidates(myHand, last);
        if (cands.isEmpty()) {
            return List.of();
        }
        if (cands.size() > MAX_ROOT) {
            cands.sort((a, b) -> {
                int c = Integer.compare(a.size(), b.size());
                return c != 0 ? c : Integer.compare(sumRank(a), sumRank(b));
            });
            cands = new ArrayList<>(cands.subList(0, MAX_ROOT));
        }

        List<Integer> best = cands.get(0);
        int bestWins = -1;
        for (List<Integer> cand : cands) {
            if (System.nanoTime() > deadlineNanos) {
                break;
            }
            int wins = 0;
            int done = 0;
            for (int i = 0; i < samples; i++) {
                if (System.nanoTime() > deadlineNanos) {
                    break;
                }
                if (rollout(cand, myHand, mySeat, landlord, unknown, handSizes, rnd)) {
                    wins++;
                }
                done++;
            }
            if (done == 0) {
                continue;
            }
            // 用「千分比」做整数比较：采样没跑完时按比例折算，
            // 免得先算的候选因为采样数不同而吃亏。
            int rate = wins * 1000 / done;
            if (rate > bestWins) {
                bestWins = rate;
                best = cand;
            }
        }
        return best;
    }

    /** 跑一条贪心线，返回我方是否获胜。 */
    private static boolean rollout(List<Integer> play, List<Integer> myHand, int mySeat,
                                   int landlord, List<Integer> unknown, int[] handSizes,
                                   Random rnd) {
        List<Integer>[] hands = new List[3];
        List<Integer> mine = new ArrayList<>(myHand);
        for (int id : play) {
            mine.remove(Integer.valueOf(id));
        }
        if (mine.isEmpty()) {
            return true;
        }
        hands[mySeat] = mine;

        // 采样对手手牌：未知牌洗匀后按各自剩余张数切分
        List<Integer> pool = new ArrayList<>(unknown);
        Collections.shuffle(pool, rnd);
        // 与牌桌一致：座位号 0=南 / 1=北 / 2=东，逆时针下家是 +2
        int s1 = (mySeat + 2) % 3;
        int s2 = (mySeat + 1) % 3;
        int n1 = Math.max(0, handSizes[s1]);
        int n2 = Math.max(0, handSizes[s2]);
        if (n1 + n2 > pool.size()) {
            // 快照对不上（理论上不该发生）⇒ 保守判负，让上层换别的候选
            return false;
        }
        hands[s1] = new ArrayList<>(pool.subList(0, n1));
        hands[s2] = new ArrayList<>(pool.subList(n1, n1 + n2));

        DdzPattern target = DdzPattern.identify(play);
        int actor = s1;
        int passes = 0;
        for (int ply = 0; ply < MAX_PLIES; ply++) {
            List<Integer> hand = hands[actor];
            List<List<Integer>> cs = candidates(hand, target);
            if (!cs.isEmpty()) {
                List<Integer> mv = cheapest(cs);
                for (int id : mv) {
                    hand.remove(Integer.valueOf(id));
                }
                if (hand.isEmpty()) {
                    return sameSide(actor, mySeat, landlord);
                }
                target = DdzPattern.identify(mv);
                passes = 0;
            } else {
                passes++;
                if (passes >= 2) {
                    // 两家都过 ⇒ 回到出牌人，重新首出
                    target = null;
                    passes = 0;
                }
            }
            actor = (actor + 2) % 3;
        }
        return false;
    }

    /** 同一阵营（都地主 / 都农民）。 */
    private static boolean sameSide(int a, int b, int landlord) {
        return (a == landlord) == (b == landlord);
    }

    /** 穷举 + 合法性终检（沿用 {@link DdzBotAi#enumerate} 的枚举，但只留能压过目标的）。 */
    private static List<List<Integer>> candidates(List<Integer> hand, DdzPattern target) {
        List<List<Integer>> raw = DdzBotAi.enumerate(hand, target);
        List<List<Integer>> legal = new ArrayList<>(raw.size());
        for (List<Integer> mv : raw) {
            DdzPattern p = DdzPattern.identify(mv);
            if (p == null) {
                continue;
            }
            if (target == null || DdzPattern.beats(p, target)) {
                legal.add(mv);
            }
        }
        return legal;
    }

    /** 贪心策略：优先出张数最少的，同张数出点数总和最小的（先跑小牌）。 */
    private static List<Integer> cheapest(List<List<Integer>> cs) {
        List<Integer> best = cs.get(0);
        for (List<Integer> c : cs) {
            if (c.size() < best.size()
                    || (c.size() == best.size() && sumRank(c) < sumRank(best))) {
                best = c;
            }
        }
        return best;
    }

    private static int sumRank(List<Integer> cards) {
        int sum = 0;
        for (int id : cards) {
            sum += DdzCards.rank(id);
        }
        return sum;
    }
}
