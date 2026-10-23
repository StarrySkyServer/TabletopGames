package com.tabletopgames.game;

import cn.nukkit.Player;
import cn.nukkit.form.element.ElementDropdown;
import cn.nukkit.form.element.ElementLabel;
import cn.nukkit.form.handler.FormResponseHandler;
import cn.nukkit.form.window.FormWindowCustom;
import com.tabletopgames.config.BoardKind;
import com.tabletopgames.config.TabletopGamesConfig;
import com.tabletopgames.config.TabletopGamesConfig.DdzTier;
import com.tabletopgames.game.ddz.DdzDifficulty;
import com.tabletopgames.ui.SettingsMenu;
import com.tabletopgames.ui.Simple;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 斗地主牌桌菜单。
 *
 * <h2>三态</h2>
 * <ul>
 *   <li><b>未入座</b>：空位「坐X位」（只列空位）+「底分档位」（当前档高亮）+「斗地主规则」；
 *       全满时只给提示。</li>
 *   <li><b>已入座 · 等待</b>：桌主可见「加人机」「开始游戏 / 下一局」「清除人机」；
 *       所有人可见「托管 / 取消托管」+「退出牌桌」。非桌主的在座玩家只看到后两个。</li>
 *   <li><b>已入座 · 结算</b>：「下一局」（= 投续局票，可取消）+「托管」+「退出牌桌」。</li>
 * </ul>
 *
 * <h2>⚠️ 与 UNO 菜单的三处差异</h2>
 * <ol>
 *   <li><b>没有「设置系数」</b>：斗地主移除了系数体系，底分只能在四档里选
 *       （免费 0 / 初级 50 / 高级 800 / 大师 10000），而这四档是<b>全局配置</b>，
 *       不是每桌可设 —— 菜单只读不写，写了会出现「两个人在同一桌选到不同底分」。</li>
 *   <li><b>没有菜单独占锁</b>：UNO 之所以要锁，是因为 0 人入座时要设每桌倍数。
 *       斗地主 0 人入座只有一个「坐下」动作，并发点两下只是第二次被「已被占了」挡掉，
 *       不需要额外锁（省掉一份状态与它的失效分支）。</li>
 *   <li><b>不自动重弹</b>：UNO 的 {@code refreshSeatMenus} 会在座位变化时给所有在座玩家
 *       重弹表单。斗地主<b>刻意不做</b>：结算后玩家可能正在看结算板，
 *       被强行关表单一无所知只会让人多点几下（UNO 那边 10-18 刚把自动弹窗去掉）。</li>
 * </ol>
 */
public final class DdzMenu {

    /** 正开着本桌菜单的玩家 → 牌桌（用于判断是否该关旧表单）。 */
    private static final Map<UUID, DdzGame> SHOWING = new HashMap<>();

    private DdzMenu() {
    }

    public static boolean isShowing(Player player, DdzGame table) {
        return player != null && SHOWING.get(player.getUniqueId()) == table;
    }

    public static void closeCurrentForm(Player player) {
        SHOWING.remove(player.getUniqueId());
        if (!player.formWindows.isEmpty()) {
            player.closeFormWindows();
        }
    }

    public static void open(Player player, DdzGame table) {
        if (player == null || table == null) {
            return;
        }
        closeCurrentForm(player);
        Simple form = new Simple("斗地主 [Beta]", table.statusText(player));
        int seat = table.seatOf(player.getUniqueId());
        if (seat >= 0) {
            buildSeated(form, player, table, seat);
        } else {
            buildVisitor(form, player, table);
        }
        if (form.buttonCount() == 0) {
            return;
        }
        UUID id = player.getUniqueId();
        form.onClose(() -> SHOWING.remove(id));
        SHOWING.put(id, table);
        form.show(player);
    }

    // ------------------------------------------------------------------

