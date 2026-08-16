package crystal.ui.fragments;

import arc.*;
import arc.graphics.*;
import arc.input.*;
import arc.math.geom.*;
import arc.scene.*;
import arc.scene.event.*;
import arc.scene.style.*;
import arc.scene.ui.*;
import arc.scene.ui.Tooltip.*;
import arc.scene.ui.layout.Stack;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import crystal.aviation.SatelliteContentFilter;
import crystal.aviation.SatelliteManager;
import crystal.aviation.input.SatelliteMissileInputHandler;
import crystal.world.meta.CBuildVisibility;
import mindustry.ai.*;
import mindustry.ai.types.*;
import mindustry.content.*;
import mindustry.core.*;
import mindustry.entities.*;
import mindustry.entities.units.*;
import mindustry.game.EventType.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.input.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.ui.fragments.PlacementFragment;
import mindustry.world.*;
import mindustry.world.blocks.ConstructBlock.*;
import mindustry.world.meta.*;

import java.util.*;

import static mindustry.Vars.*;

/**
 * 卫星地图专用建筑列表。
 * 完全复制原版 PlacementFragment 的 UI 与交互逻辑，只修改 unlocked() 过滤规则，
 * 并在轨道打击模式下隐藏整个面板。
 */
public class SatellitePlacementFragment extends PlacementFragment {
    final int rowWidth = 4;

    Seq<Block> returnArray = new Seq<>(), returnArray2 = new Seq<>(), returnArray3 = new Seq<>();
    Seq<Category> returnCatArray = new Seq<>();
    boolean[] categoryEmpty = new boolean[Category.all.length];
    /** 是否正在显示卫星建筑分区。 */
    boolean satelliteMode = false;
    ObjectMap<Category, Block> selectedBlocks = new ObjectMap<>();
    ObjectFloatMap<Category> scrollPositions = new ObjectFloatMap<>();
    @Nullable
    Block menuHoverBlock;
    @Nullable
    Displayable hover;
    @Nullable
    Building lastFlowBuild, nextFlowBuild;
    @Nullable
    Object lastDisplayState;
    @Nullable
    Team lastTeam;
    boolean wasHovered;
    Table blockTable, toggler, topTable, blockCatTable, commandTable;
    Stack mainStack;
    ScrollPane blockPane;
    Runnable rebuildCommand;
    boolean blockSelectEnd, wasCommandMode;
    int blockSelectSeq;
    long blockSelectSeqMillis;
    KeyBind[] blockSelect = {
            Binding.blockSelect01,
            Binding.blockSelect02,
            Binding.blockSelect03,
            Binding.blockSelect04,
            Binding.blockSelect05,
            Binding.blockSelect06,
            Binding.blockSelect07,
            Binding.blockSelect08,
            Binding.blockSelect09,
            Binding.blockSelect10,
            Binding.blockSelectLeft,
            Binding.blockSelectRight,
            Binding.blockSelectUp,
            Binding.blockSelectDown
    };

    public SatellitePlacementFragment() {
        super();

        // 以下监听器操作的是本类自己复制的字段，需要单独注册一份
        Events.run(Trigger.unitCommandChange, () -> {
            if (rebuildCommand != null) {
                rebuildCommand.run();
            }
        });

        Events.on(ResetEvent.class, event -> {
            selectedBlocks.clear();
        });

        Events.run(Trigger.update, () -> {
            if (lastFlowBuild != null && lastFlowBuild != nextFlowBuild) {
                if (lastFlowBuild.flowItems() != null)
                    lastFlowBuild.flowItems().stopFlow();
                if (lastFlowBuild.liquids != null)
                    lastFlowBuild.liquids.stopFlow();
            }

            lastFlowBuild = nextFlowBuild;

            if (nextFlowBuild != null) {
                if (nextFlowBuild.flowItems() != null)
                    nextFlowBuild.flowItems().updateFlow();
                if (nextFlowBuild.liquids != null)
                    nextFlowBuild.liquids.updateFlow();
            }
        });
    }

    @Override
    public Displayable hover() {
        return hover;
    }

