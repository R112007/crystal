package crystal.aviation.blocks;

import arc.util.io.Reads;
import crystal.aviation.Satellite;
import crystal.aviation.SatelliteManager;
import mindustry.type.Item;
import mindustry.world.blocks.storage.CoreBlock;
import mindustry.world.modules.ItemModule;

import static mindustry.Vars.*;

/**
 * 卫星地图专用核心。
 * 重写 onProximityUpdate，使核心容量直接使用当前卫星的 itemStorageCapacity，
 * 而不是依赖 Block.itemCapacity + 相邻储物箱。
 */
public class SatelliteCoreBlock extends CoreBlock {

    public SatelliteCoreBlock(String name) {
        super(name);
        itemCapacity = (int) Satellite.baseItemStorageCapacity;
    }

    public class SatelliteCoreBuild extends CoreBuild {

        @Override
        public void created() {
            super.created();
            Satellite sat = currentSatellite();
            if (sat != null && items != sat.items && !world.isGenerating() && !SatelliteManager.isEnteringSatellite()) {
                items = sat.items;
            }
        }

        @Override
        public void read(Reads read, byte revision) {
            Satellite sat = currentSatellite();
            super.read(read, revision);
            if (sat != null && sat.items != null && items != sat.items) {
                items = sat.items;
            }
        }

        @Override
        public void onProximityUpdate() {
            Satellite sat = currentSatellite();
            boolean inSatellite = sat != null;

            // 卫星地图中：核心的物品必须始终与卫星 items 共用同一对象。
            // 在调用 super 前强制绑定，防止原版逻辑操作到错误的 ItemModule。
            if (inSatellite && items != sat.items) {
                items = sat.items;
            }

            // 原版 CoreBlock.onProximityUpdate 会按 Block.itemCapacity 重新计算 storageCapacity
            // 并把超出该容量的物品裁剪掉；卫星地图中容量由扩容仓决定，必须保护卫星 items 不被覆盖。
            ItemModule itemsBackup = inSatellite && sat.items != null ? sat.items.copy() : null;

            // 执行原版逻辑：注册核心、合并相邻储物箱等
            super.onProximityUpdate();

            // super 可能把 items 换成新的对象，再次强制绑定回卫星 items
            if (inSatellite && items != sat.items) {
                items = sat.items;
            }

            // 恢复 super 可能裁剪掉的物品数量
            if (itemsBackup != null && sat.items != null && sat.items.total() != itemsBackup.total()) {
                sat.items.set(itemsBackup);
            }

            // 用卫星总容量覆盖计算结果
            int capacity = inSatellite ? (int) sat.itemStorageCapacity : (int) Satellite.baseItemStorageCapacity;
            storageCapacity = Math.max(1, capacity);

            // 同步同队其他卫星核心
            for (CoreBuild other : state.teams.cores(team)) {
                if (other instanceof SatelliteCoreBuild) {
                    other.storageCapacity = storageCapacity;
                    if (inSatellite && other.items != sat.items) {
                        other.items = sat.items;
                    }
                }
            }

            // 只有在非卫星地图、或卫星容量异常小时才按 capacity 裁剪单种物品。
            // 卫星地图中 sat.items 才是权威来源，不能从核心存档恢复旧数据。
            if (!inSatellite && !world.isGenerating()) {
                for (Item item : content.items()) {
                    items.set(item, Math.min(items.get(item), storageCapacity));
                }
            }
        }

        private Satellite currentSatellite() {
            if (SatelliteManager.currentSatelliteId >= 0) {
                Satellite sat = SatelliteManager.get(SatelliteManager.currentSatelliteId);
                if (sat != null)
                    return sat;
            }
            String tag = state.map != null ? state.map.tags.get("crystal-aviation-satellite") : null;
            if (tag != null && !tag.isEmpty()) {
                try {
                    return SatelliteManager.get(Integer.parseInt(tag));
                } catch (NumberFormatException ignored) {
                }
            }
            return null;
        }
    }
}