    private static void buildVisitor(Simple form, Player player, DdzGame table) {
        int free = 0;
        for (int s = 0; s < 3; s++) {
            if (table.isOccupied(s) || table.isBot(s)) {
                continue;
            }
            free++;
            final int seat = s;
            form.add("坐" + DdzGame.seatLabel(seat), () -> {
                String err = table.join(player, seat);
                if (err != null) {
                    player.sendTip(err.startsWith("§") ? err : "§c" + err);
                    open(player, table);
                } else {
                    // 入座成功 → 自动弹新表单（UNO 同款，2026-10-20）
                    open(player, table);
                }
            });
        }
        if (free == 0) {
            player.sendTip("§c座位已满（3 人），无法加入。");
            return;
        }
        // 「牌桌配置」放在规则上面（用户指定位置），但**只有 0 人入座时可调**
        // —— 与 UNO 的「设置每局倍数」同款：一旦有人坐下，场次就定死。
        if (table.occupiedSeats() == 0) {
            form.add("牌桌配置", () -> openTableConfig(player, table));
        }
        form.add("斗地主规则", () -> showRules(player));
        // 「全局配置」与 UNO 同款：**0 人入座/未入座时**就能开（OP），不必先坐下
        if (player.isOp()) {
            form.add("全局配置", () -> SettingsMenu.open(player, BoardKind.DDZ));
        }
    }

    /**
     * 「加人机」按钮：按场次决定给几个、给什么档（2026-10-22 用户要求）。
     *
     * <table>
     *   <tr><th>场次</th><th>按钮</th><th>前置</th></tr>
     *   <tr><td>免费场</td><td>「加人机 · 初级」+「加人机 · 高级」</td><td>桌主 + 有空位</td></tr>
     *   <tr><td>新手场 / 高级场</td><td>单个「加人机」= <b>高级</b></td><td>桌主 + <b>已满 2 位真人</b></td></tr>
     *   <tr><td>大师场</td><td>不显示</td><td>只许 3 位真人（{@code addBot} 里还有第二道防线）</td></tr>
     * </table>
     *
     * <p>「≥2 真人」是防刷币：1 真人 + 1 人机的结构里，真人可以从机器人身上搬钱结算；
     * 免费场底分为 0、没有钱可搬，所以不设门槛。与 UNO「免费局三档 / 倍数≠0 只给高级且">
     * 2 真人」是同一条规则。</p>
     */
    private static void addBotButtons(Simple form, Player player, DdzGame table) {
        DdzTier tier = table.getTier();
        if (tier == DdzTier.MASTER) {
            return;
        }
        if (tier == DdzTier.FREE) {
            addBotButton(form, player, table, "加人机 · 初级", DdzDifficulty.SIMPLE);
            addBotButton(form, player, table, "加人机 · 高级", DdzDifficulty.ADVANCED);
            return;
        }
        // 新手场 / 高级场：至少 2 位真人才能补人机
        if (table.humanCount() < 2) {
            return;
        }
        addBotButton(form, player, table, "加人机", DdzDifficulty.ADVANCED);
    }

    /** 单个「加人机」按钮：按指定档位补一个，加完重开表单（UNO 同款）。 */
    private static void addBotButton(Simple form, Player player, DdzGame table,
                                     String label, DdzDifficulty botTier) {
        form.add(label, () -> {
            String err = table.addBot(botTier);
            if (err != null) {
                player.sendTip("§c" + err);
            }
            open(player, table);
        });
    }

    private static void buildSeated(Simple form, Player player, DdzGame table, int seat) {
        DdzGame.TablePhase phase = table.getPhase();
        boolean owner = table.isFirstJoiner(player.getUniqueId());

        if (phase == DdzGame.TablePhase.SETTLED) {
            boolean voted = table.hasVotedNext(seat);
            form.add(voted ? "已确认「下一局」（再点取消）" : "下一局", () -> {
                String err = table.voteNext(player);
                if (err != null) {
                    player.sendTip("§c" + err);
                }
            });
        } else if (phase == DdzGame.TablePhase.WAITING) {
            if (owner) {
                // 满 3 家就不该再出现「加人机」（否则点了必然被拒：已经在座 3 人）
                if (table.seatedCount() < 3) {
                    addBotButtons(form, player, table);
                }
                // 斗地主必须 3 家（真人 + 机器人合计）；不足时按钮不出现，避免点了被拒
                if (table.seatedCount() >= 3) {
                    form.add(table.waitingButtonLabel(), () -> {
                        String err = table.startRound();
                        if (err != null) {
                            player.sendTip("§c" + err);
                        }
                    });
                }
                if (table.hasBots()) {
                    form.add("清除人机", () -> {
                        String err = table.clearBots();
                        if (err != null) {
                            player.sendTip("§c" + err);
                        }
                    });
                }
            }
        }
        // 托管按钮只在开局后提供（等待期没有可托管的东西）
        if (phase != DdzGame.TablePhase.WAITING) {
            form.add(table.isTrusted(seat) ? "取消托管" : "托管（系统代打）", () -> {
                String err = table.toggleTrust(player);
                if (err != null) {
                    player.sendTip("§c" + err);
                }
            });
        }
        // 「牌桌配置」不在这里：与 UNO 同款，只允许「0 人入座」时改（人一坐下场次就定死）。
        // 「全局配置」同理，只放在访客菜单，OP 无需先坐下。
        form.add("退出牌桌", () -> table.leave(player));
    }

