package crystal.aviation.blocks;

import arc.func.Floatp;
import arc.func.Prov;
import arc.graphics.Color;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Nullable;
import mindustry.graphics.Pal;
import mindustry.ui.Bar;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import crystal.aviation.Satellite;
import crystal.aviation.SatelliteManager;
import crystal.aviation.SatelliteSectorInfoManager;
import crystal.aviation.entities.SatellitePayloads;
import mindustry.gen.Building;
import mindustry.type.Category;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import mindustry.type.Liquid;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.Block;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;
import mindustry.world.meta.StatValues;

import static mindustry.Vars.*;
import crystal.world.meta.CBuildVisibility;

/**
 * 地面发射台。
 *
 * 仿照 LaunchPad，收集玩家输入的物品或液体后，发射一个升空实体。
 * 实体在生命周期结束（升空）后，将货物送达玩家直接选取的目标卫星。
 *
 * 行为：
 * - 无需选择具体物品/液体，自动接收任意种类。
 * - 物品：可接收任意种类，缓冲区内物品总和不超过 itemLaunchAmount；
 * 总和达到 itemLaunchAmount 后，将所有物品作为一批送往卫星。
 * - 液体：可接收任意种类，但同一时刻只缓冲一种液体，达到 liquidLaunchAmount 后发射。
 * - 发射时间到后，物品和液体只要有一个满足发射条件就发射。
 * - 判断卫星是否还能接收时，按每种物品的独立上限判断，而非物品总量。
 * - 物品与液体可同时接收、同时判定，不再通过模式切换选择。
 */
public class GroundLaunchPad extends Block {

    /** 单次发射物品数量。 */
    public int itemLaunchAmount = 100;
    /** 单次发射液体数量。 */
    public float liquidLaunchAmount = 200f;
    /** 发射间隔（秒）。 */
    public float launchInterval = 5f;

    public GroundLaunchPad(String name) {
        super(name);
        update = true;
        solid = true;
        destructible = true;
        configurable = true;
        hasItems = true;
        hasLiquids = true;
        itemCapacity = itemLaunchAmount;
        liquidCapacity = liquidLaunchAmount;
        requirements(Category.effect, new ItemStack[] {
                new ItemStack(mindustry.content.Items.copper, 200),
                new ItemStack(mindustry.content.Items.lead, 150),
                new ItemStack(mindustry.content.Items.silicon, 120),
                new ItemStack(mindustry.content.Items.titanium, 80)
        });

        config(Integer.class, (GroundLaunchPadBuild build, Integer value) -> build.applyIntegerConfig(value));
    }

    @Override
    public void setStats() {
        super.setStats();
        stats.add(Stat.output, itemLaunchAmount, StatUnit.items);
        stats.add(Stat.output, StatValues.number(liquidLaunchAmount, StatUnit.liquidUnits));
        stats.add(Stat.launchTime, launchInterval, StatUnit.seconds);
    }

    @Override
    public void setBars() {
        super.setBars();
        // 自己管理物品/液体 Bar，避免与原 Block 默认 Bar 重复
        barMap.remove("items");
        barMap.remove("liquid");
    }

    public class GroundLaunchPadBuild extends Building {
        /** 已弃用：旧版模式字段，保留仅用于旧存档读取兼容。新版不再区分模式，物品与液体同时收发。 */
        public int mode = 0;
        /** 目标卫星ID，-1 表示未选择 */
        public int targetSatelliteId = -1;
        /** 发射计时器（秒） */
        public float launchTimer = 0f;

        /** 已弃用：保留字段仅用于旧存档读取兼容。 */
        public @Nullable Item launchItem = null;
        /** 已弃用：保留字段仅用于旧存档读取兼容。 */
        public @Nullable Liquid launchLiquid = null;
        /** 当前缓冲的液体类型，用于 bar 显示与发射时锁定类型。 */
        public @Nullable Liquid bufferedLiquid = null;

