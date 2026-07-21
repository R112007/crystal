package crystal.core;

import arc.Core;
import arc.Events;
import arc.func.Boolp;
import arc.scene.style.TextureRegionDrawable;
import crystal.ui.gal.Branch;
import crystal.ui.gal.Character;
import crystal.ui.gal.CharacterActions;
import crystal.CVars;
import crystal.content.CUnits;
import crystal.content.GongFas;
import crystal.content.LxMaps;
import crystal.entities.SwordLight;
import crystal.entities.units.UnitEnum.JingJie;
import crystal.game.MultiSectorWaveTrigger;
import crystal.game.CEventType.GongFaBuQuanEvent;
import crystal.game.MultiSectorWaveTrigger.SectorWatchTask;
import crystal.ui.gal.DialogueLine;
import crystal.ui.gal.DialogueModule;
import crystal.ui.gal.Expression;
import crystal.ui.gal.GalgameDialogueManager;
import crystal.ui.gal.GalgameDialogueUI;
import crystal.ui.gal.Side;
import crystal.ui.gal.DialogueLine.DialogueOption;
import crystal.util.DLog;
import mindustry.Vars;
import mindustry.core.World;
import mindustry.core.GameState.State;
import mindustry.entities.Units;
import mindustry.game.EventType.CoreChangeEvent;
import mindustry.game.EventType.SectorCaptureEvent;
import mindustry.game.EventType.SectorLaunchEvent;
import mindustry.gen.Icon;
import mindustry.gen.Unit;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Time;
import arc.util.Timer;

import static crystal.ui.gal.Expression.*;
import static crystal.ui.gal.Side.*;
import static mindustry.Vars.*;

public class Storys {
    public static final Storys inst = new Storys();
    public static GalgameDialogueManager mgr = GalgameDialogueManager.instance;
    public static GalgameDialogueUI ui = mgr.ui;
    // 角色
    public Character player;
    public Character yi;
    public Character background;
    public Character core;

    // 主线模块
    public DialogueModule
    // 降临点的
    jianglindian_lanuch, jianglindian_teach, jianglindian_gongfa, jianglindian_fatian, jianglindian_capture,
            // 晶流断峡的
            jingliuduanxia_launch;
    // 分支
    public Branch jianglindian_gongfa_1, jianglindian_gongfa_2;

    public void init() {
        initCharacters();
        initModels();
        initBranches();
        registerModules();
        registerEvents();
    }

    public void registerEvents() {
        Events.on(SectorLaunchEvent.class, e -> {
            if (e.sector.id == LxMaps.jianglindian.sector.id) {
                Time.runTask(300f, () -> {
                    if (GalgameDialogueManager.instance != null) {
                        GalgameDialogueManager.instance.playModule(jianglindian_lanuch);
                    }
                });
            } else if (e.sector.id == LxMaps.jingliuduanxia.sector.id) {
                Time.runTask(300f, () -> {
                    if (GalgameDialogueManager.instance != null) {
                        GalgameDialogueManager.instance.playModule(jingliuduanxia_launch);
                    }
                });
            }
        });
        MultiSectorWaveTrigger.get().addTask(
                new SectorWatchTask("jianglindian-events", LxMaps.jianglindian)
                        // 参数：最小波数、最多剩余敌人、是否只执行一次、执行逻辑
                        .addRule(2, true, (sector, wave) -> {
                            DLog.info("intro监听启动");
                            mgr.playModule(jianglindian_teach);
                        })
                        .addRule(17, true, (sector, wave) -> {
                            mgr.playModule("jianglindian_fatian");
                        }));
        Events.on(GongFaBuQuanEvent.class, e -> {
            if (e.jingJie == JingJie.kaiqiao) {
                mgr.playModule("jianglindian_gongfa");
            }
        });
        Events.on(SectorCaptureEvent.class, e -> {
            if (e.sector.id == LxMaps.jianglindian.sector.id) {
                DLog.info("降临点已占领，播放占领剧情");
                Time.runTask(60f, () -> {
                    if (mgr != null) {
                        mgr.playModule("jianglindian_capture");
                    }
                });
            }
        });
    }

