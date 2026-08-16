package crystal.aviation.entities;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Mathf;
import arc.util.Nullable;
import arc.util.Time;
import crystal.aviation.Satellite;
import crystal.aviation.SatelliteManager;
import crystal.gen.SatellitePayload;
import crystal.gen.SatellitePayloadc;
import ent.anno.Annotations.EntityComponent;
import ent.anno.Annotations.EntityDef;
import ent.anno.Annotations.Import;
import mindustry.content.Fx;
import mindustry.gen.Drawc;
import mindustry.gen.Timedc;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.Item;
import mindustry.type.ItemSeq;
import mindustry.type.ItemStack;
import mindustry.type.Liquid;

/**
 * 向卫星运送货物的发射载荷实体。
 *
 * 从地面发射台升起，到达生命周期后移除，并将携带的物品与液体送达
 * 绑定到当前区块的卫星。
 */
public class SatellitePayloads {

    @EntityDef(value = { SatellitePayloadc.class })
    @EntityComponent
    public static abstract class SatellitePayloadComp implements Drawc, Timedc, SatellitePayloadc {
        @Import
        public float x, y;
        @Import
        public float lifetime;
        @Import
        public float time;
        @Import
        public int id;

        /** 携带的物品 */
        public ItemStack[] itemPayload = new ItemStack[0];
        /** 携带液体的ID（-1 表示无液体，避免 null Liquid 导致实体序列化保存失败） */
        public int liquidPayloadId = -1;
        /** 携带液体数量 */
        public float liquidAmount = 0f;

        /** 获取当前携带的液体（可能为 null）。 */
        public @Nullable Liquid liquidPayload() {
            return liquidPayloadId < 0 ? null : mindustry.Vars.content.liquid(liquidPayloadId);
        }

        /** 设置当前携带的液体。 */
        public void setLiquidPayload(@Nullable Liquid liquid) {
            liquidPayloadId = liquid == null ? -1 : liquid.id;
        }

        /** 目标区块ID */
        public int targetSectorId = -1;
        /** 目标星球名称 */
        public String planetName = "";
        /** 直接指定的目标卫星ID（优先级高于区块绑定） */
        public int targetSatelliteId = -1;

        /** 上升速度（世界单位/秒） */
        public float riseSpeed = 8f;

        /** 是否已经执行过交付，防止多次 remove 导致重复发货。 */
        public boolean delivered = false;

        @Override
        public void update() {
            time += Time.delta;

            // 到达生命周期终点：移除实体，交付逻辑在 remove() 中统一处理
            if (time >= lifetime) {
                remove();
                return;
            }

            // 向上升空
            y += riseSpeed * Time.delta;

            // 随高度增加变小，模拟远去
            float progress = Mathf.clamp(time / lifetime, 0f, 1f);

            // 偶尔产生尾焰烟雾/火星
            if (Mathf.chanceDelta(0.25f)) {
                Fx.rocketSmokeLarge.at(x + Mathf.range(3f), y - 8f + Mathf.range(2f));
            }
            if (Mathf.chanceDelta(0.15f)) {
                Fx.fire.at(x + Mathf.range(2f), y - 10f + Mathf.range(2f));
            }

            // 接近终点时产生爆发火花
            if (progress > 0.85f && Mathf.chanceDelta(0.4f)) {
                Fx.launch.at(x + Mathf.range(4f), y + Mathf.range(4f));
            }
        }

        /** 将货物送达绑定到目标区块的卫星。 */
        public void deliver() {
            if (targetSatelliteId >= 0) {
                Satellite s = SatelliteManager.get(targetSatelliteId);
                deliverToSatellite(s);
                return;
            }

            if (targetSectorId < 0 || planetName == null || planetName.isEmpty()) {
                return;
            }

            mindustry.type.Planet planet = mindustry.Vars.content.planet(planetName);
            if (planet == null) {
                return;
            }

            for (Satellite s : SatelliteManager.satellites.values()) {
                if (s.planet != planet)
                    continue;
                if (!s.boundToSector || s.targetSectorId != targetSectorId)
                    continue;

                deliverToSatellite(s);
                return;
            }
        }

