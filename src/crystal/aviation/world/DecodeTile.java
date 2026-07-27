package crystal.aviation.world;

import arc.func.*;
import arc.math.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.world.*;
import mindustry.world.blocks.environment.Floor;
import mindustry.world.modules.*;
import static mindustry.Vars.*;

/**
 * 专用于把 .msav 解码到内存 TileEntry 的临时 Tile。
 * 不触发世界事件，也不把建筑加入 Groups，更不会去修改真实世界的相邻 tile。
 */
public class DecodeTile extends Tile {

    public DecodeTile(int x, int y, int floorID, int overlayID) {
        super(x, y);
        Block floorBlock = content.block(floorID);
        this.floor = (floorBlock instanceof Floor) ? (Floor) floorBlock : Blocks.air.asFloor();
        Block overlayBlock = content.block(overlayID);
        this.overlay = (overlayBlock instanceof Floor) ? (Floor) overlayBlock : Blocks.air.asFloor();
    }

    @Override
    public void setBlock(Block type, Team team, int rotation, Prov<Building> entityprov) {
        // 解码时只需要设置本 tile 的 block 与建筑实体，不要执行原版 setBlock
        // 里的多格建筑相邻 tile 写操作（那会访问真实 world.tiles）。
        this.block = type;
        changeBuild(team, entityprov, Mathf.mod(rotation, 4));
        if (build != null) {
            build.team(team);
        }
    }

    @Override
    protected void preChanged() {
        // 解码时不应向世界触发事件
    }

    @Override
    protected void changed() {
        // 解码时不应向世界触发事件
    }

    @Override
    protected void changeBuild(Team team, Prov<Building> entityprov, int rotation) {
        build = null;
        Block block = block();
        if (block.hasBuilding()) {
            Building n = entityprov.get().init(this, team, false, rotation);
            n.tile = this;
            n.block = block;
            if (block.hasItems)
                n.items = new ItemModule();
            if (block.hasLiquids)
                n.liquids = new LiquidModule();
            if (block.hasPower)
                n.power = new PowerModule();
            build = n;
        }
    }
}
