package com.tabletopgames.entity;

import cn.nukkit.entity.custom.CustomEntity;
import cn.nukkit.entity.custom.EntityDefinition;
import cn.nukkit.entity.data.property.EntityProperty;
import cn.nukkit.entity.data.property.IntEntityProperty;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.nbt.tag.CompoundTag;

/**
 * 围棋黑棋棋子渲染实体，对应资源包中的 {@code tabletopgame:go_stone_black}。
 * <p>
 * 与白棋实体共用同一份 361 骨骼几何体 {@code geometry.tabletopgame.go_stone}，
 * 使用贴图 {@code stone_palette}（黑子采样区）与动画
 * {@code animation.tabletopgame.go_stone.layout_black}。
 * <p>
 * 属性：25 个打包属性 + 边长 + 路数 + 准星高亮 + 最后一手标记 = 29 个，低于基岩版 32 个的上限。
 * 高亮与最后一手标记只由黑实体绘制（白实体把两个标记缩放置 0），避免重复渲染。
 */
public class GoStoneBlackEntity extends GoStoneEntity implements CustomEntity {

    public static final String IDENTIFIER = "tabletopgame:go_stone_black";

    /** 准星命中的交叉点（{@code 0} = 无高亮，否则为骨骼编号 + 1）。 */
    public static final String P_HOVER = "tabletopgame:hover";
    /** 最后一手落点（{@code 0} = 无，否则为骨骼编号 + 1）。 */
    public static final String P_LAST = "tabletopgame:last";

    public static final EntityDefinition DEF = EntityDefinition.builder()
            .identifier(IDENTIFIER)
            // 基岩版客户端的软边阴影取自实体定义里的碰撞箱，自定义实体在 Nukkit 里无法直接声明，
            // 只能继承一个不绘制阴影的原版实体运行时标识符（armor_stand 明确禁用实体阴影）。
            .parentEntity("minecraft:armor_stand")
            .spawnEgg(false)
            .implementation(GoStoneBlackEntity.class)
            .build();

    /** 注册客户端侧实体属性定义。必须在任何实体生成前调用（插件 onLoad）。 */
    public static void registerProperties() {
        registerCommon(IDENTIFIER);
        EntityProperty.register(IDENTIFIER, new IntEntityProperty(P_HOVER, 0, 361, 0));
        EntityProperty.register(IDENTIFIER, new IntEntityProperty(P_LAST, 0, 361, 0));
    }

    public GoStoneBlackEntity(FullChunk chunk, CompoundTag nbt) {
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

    public void setHover(int index) {
        if (this.setIntEntityProperty(P_HOVER, index)) {
            this.sync();
        }
    }

    public void setLast(int index) {
        if (this.setIntEntityProperty(P_LAST, index)) {
            this.sync();
        }
    }
}
