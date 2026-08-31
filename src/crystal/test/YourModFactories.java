package crystal.test;

import arc.graphics.*;
import crystal.world.blocks.production.CombinedCrafter;
import mindustry.content.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.meta.*;

import static mindustry.type.ItemStack.*;

/**
 * 组合工厂测试实例 —— 中文命名
 * 
 * 注意：构造方法中的字符串是内部ID，也会作为贴图文件名。
 * 如需贴图，请在 sprites/blocks/ 下放置对应名称的 png 文件。
 */
public class YourModFactories {

    // ==================== 测试组1：基础冶炼链 ====================
    public static Block 粗炼炉;
    public static Block 精炼炉;

    // ==================== 测试组2：化工链 ====================
    public static Block 反应釜;
    public static Block 成型机;

    // ==================== 测试组3：电力相关 ====================
    public static Block 燃煤锅炉;
    public static Block 蒸汽轮机;

    // ==================== 测试组4：液体处理链 ====================
    public static Block 溶解槽;
    public static Block 结晶塔;

    // ==================== 测试组5：多级链（4级） ====================
    public static Block 粉碎机;
    public static Block 筛选机;
    public static Block 熔炼炉;
    public static Block 高压合成台;

    // ==================== 测试组6：同类型组合 ====================
    public static Block 电解槽;