        @Override
        public void updateTile() {
            Satellite target = findTargetSatellite();
            if (target == null) {
                return;
            }

            launchTimer -= Time.delta / 60f;
            if (launchTimer > 0f)
                return;

            // 直接使用卫星中保存的容量/计数（由卫星地图内建筑的 created/onRemoved 回调维护），
            // 不再在这里遍历当前世界 Groups.build，否则在星球区块中会把卫星建筑计数为 0 或误读。
            boolean launched = false;

            // 物品：缓冲总量达到发射量后，把所有物品一起发射
            if (items.total() >= itemLaunchAmount - 0.001f) {
                Seq<ItemStack> stacks = new Seq<>();
                for (Item item : content.items()) {
                    int amount = items.get(item);
                    if (amount > 0) {
                        stacks.add(new ItemStack(item, amount));
                    }
                }
                if (stacks.size > 0) {
                    // 按每种物品的独立上限判断，而非总量
                    boolean canLaunch = true;
                    for (ItemStack stack : stacks) {
                        int future = target.items.get(stack.item) + stack.amount;
                        if (future > target.itemStorageCapacity + 0.001f) {
                            canLaunch = false;
                            break;
                        }
                    }

                    if (canLaunch) {
                        ItemStack[] arr = stacks.toArray(ItemStack.class);
                        for (ItemStack stack : arr) {
                            items.remove(stack.item, stack.amount);
                        }
                        SatellitePayloads.launchItems(target, x, y, arr);
                        SatelliteSectorInfoManager.recordItemLaunch(state.rules.sector, target, arr);
                        Log.info("[GroundLaunchPad] launched items to sat @: @ stacks", target.id, arr.length);
                        launched = true;
                    }
                }
            }

            // 液体：找到第一个攒够发射量的液体并发射
            Liquid launchLiquid = currentLiquid();
            if (launchLiquid != null && liquids.get(launchLiquid) >= liquidLaunchAmount - 0.001f) {
                if (target.liquidCapacity > 0.001f
                        && target.getLiquid(launchLiquid) + liquidLaunchAmount <= target.liquidCapacity + 0.001f) {
                    liquids.remove(launchLiquid, liquidLaunchAmount);
                    SatellitePayloads.launchLiquid(target, x, y, launchLiquid, liquidLaunchAmount);
                    SatelliteSectorInfoManager.recordLiquidLaunch(state.rules.sector, target, launchLiquid,
                            liquidLaunchAmount);
                    Log.info("[GroundLaunchPad] launched liquid @ to sat @", launchLiquid.name, target.id);
                    launched = true;
                }
            }

            if (launched) {
                launchTimer = launchInterval;
            }

            // 液体发射后若缓冲区为空，重置当前液体类型以便后续接收新液体
            if (bufferedLiquid != null && liquids.get(bufferedLiquid) <= 0.001f) {
                bufferedLiquid = null;
            }
        }

        @Override
        public void buildConfiguration(Table table) {
            table.defaults().pad(2f);
            table.add("[accent]地面发射台[]").row();

            // 当前缓冲信息
            Table bufferInfo = new Table();
            bufferInfo.update(() -> {
                bufferInfo.clearChildren();
                int itemTypes = 0;
                for (Item item : content.items()) {
                    if (items.get(item) > 0)
                        itemTypes++;
                }
                bufferInfo.add("物品种类: ").style(Styles.outlineLabel);
                bufferInfo.add(itemTypes == 0 ? "[gray]无[]" : String.valueOf(itemTypes)).style(Styles.outlineLabel)
                        .row();
                bufferInfo.add("物品缓冲: " + items.total() + "/" + itemLaunchAmount).style(Styles.outlineLabel).row();

                Liquid liquid = currentLiquid();
                bufferInfo.add("当前液体: ").style(Styles.outlineLabel);
                bufferInfo.add(liquid == null ? "[gray]无[]" : liquid.localizedName).style(Styles.outlineLabel).row();
                String str;
                if (currentLiquid() != null) {
                    str = ("液体缓冲: " + (int) liquids.get(currentLiquid()) + "/" + (int) liquidLaunchAmount);
                } else
                    str = "当前无液体缓冲";
                bufferInfo.add(str)
                        .style(Styles.outlineLabel).row();
            });
            table.add(bufferInfo).row();

            Table targetTable = new Table();
            targetTable.defaults().pad(2f);
            targetTable.add("目标卫星: ").style(Styles.outlineLabel);
            TextButton targetBtn = targetTable.button("[gray]未选择[]", Styles.flatt, this::openSatelliteSelector)
                    .size(140f, 32f)
                    .get();
            targetBtn.update(() -> {
                Satellite s = findTargetSatellite();
                targetBtn.setText(s == null ? "[gray]未选择[]" : s.name);
            });
            table.add(targetTable).row();

            Table infoTable = new Table();
            infoTable.update(() -> {
                infoTable.clearChildren();
                Satellite s = findTargetSatellite();
                if (s == null) {
                    infoTable.add("[scarlet]未选择目标卫星[]").style(Styles.outlineLabel).row();
                } else {
                    infoTable.add("[lightgray]目标: []" + s.name).style(Styles.outlineLabel).row();
                    int unlocked = Math.max(1,
                            content.items().count(i -> state.rules != null && i.unlocked()));
                    int maxPerItem = (int) s.itemStorageCapacity;
                    infoTable.add("[lightgray]单种物品上限: []" + maxPerItem).style(Styles.outlineLabel).row();
                    infoTable.add("[lightgray]已解锁物品种类: []" + unlocked).style(Styles.outlineLabel).row();
                    infoTable.add("[lightgray]单种液体上限: []" + (int) s.liquidCapacity).style(Styles.outlineLabel).row();
                }
            });
            table.add(infoTable).row();
        }

        void rebuild(Table table) {
            table.clear();
            buildConfiguration(table);
        }