    @Override
    public void rebuild() {
        if (toggler == null || toggler.parent == null) {
            Log.warn("[SatellitePlacementFragment] rebuild() skipped: toggler or parent is null");
            return;
        }
        try {
            Group group = toggler.parent;
            int index = toggler.getZIndex();
            toggler.remove();
            build(group);
            if (toggler != null) {
                toggler.setZIndex(index);
            }
            lastDisplayState = null;
        } catch (Throwable t) {
            Log.err("[SatellitePlacementFragment] rebuild() failed", t);
        }
    }

    boolean updatePick(InputHandler input) {
        Tile tile = world.tileWorld(Core.input.mouseWorldX(), Core.input.mouseWorldY());
        if (tile != null && Core.input.keyTap(Binding.pick) && player.isBuilder() && !Core.scene.hasDialog()) {
            var build = tile.build;

            if (build != null && build.inFogTo(player.team())) {
                build = null;
            }

            Block tryBlock = build == null ? null : build instanceof ConstructBuild c ? c.current : build.block;
            Object tryConfig = build == null || !build.block.copyConfig ? null : build.config();

            for (BuildPlan req : player.unit().plans()) {
                if (!req.breaking && req.block.bounds(req.x, req.y, Tmp.r1).contains(Core.input.mouseWorld())) {
                    tryBlock = req.block;
                    tryConfig = req.config;
                    break;
                }
            }

            if (tryBlock == null && state.rules.editor) {
                tryBlock = tile.block() != Blocks.air ? tile.block()
                        : tile.overlay() != Blocks.air ? tile.overlay()
                                : tile.floor() != Blocks.air ? tile.floor() : null;
            }

            if (tryBlock != null && build == null && tryConfig == null) {
                tryConfig = tryBlock.getConfig(tile);
            }

            if (tryBlock != null && ((tryBlock.isVisible() && unlocked(tryBlock)) || state.rules.editor)) {
                input.block = tryBlock;
                tryBlock.lastConfig = tryConfig;
                if (tryBlock.isVisible()) {
                    if (tryBlock.buildVisibility == CBuildVisibility.satelliteOnly) {
                        satelliteMode = true;
                    } else {
                        satelliteMode = false;
                        currentCategory = input.block.category;
                    }
                }
                tryBlock.onPicked(tile);
                return true;
            }
        }
        return false;
    }

    boolean gridUpdate(InputHandler input) {
        scrollPositions.put(currentCategory, blockPane.getScrollY());

        if (updatePick(input)) {
            return true;
        }

        if (ui.chatfrag.shown() || ui.consolefrag.shown() || Core.scene.hasKeyboard())
            return false;

        for (int i = 0; i < blockSelect.length; i++) {
            if (Core.input.keyTap(blockSelect[i])) {
                if (i > 9) {
                    Seq<Block> blocks = getUnlockedByCategory(currentCategory);
                    Block currentBlock = getSelectedBlock(currentCategory);
                    for (int j = 0; j < blocks.size; j++) {
                        if (blocks.get(j) == currentBlock) {
                            switch (i) {
                                case 10 -> j = (j - 1 + blocks.size) % blocks.size;
                                case 11 -> j = (j + 1) % blocks.size;
                                case 12 -> {
                                    j = (j > 3 ? j - 4 : blocks.size - blocks.size % 4 + j);
                                    j -= (j < blocks.size ? 0 : 4);
                                }
                                case 13 -> j = (j < blocks.size - 4 ? j + 4 : j % 4);
                            }
                            input.block = blocks.get(j);
                            selectedBlocks.put(currentCategory, input.block);
                            break;
                        }
                    }
                } else if (blockSelectEnd || Time.timeSinceMillis(blockSelectSeqMillis) > 400) {
                    // 按数字键切换普通分类；卫星分区单独处理，不在这里切换
                    if (!getNormalUnlockedBlocksByCategory(Category.all[i]).isEmpty()) {
                        satelliteMode = false;
                        currentCategory = Category.all[i];
                        if (input.block != null) {
                            input.block = getSelectedBlock(currentCategory);
                        }
                        blockSelectEnd = false;
                        blockSelectSeq = 0;
                        blockSelectSeqMillis = Time.millis();
                    }
                } else {
                    if (blockSelectSeq == 0) {
                        blockSelectSeq = i + 1;
                    } else {
                        i += (blockSelectSeq - (i != 9 ? 0 : 1)) * 10;
                        blockSelectEnd = true;
                    }
                    Seq<Block> blocks = getByCategory(currentCategory);
                    if (i >= blocks.size || !unlocked(blocks.get(i)))
                        return true;
                    input.block = (i < blocks.size) ? blocks.get(i) : null;
                    selectedBlocks.put(currentCategory, input.block);
                    blockSelectSeqMillis = Time.millis();
                }
                return true;
            }
        }

        if (Core.input.keyTap(Binding.categoryPrev)) {
            satelliteMode = false;
            int i = 0;
            do {
                currentCategory = currentCategory.prev();
                i++;
            } while (categoryEmpty[currentCategory.ordinal()] && i < categoryEmpty.length);
            input.block = getSelectedBlock(currentCategory);
            return true;
        }

        if (Core.input.keyTap(Binding.categoryNext)) {
            satelliteMode = false;
            int i = 0;
            do {
                currentCategory = currentCategory.next();
                i++;
            } while (categoryEmpty[currentCategory.ordinal()] && i < categoryEmpty.length);
            input.block = getSelectedBlock(currentCategory);
            return true;
        }

        if (Core.input.keyTap(Binding.blockInfo)) {
            if (hovered() instanceof Unit unit && unit.type.unlockedNow()) {
                ui.content.show(unit.type());
            } else {
                var build = world.buildWorld(Core.input.mouseWorld().x, Core.input.mouseWorld().y);
                Block hovering = build == null ? null : build instanceof ConstructBuild c ? c.current : build.block;
                Block displayBlock = menuHoverBlock != null ? menuHoverBlock
                        : input.block != null ? input.block : hovering;
                if (displayBlock != null && displayBlock.unlockedNow()) {
                    ui.content.show(displayBlock);
                    Events.fire(new BlockInfoEvent());
                }
            }
        }

        return false;
    }