        /** 把当前载荷的物品/液体直接交付到指定卫星，按容量自动截断（参考 Sector.addItems）。 */
        public boolean deliverToSatellite(Satellite s) {
            if (s == null) {
                return false;
            }

            // 物品：按每种物品独立上限自动截断，超出部分丢弃
            if (itemPayload != null) {
                ItemSeq seq = new ItemSeq();
                for (ItemStack stack : itemPayload) {
                    if (stack == null || stack.item == null || stack.amount <= 0)
                        continue;
                    seq.add(stack.item, stack.amount);
                }
                s.addItems(seq);
            }

            // 液体：按每种液体独立上限自动截断，超出部分丢弃
            Liquid liquid = liquidPayload();
            if (liquid != null && liquidAmount > 0f) {
                s.addLiquid(liquid, liquidAmount);
            }

            SatelliteManager.save();
            s.refreshCoreCapacity();
            return true;
        }

        @Override
        public void remove() {
            if (!delivered) {
                delivered = true;
                deliver();
            }
            // 实体移除时播放到达/销毁特效
            Fx.launchPod.at(x, y);
        }

        @Override
        public void draw() {
            float progress = Mathf.clamp(time / lifetime, 0f, 1f);
            float scale = 1f - progress * 0.5f;
            float alpha = 1f - progress * 0.7f;

            Draw.z(Layer.flyingUnit + 2f);

            float w = 12f * scale;
            float h = 18f * scale;

            // 绘制上升的发射舱（箭头向上）
            Draw.color(Pal.accent);
            Draw.alpha(alpha);
            Draw.rect("launch-pod", x, y, w, h, 90f);
            Draw.color();

            // 绘制引擎尾焰
            Draw.color(Pal.lightOrange, Pal.accent, Mathf.absin(4f, 1f));
            Draw.alpha(alpha);

            float flameW = 10f * scale;
            float flameH = 14f * scale;
            float flameBaseY = y - h * 0.45f;

            // 主火焰三角形
            Fill.tri(
                    x, flameBaseY - flameH,
                    x - flameW / 2f, flameBaseY,
                    x + flameW / 2f, flameBaseY);

            // 两侧小尾焰
            float sideLen = 5f * scale;
            float sideBaseY = flameBaseY + 2f * scale;
            Angles.randLenVectors(id, 3, sideLen, (ex, ey) -> {
                Lines.stroke(1.5f * scale);
                Lines.line(x + ex * 0.5f, sideBaseY, x + ex, sideBaseY - flameH * 0.6f);
            });

            // 偶尔绘制闪烁的亮点，模拟 LaunchPayloadComp 的尾焰闪烁
            if (Mathf.chance(0.3f)) {
                Draw.color(Color.white);
                Draw.alpha(alpha * 0.7f);
                Fill.circle(x, flameBaseY - flameH * 0.5f, 2f * scale);
            }

            Draw.color();
            Draw.z(0f);
        }
    }

    /** 创建并发射一个直接送往指定卫星的载荷（物品）。 */
    public static void launchItems(Satellite satellite, float x, float y, ItemStack[] items) {
        if (satellite == null || items == null || items.length == 0)
            return;

        SatellitePayload payload = SatellitePayload.create();
        payload.set(x, y);
        payload.lifetime = 120f;
        payload.itemPayload = items;
        payload.targetSatelliteId = satellite.id;

        // 立即交付，避免依赖实体生命周期；失败则直接销毁
        boolean ok = payload.deliverToSatellite(satellite);
        if (ok) {
            payload.delivered = true;
            payload.add();
            Fx.launch.at(x, y);
        } else {
            Fx.launchPod.at(x, y);
        }
    }

    /** 创建并发射一个直接送往指定卫星的载荷（液体）。 */
    public static void launchLiquid(Satellite satellite, float x, float y, Liquid liquid, float amount) {
        if (satellite == null || liquid == null || amount <= 0f)
            return;

        SatellitePayload payload = SatellitePayload.create();
        payload.set(x, y);
        payload.lifetime = 120f;
        payload.setLiquidPayload(liquid);
        payload.liquidAmount = amount;
        payload.targetSatelliteId = satellite.id;

        // 立即交付，避免依赖实体生命周期；失败则直接销毁
        boolean ok = payload.deliverToSatellite(satellite);
        if (ok) {
            payload.delivered = true;
            payload.add();
            Fx.launch.at(x, y);
        } else {
            Fx.launchPod.at(x, y);
        }
    }
}
