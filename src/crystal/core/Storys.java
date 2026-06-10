package crystal.core;

import arc.Core;
import arc.Events;
import crystal.ui.dialogs.GongFaDialog.GongFaUnlockEvent;
import crystal.ui.gal.Branch;
import crystal.ui.gal.Character;
import crystal.ui.gal.CharacterActions;
import crystal.CVars;
import crystal.content.GongFas;
import crystal.content.LxMaps;
import crystal.game.MultiSectorWaveTrigger;
import crystal.game.MultiSectorWaveTrigger.SectorWatchTask;
import crystal.ui.gal.DialogueLine;
import crystal.ui.gal.DialogueModule;
import crystal.ui.gal.Expression;
import crystal.ui.gal.GalgameDialogueManager;
import crystal.ui.gal.GalgameDialogueUI;
import crystal.ui.gal.DialogueLine.DialogueOption;
import crystal.util.DLog;
import mindustry.game.EventType.SectorCaptureEvent;
import mindustry.game.EventType.SectorLaunchEvent;
import mindustry.game.EventType.SectorLaunchLoadoutEvent;
import mindustry.gen.Icon;
import arc.struct.Seq;
import arc.util.Time;

import static crystal.ui.gal.Expression.*;

public class Storys {
  public static final Storys inst = new Storys();
  public static GalgameDialogueManager mgr = GalgameDialogueManager.instance;
  public static GalgameDialogueUI ui = mgr.ui;
  // 角色
  public Character player;
  public Character yi;
  public Character background;

  // 主线模块
  public DialogueModule mainStoryModule,
      // 降临点的
      jianglindian_lanuch, jianglindian_intro, jianglindian_gongfa, jianglindian_fatian, jianglindian_capture;
  // 分支
  public Branch easyModeBranch, hardModeBranch, liliStoryBranch,
      jianglindian_gongfa_1, jianglindian_gongfa_2;

  public void init() {
    initCharacters();
    initBranches();
    initModels();
    registerModules();
    registerEvents();
  }

  public void registerEvents() {
    Events.on(SectorLaunchEvent.class, e -> {
      if (e.sector.id == LxMaps.jianglindian.sector.id) {
        DLog.info("降临点发射监听启动");
        Time.runTask(300f, () -> {
          if (GalgameDialogueManager.instance != null) {
            GalgameDialogueManager.instance.playModule("jianglindian_lanuch");
          }
        });
      }
    });
    MultiSectorWaveTrigger.get().addTask(
        new SectorWatchTask("jianglindian-events", LxMaps.jianglindian)
            // 参数：最小波数、最多剩余敌人、是否只执行一次、执行逻辑
            .addRule(2, true, (sector, wave) -> {
              DLog.info("intro监听启动");
              mgr.playModule("jianglindian_intro");
            })
            .addWaveEnemyRule(9, 5, true, (sector, wave) -> {
              mgr.playModule("jianglindian_gongfa");
            })
            .addRule(17, true, (sector, wave) -> {
              mgr.playModule("jianglindian_fatian");
            }));
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
  }