    // 初始化角色
    private void initCharacters() {
        player = new Character("player", CVars.playerName);
        yi = new Character("yi", Core.bundle.get("yi"));
        background = new Character("background", "");
        player.addExpression(Expression.normal, Icon.players);
        yi.addExpression(Expression.normal, Core.atlas.getDrawable("crystal-yi-normal"));
        yi.addExpression(Expression.angry, Core.atlas.getDrawable("crystal-yi-angry"));
        yi.addExpression(Expression.happy, Core.atlas.getDrawable("crystal-yi-happy"));
        yi.addExpression(Expression.relax, Core.atlas.getDrawable("crystal-yi-relax"));
        yi.addExpression(Expression.hit, Core.atlas.getDrawable("crystal-yi-hit"));
        yi.addExpression(Expression.cry, Core.atlas.getDrawable("crystal-yi-cry"));
        yi.addExpression(Expression.wuyu, Core.atlas.getDrawable("crystal-yi-wuyu"));
        yi.addExpression(Expression.soft, Core.atlas.getDrawable("crystal-yi-soft"));
        yi.addExpression(Expression.expected, Core.atlas.getDrawable("crystal-yi-expected"));
        yi.addExpression(Expression.surprise, Core.atlas.getDrawable("crystal-yi-surprise"));
        yi.addExpression(Expression.happy, Core.atlas.getDrawable("crystal-yi-happy"));
        yi.addExpression(Expression.shy, Core.atlas.getDrawable("crystal-yi-shy"));
        yi.addExpression(Expression.abashed, Core.atlas.getDrawable("crystal-yi-abashed"));
        background.addExpression(Expression.normal, Core.atlas.getDrawable("crystal-background"));
        core = new Character("core", Core.bundle.get("character.core")) {
            @Override
            public void init() {
                Events.on(CoreChangeEvent.class, e -> {
                    if (Vars.state.isCampaign()) {
                        Time.runTask(8f, () -> {
                            CoreBuild cor = Vars.player.team().data().cores.first();
                            for (var c : Vars.player.team().data().cores) {
                                if (c.health > cor.health)
                                    cor = c;
                            }
                            addExpression(Expression.normal, new TextureRegionDrawable(cor.block.fullIcon));
                        });
                    }
                });
            }
        };
    }