    public static void load() {

        // ========== 测试组1：基础冶炼链 ==========

        粗炼炉 = new CombinedCrafter("粗炼炉") {
            {
                requirements(Category.crafting, with(Items.copper, 30, Items.lead, 20));
                localizedName = "粗炼炉";
                description = "将矿石初步冶炼为铁锭。可与精炼炉组合，自动内部流转。";
                size = 2;
                craftTime = 60f;
                hasItems = true;
                hasPower = true;

                consumeItems(with(Items.coal, 1, Items.sand, 2));
                consumePower(1f);
                outputItems = new ItemStack[] { new ItemStack(Items.graphite, 1) };

                allowCrossTypeCombo = true;
                itemCapacityMultiplier = 1f;
                flowSmoothing = 0.92f;
                safetyBufferSeconds = 3f;

                craftEffect = Fx.pulverizeMedium;
                updateEffect = Fx.plasticburn;
                drawer = new mindustry.world.draw.DrawMulti(
                        new mindustry.world.draw.DrawDefault(),
                        new mindustry.world.draw.DrawFlame(Color.orange));
            }
        };

        精炼炉 = new CombinedCrafter("精炼炉") {
            {
                requirements(Category.crafting, with(Items.copper, 50, Items.lead, 40, Items.graphite, 20));
                localizedName = "精炼炉";
                description = "将铁锭精炼为钢锭。与粗炼炉组合时，铁锭自动内部流转。";
                size = 2;
                craftTime = 90f;
                hasItems = true;
                hasPower = true;

                consumeItems(with(Items.graphite, 2));
                consumePower(2f);
                outputItems = new ItemStack[] { new ItemStack(Items.metaglass, 1) };

                allowCrossTypeCombo = true;
                itemCapacityMultiplier = 1f;
                flowSmoothing = 0.92f;
                safetyBufferSeconds = 3f;

                craftEffect = Fx.smeltsmoke;
                updateEffect = Fx.plasticburn;
                drawer = new mindustry.world.draw.DrawMulti(
                        new mindustry.world.draw.DrawDefault(),
                        new mindustry.world.draw.DrawFlame(Color.valueOf("87ceeb")));
            }
        };

        // ========== 测试组2：化工链 ==========

        反应釜 = new CombinedCrafter("反应釜") {
            {
                requirements(Category.crafting, with(Items.copper, 40, Items.lead, 30, Items.metaglass, 10));
                localizedName = "反应釜";
                description = "石油与水反应生成塑料原料。可与成型机组合。";
                size = 2;
                craftTime = 80f;
                hasItems = true;
                hasLiquids = true;
                hasPower = true;

                consumeItems(with(Items.coal, 2));
                consumeLiquid(Liquids.water, 0.15f);
                consumePower(1.5f);
                outputItems = new ItemStack[] { new ItemStack(Items.titanium, 1) };

                allowCrossTypeCombo = true;
                itemCapacityMultiplier = 1f;
                liquidCapacityMultiplier = 1f;
                flowSmoothing = 0.92f;
                safetyBufferSeconds = 3f;

                craftEffect = Fx.smeltsmoke;
                drawer = new mindustry.world.draw.DrawMulti(
                        new mindustry.world.draw.DrawDefault(),
                        new mindustry.world.draw.DrawLiquidRegion(Liquids.water),
                        new mindustry.world.draw.DrawBubbles(Color.valueOf("4a90d9")));
            }
        };

        成型机 = new CombinedCrafter("成型机") {
            {
                requirements(Category.crafting, with(Items.copper, 60, Items.lead, 50, Items.silicon, 20));
                localizedName = "成型机";
                description = "将塑料原料成型为高级塑料。与反应釜组合时，塑料原料自动内部流转。";
                size = 2;
                craftTime = 70f;
                hasItems = true;
                hasPower = true;

                consumeItems(with(Items.titanium, 2));
                consumePower(2.5f);
                outputItems = new ItemStack[] { new ItemStack(Items.silicon, 1) };

                allowCrossTypeCombo = true;
                itemCapacityMultiplier = 1f;
                flowSmoothing = 0.92f;
                safetyBufferSeconds = 3f;

                craftEffect = Fx.smeltsmoke;
                drawer = new mindustry.world.draw.DrawDefault();
            }
        };

        // ========== 测试组3：电力相关 ==========

        燃煤锅炉 = new CombinedCrafter("燃煤锅炉") {
            {
                requirements(Category.crafting, with(Items.copper, 20, Items.lead, 30));
                localizedName = "燃煤锅炉";
                description = "燃烧煤炭产生热能。可与蒸汽轮机组合。";
                size = 2;
                craftTime = 45f;
                hasItems = true;
                hasPower = true;

                consumeItems(with(Items.coal, 2));
                consumePower(0.5f);
                outputItems = new ItemStack[] { new ItemStack(Items.graphite, 1) };

                allowCrossTypeCombo = true;
                itemCapacityMultiplier = 1f;
                flowSmoothing = 0.92f;
                safetyBufferSeconds = 3f;

                craftEffect = Fx.burning;
                drawer = new mindustry.world.draw.DrawMulti(
                        new mindustry.world.draw.DrawDefault(),
                        new mindustry.world.draw.DrawFlame(Color.red));
            }
        };

        蒸汽轮机 = new CombinedCrafter("蒸汽轮机") {
            {
                requirements(Category.crafting, with(Items.copper, 50, Items.lead, 40, Items.graphite, 30));
                localizedName = "蒸汽轮机";
                description = "利用热能发电。与燃煤锅炉组合时，热能自动内部流转。";
                size = 2;
                craftTime = 60f;
                hasItems = true;
                hasPower = true;

                consumeItems(with(Items.graphite, 1));
                consumePower(0.5f);
                outputItems = new ItemStack[] { new ItemStack(Items.surgeAlloy, 1) };

                allowCrossTypeCombo = true;
                itemCapacityMultiplier = 1f;
                flowSmoothing = 0.92f;
                safetyBufferSeconds = 3f;

                craftEffect = Fx.shockwave;
                drawer = new mindustry.world.draw.DrawDefault();
            }
        };

        // ========== 测试组4：液体处理链 ==========

        溶解槽 = new CombinedCrafter("溶解槽") {
            {
                requirements(Category.crafting, with(Items.copper, 30, Items.metaglass, 20));
                localizedName = "溶解槽";
                description = "将矿物溶解为浓缩液。可与结晶塔组合。";
                size = 2;
                craftTime = 75f;
                hasItems = true;
                hasLiquids = true;
                hasPower = true;

                consumeItems(with(Items.sand, 3));
                consumeLiquid(Liquids.water, 0.2f);
                consumePower(1f);
                outputItems = new ItemStack[] { new ItemStack(Items.titanium, 1) };

                allowCrossTypeCombo = true;
                itemCapacityMultiplier = 1f;
                liquidCapacityMultiplier = 1f;
                flowSmoothing = 0.92f;
                safetyBufferSeconds = 3f;

                craftEffect = Fx.bubble;
                drawer = new mindustry.world.draw.DrawMulti(
                        new mindustry.world.draw.DrawDefault(),
                        new mindustry.world.draw.DrawLiquidRegion(Liquids.water),
                        new mindustry.world.draw.DrawBubbles(Color.valueOf("00ffff")));
            }
        };

        结晶塔 = new CombinedCrafter("结晶塔") {
            {
                requirements(Category.crafting, with(Items.copper, 60, Items.lead, 40, Items.silicon, 30));
                localizedName = "结晶塔";
                description = "将浓缩液结晶为能量晶体。与溶解槽组合时，浓缩液自动内部流转。";
                size = 2;
                craftTime = 100f;
                hasItems = true;
                hasPower = true;

                consumeItems(with(Items.titanium, 2));
                consumePower(3f);
                outputItems = new ItemStack[] { new ItemStack(Items.phaseFabric, 1) };

                allowCrossTypeCombo = true;
                itemCapacityMultiplier = 1f;
                flowSmoothing = 0.92f;
                safetyBufferSeconds = 3f;

                craftEffect = Fx.lancerLaserCharge;
                drawer = new mindustry.world.draw.DrawDefault();
            }
        };

        // ========== 测试组5：多级链（4级） ==========

        粉碎机 = new CombinedCrafter("粉碎机") {
            {
                requirements(Category.crafting, with(Items.copper, 20, Items.lead, 15));
                localizedName = "粉碎机";
                description = "粉碎原始矿石为粗矿粉。多级链第一环。";
                size = 2;
                craftTime = 40f;
                hasItems = true;
                hasPower = true;

                consumeItems(with(Items.sand, 2));
                consumePower(0.5f);
                outputItems = new ItemStack[] { new ItemStack(Items.coal, 1) };

                allowCrossTypeCombo = true;
                itemCapacityMultiplier = 1f;
                flowSmoothing = 0.92f;
                safetyBufferSeconds = 3f;

                craftEffect = Fx.pulverize;
                drawer = new mindustry.world.draw.DrawDefault();
            }
        };

        筛选机 = new CombinedCrafter("筛选机") {
            {
                requirements(Category.crafting, with(Items.copper, 35, Items.lead, 25, Items.graphite, 10));
                localizedName = "筛选机";
                description = "筛选粗矿粉为精矿粉。多级链第二环。";
                size = 2;
                craftTime = 55f;
                hasItems = true;
                hasPower = true;

                consumeItems(with(Items.coal, 2));
                consumePower(1f);
                outputItems = new ItemStack[] { new ItemStack(Items.graphite, 1) };

                allowCrossTypeCombo = true;
                itemCapacityMultiplier = 1f;
                flowSmoothing = 0.92f;
                safetyBufferSeconds = 3f;

                craftEffect = Fx.pulverizeSmall;
                drawer = new mindustry.world.draw.DrawDefault();
            }
        };

        熔炼炉 = new CombinedCrafter("熔炼炉") {
            {
                requirements(Category.crafting, with(Items.copper, 50, Items.lead, 40, Items.graphite, 30));
                localizedName = "熔炼炉";
                description = "熔炼精矿粉为合金锭。多级链第三环。";
                size = 2;
                craftTime = 70f;
                hasItems = true;
                hasLiquids = true;
                hasPower = true;

                consumeItems(with(Items.graphite, 2));
                consumeLiquid(Liquids.water, 0.1f);
                consumePower(2f);
                outputItems = new ItemStack[] { new ItemStack(Items.titanium, 1) };

                allowCrossTypeCombo = true;
                itemCapacityMultiplier = 1f;
                liquidCapacityMultiplier = 1f;
                flowSmoothing = 0.92f;
                safetyBufferSeconds = 3f;

                craftEffect = Fx.smeltsmoke;
                drawer = new mindustry.world.draw.DrawMulti(
                        new mindustry.world.draw.DrawDefault(),
                        new mindustry.world.draw.DrawLiquidRegion(Liquids.water),
                        new mindustry.world.draw.DrawFlame(Color.valueOf("ff6600")));
            }
        };

        高压合成台 = new CombinedCrafter("高压合成台") {
            {
                requirements(Category.crafting,
                        with(Items.copper, 80, Items.lead, 60, Items.silicon, 40, Items.titanium, 30));
                localizedName = "高压合成台";
                description = "将合金锭高压合成为超合金。多级链最终环。";
                size = 3;
                craftTime = 120f;
                hasItems = true;
                hasPower = true;

                consumeItems(with(Items.titanium, 3));
                consumePower(4f);
                outputItems = new ItemStack[] { new ItemStack(Items.surgeAlloy, 1) };

                allowCrossTypeCombo = true;
                itemCapacityMultiplier = 1f;
                flowSmoothing = 0.92f;
                safetyBufferSeconds = 3f;

                craftEffect = Fx.shockwave;
                drawer = new mindustry.world.draw.DrawDefault();
            }
        };

        // ========== 测试组6：同类型组合 ==========

        电解槽 = new CombinedCrafter("电解槽") {
            {
                requirements(Category.crafting, with(Items.copper, 40, Items.lead, 30, Items.metaglass, 20));
                localizedName = "电解槽";
                description = "电解盐水产生氯气和氢氧化钠。多个相邻时容量累加、产出倍增。";
                size = 2;
                craftTime = 50f;
                hasItems = true;
                hasLiquids = true;
                hasPower = true;

                consumeItems(with(Items.sand, 1));
                consumeLiquid(Liquids.water, 0.25f);
                consumePower(1.5f);
                outputItems = new ItemStack[] {
                        new ItemStack(Items.coal, 1),
                        new ItemStack(Items.graphite, 1)
                };

                allowCrossTypeCombo = true;
                itemCapacityMultiplier = 1f;
                liquidCapacityMultiplier = 1f;
                flowSmoothing = 0.92f;
                safetyBufferSeconds = 3f;

                craftEffect = Fx.bubble;
                drawer = new mindustry.world.draw.DrawMulti(
                        new mindustry.world.draw.DrawDefault(),
                        new mindustry.world.draw.DrawLiquidRegion(Liquids.water),
                        new mindustry.world.draw.DrawBubbles(Color.valueOf("88ccff")));
            }
        };
    }
}
