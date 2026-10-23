package com.tabletopgames.config;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.Header;

/**
 * TabletopGames 全局配置（全插件唯一一份，文件 {@code config.yml}）。
 * <p>
 * 文件内部按棋类 / 牌桌分节：{@code gomoku}（五子棋）、{@code xiangqi}（中国象棋）、
 * {@code othello}（黑白棋）、{@code go}（围棋）、{@code doushouqi}（斗兽棋）、{@code uno}（UNO 牌桌）、
 * {@code ddz}（斗地主牌桌）。
 * 各节互不影响 —— 改一类的设置不会波及另一类。
 * <p>
 * 推荐用游戏内对应棋菜单的「全局配置」表单调整（OP，提交后立即生效并自动写回本文件）；
 * 手动编辑后可在服务器执行 {@code /tabletopgame reload} 重载。
 */
@Header({
        "TabletopGames 全局配置（全插件唯一一份）",
        "",
        "按棋类 / 牌桌分节：gomoku=五子棋、xiangqi=中国象棋、othello=黑白棋、go=围棋、",
        "doushouqi=斗兽棋、uno=UNO 牌桌、ddz=斗地主。各节互不影响。",
        "",
        "推荐用游戏内对应棋菜单的「全局配置」表单调整（OP，提交后立即生效并自动写回本文件）；",
        "手动编辑后可在服务器执行 /tabletopgame reload 重载。各键上方均带说明注释。"})
public class TabletopGamesConfig extends OkaeriConfig {

    // ------------------------------------------------------------------
    // 棋类：五子棋 / 中国象棋 / 黑白棋 / 围棋 / 斗兽棋（字段集相同）
    // ------------------------------------------------------------------

    @Comment("五子棋设置")
    private GameSettings gomoku = new GameSettings();

    @Comment("中国象棋设置")
    private GameSettings xiangqi = new GameSettings();

    @Comment("黑白棋（奥赛罗）设置")
    private GameSettings othello = new GameSettings();

    @Comment("围棋设置（9 / 13 / 19 路共用本节的设置）")
    private GameSettings go = new GameSettings();

    @Comment("斗兽棋设置")
    private GameSettings doushouqi = new GameSettings();

    @Comment("军旗（二国陆战棋）设置")
    private JunqiSettings junqi = new JunqiSettings();

    // ------------------------------------------------------------------
    // UNO 牌桌：按倍数 k 结算，没有「每局下注金额」
    // ------------------------------------------------------------------

    @Comment("UNO 牌桌设置")
    private UnoSettings uno = new UnoSettings();

    // ------------------------------------------------------------------
    // 斗地主：4 档固定底分制（免费 / 初级 / 高级 / 大师），**没有倍数 k 区间、没有第二回合时间**
    // ------------------------------------------------------------------

    @Comment("斗地主牌桌设置")
    private DdzSettings ddz = new DdzSettings();

    /** 斗地主档位（与 {@link DdzSettings#getTier()} 对应，字符串形式便于直接改 yml）。 */
    public enum DdzTier {
        /** 免费场：底分 0，不扣钱不结算。 */
        FREE("免费场", 0.0),
        /** 新手场：底分 50。<b>原名「初级场」，用户 2026-10-20 要求统一改名。</b> */
        BASIC("新手场", 50.0),
        /** 高级场：底分 800。 */
        ADVANCED("高级场", 800.0),
        /** 大师场：底分 10000。 */
        MASTER("大师场", 10000.0);

        private final String label;
        private final double baseScore;

        DdzTier(String label, double baseScore) {
            this.label = label;
            this.baseScore = baseScore;
        }

        public String label() {
            return label;
        }

        /** 该档的底分（{@code unit = baseScore × 倍数 × 加倍因子} 里的 baseScore）。 */
        public double baseScore() {
            return baseScore;
        }

        public boolean free() {
            return this == FREE;
        }