  public void initModels() {
    mainStoryModule = new DialogueModule("main", "主线");
    mainStoryModule.addNode(
        new DialogueLine(yi, Expression.normal, "你终于醒了！这里是异星矿区，我是安亦雨。(点头)"),
        new DialogueLine(yi, Expression.normal, "(轻微颤抖)").withSpriteAction(() -> {
          CharacterActions.shake(ui);
        }),
        new DialogueLine(yi, Expression.normal, "(剧烈颤抖)").withSpriteAction(() -> {
          CharacterActions.shakeHard(ui);
        }),
        new DialogueLine(yi, Expression.normal, "(上下浮动)").withSpriteAction(() -> {
          CharacterActions.floatUpDown(ui);
        }),
        new DialogueLine(yi, Expression.normal, "(快速跑来)").withSpriteAction(() -> {
          CharacterActions.runIn(ui);
        }),
        new DialogueLine(yi, Expression.normal, "(快速跑开)").withSpriteAction(() -> {
          CharacterActions.runOut(ui);
        }),
        new DialogueLine(yi, Expression.normal, "(摔倒)").withSpriteAction(() -> {
          CharacterActions.fallDown(ui);
        }),
        new DialogueLine(yi, Expression.normal, "(被打后仰)").withSpriteAction(() -> {
          CharacterActions.hitBack(ui);
        }),
        new DialogueLine(yi, Expression.normal, "(跳一下)").withSpriteAction(() -> {
          CharacterActions.jump(ui);
        }),
        new DialogueLine(yi, Expression.normal, "(点头)").withSpriteAction(() -> {
          CharacterActions.nod(ui);
        }),
        new DialogueLine(yi, Expression.normal, "(摇头)").withSpriteAction(() -> {
          CharacterActions.shakeHead(ui);
        }),
        new DialogueLine(yi, Expression.normal, "(心跳缩放)").withSpriteAction(() -> {
          CharacterActions.heartbeat(ui);
        }),
        new DialogueLine(player, Expression.normal, "我…头好痛，这里是哪里？我什么都记不起来了。"),
        new DialogueLine(yi, Expression.abashed, "别慌，你只是坠机撞到了头部，记忆会慢慢恢复的。")
            .withEnterAction(() -> {
              CharacterActions.shySteam(ui);
            }),
        new DialogueLine(yi, Expression.normal, "现在我们有两条前进路线，你想选哪一条？"),
        new DialogueLine(player, Expression.normal, "我得好好选一下…",
            new DialogueOption("稳妥勘探路线（低风险）", option -> {
              mgr.addBranch(easyModeBranch);
            }),
            new DialogueOption("核心矿区路线（高风险）", option -> {
              mgr.addBranch(hardModeBranch);
            })));
    jianglindian_lanuch = new DialogueModule("jianglindian_lanuch", Core.bundle.get("gal.jianglindian_lanuch"))
        .addNode(new Seq<>(new DialogueLine[] {
            new DialogueLine(background, normal, "@jianglindian_lanuch-1"),
            new DialogueLine(background, normal, "@jianglindian_lanuch-2"),
            new DialogueLine(background, normal, "@jianglindian_lanuch-3"),
            new DialogueLine(background, normal, "@jianglindian_lanuch-4"),
            new DialogueLine(background, normal, "@jianglindian_lanuch-5"),
            new DialogueLine(yi, normal, "@jianglindian_lanuch-6"),
            new DialogueLine(player, hit, "@jianglindian_lanuch-7"),
            new DialogueLine(yi, relax, "@jianglindian_lanuch-8"),
            new DialogueLine(player, confused, "@jianglindian_lanuch-9"),
            new DialogueLine(yi, normal, "@jianglindian_lanuch-10"),
            new DialogueLine(player, confused, Core.bundle.format("jianglindian_lanuch-11", player.getName())),
            new DialogueLine(yi, normal, "@jianglindian_lanuch-12"),
            new DialogueLine(player, normal, "@jianglindian_lanuch-13"),
            new DialogueLine(yi, normal, "@jianglindian_lanuch-14"),
            new DialogueLine(player, normal, "@jianglindian_lanuch-15"),
            new DialogueLine(yi, normal, "@jianglindian_lanuch-16"),
            new DialogueLine(player, surprise, "@jianglindian_lanuch-17"),
            new DialogueLine(yi, angry, "@jianglindian_lanuch-18"),
            new DialogueLine(player, normal, "@jianglindian_lanuch-19"),
            new DialogueLine(yi, wuyu, "@jianglindian_lanuch-20"),
            new DialogueLine(player, normal, "@jianglindian_lanuch-21"),
            new DialogueLine(yi, normal, "@jianglindian_lanuch-22"),
            new DialogueLine(player, normal, "@jianglindian_lanuch-23"),
            new DialogueLine(yi, happy, "@jianglindian_lanuch-24"),
            new DialogueLine(yi, surprise, "@jianglindian_lanuch-25"),
            new DialogueLine(player, confused, "@jianglindian_lanuch-26"),
            new DialogueLine(yi, normal, "@jianglindian_lanuch-27"),
            new DialogueLine(player, normal, "@jianglindian_lanuch-28"),
            new DialogueLine(yi, normal, "@jianglindian_lanuch-29"),
            new DialogueLine(player, normal, "@jianglindian_lanuch-30"),
            new DialogueLine(yi, normal, "@jianglindian_lanuch-31"),
            new DialogueLine(player, confused, "@jianglindian_lanuch-32"),
            new DialogueLine(yi, normal, "@jianglindian_lanuch-33"),
            new DialogueLine(yi, normal, "@jianglindian_lanuch-34"),
            new DialogueLine(yi, normal, "@jianglindian_lanuch-35"),
            new DialogueLine(yi, normal, Core.bundle.format("jianglindian_lanuch-36", player.getName())),
            new DialogueLine(player, normal, "@jianglindian_lanuch-37"),
            new DialogueLine(yi, normal, "@jianglindian_lanuch-38"),
            new DialogueLine(player, normal, "@jianglindian_lanuch-39"),
            new DialogueLine(yi, normal, Core.bundle.format("jianglindian_lanuch-40", player.getName()))
        }));
    jianglindian_intro = new DialogueModule("jianglindian_intro",
        Core.bundle.get("gal.jianglindian_intro"))
        .addNode(new Seq<>(new DialogueLine[] {
            new DialogueLine(player, normal, "@jianglindian_intro-1"),
            new DialogueLine(yi, wuyu, "@jianglindian_intro-2"),
            new DialogueLine(player, normal, "@jianglindian_intro-3"),
            new DialogueLine(yi, normal, "@jianglindian_intro-4"),
            new DialogueLine(player, cautious, "@jianglindian_intro-5"),
            new DialogueLine(yi, wuyu, "@jianglindian_intro-6"),
            new DialogueLine(player, normal, "@jianglindian_intro-7"),
            new DialogueLine(yi, wuyu, "@jianglindian_intro-8"),
            new DialogueLine(yi, normal, "@jianglindian_intro-9"),
            new DialogueLine(yi, normal, "@jianglindian_intro-10"),
            new DialogueLine(yi, normal, "@jianglindian_intro-11"),
            new DialogueLine(player, normal, "@jianglindian_intro-12"),
            new DialogueLine(yi, relax, "@jianglindian_intro-13"),
            new DialogueLine(player, normal, "@jianglindian_intro-14"),
            new DialogueLine(yi, relax, "@jianglindian_intro-15"),
            new DialogueLine(player, wuyu, "@jianglindian_intro-16")
        }));
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
  }