    public void initModels() {
        jianglindian_lanuch = new DialogueModule("jianglindian_lanuch", Core.bundle.get("gal.jianglindian_lanuch"))
                .addNode(new Seq<>(new DialogueLine[] {
                        new DialogueLine(background, normal, "@jianglindian_lanuch-1"), // 科技文明与修仙文明，不过是宇宙在漫长遗忘中，偶然翻涌起的两朵浪花。
                        new DialogueLine(background, normal, "@jianglindian_lanuch-2"), // 科技文明在无尽的征服与建造中，试图用物质的堆积来唤醒那沉睡的完整
                        new DialogueLine(background, normal, "@jianglindian_lanuch-3"), // 他们造出能撕裂维度的战舰，造出能演算过去未来的机器，造出能覆盖整个星系的网络。
                        new DialogueLine(background, normal, "@jianglindian_lanuch-4"), // 他们以为，只要收集足够多的碎片，就能拼出造物主最初的模样。
                        new DialogueLine(background, normal, "@jianglindian_lanuch-5"), // 而修行文明将万物融归于混沌的源头，以感悟为丝线重新编织，借因果作经纬，织就名为“修行”的无尽长卷
                        new DialogueLine(background, normal, "@jianglindian_lanuch-6"), // 他们闭关千年，在识海深处打捞沉没的往昔；他们历劫万载，在生死轮回中擦拭被尘埃蒙蔽的本真。
                        new DialogueLine(background, normal, "@jianglindian_lanuch-7"), // 他们御风而行，不是征服风，而是记起自己曾是风；他们移山填海，不是命令山，而是记起自己曾是山。
                        new DialogueLine(background, normal, "@jianglindian_lanuch-8"), // 他们只相信内证，相信只要足够安静，足够纯粹，就能听见那来自造物主的古老回响。
                        new DialogueLine(background, normal, "@jianglindian_lanuch-9"), // 科技文明称造物主为规律，修行文明称造物主为大道
                        new DialogueLine(background, normal, "@jianglindian_lanuch-10"), // 随着宇宙变迁，两种截然不同的文明也开始了碰撞
                        new DialogueLine(background, normal, "@jianglindian_lanuch-11"), // 孰弱孰强，无法直接判断，科技文明称他们的科技结晶埋葬在了超古代，修行文明称他们的大道文明在衰退，现今的人早已没有几个纪元前的古人强大
                        new DialogueLine(background, normal, "@jianglindian_lanuch-12"), // 两者维持着微妙的平衡，有合作，有交易，也有对峙
                        new DialogueLine(background, normal, "@jianglindian_lanuch-13"), // ……
                        new DialogueLine(background, normal, "@jianglindian_lanuch-14"), // 曾有大能企图融合两者，成就无上大道，可惜引来了道劫，最终身死道消
                        new DialogueLine(background, normal, "@jianglindian_lanuch-15"), // 从此无人再敢尝试
                        new DialogueLine(background, normal, "@jianglindian_lanuch-16"), // 那道劫并未因大能的陨落而彻底消散。
                        new DialogueLine(background, normal, "@jianglindian_lanuch-17"), // 它像一滴浓墨坠入清水，在两种文明的交界处晕染开来，科技与修行开始相互渗透，破灭，融合
                        new DialogueLine(background, normal, "@jianglindian_lanuch-18"), // 由此诞生出异种——万界魇铸体，这种科技与修行初步融合的产物在两个文明世界中展现出强大的实力，几乎毁灭了两个文明
                        new DialogueLine(background, normal, "@jianglindian_lanuch-19"), // 但是这种异种并没有完全毁灭文明，它们要的不是毁灭，似乎是统治与征服……
                        new DialogueLine(yi, normal, "@jianglindian_lanuch-20"), // 咳咳，这就是这个世界之前的故事，怎么样，想起来什么没？
                        new DialogueLine(player, normal, "@jianglindian_lanuch-21"), // 没有…
                        new DialogueLine(yi, normal, "@jianglindian_lanuch-22"), // 额，没事没事，慢慢想，总会想起来的，反正有时间
                        new DialogueLine(yi, normal, "@jianglindian_lanuch-23"), // 讲了那么久，还没自我介绍呢，我叫安亦雨，你呢
                        new DialogueLine(player, normal,
                                Core.bundle.format("jianglindian_lanuch-24", player.getName())), // 我，应该叫{0}吧
                        new DialogueLine(yi, surprise, Core.bundle.format("jianglindian_lanuch-25", player.getName())), // 那{0}，我们先离开这里吧，这里可不太平。要不是在这矿洞里，我俩早被"净墟"碾成渣了。
                        new DialogueLine(player, confused, "@jianglindian_lanuch-26"), // 净墟…是什么？
                        new DialogueLine(yi, normal, "@jianglindian_lanuch-27"), // 它们是附近的一股小魇铸体势力，他们自我标榜净化世界的废墟，但是手段狠辣，以屠戮生物为乐
                        new DialogueLine(player, normal, "@jianglindian_lanuch-28"), // 那我们现在跑路吗？
                        new DialogueLine(yi, confused, "@jianglindian_lanuch-29"), // 嗯…等等，你身上的气息有点奇怪，似乎混杂着一些其他东西
                        new DialogueLine(yi, normal, "@jianglindian_lanuch-30"), // (释放出一点灵力触碰那股异样的气息)咦？
                        new DialogueLine(background, normal,
                                Core.bundle.format("jianglindian_lanuch-31", player.getName())), // 那股气息接触到灵力后开始暴涨，从{0}身上散发出几十条光束牵引着周围的金属，然后…金属融合变成了一个看似精密的机械
                        new DialogueLine(yi, surprise, "@jianglindian_lanuch-32"), // 什么？！科技核心？(退后半步，倒吸一口凉气)你到底是什么来头？
                        new DialogueLine(player, normal, "@jianglindian_lanuch-33"), // 怎么了？这个东西是什么不详之物吗？
                        new DialogueLine(yi, normal, "@jianglindian_lanuch-34"), // 你身上有着修行文明的气息，却又诞生了科技核心，这明明是魇铸体的特征，从来没有一个人族同时拥有这两种力量，就算是科技文明的人也只是使用外部的科技而不会拥有科技核心。
                        new DialogueLine(player, normal, "@jianglindian_lanuch-35"), // 但我是纯正的人族吧
                        new DialogueLine(yi, normal, "@jianglindian_lanuch-36"), // 你确实是，但也从来没有过你这样的人族
                        new DialogueLine(background, normal, "@jianglindian_lanuch-37").beforePlay(() -> {
                            if (Vars.state.isPaused()) {
                                Vars.state.set(State.playing);
                            }
                            control.input.logicCutscene = true;
                            control.input.logicCamPan.set(World.unconv(120), World.unconv(115));
                            control.input.logicCutsceneZoom = 0.2f;
                            control.input.logicCamSpeed = 0.8f;
                            CUnits.chujia1.spawn(Vars.state.rules.waveTeam, 125f * 8, 117f * 8, 180f);
                        }), // 嘀嘀，发现人族！
                        new DialogueLine(yi, surprise, "@jianglindian_lanuch-38"),
                        // 什么？！净墟居然找到这里了(灵力化剑)
                        new DialogueLine(background, normal, "@jianglindian_lanuch-39")
                                .onComplete(() -> {
                                    Unit unit = Units.closest(Vars.state.rules.waveTeam, Vars.player.x, Vars.player.y,
                                            b -> true);
                                    if (unit == null)
                                        return;
                                    forceRun(() -> {
                                        SwordLight.create(Vars.player.team(), unit.x, unit.y, 500, 120, 15);
                                        if (unit.dead)
                                            return true;
                                        else
                                            return false;

                                    });
                                    control.input.logicCutscene = false;
                                }),
                        // 那个机甲发射了几枚子弹，但随着剑光一闪，子弹被弹开，机甲核心已被洞穿
                        new DialogueLine(yi, normal, "@jianglindian_lanuch-40"), // (甩了甩剑上的机油，目光扫过机甲残骸)还好这个比较弱，咦？
                        new DialogueLine(background, wuyu,
                                Core.bundle.format("jianglindian_lanuch-41", player.getName())), // 几缕光点从被击毁的核心中飞出，融入了科技核心，随后安亦雨感知到{0}的气息似乎变强了一点
                        new DialogueLine(yi, normal, "@jianglindian_lanuch-42"), // 你能吸收它们的能量来增加修为？不对，现在不是说这个的时候，我们快走吧
                        new DialogueLine(player, normal, "@jianglindian_lanuch-43"), // 嗯，先离开这里再说
                        new DialogueLine(background, normal, "@jianglindian_lanuch-44"), // （远处传来金属摩擦地面的刺耳声响）
                        new DialogueLine(yi, normal, "@jianglindian_lanuch-45"), // 不好！那些魇铸体肯定是被刚才的动静吸引过来的，它们已经锁定这里了，而且它们擅长群体作战，还有高等级的机甲，我目前无法抗衡，只能试试突围了。
                        new DialogueLine(player, normal, "@jianglindian_lanuch-46"), // 等等，我应该有办法，”它”告诉我，该如何使用"它"
                        new DialogueLine(yi, confused, "@jianglindian_lanuch-47"), // 什么？"它"是谁
                        new DialogueLine(player, normal, "@jianglindian_lanuch-48"), // "它"就是你说的科技核心，刚才"它"吸收了能量后似乎把说明书放在了我的脑子里，你看
                        new DialogueLine(player, normal, "@jianglindian_lanuch-49"), // （深吸一口气，手掌触地，一股奇异的吸力从掌心涌出，周围的废铁、矿石、甚至岩层中的金属元素都被抽离出来，融入了核心之中）
                        new DialogueLine(yi, surprise, "@jianglindian_lanuch-50"), // 竟然真的行…
                        new DialogueLine(player, normal,
                                Core.bundle.format("jianglindian_lanuch-51", player.getName())), // "它"告诉我科技文明的战斗方式不是逃跑，是建立据点、扩张、占领。你在我身后待着，不要乱跑。接下来就看我的表演吧
                        new DialogueLine(yi, normal, Core.bundle.format("jianglindian_lanuch-52", player.getName())) // 我…我知道了。注意安全，{0}。
                }));
        jianglindian_teach = new DialogueModule("jianglindian_teach", Core.bundle.get("gal.jianglindian_teach"))
                .addNode(
                        new DialogueLine(player, normal, yi, normal, Side.left, "@jianglindian_teach-1"),
                        new DialogueLine(player, normal, yi, normal, right, "@jianglindian_teach-2"),
                        new DialogueLine(player, normal, yi, normal, right, "@jianglindian_teach-3"),
                        new DialogueLine(player, normal, yi, normal, right, "@jianglindian_teach-4"),
                        new DialogueLine(player, normal, core, normal, left, "@jianglindian_teach-5"),
                        new DialogueLine(player, normal, core, normal, right, "@jianglindian_teach-6"),
                        new DialogueLine(player, normal, core, normal, left, "@jianglindian_teach-7"),
                        new DialogueLine(player, normal, core, normal, right, "@jianglindian_teach-8"),
                        new DialogueLine(player, normal, core, normal, left, "@jianglindian_teach-9"),
                        new DialogueLine(player, normal, core, normal, right, "@jianglindian_teach-10"),
                        new DialogueLine(player, normal, core, normal, Side.left, "@jianglindian_teach-11"));
        jianglindian_gongfa = new DialogueModule("jianglindian_gongfa",
                Core.bundle.get("gal.jianglindian_gongfa"))
                .addNode(new Seq<>(new DialogueLine[] {
                        new DialogueLine(yi, normal, "@jianglindian_gongfa-1"),
                        new DialogueLine(yi, normal, "@jianglindian_gongfa-2"),
                        new DialogueLine(player, expected, "@jianglindian_gongfa-3"),
                        new DialogueLine(yi, happy, "@jianglindian_gongfa-4",
                                new DialogueOption("@jianglindian_gongfa-branch1-1",
                                        opt -> {
                                            mgr.addBranch(jianglindian_gongfa_1);
                                        }),
                                new DialogueOption("@jianglindian_gongfa-branch2-1",
                                        opt -> {
                                            mgr.addBranch(jianglindian_gongfa_2);
                                        }))
                }));
        jianglindian_fatian = new DialogueModule("jianglindian_fatian",
                Core.bundle.get("gal.jianglindian_fatian"))
                .addNode(new Seq<>(new DialogueLine[] {
                        new DialogueLine(yi, normal,
                                Core.bundle.format("jianglindian_fatian-1",
                                        player.getName())),
                        new DialogueLine(player, confused, "@jianglindian_fatian-2"),
                        new DialogueLine(yi, normal, "@jianglindian_fatian-3"),
                        new DialogueLine(player, cautious, "@jianglindian_fatian-4"),
                        new DialogueLine(yi, relax, "@jianglindian_fatian-5"),
                        new DialogueLine(player, normal, "@jianglindian_fatian-6"),
                        new DialogueLine(yi, happy, "@jianglindian_fatian-7"),
                        new DialogueLine(yi, relax, "@jianglindian_fatian-8"),
                        new DialogueLine(player, normal, "@jianglindian_fatian-9")
                }));
        jianglindian_capture = new DialogueModule("jianglindian_capture", Core.bundle.get("gal.jianglindian_capture"))
                .addNode(new Seq<>(new DialogueLine[] {
                        new DialogueLine(yi, happy, Core.bundle.format("jianglindian_capture-1", player.getName())),
                        new DialogueLine(player, normal, "@jianglindian_capture-2"),
                        new DialogueLine(player, angry, "@jianglindian_capture-3"),
                        new DialogueLine(yi, normal, "@jianglindian_capture-4"),
                        new DialogueLine(player, angry, "@jianglindian_capture-5"),
                        new DialogueLine(yi, normal, "@jianglindian_capture-6"),
                        new DialogueLine(yi, normal, "@jianglindian_capture-7"),
                        new DialogueLine(yi, normal, "@jianglindian_capture-8"),
                        new DialogueLine(player, normal, "@jianglindian_capture-9"),
                        new DialogueLine(yi, happy, "@jianglindian_capture-10"),
                        new DialogueLine(yi, normal, "@jianglindian_capture-11"),
                        new DialogueLine(player, normal, "@jianglindian_capture-12"),
                        new DialogueLine(yi, happy, "@jianglindian_capture-13")
                }));
        jingliuduanxia_launch = new DialogueModule("jingliuduanxia",
                Core.bundle.get("sector.crystal-jingliuduanxia.name"))
                .addNode(new Seq<>(new DialogueLine[] {
                        new DialogueLine(yi, normal, Core.bundle.format("jingliuduanxia_launch-1", player.getName())), // {0},穿过这边，再走一段路就能到了，路上可能会遇到净墟的单位，要随时准备好战斗
                        new DialogueLine(player, normal, "@jingliuduanxia_launch-2"), // ……它们已经在前面了
                        new DialogueLine(yi, wuyu, Core.bundle.format("jingliuduanxia_launch-3", player.getName())), // 这么倒霉吗？居然是陆空混合编队，{0}你要注意它们的空军
                        new DialogueLine(yi, normal, "@jingliuduanxia_launch-4"), // 它们在遭受一定的攻击后会有一些变化
                        new DialogueLine(player, normal, "@jingliuduanxia_launch-5") // 明白了，你退后，我要攻击了
                }));
    }

