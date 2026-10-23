package com.tabletopgames.board;

import cn.nukkit.block.custom.CustomBlockDefinition;
import cn.nukkit.block.custom.CustomBlockManager;
import cn.nukkit.block.custom.container.BlockContainerFactory;
import cn.nukkit.block.custom.container.CustomBlockMeta;
import cn.nukkit.block.custom.container.data.Component;
import cn.nukkit.block.custom.container.data.Geometry;
import cn.nukkit.block.custom.container.data.Materials;
import cn.nukkit.block.custom.container.data.Permutation;
import cn.nukkit.block.custom.properties.BlockProperties;
import cn.nukkit.block.custom.properties.IntBlockProperty;
import cn.nukkit.item.ItemTool;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.math.Vector3f;
import cn.nukkit.network.protocol.types.inventory.creative.CreativeItemCategory;

/**
 * 斗地主物理牌桌方块（3x3）：中心 1 块 + 边 4 块 + 角 4 块。
 * <p>
 * 与 {@link UnoTableBlocks} <b>完全同构</b>，唯一差别是 identifier / 几何体前缀 / 贴图名，
 * 以及贴图是<b>墨绿</b>那套（模组 `textures/ddz/physical_table_block.png`）而不是 UNO 的黄褐。
 * 几何体由 {@code tools/ddz/port_table_block.py} 从模组已验证版本改名而来。
 * <p>
 * 两条已被 UNO 真机验证、必须沿用的约定：
 * <ol>
 *   <li><b>方块 geometry 的局部 +X 与世界 -X 同向</b> —— 源 geo 里的 cube x 已预先镜像，
 *       本类不做任何反镜像处理；改动 cube 坐标时保持该约定，否则木框会左右反。</li>
 *   <li>贴图是「世界对齐」的，所以 4 个 facing 挂 <b>4 张独立几何体、rotation 全 0</b>；
 *       用 [0,y,0] 旋转会连 UV 一起转，导致边块/角块采样的贴图区域错位。</li>
 * </ol>
 * facing 取值：边块 0=北 1=西 2=南 3=东；角块 0=NE 1=NW 2=SW 3=SE。
 * <p>
 * 牌桌是 1 格高的实心台子（碰撞箱整格 0..16），因此不复用 {@code BaseTabletopGamesBoardBlock}。
 */
public final class DdzTableBlocks {

    public static final String CENTER = "tabletopgame:ddz_physical_table_center";
    public static final String EDGE = "tabletopgame:ddz_physical_table_edge";
    public static final String CORNER = "tabletopgame:ddz_physical_table_corner";

    /** 顶面/侧面/底面共用的贴图名，由 terrain_texture.json 的 {@code tabletopgame_ddz_physical_table} 登记。 */
    private static final String TEXTURE = "tabletopgame_ddz_physical_table";

    /**
     * 边/角朝向。每个方块类型各自持有独立实例（与 UNO 一致），
     * 避免多个方块定义共享同一属性对象时客户端收到的 permutation 数据互相串味。
     */
    private static final IntBlockProperty CENTER_FACING = new IntBlockProperty("tabletopgame:ddz_facing", true, 3, 0);
    private static final IntBlockProperty EDGE_FACING = new IntBlockProperty("tabletopgame:ddz_facing", true, 3, 0);
    private static final IntBlockProperty CORNER_FACING = new IntBlockProperty("tabletopgame:ddz_facing", true, 3, 0);

    private DdzTableBlocks() {
    }

    // ------------------------------------------------------------------
    // 方块类
    // ------------------------------------------------------------------

    /** 牌桌方块公共行为：硬度较高、木质音效、碰撞箱 = 整格实心箱体（0..16）。 */
    public abstract static class DdzTableBlock extends CustomBlockMeta {

        protected DdzTableBlock(String identifier, BlockProperties properties, int meta) {
            super(identifier, properties, meta);
        }

        @Override
        public double getHardness() {
            return 4.0;
        }

        @Override
        public boolean isBreakable(cn.nukkit.item.Item item) {
            return true;
        }

        @Override
        public double getResistance() {
            return 20.0;
        }

        @Override
        public int getToolType() {
            return ItemTool.TYPE_AXE;
        }

        @Override
        public boolean canBeActivated() {
            return true;
        }

        /** 服务端碰撞箱与客户端一致：整格实心箱体（模型为实心牌桌）。 */
        @Override
        protected AxisAlignedBB recalculateBoundingBox() {
            return new SimpleAxisAlignedBB(this.x, this.y, this.z,
                    this.x + 1.0D, this.y + 1.0D, this.z + 1.0D);
        }
    }

    /** 中心块（几何对称，facing 形同虚设，仅用于统一走 permutation 通道）。 */
    public static class CenterBlock extends DdzTableBlock {

        private static final BlockProperties PROPERTIES = new BlockProperties(CENTER_FACING);

        public CenterBlock() {
            this(0);
        }

