package crystal.world.blocks.production;

import arc.*;
import arc.util.*;
import arc.math.*;
import arc.util.io.*;
import arc.struct.*;
import arc.scene.ui.*;
import arc.scene.Element;
import arc.scene.event.Touchable;
import arc.graphics.*;
import mindustry.ui.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.*;
import arc.graphics.g2d.*;
import arc.scene.ui.layout.*;
import mindustry.graphics.*;
import mindustry.world.draw.*;
import mindustry.world.meta.*;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.entities.units.*;
import mindustry.world.consumers.*;

public class MultipleCrafter extends Block {
    public Seq<Formula> formulas = new Seq<>();

    public boolean dumpExtraLiquid = true;
    public boolean ignoreLiquidFullness = false;

    public DrawBlock drawer = new DrawDefault();

    private static final Stat recipeListStat = new Stat("recipelist", StatCat.crafting);

    public MultipleCrafter(String name) {
        super(name);
        update = true;
        configurable = true;
        solid = true;
        ambientSound = Sounds.loopMachine;
        sync = true;
        ambientSoundVolume = 0.03f;
        drawArrow = false;
        flags = EnumSet.of(BlockFlag.factory);

        config(Integer.class, (MultipleCrafterBuild build, Integer value) -> build.idx = value);
    }

    @Override
    public void init() {
        if (consumesPower && hasPower && whetherConsumesPower()) {
            consume(new ConsumePowerDynamic(build -> {
                if (build instanceof MultipleCrafterBuild mcb && mcb.validFormula() &&
                        mcb.formula.inputs.contains(c -> c instanceof ConsumePower) &&
                        mcb.shouldConsume() && mcb.efficiencyScale() > 0)
                    return ((ConsumePower) mcb.formula.inputs.find(c -> c instanceof ConsumePower)).usage;
                return 0f;
            }) {
                @Override
                public float efficiency(Building b) {
                    return Mathf.zero(requestedPower(b)) ? 1f : b.power.status;
                }
            });
        }

        super.init();

        formulas.each(formula -> {
            // if(formula.outputItems != null) outputsItem = true;
            if (formula.outputLiquids != null)
                outputsLiquid = true;
            if (formula.inputs.contains(c -> c instanceof ConsumePower))
                consumesPower = true;
        });
    }

    @Override
    public void load() {
        super.load();

        drawer.load(this);
    }

    @Override
    public void drawPlanRegion(BuildPlan plan, Eachable<BuildPlan> list) {
        drawer.drawPlan(this, plan, list);
    }

    @Override
    public TextureRegion[] icons() {
        return drawer.finalIcons(this);
    }

    @Override
    public void setStats() {
        super.setStats();

        stats.add(recipeListStat, table -> {

            table.row();

            formulas.each(formula -> {
                table.table(Styles.grayPanel, row -> {

                    if (formula.inputs.any()) {
                        row.add("[accent]" + Core.bundle.format("stat.input") + ":[]").left().pad(4f).padTop(4f)
                                .padLeft(4f).row();

                        if (formula.inputItems() != null)
                            row.table(add -> formula.inputItems().each(stack -> add
                                    .add(StatValues.displayItem(stack.item, stack.amount)).pad(4f).padLeft(4f))).left();
                        if (formula.inputLiquids() != null)
                            row.table(add -> formula.inputLiquids().each(stack -> add
                                    .add(StatValues.displayLiquid(stack.liquid, stack.amount * 60, true)).pad(4f)))
                                    .left();

                        row.row();
                    }

                    if (formula.outputItems != null || formula.outputLiquids != null) {
                        row.add("[accent]" + Core.bundle.format("stat.output") + ":[]").left().pad(4f).padLeft(4f)
                                .row();

                        if (formula.outputItems != null)
                            row.table(add -> formula.outputItems.each(stack -> add
                                    .add(StatValues.displayItem(stack.item, stack.amount)).pad(4f).padLeft(4f))).left();
                        if (formula.outputLiquids != null)
                            row.table(add -> formula.outputLiquids.each(stack -> add
                                    .add(StatValues.displayLiquid(stack.liquid, stack.amount * 60, true)).pad(4f)))
                                    .left();

                        row.row();
                    }

                    if (formula.inputPower() != 0f)
                        row.add("[accent]" + Core.bundle.format("stat.poweruse") + ": " + Iconc.power + "[]"
                                + String.valueOf(formula.inputPower() * 60) + StatUnit.powerSecond.localized()).left()
                                .pad(4f).padLeft(4f).row();

                    row.add("[accent]" + Core.bundle.format("stat.productiontime") + ": []"
                            + Strings.autoFixed(formula.craftTime / 60f, 2) + "s").left().pad(4f).padLeft(4f)
                            .padBottom(4f).row();

                    row.left();
                }).left().growX().pad(4f).row();
            });
        });
    }

