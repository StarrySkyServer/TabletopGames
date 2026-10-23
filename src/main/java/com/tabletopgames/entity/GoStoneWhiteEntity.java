package com.tabletopgames.entity;

import cn.nukkit.entity.custom.CustomEntity;
import cn.nukkit.entity.custom.EntityDefinition;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.nbt.tag.CompoundTag;

/**
 * 围棋白棋棋子渲染实体，对应资源包中的 {@code tabletopgame:go_stone_white}。
 * <p>
 * 与黑棋实体共用同一份 361 骨骼几何体 {@code geometry.tabletopgame.go_stone}，
 * 使用贴图 {@code stone_palette_white} 与动画 {@code animation.tabletopgame.go_stone.layout_white}。
 * <p>
 * 属性：25 个打包属性 + 边长 + 路数 = 27 个，低于基岩版 32 个的上限。
 * 该动画把 {@code hover_marker} / {@code last_move_marker} 缩放置 0——两个标记由黑实体绘制，
 * 共用几何体时必须显式隐藏，否则会在实体原点重复渲染。
 */
public class GoStoneWhiteEntity extends GoStoneEntity implements CustomEntity {

    public static final String IDENTIFIER = "tabletopgame:go_stone_white";

    public static final EntityDefinition DEF = EntityDefinition.builder()
            .identifier(IDENTIFIER)
            .parentEntity("minecraft:armor_stand")
            .spawnEgg(false)
            .implementation(GoStoneWhiteEntity.class)
            .build();

    /** 注册客户端侧实体属性定义。必须在任何实体生成前调用（插件 onLoad）。 */
    public static void registerProperties() {
        registerCommon(IDENTIFIER);
    }

    public GoStoneWhiteEntity(FullChunk chunk, CompoundTag nbt) {
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
}