    @Override
    public void build(Group parent) {
        if (parent == null) {
            Log.warn("[SatellitePlacementFragment] build() called with null parent, skipping");
            return;
        }
        // 如果之前已经构建过，先移除旧 toggler，防止 HUD 重建或重复安装时出现两个建筑列表
        if (toggler != null && toggler.parent != null) {
            toggler.remove();
        }
        parent.fill(full -> {
            toggler = full;
            full.name = "placement-toggler";
            full.bottom().right().visible(() -> ui.hudfrag.shown && !SatelliteMissileInputHandler.orbitalStrikeMode);

            full.table(frame -> {

                Runnable rebuildCategory = () -> {
                    blockTable.clear();
                    blockTable.top().margin(5);

                    int index = 0;

                    ButtonGroup<ImageButton> group = new ButtonGroup<>();
                    group.setMinCheckCount(0);

                    for (Block block : getDisplayedBlocks()) {
                        if (!unlocked(block))
                            continue;
                        if (index++ % rowWidth == 0) {
                            blockTable.row();
                        }

                        ImageButton button = blockTable
                                .button(new TextureRegionDrawable(block.uiIcon), Styles.selecti, () -> {
                                    if (unlocked(block)) {
                                        if ((Core.input.keyDown(KeyCode.shiftLeft)
                                                || Core.input.keyDown(KeyCode.controlLeft))
                                                && Fonts.getUnicode(block.name) != 0) {
                                            Core.app.setClipboardText((char) Fonts.getUnicode(block.name) + "");
                                            ui.showInfoFade("@copied");
                                        } else {
                                            control.input.block = control.input.block == block ? null : block;
                                            selectedBlocks.put(currentCategory, control.input.block);
                                        }
                                    }
                                }).size(46f).group(group).name("block-" + block.name).get();
                        button.resizeImage(iconMed);

                        button.update(() -> {
                            Building core = player.core();
                            Color color = (state.rules.infiniteResources || (core != null
                                    && (core.items.has(block.requirements, state.rules.buildCostMultiplier)
                                            || state.rules.infiniteResources)))
                                    && player.isBuilder() ? Color.white : Color.gray;
                            button.forEach(elem -> elem.setColor(color));
                            button.setChecked(control.input.block == block);

                            if (!block.isPlaceable()) {
                                button.forEach(elem -> elem.setColor(Color.darkGray));
                            }
                        });

                        button.hovered(() -> menuHoverBlock = block);
                        button.exited(() -> {
                            if (menuHoverBlock == block) {
                                menuHoverBlock = null;
                            }
                        });
                    }
                    if (index < 4) {
                        for (int i = 0; i < 4 - index; i++) {
                            blockTable.add().size(46f);
                        }
                    }
                    blockTable.act(0f);
                    blockPane.setScrollYForce(scrollPositions.get(currentCategory, 0));
                    Core.app.post(() -> {
                        blockPane.setScrollYForce(scrollPositions.get(currentCategory, 0));
                        blockPane.act(0f);
                        blockPane.layout();
                    });
                };

                frame.table(Tex.buttonEdge2, top -> {
                    topTable = top;
                    top.add(new Table()).growX().update(topTable -> {

                        Displayable hovered = hover;
                        Block displayBlock = menuHoverBlock != null ? menuHoverBlock : control.input.block;
                        Object displayState = displayBlock != null ? displayBlock : hovered;
                        boolean isHovered = displayBlock == null;

                        if (wasHovered == isHovered && lastDisplayState == displayState && lastTeam == player.team())
                            return;

                        topTable.clear();
                        topTable.top().left().margin(5);

                        lastDisplayState = displayState;
                        wasHovered = isHovered;
                        lastTeam = player.team();

                        if (displayBlock != null) {

                            topTable.table(header -> {
                                String keyCombo = "";
                                if (!mobile) {
                                    Seq<Block> blocks = getByCategory(currentCategory);
                                    for (int i = 0; i < blocks.size; i++) {
                                        if (blocks.get(i) == displayBlock && (i + 1) / 10 - 1 < blockSelect.length) {
                                            keyCombo = Core.bundle.format("placement.blockselectkeys",
                                                    blockSelect[currentCategory.ordinal()].value.key.toString())
                                                    + (i < 10 ? ""
                                                            : blockSelect[(i + 1) / 10 - 1].value.key.toString() + ",")
                                                    + blockSelect[i % 10].value.key.toString() + "]";
                                            break;
                                        }
                                    }
                                }
                                String keyComboFinal = keyCombo;
                                header.left();
                                header.add(new Image(displayBlock.uiIcon)).scaling(Scaling.fit).size(8 * 4);
                                header.labelWrap(() -> !unlocked(displayBlock) ? Core.bundle.get("block.unknown")
                                        : displayBlock.localizedName + keyComboFinal)
                                        .left().width(190f).padLeft(5);
                                header.add().growX();
                                if (unlocked(displayBlock)) {
                                    header.button("?", Styles.flatBordert, () -> {
                                        ui.content.show(displayBlock);
                                        Events.fire(new BlockInfoEvent());
                                    }).size(8 * 5).padTop(-5).padRight(-5).right().grow().name("blockinfo");
                                }
                            }).growX().left();
                            topTable.row();

                            topTable.table(req -> {
                                req.top().left();

                                for (ItemStack stack : displayBlock.requirements) {
                                    req.table(line -> {
                                        line.left();
                                        line.image(stack.item.uiIcon).size(8 * 2);
                                        line.add(stack.item.localizedName).maxWidth(140f).fillX().color(Color.lightGray)
                                                .padLeft(2).left().get().setEllipsis(true);
                                        line.labelWrap(() -> {
                                            Building core = player.core();
                                            int stackamount = Math
                                                    .round(stack.amount * state.rules.buildCostMultiplier);
                                            if (core == null || state.rules.infiniteResources)
                                                return "*/" + stackamount;

                                            int amount = core.items.get(stack.item);
                                            String color = (amount < stackamount / 2f ? "[scarlet]"
                                                    : amount < stackamount ? "[accent]" : "[white]");

                                            return color + UI.formatAmount(amount) + "[white]/" + stackamount;
                                        }).padLeft(5);
                                    }).left();
                                    req.row();
                                }
                            }).growX().left().margin(3);

                            topTable.row();
                            topTable.collapser(b -> {
                                b.left();
                                b.marginTop(2f);
                                b.image(Icon.cancel).padRight(2).color(Color.scarlet);
                                b.label(() -> {
                                    var reason = getUnplaceableReason(displayBlock);
                                    return reason == null ? "" : reason;
                                }).width(190f).wrap();
                            }, () -> getUnplaceableReason(displayBlock) != null).left();

                        } else if (hovered != null) {
                            hovered.display(topTable);
                        }
                    });
                }).colspan(3).fillX().visible(this::hasInfoBox).touchable(Touchable.enabled).row();

                frame.image().color(Pal.gray).colspan(3).height(4).growX().row();

                blockCatTable = new Table();
                commandTable = new Table(Tex.pane2);
                mainStack = new Stack();

                mainStack.update(() -> {
                    if (control.input.commandMode != wasCommandMode) {
                        mainStack.clearChildren();
                        mainStack.addChild(control.input.commandMode ? commandTable : blockCatTable);

                        if (control.input.commandMode) {
                            commandTable.getCells().peek().width(blockCatTable.getWidth() / Scl.scl(1f));
                        }

                        wasCommandMode = control.input.commandMode;
                    }
                });

                frame.add(mainStack).colspan(3).fill();

                frame.row();

                frame.rect((x, y, w, h) -> {
                    if (Core.scene.marginBottom > 0) {
                        Tex.paneLeft.draw(x, 0, w, y);
                    }
                }).colspan(3).fillX().row();

                {
                    commandTable.touchable = Touchable.enabled;
                    commandTable.add(Core.bundle.get("commandmode.name")).fill().center().labelAlign(Align.center)
                            .row();
                    commandTable.image().color(Pal.accent).growX().pad(20f).padTop(0f).padBottom(4f).row();
                    commandTable.table(u -> {

                        Bits activeCommands = new Bits(content.unitCommands().size);
                        Bits activeStances = new Bits(content.unitStances().size);

                        Bits availableCommands = new Bits(content.unitCommands().size);
                        Bits availableStances = new Bits(content.unitStances().size);
                        Bits activeTypes = new Bits(content.units().size),
                                prevActiveTypes = new Bits(content.units().size);

                        u.left();
                        Bits usedCommands = new Bits(content.unitCommands().size);
                        var commands = new Seq<UnitCommand>();

                        Bits usedStances = new Bits(content.unitStances().size);
                        var stances = new Seq<UnitStance>();
                        var stancesOut = new Seq<UnitStance>();

                        int[][] countBox = new int[1][0];

                        rebuildCommand = () -> {
                            if (countBox[0].length != content.units().size)
                                countBox[0] = new int[content.units().size];
                            int[] counts = countBox[0];

                            u.clearChildren();
                            var units = control.input.selectedUnits;
                            if (units.size > 0) {
                                usedCommands.clear();
                                usedStances.clear();
                                commands.clear();
                                stances.clear();
                                Arrays.fill(counts, 0);

                                for (var unit : units) {
                                    counts[unit.type.id]++;

                                    stancesOut.clear();
                                    unit.type.getUnitStances(unit, stancesOut);

                                    for (var stance : stancesOut) {
                                        if (!usedStances.get(stance.id)) {
                                            stances.add(stance);
                                            usedStances.set(stance.id);
                                        }
                                    }
                                }

                                Table unitlist = u.table().growX().left().get();
                                unitlist.left();

                                int col = 0;
                                for (int i = 0; i < counts.length; i++) {
                                    int fi = i;
                                    if (counts[i] > 0) {
                                        var type = content.unit(i);
                                        unitlist.add(StatValues.stack(type, counts[i])).pad(4).with(b -> {
                                            b.clearListeners();
                                            b.addListener(Tooltips.getInstance().create(type.localizedName, false));

                                            Label amountLabel = b.find("stack amount");
                                            if (amountLabel != null) {
                                                amountLabel.setText(() -> counts[fi] + "");
                                            }

                                            var listener = new ClickListener();

                                            b.clicked(KeyCode.mouseLeft, () -> {
                                                control.input.selectedUnits.removeAll(unit -> unit.type != type);
                                                Events.fire(Trigger.unitCommandChange);
                                            });
                                            b.clicked(KeyCode.mouseRight, () -> {
                                                control.input.selectedUnits.removeAll(unit -> unit.type == type);
                                                Events.fire(Trigger.unitCommandChange);
                                            });

                                            b.addListener(listener);
                                            b.addListener(new HandCursorListener());
                                            b.update(() -> ((Group) b.getChildren().first()).getChildren().first()
                                                    .setColor(listener.isOver() ? Color.lightGray : Color.white));
                                        });

                                        if (++col % 7 == 0) {
                                            unitlist.row();
                                        }

                                        for (var command : type.commands) {
                                            if (!usedCommands.get(command.id)) {
                                                commands.add(command);
                                                usedCommands.set(command.id);
                                            }
                                        }
                                    }
                                }

                                if (commands.size > 1) {
                                    u.row();

                                    u.table(coms -> {
                                        coms.left();
                                        int scol = 0;
                                        for (var command : commands) {
                                            coms.button(Icon.icons.get(command.icon, Icon.cancel),
                                                    Styles.clearNoneTogglei, () -> {
                                                        Call.setUnitCommand(player,
                                                                units.mapInt(un -> un.id,
                                                                        un -> un.type.allowCommand(un, command))
                                                                        .toArray(),
                                                                command);
                                                    }).checked(i -> activeCommands.get(command.id)).size(50f)
                                                    .tooltip(command.localized(), true);

                                            if (++scol % 6 == 0)
                                                coms.row();
                                        }

                                    }).fillX().padTop(4f).left();
                                }

                                if (stances.size > 1) {
                                    u.row();

                                    if (commands.size > 1) {
                                        u.add(new Image(Tex.whiteui)).height(3f).color(Pal.gray).pad(7f).growX().row();
                                    }

                                    u.table(coms -> {
                                        coms.left();
                                        int scol = 0;
                                        for (var stance : stances) {

                                            coms.button(stance.getIcon(), Styles.clearNoneTogglei, () -> {
                                                Call.setUnitStance(player,
                                                        units.mapInt(un -> un.id, un -> un.type.allowStance(un, stance))
                                                                .toArray(),
                                                        stance, !activeStances.get(stance.id));
                                            }).checked(i -> activeStances.get(stance.id)).size(50f)
                                                    .tooltip(stance.localized(), true);

                                            if (++scol % 6 == 0)
                                                coms.row();
                                        }
                                    }).fillX().padTop(4f).left();
                                }
                            } else {
                                u.add(Core.bundle.get("commandmode.nounits")).color(Color.lightGray).growX().center()
                                        .labelAlign(Align.center).pad(6);
                            }
                        };

                        u.update(() -> {
                            {
                                if (countBox[0].length != content.units().size)
                                    countBox[0] = new int[content.units().size];
                                int[] counts = countBox[0];
                                activeCommands.clear();
                                activeStances.clear();
                                availableCommands.clear();
                                availableStances.clear();
                                activeTypes.clear();

                                Arrays.fill(counts, 0);

                                for (var unit : control.input.selectedUnits) {
                                    if (unit.controller() instanceof CommandAI cmd) {
                                        activeCommands.set(cmd.command.id);
                                        activeStances.set(cmd.stances);
                                    }

                                    counts[unit.type.id]++;

                                    activeTypes.set(unit.type.id);

                                    stancesOut.clear();
                                    unit.type.getUnitStances(unit, stancesOut);

                                    for (var stance : stancesOut) {
                                        availableStances.set(stance.id);
                                    }

                                    for (var command : unit.type.commands) {
                                        availableCommands.set(command.id);
                                    }
                                }

                                if (!usedCommands.equals(availableCommands) || !usedStances.equals(availableStances)
                                        || !prevActiveTypes.equals(activeTypes)) {
                                    rebuildCommand.run();
                                    prevActiveTypes.set(activeTypes);
                                }

                                for (UnitStance stance : stances) {
                                    if (stance.keybind != null && Core.input.keyTap(stance.keybind)) {
                                        Call.setUnitStance(player, control.input.selectedUnits
                                                .mapInt(un -> un.id, un -> un.type.allowStance(un, stance)).toArray(),
                                                stance, !activeStances.get(stance.id));
                                    }
                                }

                                for (UnitCommand command : commands) {
                                    if (command.keybind != null && Core.input.keyTap(command.keybind)) {
                                        Call.setUnitCommand(player, control.input.selectedUnits
                                                .mapInt(un -> un.id, un -> un.type.allowCommand(un, command)).toArray(),
                                                command);
                                    }
                                }
                            }
                        });
                        rebuildCommand.run();
                    }).grow();
                }

                {
                    blockCatTable.table(Tex.pane2, blocksSelect -> {
                        blocksSelect.margin(4).marginTop(0);
                        blockPane = blocksSelect.pane(blocks -> blockTable = blocks).height(194f).update(pane -> {
                            if (pane.hasScroll()) {
                                Element result = Core.scene.getHoverElement();
                                if (result == null || !result.isDescendantOf(pane)) {
                                    Core.scene.setScrollFocus(null);
                                }
                            }
                        }).grow().get();
                        blockPane.setStyle(Styles.smallPane);
                        blocksSelect.row();
                        blocksSelect.table(t -> {
                            t.image().color(Pal.gray).height(4f).colspan(4).growX();
                            t.row();
                            control.input.buildPlacementUI(t);
                        }).name("inputTable").growX();
                    }).fillY().bottom().touchable(Touchable.enabled);
                    blockCatTable.table(categories -> {
                        categories.bottom();
                        categories.add(new Image(Styles.black6) {
                            @Override
                            public void draw() {
                                if (height <= Scl.scl(3f))
                                    return;
                                getDrawable().draw(x, y, width, height - Scl.scl(3f));
                            }
                        }).colspan(2).growX().growY().padTop(-3f).row();
                        categories.defaults().size(50f);

                        ButtonGroup<ImageButton> group = new ButtonGroup<>();

                        for (Category cat : Category.all) {
                            Seq<Block> blocks = getNormalUnlockedBlocksByCategory(cat);
                            categoryEmpty[cat.ordinal()] = blocks.isEmpty();
                        }

                        boolean needsAssign = satelliteMode ? false : categoryEmpty[currentCategory.ordinal()];

                        int f = 0;
                        for (Category cat : getCategories()) {
                            if (f++ % 2 == 0)
                                categories.row();

                            if (categoryEmpty[cat.ordinal()]) {
                                categories.image(Styles.black6);
                                continue;
                            }

                            if (needsAssign) {
                                currentCategory = cat;
                                needsAssign = false;
                            }

                            categories.button(ui.getIcon(cat.name()), Styles.clearTogglei, () -> {
                                satelliteMode = false;
                                currentCategory = cat;
                                if (control.input.block != null) {
                                    control.input.block = getSelectedBlock(currentCategory);
                                }
                                rebuildCategory.run();
                            }).group(group).update(i -> i.setChecked(!satelliteMode && currentCategory == cat))
                                    .name("category-" + cat.name());
                        }

                        // 在所有普通分类之后，追加一个卫星建筑分区入口图标
                        if (!getSatelliteBlocks().isEmpty()) {
                            categories.row();
                            categories.button(Icon.planet, Styles.clearTogglei, () -> {
                                satelliteMode = true;
                                rebuildCategory.run();
                            }).update(i -> i.setChecked(satelliteMode)).name("category-satellite");
                        }
                    }).fillY().bottom().touchable(Touchable.enabled);
                }

                mainStack.add(blockCatTable);

                rebuildCategory.run();
                frame.update(() -> {
                    if (!control.input.commandMode && gridUpdate(control.input)) {
                        rebuildCategory.run();
                    }
                });
            });
        });
    }