    @Override
    public void setBars() {
        super.setBars();

        addBar("efficiency", (MultipleCrafterBuild e) -> new Bar(
                () -> Core.bundle.format("bar.efficiency", (int) (e.efficiency * 100)),
                () -> Pal.lightOrange,
                () -> e.efficiency));
    }

    public boolean whetherConsumesPower() {
        for (var formula : formulas) {
            for (var consumer : formula.inputs) {
                if (consumer instanceof ConsumePower)
                    return true;
            }
        }

        return false;
    }

    public static class Formula {
        public Seq<Consume> inputs = new Seq<>();
        public @Nullable Seq<ItemStack> outputItems = new Seq<>();
        public @Nullable Seq<LiquidStack> outputLiquids = new Seq<>();

        public int[] liquidOutputDirections = { -1 };

        public @Nullable Effect craftEffect = Fx.none;
        public Effect updateEffect = Fx.none;
        public float updateEffectChance = 0.04f;
        public float updateEffectSpread = 4f;
        public float warmupSpeed = 0.019f;

        public float craftTime = 60f;

        public Formula() {
        }

        @Nullable
        public Seq<ItemStack> inputItems() {
            if (!inputs.contains(consume -> consume instanceof ConsumeItems))
                return null;

            Seq<ItemStack> ret = new Seq<>();
            for (var cons : inputs.select(input -> input instanceof ConsumeItems))
                for (var item : ((ConsumeItems) cons).items)
                    ret.add(item);

            return ret;
        }

        @Nullable
        public Seq<LiquidStack> inputLiquids() {
            if (!inputs.contains(consume -> consume instanceof ConsumeLiquidBase))
                return null;

            Seq<LiquidStack> ret = new Seq<>();
            for (var cons : inputs.select(input -> input instanceof ConsumeLiquid)) {
                var consl = (ConsumeLiquid) cons;
                ret.add(new LiquidStack(consl.liquid, consl.amount));
            }
            for (var cons : inputs.select(input -> input instanceof ConsumeLiquids))
                for (var liquid : ((ConsumeLiquids) cons).liquids)
                    ret.add(liquid);

            return ret;
        }

        public float inputPower() {
            if (!inputs.contains(consumer -> consumer instanceof ConsumePower))
                return 0f;

            return ((ConsumePower) inputs.find(consumer -> consumer instanceof ConsumePower)).usage;
        }
    }

    public class MultipleCrafterBuild extends Building {
        public @Nullable Formula formula = null;
        public int idx = 0;
        public float progress = 0f;
        public float warmup = 0f;
        public float totalProgress = 0f;

        public boolean validFormula() {
            return formula != null;
        }

        public float warmupTarget() {
            return 1f;
        }

