package com.tabletopgames.entity;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.data.Vector3fEntityData;
import cn.nukkit.entity.data.property.EntityProperty;
import cn.nukkit.entity.data.property.IntEntityProperty;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.nbt.tag.CompoundTag;
import com.tabletopgames.game.TabletopGamesBoard;

/**
 * 五子棋棋子渲染实体的公共基类。
 * <p>
 * 采用「一个实体绘制整盘」：棋盘 225 个落点的棋子状态通过「按行三进制编码」同步给客户端。
 * 每行 15 格，每格取值 0=空 / 1=黑 / 2=白，整行编码为 {@code Σ cell[c]*3^c}，最大
 * {@code 3^15-1 = 14348906 < 2^24}，避免客户端浮点精度问题。15 个行属性 + 尺寸 = 16 个属性，
 * 远低于基岩版「每个实体类型最多 32 个 Entity Property」的上限。
 * <p>
 * 黑白两方拆成两个实体类型（{@link TabletopGamesStoneBlackEntity} / {@link TabletopGamesStoneWhiteEntity}），
 * 二者共用同一份 225 骨骼几何体 {@code geometry.tabletopgame.stone}，仅靠贴图与动画区分棋色：
 * 黑实体的格子骨骼在「该格为黑」时缩放为 1，白实体则在「该格为白」时缩放为 1。
 * 这样资源包几何体只需一份 225 骨骼（而非每格黑白各一根的 450 骨骼），体积减半。
 * <p>
 * 该实体只负责渲染：无碰撞、无阴影、不可攻击、不可被 /kill 清除，
 * 收回棋盘的唯一途径是破坏棋盘方块（由 {@link TabletopGamesBoard#remove()} 直接 close）。
 */
public abstract class TabletopGamesStoneEntity extends Entity {

    /** 棋盘边长（网格始终为 15x15）。 */
    public static final int SIZE = 15;

    /** 单行三进制编码的最大值：{@code 3^15-1}。 */
    public static final int MAX_ROW_VALUE = 14348906;

    /** 棋盘边长属性（2 = 标准，3 = 中号，4 = 大号）。 */
    public static final String P_SIZE = "tabletopgame:size";

    /** 行编码属性名：{@code tabletopgame:row_R}（R = 0..14）。 */
    public static String rowName(int row) {
        return "tabletopgame:row_" + row;
    }

    protected TabletopGamesStoneEntity(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    /**
     * 注册共用的行编码属性与边长属性。必须在任何实体生成前调用（插件 onLoad）。
     * 黑白两个实体类型各自调用一次，属性按 identifier 分组注册。
     */
    protected static void registerRowProperties(String identifier) {
        for (int r = 0; r < SIZE; r++) {
            EntityProperty.register(identifier,
                    new IntEntityProperty(rowName(r), 0, MAX_ROW_VALUE, 0));
        }
        EntityProperty.register(identifier, new IntEntityProperty(P_SIZE, 0, 4, 2));
    }

    /**
     * 命中箱设为极小值，避免遮挡棋盘方块点击（右键菜单、左键破坏都会穿过实体命中方块）。
     * 棋子模型的渲染由资源包几何体按实体坐标绘制，与命中箱大小无关。
     */
    @Override
    public float getHeight() {
        return 0.001f;
    }

    @Override
    public float getWidth() {
        return 0.001f;
    }

    @Override
    public float getLength() {
        return 0.001f;
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
        // 再同步一个极小碰撞箱，双保险：即便某些版本按 metadata 绘制阴影/命中箱也能消掉
        this.setDataProperty(new Vector3fEntityData(DATA_COLLISION_BOX, 0.001f, 0.001f, 0.001f));
        this.setIntEntityProperty(P_SIZE, 2);
    }

    @Override
    public boolean canCollide() {
        return false;
    }

    @Override
    public boolean canBePushed() {
        return false;
    }

    /** 棋子仅用于渲染，不参与破坏：攻击无效，收回只能靠破坏棋盘方块。 */
    @Override
    public boolean attack(EntityDamageEvent source) {
        return false;
    }

    /** 禁止一切击杀途径（如 {@code /kill @e}）；正常收回走 {@code close()}，不经过此方法。 */
    @Override
    public void kill() {
    }

    private TabletopGamesBoard board;

    public void setBoard(TabletopGamesBoard board) {
        this.board = board;
    }

    public TabletopGamesBoard getBoard() {
        return board;
    }

    /** 批量提交标记：置位期间所有属性变更只改本地值，不发包。 */
    private boolean batching;

    /**
     * 开始批量提交属性。整盘刷新会同时改动 15 个行属性，若逐条同步会连发十几个元数据包，
     * 用 beginBatch/endBatch 包住后整批只发一个。
     */
    public void beginBatch() {
        this.batching = true;
    }

    /** 结束批量提交并一次性同步全部变更。 */
    public void endBatch() {
        this.batching = false;
        this.syncProperties();
    }

    /**
     * 写入某一行的三进制编码（{@code value = Σ cell[c]*3^c}，cell 取值 0/1/2）。
     */
    public void setRow(int row, int value) {
        if (this.setIntEntityProperty(rowName(row), value)) {
            this.sync();
        }
    }

    /** 设置棋盘边长（2 = 标准，3 = 中号，4 = 大号），客户端据此缩放整个网格。 */
    public void setBoardSize(int size) {
        int value = size >= 4 ? 4 : size == 3 ? 3 : 2;
        if (this.setIntEntityProperty(P_SIZE, value)) {
            this.sync();
        }
    }

    /** 单条属性变更后的同步；处于批量模式时跳过，由 endBatch 统一发包。 */
    protected void sync() {
        if (this.batching) {
            return;
        }
        this.syncProperties();
    }

    public void syncProperties() {
        if (this.getViewers().isEmpty()) {
            return;
        }
        this.sendData(this.getViewers().values().toArray(new Player[0]));
    }
}