        /** 获取当前缓冲中的物品类型（唯一类型）。 */
        @Nullable
        Item currentItem() {
            for (Item item : content.items()) {
                if (items.get(item) > 0)
                    return item;
            }
            return null;
        }

        /** 获取当前缓冲中的液体类型（唯一类型）。 */
        @Nullable
        Liquid currentLiquid() {
            if (bufferedLiquid != null && liquids.get(bufferedLiquid) > 0.001f) {
                return bufferedLiquid;
            }
            for (Liquid liquid : content.liquids()) {
                if (liquids.get(liquid) > 0.001f) {
                    bufferedLiquid = liquid;
                    return liquid;
                }
            }
            bufferedLiquid = null;
            return null;
        }

        @Override
        public void display(Table table) {
            super.display(table);
            table.row();
            table.add(new Bar(() -> "发射间隔", () -> Pal.accent, () -> 1f - launchTimer / launchInterval))
                    .growX().height(18f).row();

            table.add(new Bar(
                    () -> "物品: " + (items == null ? 0 : items.total()) + "/" + itemLaunchAmount,
                    () -> Pal.items,
                    () -> items == null ? 0f : items.total() / (float) itemCapacity)).growX().height(18f).row();
            Prov<CharSequence> barName = () -> {
                Liquid liquid = currentLiquid();
                return liquid == null ? "液体: 0/" + (int) liquidLaunchAmount
                        : liquid.localizedName + ": " + (int) liquids.get(liquid) + "/"
                                + (int) liquidLaunchAmount;
            };

            Prov<Color> barColor = () -> {
                Liquid liquid = currentLiquid();
                return liquid == null ? Color.white : liquid.color;
            };

            Floatp barFrac = () -> {
                Liquid liquid = currentLiquid();
                return liquid == null ? 0f : liquids.get(liquid) / liquidCapacity;
            };

            table.add(new Bar(barName, barColor, barFrac)).growX().height(18f).row();

        }

        void openSatelliteSelector() {
            BaseDialog dialog = new BaseDialog("选择目标卫星");
            dialog.cont.defaults().pad(6f);
            if (SatelliteManager.satellites.isEmpty()) {
                dialog.cont.add("[gray]当前没有可用卫星[]").row();
            } else {
                for (Satellite s : SatelliteManager.satellites.values()) {
                    String planetName = s.planet != null ? s.planet.localizedName : "未知";
                    dialog.cont.button("[lightgray]ID:" + s.id + "[] " + s.name + " [lightgray](" + planetName + ")",
                            Styles.flatt, () -> {
                                targetSatelliteId = s.id;
                                configure(targetSatelliteId);
                                dialog.hide();
                            }).size(320f, 50f).row();
                }
            }
            dialog.addCloseButton();
            dialog.show();
        }

        /** 查找玩家直接选取的目标卫星。 */
        Satellite findTargetSatellite() {
            return targetSatelliteId < 0 ? null : SatelliteManager.get(targetSatelliteId);
        }

        /** 应用整数类型配置：仅用于设置目标卫星ID。 */
        void applyIntegerConfig(int value) {
            targetSatelliteId = value;
        }

        @Override
        public void configured(mindustry.gen.Unit builder, Object value) {
            if (value instanceof Integer) {
                applyIntegerConfig((Integer) value);
            }
        }

        @Override
        public Object config() {
            // 多配置项不通过单一 config() 同步，避免自动复制时冲突；持久化由 write/read 处理
            return null;
        }

        @Override
        public boolean acceptItem(Building source, Item item) {
            boolean ok = item != null && items.total() < itemCapacity;
            return ok;
        }

        @Override
        public void handleItem(Building source, Item item) {
            super.handleItem(source, item);
        }

        @Override
        public boolean acceptLiquid(Building source, Liquid liquid) {
            return liquid != null && liquids.get(liquid) < liquidCapacity;
        }

        @Override
        public void handleLiquid(Building source, Liquid liquid, float amount) {
            if (liquid == null || amount <= 0.001f)
                return;

            Liquid current = currentLiquid();
            if (current != null && current != liquid) {
                // 输入液体改变：清空所有旧液体缓冲，确保 bar 与注入都指向新液体
                for (Liquid l : content.liquids()) {
                    if (l != liquid) {
                        float old = liquids.get(l);
                        if (old > 0.001f) {
                            liquids.remove(l, old + 1f);
                        }
                    }
                }
            }
            bufferedLiquid = liquid;
            super.handleLiquid(source, liquid, amount);
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.i(mode);
            write.i(launchItem == null ? -1 : launchItem.id);
            write.i(launchLiquid == null ? -1 : launchLiquid.id);
            write.i(targetSatelliteId);
            write.f(launchTimer);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            mode = read.i();
            int itemId = read.i();
            launchItem = itemId < 0 ? null : content.item(itemId);
            int liquidId = read.i();
            launchLiquid = liquidId < 0 ? null : content.liquid(liquidId);
            targetSatelliteId = read.i();
            launchTimer = read.f();
        }
    }
}