    /**
     * 牌桌配置表单：只有「经济场次」一个下拉（用户 2026-10-20 要求）。
     *
     * <p>用 {@link ElementDropdown}（能展开更多选项的那种），而不是一排单选按钮 ——
     * 与模组的「牌桌配置 → 经济场次」同款控件。</p>
     */
    private static void openTableConfig(Player player, DdzGame table) {
        // 双保险：按钮已经在 occupiedSeats()==0 时才出现，这里再拦一次
        if (table.occupiedSeats() > 0) {
            player.sendTip("§c本桌已经有人入座，场次不能再改（等所有人离开后可调）。");
            return;
        }
        TabletopGamesConfig.DdzTier current = table.getTier();
        FormWindowCustom form = new FormWindowCustom("斗地主 [Beta] · 牌桌配置");
        form.addElement(new ElementLabel(
                "§7本桌当前场次：§e" + current.label()
                        + "§7（底分 " + trimNum(table.baseScoreOfTier()) + "）"));
        form.addElement(new ElementDropdown("经济场次",
                java.util.Arrays.asList(DdzTier.labels()), current.ordinal()));
        form.addElement(new ElementLabel(
                "§7· 免费场：底分 0，不涉及金币\n"
                        + "§7· 新手场：底分 50\n"
                        + "§7· 高级场：底分 800\n"
                        + "§7· 大师场：底分 10000\n"
                        + "§7入座门槛 = 底分 × 全局门槛系数（OP 可在全局配置里调）。\n"
                        + "§7对局进行中不能改场次，改动在下一局生效。"));
        form.addHandler(FormResponseHandler.withoutPlayer(ignored -> {
            if (form.wasClosed()) {
                return;
            }
            // ⚠️ Nukkit 的 FormResponseData 只暴露 getElementContent()（选中项的**文本**）、
            //    没有 getSelectedOptionIndex()，所以只能拿文本反查下标。
            String picked;
            try {
                picked = form.getResponse().getDropdownResponse(1).getElementContent();
            } catch (RuntimeException ex) {
                picked = null;
            }
            TabletopGamesConfig.DdzTier next = current;
            String[] labels = DdzTier.labels();
            for (int i = 0; picked != null && i < labels.length; i++) {
                if (labels[i].equals(picked)) {
                    next = DdzTier.byOrdinal(i);
                    break;
                }
            }
            String err = table.setTier(next);
            if (err != null) {
                player.sendTip("§c" + err);
            } else {
                player.sendTip("§a本桌场次已设为 §e" + next.label()
                        + "§a（底分 " + trimNum(next.baseScore()) + "）。");
            }
        }));
        player.showFormWindow(form);
    }

    /** 去掉金额的小数尾巴（50 / 800 / 10000 而不是 50.0）。 */
    private static String trimNum(double v) {
        if (v == Math.rint(v)) {
            return String.valueOf((long) v);
        }
        return String.valueOf(Math.round(v * 100.0) / 100.0);
    }

    private static void showRules(Player player) {
        player.sendMessage("§b[斗地主规则]");
        player.sendMessage("§7· 三家，叫地主／抢地主定地主；地主拿 3 张底牌，共 20 张。");
        player.sendMessage("§7· 定地主后每人选一次加倍：不加倍 ×1 / 加倍 ×2 / 超级加倍 ×4。");
        player.sendMessage("§7· 牌型：单张、对子、三张、三带一/二、顺子（≥5 连）、连对（≥3 连）、");
        player.sendMessage("§7  飞机（带单/带对）、四带二/两对、炸弹、王炸。");
        player.sendMessage("§7· 跟牌必须同牌型且更大；炸弹／王炸可压任何牌；一轮两家不出即上一手自由出。");
        player.sendMessage("§7· 倍数 = 抢地主次数的 2 的幂 × 每家加倍因子，炸弹与春天再 ×2（有上限）。");
        player.sendMessage("§7· 结算：unit = 底分 × 倍数 × 地主因子 × 该农民因子；");
        player.sendMessage("§7  入座先扣「底分 × 入座门槛系数」，结算时再从赢家应得里按抽水比例扣。");
    }
}