        @Override
        public void updateTile() {
            if (formulas.isEmpty())
                return;

            formula = formulas.get(idx);
            if (!validFormula())
                return;

            if (efficiency > 0) {
                progress += getProgressIncrease(formula.craftTime);
                warmup = Mathf.approachDelta(warmup, warmupTarget(), formula.warmupSpeed);

                var outputLiquids = formula.outputLiquids;
                if (outputLiquids != null) {
                    float inc = getProgressIncrease(1f);
                    for (var output : outputLiquids) {
                        handleLiquid(this, output.liquid,
                                Math.min(output.amount * inc, liquidCapacity - liquids.get(output.liquid)));
                    }
                }

                if (wasVisible && Mathf.chanceDelta(formula.updateEffectChance)) {
                    formula.updateEffect.at(x + Mathf.range(size * formula.updateEffectSpread),
                            y + Mathf.range(size * formula.updateEffectSpread));
                }
            } else
                warmup = Mathf.approachDelta(warmup, 0f, formula.warmupSpeed);

            totalProgress += warmup * Time.delta;

            if (progress >= 1f) {
                craft();
            }

            dumpOutputs();
        }

        public void craft() {
            consume();

            var outputItems = formula.outputItems;
            if (outputItems != null) {
                for (var output : outputItems) {
                    for (int i = 0; i < output.amount; i++) {
                        offload(output.item);
                    }
                }
            }

            if (wasVisible) {
                formula.craftEffect.at(x, y);
            }
            progress %= 1f;
        }

        public void dumpOutputs() {
            var totalOutputItems = new Seq<Item>();
            formulas.each(f -> f.outputItems != null,
                    f -> f.outputItems.each(stack -> totalOutputItems.add(stack.item)));

            if (timer(timerDump, dumpTime / timeScale))
                totalOutputItems.each(item -> items.has(item), item -> dump(item));

            formulas.each(f -> f.outputLiquids != null, f -> {
                for (int i = 0; i < f.outputLiquids.size; i++) {
                    int dir = f.liquidOutputDirections.length > i ? f.liquidOutputDirections[i] : -1;

                    var dump = f.outputLiquids.get(i).liquid;
                    if (liquids.get(dump) >= 0.001f)
                        dumpLiquid(dump, 2f, dir);
                }
            });
        }

        @Override
        public float getProgressIncrease(float baseTime) {
            if (ignoreLiquidFullness) {
                return super.getProgressIncrease(baseTime);
            }

            float scaling = 1f, max = 1f;
            if (formula.outputLiquids != null) {
                max = 0f;
                for (var s : formula.outputLiquids) {
                    float value = (liquidCapacity - liquids.get(s.liquid)) / (s.amount * edelta());
                    scaling = Math.min(scaling, value);
                    max = Math.max(max, value);
                }
            }

            return super.getProgressIncrease(baseTime) * (dumpExtraLiquid ? Math.min(max, 1f) : scaling);
        }

        @Override
        public void consume() {
            formula.inputs.each(consumer -> consumer.trigger(this));
        }

        @Override
        public void draw() {
            drawer.draw(this);
        }

        @Override
        public void drawLight() {
            super.drawLight();
            drawer.drawLight(this);
        }

        @Override
        public boolean shouldConsume() {
            if (!validFormula())
                return false;

            var outputItems = formula.outputItems;
            if (outputItems != null) {
                for (var output : outputItems) {
                    if (items.get(output.item) + output.amount > itemCapacity) {
                        return false;
                    }
                }
            }

            var outputLiquids = formula.outputLiquids;
            if (outputLiquids != null && !ignoreLiquidFullness) {
                boolean allFull = true;
                for (var output : outputLiquids) {
                    if (liquids.get(output.liquid) >= liquidCapacity - 0.001f) {
                        if (!dumpExtraLiquid) {
                            return false;
                        }
                    } else {
                        allFull = false;
                    }
                }

                if (allFull) {
                    return false;
                }
            }

            return enabled;
        }

        @Override
        public boolean acceptItem(Building source, Item item) {
            // 如果配方为空，不接受物品(一般而言也不会配方为空，毕竟idx=0)
            if (!validFormula())
                return true;

            for (var consume : formula.inputs) {
                if (!(consume instanceof ConsumeItems))
                    continue;

                // 如果但凡当前配方有需要该物品且未满，接受
                // 判断已经把非ConsumeItems的全过滤了，放心强转
                for (var stack : ((ConsumeItems) consume).items) {
                    if (stack.item == item)
                        return items.get(item) < getMaximumAccepted(item);
                }
            }

            return false;
        }