    @Nullable
    String getUnplaceableReason(Block block) {
        if (block == null)
            return null;
        if (!player.isBuilder())
            return "@unit.nobuild";
        if (state.isEditor())
            return null;
        if (!block.supportsEnv(state.rules.env))
            return "@unsupported.environment";
        if (block.isBanned())
            return "@banned";
        if (block.isOverPlacementLimit(player.team()))
            return Core.bundle.format("block.limit", state.rules.blockLimits.get(block));
        return null;
    }

    Seq<Category> getCategories() {
        return returnCatArray.clear().addAll(Category.all)
                .sort((c1, c2) -> Boolean.compare(categoryEmpty[c1.ordinal()], categoryEmpty[c2.ordinal()]));
    }

    /** 返回所有卫星专属建筑，忽略它们原本的 Category。 */
    Seq<Block> getSatelliteBlocks() {
        return returnArray.selectFrom(content.blocks(),
                block -> block.buildVisibility == CBuildVisibility.satelliteOnly && block.isVisible()
                        && unlocked(block))
                .sort((b1, b2) -> Boolean.compare(!b1.isPlaceable(), !b2.isPlaceable()));
    }

    /** 返回指定普通分类的建筑（不含卫星建筑）。 */
    Seq<Block> getNormalBlocksByCategory(Category cat) {
        return returnArray2.selectFrom(content.blocks(),
                block -> block.category == cat && block.isVisible() && block.environmentBuildable()
                        && block.buildVisibility != CBuildVisibility.satelliteOnly);
    }