    public void registerModules() {
        GalgameDialogueManager.instance.modules.addAll(jianglindian_lanuch, jianglindian_teach,
                jianglindian_gongfa, jianglindian_fatian, jianglindian_capture, jingliuduanxia_launch);
        for (DialogueModule module : mgr.modules) {
            if (module != null)
                module.loadModuleData();
        }
        GalgameDialogueManager.instance.loadWaitingQueue();
        Core.app.post(GalgameDialogueManager.instance::processWaitingQueue);
    }

    public void initBranches() {
        jianglindian_gongfa_1 = new Branch("jianglindian_gongfa_1")
                .addNode(new DialogueLine(player, happy, "@jianglindian_gongfa-branch1-1"))
                .addNode(new DialogueLine(yi, shy, "@jianglindian_gongfa-branch1-2").onComplete(() -> {
                    Affection.affection.increaseAffection(1);
                }))
                .addNode(new DialogueLine(yi, shy, "@jianglindian_gongfa-branch1-3")
                        .withSpriteAction(() -> {
                            CharacterActions.shySteam(ui);
                        }))
                .addNode(new DialogueLine(yi, shy, "@jianglindian_gongfa-branch1-4")
                        .onComplete(() -> GongFas.taiXuanTianGong1.unlock()));

        jianglindian_gongfa_2 = new Branch("jianglindian_gongfa_2")
                .addNode(new DialogueLine(player, happy, "@jianglindian_gongfa-branch2-1"))
                .addNode(new DialogueLine(yi, shy, "@jianglindian_gongfa-branch2-2").onComplete(() -> {
                    Affection.affection.increaseAffection(2);
                }))
                .addNode(new DialogueLine(yi, abashed, "@jianglindian_gongfa-branch2-3")
                        .withSpriteAction(() -> {
                            DLog.info("选项2shake");
                            CharacterActions.shySteam(ui);
                        }))
                .addNode(new DialogueLine(yi, angry, "@jianglindian_gongfa-branch2-4")
                        .onComplete(() -> GongFas.taiXuanTianGong1.unlock()));
    }

    public static void forceRun(Boolp boolp) {
        Timer.schedule(new Timer.Task() {
            public void run() {
                try {
                    if (boolp.get()) {
                        this.cancel();
                    }
                } catch (final Throwable e) {
                    Log.err(e);
                    this.cancel();
                }
            }
        }, 0.0f, 0.5f, -1);
    }
}