  public void registerModules() {
    GalgameDialogueManager.instance.modules.addAll(mainStoryModule, jianglindian_lanuch, jianglindian_intro,
        jianglindian_gongfa, jianglindian_fatian, jianglindian_capture);
    for (DialogueModule module : mgr.modules) {
      if (module != null)
        module.loadModuleData();
    }
    GalgameDialogueManager.instance.loadWaitingQueue();
    Core.app.post(GalgameDialogueManager.instance::processWaitingQueue);
  }

  public void initBranches() {
    easyModeBranch = new Branch("easy-mode");
    easyModeBranch.addAll(Seq.with(
        new DialogueLine(yi, Expression.relax, "你选择了稳妥的勘探路线，我们先熟悉周边环境，风险会小很多。"),
        new DialogueLine(player, Expression.normal, "嗯，刚到这个星球，稳妥点总没错。"),
        new DialogueLine(yi, Expression.normal, "前面有个废弃的前哨站，我们先去那里补给休整，再继续前进。"),
        new DialogueLine(yi, Expression.wuyu, "说起来，这个前哨站还有段小故事呢。").onComplete(() -> {
          mgr.addBranch(liliStoryBranch);
        })));

    liliStoryBranch = new Branch("lili");
    liliStoryBranch.addAll(Seq.with(
        new DialogueLine(yi, Expression.relax, "这个前哨站是我和队友第一次登陆时搭建的，可惜后来出了意外。"),
        new DialogueLine(player, Expression.normal, "意外？发生了什么？"),
        new DialogueLine(yi, Expression.cry, "矿脉坍塌，他们没能出来…只有我一个人活了下来。"),
        new DialogueLine(player, Expression.normal, "对不起，我不该问的。"),
        new DialogueLine(yi, Expression.relax, "没事，都过去了。有你在，这次一定会不一样的。")));

    hardModeBranch = new Branch("hard");
    hardModeBranch.addAll(Seq.with(
        new DialogueLine(yi, Expression.angry, "你确定要直接闯核心矿区？这里的异化生物攻击性极强！"),
        new DialogueLine(player, Expression.normal, "放心，我有应对的底气，速战速决反而更安全。"),
        new DialogueLine(yi, Expression.hit, "真是犟不过你！要是出了危险，我可不会回头救你！"),
        new DialogueLine(yi, Expression.normal, "前面就是矿区入口，把武器准备好，我们必须一步都不能错。")));
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
}