    /** 返回指定普通分类已解锁的建筑（不含卫星建筑）。 */
    Seq<Block> getNormalUnlockedBlocksByCategory(Category cat) {
        return returnArray3.selectFrom(content.blocks(),
                block -> block.category == cat && block.isVisible() && unlocked(block)
                        && block.buildVisibility != CBuildVisibility.satelliteOnly)
                .sort((b1, b2) -> Boolean.compare(!b1.isPlaceable(), !b2.isPlaceable()));
    }

    /** 当前应显示的建筑列表：卫星模式下显示全部卫星建筑，否则显示当前普通分类的建筑。 */
    Seq<Block> getDisplayedBlocks() {
        return satelliteMode ? getSatelliteBlocks() : getNormalUnlockedBlocksByCategory(currentCategory);
    }

    Seq<Block> getByCategory(Category cat) {
        return satelliteMode ? getSatelliteBlocks() : getNormalBlocksByCategory(cat);
    }

    Seq<Block> getUnlockedByCategory(Category cat) {
        return satelliteMode ? getSatelliteBlocks() : getNormalUnlockedBlocksByCategory(cat);
    }

    Block getSelectedBlock(Category cat) {
        Block selected = selectedBlocks.get(cat);
        if (selected != null && getByCategory(cat).contains(selected)) {
            return selected;
        }
        Block fallback = getByCategory(cat).find(this::unlocked);
        selectedBlocks.put(cat, fallback);
        return fallback;
    }