        @Override
        public boolean acceptLiquid(Building source, Liquid liquid) {
            if (!validFormula())
                return true;
            if (formula.inputLiquids() == null || !block.hasLiquids)
                return false;

            for (var stack : formula.inputLiquids())
                if (liquid == stack.liquid)
                    return true;

            return false;
        }

        @Override
        @Nullable
        public Object config() {
            return lastConfig;
        }

        @Override
        public void buildConfiguration(Table table) {
            super.buildConfiguration(table);

            if (formulas == null || formulas.isEmpty())
                return;

            table.row();

            for (int i = 0; i < formulas.size; i++) {
                Formula r = formulas.get(i);
                int _idx = i;

                Table container = new Table(Styles.grayPanel) {
                    @Override
                    public void draw() {
                        super.draw();
                        if (_idx == idx) {
                            Draw.color(Pal.accent);
                            Lines.stroke(3f);
                            Lines.rect(x + 1f, y + 1f, width - 2f, height - 2f);
                            Draw.reset();
                        }
                    }
                };
                container.defaults().pad(4f).left();
                container.touchable = Touchable.enabled;

                container.table(row -> {
                    if (r.inputs.any()) {
                        if (r.inputItems() != null)
                            r.inputItems().each(stack -> row.add(StatValues.displayItem(stack.item, stack.amount))
                                    .left().padRight(6f));
                        if (r.inputLiquids() != null)
                            r.inputLiquids()
                                    .each(stack -> row
                                            .add(StatValues.displayLiquid(stack.liquid, stack.amount * 60, true)).left()
                                            .padRight(6f));
                    } else
                        row.add("[red]N.[yellow]U.[blue]L.[green]L.[]").padRight(6f);

                    row.add(String.valueOf(Iconc.right)).padLeft(6f).padRight(12f);

                    if (r.outputItems != null || r.outputLiquids != null) {
                        if (r.outputItems != null)
                            r.outputItems.each(stack -> row.add(StatValues.displayItem(stack.item, stack.amount)).left()
                                    .padRight(6f));
                        if (r.outputLiquids != null)
                            r.outputLiquids.each(
                                    stack -> row.add(StatValues.displayLiquid(stack.liquid, stack.amount * 60, true))
                                            .left().padRight(6f));
                    } else
                        row.add("[red]N.[yellow]U.[blue]L.[green]L.[]").padRight(6f);

                    row.add("[accent]with[]").padLeft(6f).padRight(12f);

                    if (r.inputPower() != 0f) {
                        row.add("[accent]" + Iconc.power + "[]" + String.valueOf(r.inputPower() * 60f)).padRight(12f);
                        row.add("+").padRight(12f);
                    }

                    row.add(Strings.autoFixed(r.craftTime / 60f, 3) + "s");
                });

                container.clicked(() -> {
                    if (_idx != idx)
                        configure(_idx);
                    Vars.control.input.config.hideConfig();
                });

                table.add(container).growX().pad(4f).row();
            }
        }

        @Override
        public void displayConsumption(Table table) {
            if (!validFormula())
                return;

            table.left();
            formula.inputs.each(cons -> !(cons.optional && cons.booster), cons -> cons.build(this, table));
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.i(idx);
            write.f(progress);
            write.f(warmup);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            idx = read.i();
            progress = read.f();
            warmup = read.f();
        }

        @Override
        public float efficiencyScale() {
            if (!validFormula())
                return 0f;

            if (formula.inputItems() != null)
                if (!items.has(formula.inputItems().toArray(ItemStack.class)))
                    return 0f;

            if (formula.inputLiquids() != null)
                for (var stack : formula.inputLiquids())
                    if (liquids.get(stack.liquid) <= 0.001f)
                        return 0f;

            return super.efficiencyScale();
        }
    }
}