        /** 全部档位的中文名（按 ordinal 顺序，给表单下拉用）。 */
        public static String[] labels() {
            DdzTier[] all = values();
            String[] out = new String[all.length];
            for (int i = 0; i < all.length; i++) {
                out[i] = all[i].label;
            }
            return out;
        }

        /** 按 ordinal 取档位，越界回落到 BASIC。 */
        public static DdzTier byOrdinal(int ordinal) {
            DdzTier[] all = values();
            return ordinal >= 0 && ordinal < all.length ? all[ordinal] : BASIC;
        }
    }

    /** 机器人难度（与模组三档对齐）。 */
    public enum DdzAi {
        SIMPLE("简单"),
        MEDIUM("普通"),
        HARD("困难");

        private final String label;

        DdzAi(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public GameSettings getGomoku() {
        return gomoku;
    }

    public void setGomoku(GameSettings gomoku) {
        this.gomoku = gomoku;
    }

    public GameSettings getXiangqi() {
        return xiangqi;
    }

    public void setXiangqi(GameSettings xiangqi) {
        this.xiangqi = xiangqi;
    }

    public GameSettings getOthello() {
        return othello;
    }

    public void setOthello(GameSettings othello) {
        this.othello = othello;
    }

    public GameSettings getGo() {
        return go;
    }

    public void setGo(GameSettings go) {
        this.go = go;
    }

    public GameSettings getDoushouqi() {
        return doushouqi;
    }

    public void setDoushouqi(GameSettings doushouqi) {
        this.doushouqi = doushouqi;
    }

    public JunqiSettings getJunqi() {
        return junqi;
    }

    public void setJunqi(JunqiSettings junqi) {
        this.junqi = junqi;
    }

    public UnoSettings getUno() {
        return uno;
    }

    public void setUno(UnoSettings uno) {
        this.uno = uno;
    }

    public DdzSettings getDdz() {
        return ddz;
    }

    public void setDdz(DdzSettings ddz) {
        this.ddz = ddz;
    }

    /**
     * 取指定棋类的设置节。
     * <p>UNO 牌桌字段集不同，不走本方法（请用 {@link #getUno()}）。
     */
    public GameSettings section(BoardKind kind) {
        switch (kind) {
            case GOMOKU:
                return gomoku;
            case XIANGQI:
                return xiangqi;
            case OTHELLO:
                return othello;
            case GO:
                return go;
            case DOUSHUQI:
                return doushouqi;
            case JUNQI:
                return junqi;
            default:
                throw new IllegalArgumentException("UNO / 斗地主使用独立字段集，请用 getUno() / getDdz()");
        }
    }

    /**
     * 取某一类棋牌的「玩家识别半径」。
     *
     * <p>⚠️ <b>必须逐个显式处理 UNO / 斗地主</b>：它们用独立字段集
     * （{@link UnoSettings} / {@link DdzSettings}），走 {@link #section(BoardKind)} 会抛
     * {@code IllegalArgumentException}。</p>
     *
     * <p>⚠️ 这是 2026-10-20 真机事故的根因：初版只特判了 UNO，
     * 加进 {@link BoardKind#DDZ} 之后 {@code TabletopGamesManager.refreshPlayerRanges()}
     * 在 <b>插件 onEnable 阶段</b>就抛出 —— 整个插件加载失败。表现极具误导性：
     * 物品/方块在 onLoad 已注册（创造栏看得见、能拿到手上），
     * 但 onEnable 里后续的 {@code registerEvents} 一个都没执行 ⇒ <b>所有物品右键全无反应</b>。
     * 新增棋牌种类时，这里必须同步。</p>
     */
    public double playerRangeOf(BoardKind kind) {
        switch (kind) {
            case UNO:
                return uno.getPlayerRange();
            case DDZ:
                return ddz.getPlayerRange();
            default:
                return section(kind).getPlayerRange();
        }
    }

    /**
     * 棋类设置节：五种棋共用同一字段集。
     */
    public static class GameSettings extends OkaeriConfig {

        @Comment("玩家离开棋盘中心多少格后自动退出棋局（水平方向）")
        private double playerRange = 7.0;

        @Comment("0 人入座时菜单的独占超时（秒）。超时或持有者下线会自动释放占用，让给其他人；有人入座后不再有该兜底")
        private int menuLockSeconds = 30;

        @Comment("每局下注金额区间与官方抽水（仅玩家对玩家对局生效；本插件强依赖 EconomyAPI）")
        private Betting betting = new Betting();

        public double getPlayerRange() {
            return playerRange;
        }

        public void setPlayerRange(double playerRange) {
            this.playerRange = playerRange;
        }

        /** 菜单独占超时秒数（至少 1 秒）。 */
        public int getMenuLockSeconds() {
            return Math.max(1, menuLockSeconds);
        }

        public void setMenuLockSeconds(int menuLockSeconds) {
            this.menuLockSeconds = menuLockSeconds;
        }

        public Betting getBetting() {
            return betting;
        }

        public void setBetting(Betting betting) {
            this.betting = betting;
        }

        /** 每局下注区间与官方抽水比例。 */
        public static class Betting extends OkaeriConfig {

            @Comment("官方每局抽水百分比（0~100，保留两位小数）。默认 1.0 即 1%；100 表示赢家也拿不到奖金")
            private double cut = 1.0;

            @Comment("每局可下注的最小金额（1 ~ 10000000，默认 100；填 0 的免费局不受此限制）")
            private double minBet = 100.0;

            @Comment("每局可下注的最大金额（1 ~ 10000000，默认 1000000）；运行时若小于最小值则按最小值生效")
            private double maxBet = 1000000.0;

            public double getCut() {
                return cut;
            }

            public void setCut(double cut) {
                this.cut = cut;
            }

            public double getMinBet() {
                return minBet;
            }

            public void setMinBet(double minBet) {
                this.minBet = minBet;
            }

            public double getMaxBet() {
                return maxBet;
            }

            public void setMaxBet(double maxBet) {
                this.maxBet = maxBet;
            }

            /** 抽水比例：钳制到 0~100 并四舍五入保留两位小数。 */
            public double normalizedCut() {
                double c = Math.max(0.0, Math.min(100.0, cut));
                return Math.round(c * 100.0) / 100.0;
            }

            /** 每局最小下注金额（负数归零）。 */
            public double normalizedMinBet() {
                return Math.max(0.0, Math.round(minBet * 100.0) / 100.0);
            }

            /** 每局最大下注金额；若配置小于最小值则取最小值。 */
            public double normalizedMaxBet() {
                double max = Math.max(0.0, Math.round(maxBet * 100.0) / 100.0);
                return Math.max(max, normalizedMinBet());
            }
        }
    }

    /**
     * 军旗设置：棋类通用字段 + 军旗规则开关。
     */
    public static class JunqiSettings extends GameSettings {

        @Comment("军旗规则开关")
        private Rules rules = new Rules();

        public Rules getRules() {
            return rules;
        }

        public void setRules(Rules rules) {
            this.rules = rules;
        }

        /** 军旗专属规则（通用的半径 / 菜单锁 / 下注字段继承自 {@link GameSettings}）。 */
        public static class Rules extends OkaeriConfig {

            @Comment({"行营的斜线是否可以走（棋盘上画了斜线，默认允许）", "默认 true"})
            private boolean campDiagonal = true;

            @Comment({"连续多少手无吃子判和", "默认 150（0 = 不判和）"})
            private int drawQuietMoves = 150;

            @Comment({"先手：red = 红先，black = 黑先，random = 随机", "默认 red"})
            private String firstMove = "red";

            public boolean isCampDiagonal() {
                return campDiagonal;
            }

            public void setCampDiagonal(boolean campDiagonal) {
                this.campDiagonal = campDiagonal;
            }

            public int getDrawQuietMoves() {
                return Math.max(0, drawQuietMoves);
            }

            public void setDrawQuietMoves(int drawQuietMoves) {
                this.drawQuietMoves = drawQuietMoves;
            }

            public String getFirstMove() {
                return firstMove == null ? "red" : firstMove.toLowerCase(java.util.Locale.ROOT);
            }

            public void setFirstMove(String firstMove) {
                this.firstMove = firstMove;
            }
        }
    }

    /**
     * UNO 牌桌设置节。
     * <p>
     * 与棋类配置的差别：没有「最小/最大下注金额」（UNO 按倍数 k 结算），
     * 取而代之的是「最小/最大倍数 k」「入座门槛系数」「是否能合成（工作台配方）」与
     * 「第一次 / 第二次回合时间」。
     * <p>
     * 回合时间分两档：第一次 = 还没摸牌时的「出牌 / 摸牌」时限（默认 20 秒）；
     * 第二次 = 摸牌之后「出牌 / 跳过」的时限（默认 15 秒）；计时在摸牌那刻清零重算。
     */
    public static class UnoSettings extends OkaeriConfig {

        @Comment("玩家离开牌桌中心多少格后自动退出（水平方向）")
        private double playerRange = 7.0;

        @Comment("0 人入座时菜单的独占超时（秒）。超时或持有者下线会自动释放占用，让给其他人；有人入座后不再有该兜底")
        private int menuLockSeconds = 30;

        @Comment("官方每局抽水百分比（0~100，保留两位小数）。默认 1.0 即 1%；100 表示赢家也拿不到奖金")
        private double cut = 1.0;

        // 注意：多行注释必须用「数组」形式。写成单个字符串 + "\n" 时，
        // okaeri 只会给整串开头加 "#"，续行会裸输出成非法 YAML 键，导致配置解析失败、插件加载不起来。
        @Comment({"入座门槛系数：门槛 = 本系数 × 本桌倍数 k（默认 10，即倍数的 10 倍）。",
                "倍数 k > 0 时，每位真人玩家入座时余额必须【大于等于】该门槛，不足则直接拒绝入座；",
                "k = 0 的免费局不受此限制。例：系数 10 + 倍数 10 → 入座要求余额 ≥ 100。"})
        private double joinBalanceMultiplier = 10.0;

        @Comment({"可设置的最小倍数 k（默认 10）。设置「倍数 k」表单的校验下界",
                "注意：倍数 0（免费局）始终可填，不受本值限制；",
                "本项与「最大倍数 k」之间：最大 < 最小 时按最小生效"})
        private double minCoefficient = 10.0;

        @Comment("可设置的最大倍数 k（硬上限 1000，超出部分运行时钳制）")
        private double maxCoefficient = 1000.0;

        @Comment({"UNO 牌桌物品是否可在工作台合成（默认 false = 不能合成，只能由 OP 用 /tabletopgame uno 发放）",
                "开启后配方为（青色地毯 x3 / 深色橡木原木 x3 / 橡木板 x3）：",
                "  青色地毯   青色地毯   青色地毯",
                "  深色橡木原木 深色橡木原木 深色橡木原木",
                "  橡木板     橡木板     橡木板"})
        private boolean craftable = false;

        @Comment({"「第一次回合时间」（秒，5~600）：轮到人类玩家后、**还没有摸牌**时的行动时限。",
                "也就是「出牌 / 摸牌」这一步的思考时间；机器人 / 托管的固定 2 秒思考不受影响"})
        private int turnSeconds = 20;

        @Comment({"「第二次回合时间」（秒，5~600）：玩家【摸牌之后】必须「出牌 / 跳过」的时限。",
                "摸牌会让计时重新起算并改用本项（一般比第一次短，逼玩家尽快决定）"})
        private int secondTurnSeconds = 15;

        public double getPlayerRange() {
            return playerRange;
        }

        public void setPlayerRange(double playerRange) {
            this.playerRange = playerRange;
        }

        /** 菜单独占超时秒数（至少 1 秒）。 */
        public int getMenuLockSeconds() {
            return Math.max(1, menuLockSeconds);
        }

        public void setMenuLockSeconds(int menuLockSeconds) {
            this.menuLockSeconds = menuLockSeconds;
        }

        public double getCut() {
            return cut;
        }

        public void setCut(double cut) {
            this.cut = cut;
        }

        public double getJoinBalanceMultiplier() {
            return joinBalanceMultiplier;
        }

        public void setJoinBalanceMultiplier(double joinBalanceMultiplier) {
            this.joinBalanceMultiplier = joinBalanceMultiplier;
        }

        public double getMinCoefficient() {
            return minCoefficient;
        }

        public void setMinCoefficient(double minCoefficient) {
            this.minCoefficient = minCoefficient;
        }

        public double getMaxCoefficient() {
            return maxCoefficient;
        }

        public void setMaxCoefficient(double maxCoefficient) {
            this.maxCoefficient = maxCoefficient;
        }

        /** 是否能合成：UNO 牌桌物品是否可在工作台合成。false 时不会注册任何配方。 */
        public boolean isCraftable() {
            return craftable;
        }

        public void setCraftable(boolean craftable) {
            this.craftable = craftable;
        }

        public int getTurnSeconds() {
            return turnSeconds;
        }

        public void setTurnSeconds(int turnSeconds) {
            this.turnSeconds = turnSeconds;
        }

        public int getSecondTurnSeconds() {
            return secondTurnSeconds;
        }

        public void setSecondTurnSeconds(int secondTurnSeconds) {
            this.secondTurnSeconds = secondTurnSeconds;
        }

        /** 抽水比例：钳制到 0~100 并四舍五入保留两位小数。 */
        public double normalizedCut() {
            double c = Math.max(0.0, Math.min(100.0, cut));
            return Math.round(c * 100.0) / 100.0;
        }

        /** 入座门槛系数（负数归零）。实际门槛 = 本值 × 倍数 k。 */
        public double normalizedJoinBalanceMultiplier() {
            return Math.max(0.0, Math.round(joinBalanceMultiplier * 100.0) / 100.0);
        }

        /** 最小倍数 k（负数归零；硬上限 1000 由 UnoTable 侧再钳一次）。 */
        public double normalizedMinCoefficient() {
            return Math.max(0.0, Math.floor(minCoefficient));
        }

        /** 最大倍数 k；若小于最小值则取最小值。硬上限 1000 由 UnoTable 侧再钳一次。 */
        public double normalizedMaxCoefficient() {
            return Math.max(Math.floor(maxCoefficient), normalizedMinCoefficient());
        }

        /** 第一次回合时间（秒，未摸牌时的出牌/摸牌时限）：钳制到 5 ~ 600。 */
        public int normalizedTurnSeconds() {
            return Math.max(5, Math.min(600, turnSeconds));
        }

        /** 第二次回合时间（秒，摸牌后的出牌/跳过时限）：钳制到 5 ~ 600。 */
        public int normalizedSecondTurnSeconds() {
            return Math.max(5, Math.min(600, secondTurnSeconds));
        }
    }

    /**
     * 斗地主牌桌设置。
     * <p>
     * <b>与 UNO 的三处关键差异（用户明确要求）：</b>
     * <ol>
     *   <li><b>没有「第二回合时间」</b> —— 斗地主一局里每次轮到的动作都是「出牌 / 不出」，
     *       不存在 UNO 那种「先摸牌、再限时出牌」的两段时限，一个 {@code turnSeconds} 即可。</li>
     *   <li><b>没有倍数 k 区间</b> —— 改成 4 档固定底分（免费 0 / 初级 50 / 高级 800 / 大师 10000）。
     *       输赢幅度由「倍数」决定（叫抢 2^n、加倍 ×2/×4、炸弹 ×2、春天 ×2），与档位底分正交，
     *       不需要再叠一层系数体系。</li>
     *   <li><b>抽水改成两段式</b> —— 入座时先实扣本金（余额真的少），结算时赢家侧再扣抽水，
     *       前端显示「应得 / 抽水 / 实收」三行。</li>
     * </ol>
     */
    public static class DdzSettings extends OkaeriConfig {

        @Comment("玩家离开牌桌中心多少格后自动退出（水平方向）")
        private double playerRange = 7.0;

        @Comment("0 人入座时菜单的独占超时（秒）。超时或持有者下线会自动释放占用，让给其他人；有人入座后不再有该兜底")
        private int menuLockSeconds = 30;

        @Comment({"官方每局抽水百分比（0~100，保留两位小数）。默认 1.0 即 1%；100 表示赢家也拿不到奖金。",
                "斗地主与 UNO 同口径：入座与开局都不扣钱，结算时按输赢一次性扣（扣输家 → 汇奖池 → 按本比例抽水 → 给赢家）。"})
        private double cut = 1.0;

        @Comment({"入座门槛系数：门槛 = 本系数 × 本桌底分（默认 10，即底分的 10 倍）。",
                "只用于【校验余额够不够入座】，不扣钱 —— 钱在对局结束时才结算。",
                "免费场（底分 0）不受此限制，任何余额都能进。",
                "例：初级场底分 50 + 系数 10 → 入座要求余额 ≥ 500。"})
        private double joinBalanceMultiplier = 10.0;

        @Comment({"档位（决定底分）：free=免费场(0) / basic=初级场(50) / advanced=高级场(800) / master=大师场(10000)。",
                "填错或留空按 free 处理（2026-10-22 起默认免费场）。底分不可自定义，只能选这 4 档。"})
        private String tier = "free";

        @Comment({"机器人难度：simple=简单 / medium=普通 / hard=困难。填错按 medium 处理。"})
        private String aiDifficulty = "medium";

        @Comment({"斗地主牌桌物品是否可在工作台合成（默认 false = 不能合成，只能由 OP 用 /tabletopgame ddz 发放）"})
        private boolean craftable = false;

        @Comment({"回合时间（秒，5~600）：轮到人类玩家后的行动时限（出牌 / 不出 / 叫抢 / 加倍都算）。",
                "机器人与托管固定 2 秒思考，不受本值影响。"})
        private int turnSeconds = 20;

        @Comment({"发牌节奏：每张牌间隔毫秒（20~200，默认 200 = UNO 每张 100ms 的两倍）。",
                "200ms × 17 张 = 3.4 秒发完（用户 2026-10-20：按 UNO 的两倍速度）。"})
        private int dealStepMillis = 200;

        @Comment({"倍数上限（16~65536，默认 1024）。叫抢 2^n + 炸弹/春天 ×2 会指数增长，靠本值截断防炸档。"})
        private int maxMultiplier = 1024;

        public double getPlayerRange() {
            return playerRange;
        }

        public void setPlayerRange(double playerRange) {
            this.playerRange = playerRange;
        }

        /** 菜单独占超时秒数（至少 1 秒）。 */
        public int getMenuLockSeconds() {
            return Math.max(1, menuLockSeconds);
        }

        public void setMenuLockSeconds(int menuLockSeconds) {
            this.menuLockSeconds = menuLockSeconds;
        }

        public double getCut() {
            return cut;
        }

        public void setCut(double cut) {
            this.cut = cut;
        }

        public double getJoinBalanceMultiplier() {
            return joinBalanceMultiplier;
        }

        public void setJoinBalanceMultiplier(double joinBalanceMultiplier) {
            this.joinBalanceMultiplier = joinBalanceMultiplier;
        }

        public String getTier() {
            return tier;
        }

        public void setTier(String tier) {
            this.tier = tier;
        }

        public String getAiDifficulty() {
            return aiDifficulty;
        }

        public void setAiDifficulty(String aiDifficulty) {
            this.aiDifficulty = aiDifficulty;
        }

        public boolean isCraftable() {
            return craftable;
        }

        public void setCraftable(boolean craftable) {
            this.craftable = craftable;
        }

        public int getTurnSeconds() {
            return turnSeconds;
        }

        public void setTurnSeconds(int turnSeconds) {
            this.turnSeconds = turnSeconds;
        }

        public int getDealStepMillis() {
            return dealStepMillis;
        }

        public void setDealStepMillis(int dealStepMillis) {
            this.dealStepMillis = dealStepMillis;
        }

        public int getMaxMultiplier() {
            return maxMultiplier;
        }

        public void setMaxMultiplier(int maxMultiplier) {
            this.maxMultiplier = maxMultiplier;
        }

        /** 抽水比例：钳制到 0~100 并四舍五入保留两位小数。 */
        public double normalizedCut() {
            double c = Math.max(0.0, Math.min(100.0, cut));
            return Math.round(c * 100.0) / 100.0;
        }

        /** 入座门槛系数（负数归零）。实际门槛 = 本值 × 底分。 */
        public double normalizedJoinBalanceMultiplier() {
            return Math.max(0.0, Math.round(joinBalanceMultiplier * 100.0) / 100.0);
        }

        /** 档位（填错 / 留空 / 非法值一律回落到 {@link DdzTier#FREE}，与默认值一致）。 */
        public DdzTier normalizedTier() {
            if (tier == null) {
                return DdzTier.FREE;
            }
            String t = tier.trim().toLowerCase(java.util.Locale.ROOT);
            for (DdzTier v : DdzTier.values()) {
                if (v.name().toLowerCase(java.util.Locale.ROOT).equals(t)) {
                    return v;
                }
            }
            return DdzTier.FREE;
        }

        /** 机器人难度（填错 / 留空一律回落到 {@link DdzAi#MEDIUM}）。 */
        public DdzAi normalizedAiDifficulty() {
            if (aiDifficulty == null) {
                return DdzAi.MEDIUM;
            }
            String t = aiDifficulty.trim().toLowerCase(java.util.Locale.ROOT);
            for (DdzAi v : DdzAi.values()) {
                if (v.name().toLowerCase(java.util.Locale.ROOT).equals(t)) {
                    return v;
                }
            }
            return DdzAi.MEDIUM;
        }

        /** 本桌底分（= 档位基底；免费场为 0）。 */
        public double baseScore() {
            return normalizedTier().baseScore();
        }

        /**
         * 入座<b>门槛</b>金额（只校验余额，不扣钱）。
         * <p>
         * 免费场返回 0；其余 = 底分 × 门槛系数。钱在对局结束时按输赢结算，
         * 见 {@code DdzGame#settleEconomy}。
         */
        public double stakeAmount() {
            double base = baseScore();
            if (base <= 0.0) {
                return 0.0;
            }
            return Math.round(base * normalizedJoinBalanceMultiplier() * 100.0) / 100.0;
        }

        /** 回合时间（秒）：钳制到 5 ~ 600。 */
        public int normalizedTurnSeconds() {
            return Math.max(5, Math.min(600, turnSeconds));
        }

        /** 发牌间隔（毫秒）：钳制到 20 ~ 200。 */
        public int normalizedDealStepMillis() {
            return Math.max(20, Math.min(200, dealStepMillis));
        }

        /** 倍数上限：钳制到 16 ~ 65536。 */
        public int normalizedMaxMultiplier() {
            return Math.max(16, Math.min(65536, maxMultiplier));
        }
    }
}