    protected boolean unlocked(Block block) {
        boolean onSatellite = SatelliteManager.currentSatelliteId >= 0;
        boolean campaignSource = SatelliteManager.lastSector != null;

        // 只有在战役模式来源且确实位于卫星上时，才强制只显示已解锁建筑。
        // 非战役来源或不在卫星上时，不过滤解锁状态，允许沙盒/自定义游戏使用全部建筑。
        if (onSatellite && campaignSource) {
            if (!block.unlocked() || !block.placeablePlayer || !block.environmentBuildable() ||
                    !block.supportsEnv(state.rules.env))
                return false;
        } else {
            if (!block.placeablePlayer || !block.environmentBuildable() ||
                    !block.supportsEnv(state.rules.env))
                return false;
        }

        // 卫星地图：只显示属于绑定星球的建筑
        return SatelliteContentFilter.allowed(block);
    }

    boolean hasInfoBox() {
        hover = hovered();
        return control.input.block != null || menuHoverBlock != null || hover != null;
    }

    @Override
    public @Nullable Displayable hovered() {
        Vec2 v = topTable.stageToLocalCoordinates(Core.input.mouse());

        if (Core.scene.hasMouse(Core.input.mouseX(), Core.input.mouseY()) || topTable.hit(v.x, v.y, false) != null)
            return null;

        Unit unit = Units.closestOverlap(player.team(), Core.input.mouseWorldX(), Core.input.mouseWorldY(), 5f,
                u -> !u.isLocal() && u.displayable());
        if (unit != null)
            return unit;

        Tile hoverTile = world.tileWorld(Core.input.mouseWorld().x, Core.input.mouseWorld().y);
        if (hoverTile != null && hoverTile.inMapArea()) {
            if (hoverTile.build != null && hoverTile.build.displayable() && !hoverTile.build.inFogTo(player.team())
                    && hoverTile.build.inMapArea()) {
                return nextFlowBuild = hoverTile.build;
            }

            if ((hoverTile.drop() != null && hoverTile.block() == Blocks.air) || hoverTile.wallDrop() != null
                    || hoverTile.floor().liquidDrop != null) {
                return hoverTile;
            }
        }

        return null;
    }
}
