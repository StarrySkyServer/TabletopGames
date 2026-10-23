package com.tabletopgames.entity;

import cn.nukkit.entity.Entity;
import cn.nukkit.entity.custom.CustomEntity;
import cn.nukkit.entity.custom.EntityDefinition;
import cn.nukkit.entity.data.Vector3fEntityData;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.nbt.tag.CompoundTag;
import com.tabletopgames.game.UnoTable;

/**
 * UNO 牌桌的「隐形载体」实体，对应资源包中的 {@code tabletopgame:uno_clickbox}。
 * <p>
 * 它<b>什么都不画</b>（几何体只有一根空 root 骨骼，做法与斗地主插件的 {@code ddz:table_anchor} 一致），
 * 一个实体类承担两种用途，由 {@link UnoTable} 各生成一个实例：
 * <ul>
 *   <li><b>隐形载体</b>（历史用途：点击捕获框）：它曾把客户端碰撞箱放大到 3×1.9×3，
 *       让准星在悬空手牌（第一行底边离台面就有 0.26 格）上也能命中"某个实体"。
 *       2026-10-15 用户要求**恢复原样**，不再放大（见 {@link #configureClickHitbox}），
 *       手牌点击改由「定位器」持握道具承担（手持物品的"使用"事务一定会上报服务端）。</li>
 *   <li><b>悬浮倒计时</b>（{@link #setFloatingText}）：同样不画模型，只显示头顶的
 *       {@code name tag} —— 也就是玩家要的「固定在方块上方的悬浮窗」（不是屏幕提示）。
 *       放在「牌桌原点 + 0.62 格」（见 {@code UnoTable.COUNTDOWN_LIFT} 的推导），
 *       任何角度看都是同一块牌子、永远挂在牌桌上。</li>
 * </ul>
 * 无实体属性（客户端不需要读任何 {@code q.property}）；无碰撞、无重力、不可攻击、
 * 不随区块保存；随牌桌创建、随牌桌销毁。
 */
public class UnoClickBoxEntity extends Entity implements CustomEntity {

    public static final String IDENTIFIER = "tabletopgame:uno_clickbox";

    public static final EntityDefinition DEF = EntityDefinition.builder()
            .identifier(IDENTIFIER)
            // 与牌桌实体同理：基岩版客户端的软边阴影取自实体定义的碰撞箱，
            // 自定义实体继承不绘制阴影的 armor_stand 运行时标识符。
            .parentEntity("minecraft:armor_stand")
            .spawnEgg(false)
            .implementation(UnoClickBoxEntity.class)
            .build();

    public UnoClickBoxEntity(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    @Override
    public EntityDefinition getEntityDefinition() {
        return DEF;
    }

    @Override
    public int getNetworkId() {
        return DEF.getRuntimeId();
    }

    /** 客户端侧碰撞箱（方块）。默认 0.001 = 「等于不存在」；放大入口见 {@link #configureClickHitbox}。 */
    private float hitWidth = 0.001f;
    private float hitHeight = 0.001f;

    @Override
    public float getHeight() {
        return hitHeight;
    }

    @Override
    public float getWidth() {
        return hitWidth;
    }

    @Override
    public float getLength() {
        return hitWidth;
    }

    /**
     * 放大客户端碰撞箱（宽/深 {@code width} 格、高 {@code height} 格，从实体脚下算起）。
     * <p><b>已退役</b>（2026-10-15 用户要求把捕获框恢复原样）：牌桌不再调用它，
     * 实体保持 {@code initEntity()} 里写入的默认 0.001 箱子。方法保留以便日后回退——
     * 若重新启用，必须在 {@code spawnToAll()} <b>之前</b>调用，否则客户端先收到 0.001 的箱子，
     * 后面再改也不会重新拾取。
     */
    public void configureClickHitbox(float width, float height) {
        this.hitWidth = width;
        this.hitHeight = height;
        this.setDataProperty(new Vector3fEntityData(DATA_COLLISION_BOX, width, height, width));
    }

    /**
     * 设置/清除**悬浮文字**（世界里的固定牌子，靠 name tag 实现）。
     * <p>
     * 需求原话（2026-10-15）：「看看斗地主的模组，是那种悬浮窗显示在方块上面，
     * （位置永远固定在方块上），不是屏幕提示，我想用那种」。
     * <p>
     * 传 {@code null} / 空串 = 隐藏。文字支持 {@code §} 颜色码与 {@code \n} 换行
     * （基岩客户端把 name tag 当多行文本渲染），所以"轮到谁 / 剩余几秒"可以分两行写。
     * <p>
     * 为什么用 name tag 而不是粒子/屏幕提示：它天然是<b>世界坐标</b>的、永远挂在实体头顶、
     * 任何角度都正对镜头，改成每一秒 {@code setNameTag} 一次即可原地刷新，不需要销毁重建。
     */
    public void setFloatingText(String text) {
        boolean show = text != null && !text.isEmpty();
        if (show) {
            this.setNameTag(text);
        }
        this.setNameTagVisible(show);
        this.setNameTagAlwaysVisible(show);
    }

    @Override
    protected void initEntity() {
        super.initEntity();
        this.setImmobile(true);
        this.setDataFlag(DATA_FLAGS, DATA_FLAG_GRAVITY, false);
        this.setDataFlag(DATA_FLAGS, DATA_FLAG_HAS_COLLISION, false);
        this.setNameTagVisible(false);
        this.setNameTagAlwaysVisible(false);
        this.setCanBeSavedWithChunk(false);
        this.setDataProperty(new Vector3fEntityData(DATA_COLLISION_BOX, 0.001f, 0.001f, 0.001f));
    }

    @Override
    public boolean canCollide() {
        return false;
    }

    @Override
    public boolean canBePushed() {
        return false;
    }

    /** 不可被攻击（左键打到捕获框不该有任何伤害表现）。 */
    @Override
    public boolean attack(EntityDamageEvent source) {
        return false;
    }

    @Override
    public void kill() {
    }

    // ------------------------------------------------------------------
    // 反向引用所属牌桌（右键命中后由 UnoManager 据此转发交互）
    // ------------------------------------------------------------------

    private UnoTable table;

    public void setTable(UnoTable table) {
        this.table = table;
    }

    public UnoTable getTable() {
        return table;
    }
}
