package crystal.aviation.blocks;

import arc.Core;
import arc.scene.ui.layout.Table;
import arc.util.io.Reads;
import arc.util.io.Writes;
import crystal.aviation.Satellite;
import crystal.aviation.SatelliteManager;
import mindustry.content.Blocks;
import mindustry.gen.Building;
import mindustry.type.Category;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import mindustry.ui.Styles;
import mindustry.world.Block;
import mindustry.world.meta.BuildVisibility;

import static mindustry.Vars.*;

/**
 * 卫星升级中心。
 * 放置在卫星地图上，向建筑注入指定物品后升级当前卫星（tier + 1、外观改变），
 * 随后该建筑自毁。
 */
public class SatelliteUpgradeCenter extends Block {

    /** 单次升级所需的物品 */
    public ItemStack[] upgradeCost = new ItemStack[]{
            new ItemStack(mindustry.content.Items.silicon, 200),
            new ItemStack(mindustry.content.Items.titanium, 150),
            new ItemStack(mindustry.content.Items.thorium, 80)
    };

    public SatelliteUpgradeCenter(String name){
        super(name);
        update = true;
        solid = true;
        destructible = true;
        configurable = true;
        hasItems = true;
        requirements(Category.effect, BuildVisibility.shown, new ItemStack[]{
                new ItemStack(mindustry.content.Items.silicon, 100),
                new ItemStack(mindustry.content.Items.titanium, 80),
                new ItemStack(mindustry.content.Items.copper, 120)
        });
    }

    @Override
    public void init(){
        // 根据升级消耗设置足够库存
        int max = 0;
        for(ItemStack stack : upgradeCost){
            max = Math.max(max, stack.amount);
        }
        itemCapacity = Math.max(itemCapacity, max * 2 + 10);
        super.init();
    }

    public class SatelliteUpgradeCenterBuild extends Building {
        /** 绑定的卫星ID */
        public int satelliteId = -1;
        /** 是否已完成升级并等待自毁 */
        public boolean upgraded = false;

        @Override
        public void created(){
            super.created();
            if(SatelliteManager.currentSatelliteId >= 0 && satelliteId < 0){
                satelliteId = SatelliteManager.currentSatelliteId;
            }
        }

        @Override
        public void updateTile(){
            if(upgraded || satelliteId < 0) return;
            if(canUpgrade()){
                Satellite s = SatelliteManager.get(satelliteId);
                if(s != null){
                    for(ItemStack stack : upgradeCost){
                        items.remove(stack.item, stack.amount);
                    }
                    s.upgrade();
                    SatelliteManager.save();
                    ui.showInfoFade("卫星 \"" + s.name + "\" 已升级至等级 " + s.tier);
                }
                upgraded = true;
                // 下一逻辑帧自毁，避免在 updateTile 中直接 kill 造成竞态
                Core.app.post(() -> {
                    if(!dead && tile != null){
                        tile.setBlock(Blocks.air);
                    }
                });
            }
        }

        boolean canUpgrade(){
            if(satelliteId < 0) return false;
            for(ItemStack stack : upgradeCost){
                if(items.get(stack.item) < stack.amount) return false;
            }
            return true;
        }

        @Override
        public boolean acceptItem(Building source, Item item){
            if(upgraded || item == null) return false;
            for(ItemStack stack : upgradeCost){
                if(stack.item == item){
                    return items.get(item) < stack.amount;
                }
            }
            return false;
        }

        @Override
        public void handleItem(Building source, Item item){
            items.add(item, 1);
        }

        @Override
        public int getMaximumAccepted(Item item){
            for(ItemStack stack : upgradeCost){
                if(stack.item == item){
                    return stack.amount;
                }
            }
            return 0;
        }

        @Override
        public void buildConfiguration(Table table){
            Satellite s = SatelliteManager.get(satelliteId);
            table.defaults().pad(4f);

            if(s != null){
                table.add("当前等级: " + s.tier).style(Styles.outlineLabel).row();
            }

            table.add("升级所需物品:").style(Styles.outlineLabel).row();
            for(ItemStack stack : upgradeCost){
                int have = items.get(stack.item);
                String color = have >= stack.amount ? "[lightgray]" : "[scarlet]";
                table.add(color + stack.item.localizedName + ": " + have + "/" + stack.amount)
                        .style(Styles.outlineLabel).row();
            }

            table.add("[gray]物品注满后自动升级并移除建筑").style(Styles.outlineLabel).row();
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.i(satelliteId);
            write.bool(upgraded);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            satelliteId = read.i();
            upgraded = read.bool();
        }
    }
}