        public CenterBlock(int meta) {
            super(CENTER, PROPERTIES, meta);
        }

        @Override
        public String getName() {
            return "斗地主牌桌";
        }
    }

    /** 边块（棱边朝向由 facing 决定）。 */
    public static class EdgeBlock extends DdzTableBlock {

        private static final BlockProperties PROPERTIES = new BlockProperties(EDGE_FACING);

        public EdgeBlock() {
            this(0);
        }

        public EdgeBlock(int meta) {
            super(EDGE, PROPERTIES, meta);
        }

        public int getFacing() {
            return this.getIntValue(EDGE_FACING);
        }

        public void setFacing(int facing) {
            this.setIntValue(EDGE_FACING, facing & 3);
        }

        @Override
        public String getName() {
            return "斗地主牌桌（边）";
        }
    }

    /** 角块（模型棱边在北+东，即 NE 角；facing 旋转到四角）。 */
    public static class CornerBlock extends DdzTableBlock {

        private static final BlockProperties PROPERTIES = new BlockProperties(CORNER_FACING);

        public CornerBlock() {
            this(0);
        }

        public CornerBlock(int meta) {
            super(CORNER, PROPERTIES, meta);
        }

        public int getFacing() {
            return this.getIntValue(CORNER_FACING);
        }

        public void setFacing(int facing) {
            this.setIntValue(CORNER_FACING, facing & 3);
        }

        @Override
        public String getName() {
            return "斗地主牌桌（角）";
        }
    }

    // ------------------------------------------------------------------
    // 注册
    // ------------------------------------------------------------------

    /** 注册三个牌桌方块（插件 onLoad 期调用，紧跟 {@link UnoTableBlocks#register()}）。 */
    public static void register() {
        registerOne(CENTER, new String[]{"geometry.tabletopgame.ddz_physical_table_center"},
                "斗地主牌桌", CENTER_FACING, CenterBlock::new);
        registerOne(EDGE, new String[]{
                        "geometry.tabletopgame.ddz_physical_table_edge_n",
                        "geometry.tabletopgame.ddz_physical_table_edge_w",
                        "geometry.tabletopgame.ddz_physical_table_edge_s",
                        "geometry.tabletopgame.ddz_physical_table_edge_e"},
                "斗地主牌桌（边）", EDGE_FACING, EdgeBlock::new);
        registerOne(CORNER, new String[]{
                        "geometry.tabletopgame.ddz_physical_table_corner_ne",
                        "geometry.tabletopgame.ddz_physical_table_corner_nw",
                        "geometry.tabletopgame.ddz_physical_table_corner_sw",
                        "geometry.tabletopgame.ddz_physical_table_corner_se"},
                "斗地主牌桌（角）", CORNER_FACING, CornerBlock::new);
    }

    /**
     * @param geometries 按 facing 索引排列的几何体标识符；长度 1（中心对称块）时 4 个 facing 共用
     * @param facing     本方块类型自己的朝向属性（每个类型一份，不与其他类型共享）
     */
    private static void registerOne(String identifier, String[] geometries, String name,
                                    IntBlockProperty facing, BlockContainerFactory factory) {
        Materials materials = Materials.builder();
        materials.any(Materials.RenderMethod.OPAQUE, false, true, TEXTURE);
        materials.up(Materials.RenderMethod.OPAQUE, false, true, TEXTURE);

        CustomBlockDefinition.Builder builder = CustomBlockDefinition.builder(factory.create(0))
                .name(name)
                .breakTime(4.0)
                .blockTags("wood", "minecraft:is_axe_item_destructible")
                // 选箱整格宽高：牌桌是 3x3 拼接，选箱不能内缩，否则准星落缝上选不中；
                // 高度取整格保证右键桌沿下方空气感区域也能命中菜单。
                .collisionBox(new Vector3f(-8f, 0f, -8f), new Vector3f(16f, 16f, 16f))
                .selectionBox(new Vector3f(-8f, 0f, -8f), new Vector3f(16f, 16f, 16f))
                .creativeCategory(CreativeItemCategory.CONSTRUCTION)
                .registerCreativeItem(false)
                .geometry(new Geometry(geometries[0]))
                .materials(materials);

        for (int f = 0; f < 4; f++) {
            String geometry = geometries.length == 1 ? geometries[0] : geometries[f];
            Component component = Component.builder()
                    .geometry(new Geometry(geometry))
                    .materialInstances(materials)
                    // ⚠️ 恒为 0：贴图世界对齐，旋转会把 UV 一起转（详见类注释）
                    .rotation(new Vector3f(0f, 0f, 0f))
                    // 消除软边阴影：自定义方块默认 light_dampening=15
                    .lightDampening(0)
                    .build();
            builder.permutation(new Permutation(component,
                    "query.block_property('" + facing.getName() + "') == " + f));
        }

        CustomBlockManager.get().registerCustomBlock(identifier,
                new BlockProperties(facing), builder.build(), factory);
    }
}
