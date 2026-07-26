package crystal.core;

import arc.Core;
import arc.Events;
import arc.math.Mathf;
import arc.struct.Seq;
import crystal.Crystal;
import crystal.aviation.CrystalAviationMod;
import crystal.aviation.ui.SatelliteDebugDialog;
import crystal.content.WorldStuffs;
import crystal.entities.SwordLight;
import crystal.graphics.BlackHoleRenderer;
import crystal.type.Contributor;
import crystal.ui.dialogs.CPausedDialog;
import crystal.ui.dialogs.CPlanetDialog;
import crystal.ui.dialogs.CResearchDialog;
import crystal.ui.dialogs.GongFaDialog;
import crystal.ui.dialogs.MagicWaveDialog;
import crystal.ui.dialogs.RelativeDialog;
import crystal.ui.dialogs.WorldStuffDialog;
import crystal.ui.gal.GalgameDialogueManager;
import mindustry.Vars;
import mindustry.game.EventType.TapEvent;
import mindustry.gen.Icon;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;

import static mindustry.Vars.*;
import static crystal.CVars.debug;

public class UI {
  public CResearchDialog cresearch;
  public CPlanetDialog cplanet;
  public CPausedDialog cpause;
  public WorldStuffDialog stuff;
  public GongFaDialog gongFa;
  float height = 100;
  public BaseDialog generalMagicDialog;
  public RelativeDialog relativeDialog;

  public void init() {
    WorldStuffs.load();
    cresearch = new CResearchDialog();
    cplanet = new CPlanetDialog();
    cpause = new CPausedDialog();
    stuff = new WorldStuffDialog();
    gongFa = new GongFaDialog();

    setupGeneralMagicDialog();
    setupRelativeDialog();

    Vars.ui.paused.shown(() -> {
      Vars.ui.paused.cont.row();
      Vars.ui.paused.cont.button(
          Core.bundle.get("showgongfas"),
          Icon.bookOpen,
          gongFa::show).size(Vars.mobile ? 130f : 220f, Vars.mobile ? 130f : 55f).pad(5f).colspan(2);
      Vars.ui.paused.cont.button(
          "历史记录",
          Icon.book, () -> {
            GalgameDialogueManager.instance.historyUI.show();
          }).size(Vars.mobile ? 130f : 220f, Vars.mobile ? 130f : 55f)
          .pad(5f).colspan(20);
    });

    if (debug)
      Vars.ui.hudGroup.fill(null, table -> {
        table.table(null, t -> {
          t.button("调试面板", Styles.flatt, () -> {
            GalgameDialogueManager.instance.debugDialog.show();
          }).size(100, 70);
        }).size(100, 70);
        table.center().left().update(() -> {
          table.translation.set(100, height);
        });
      });
    if (CrystalAviationMod.allow)
      Vars.ui.hudGroup.fill(null, table -> {
        table.table(null, t -> {
          t.button("调试星球", Styles.flatt, () -> {
            new SatelliteDebugDialog().show();
          }).size(100, 70);
        }).size(100, 70);
        table.center().left().update(() -> {
          table.translation.set(100, height + 150);
        });
      });
    if (debug)
      Vars.ui.hudGroup.fill(null, table -> {
        table.table(null, t -> {
          t.button("修为调试面板", Styles.flatt, () -> {
            new MagicWaveDialog().show();
          }).size(100, 70);
        }).size(100, 70);
        table.center().left().update(() -> {
          table.translation.set(0, height + 260);
        });
      });
    if (debug)
      Vars.ui.hudGroup.fill(null, table -> {
        table.table(null, t -> {
          t.button("对话", () -> {
            GalgameDialogueManager.instance.getModule("main").resetProgress();
            GalgameDialogueManager.instance.playModule("main");
          }).size(100, 70);
        }).size(100, 70);
        table.center().left().update(() -> {
          table.translation.set(0, height);
        });
      });
  }

  void setupGeneralMagicDialog() {
    generalMagicDialog = new BaseDialog(Core.bundle.get("generalMagic", "综合术法"));
    generalMagicDialog.cont.defaults().size(Vars.mobile ? 130f : 220f, Vars.mobile ? 55f : 55f).pad(12f);

    generalMagicDialog.cont.button(Core.bundle.get("showgongfas", "功法"), Icon.bookOpen, () -> {
      generalMagicDialog.hide();
      gongFa.show();
    }).row();

    generalMagicDialog.cont.button(Core.bundle.get("showmemory", "回忆"), Icon.bookOpen, () -> {
      generalMagicDialog.hide();
      GalgameDialogueManager.instance.openModuleGallery();
    }).row();

    generalMagicDialog.addCloseButton();

    Vars.ui.menufrag.addButton(Core.bundle.get("generalMagic", "综合术法"), Icon.book, () -> generalMagicDialog.show());
  }

  void setupRelativeDialog() {
    relativeDialog = new RelativeDialog(Core.bundle.get("contributors", "制作组"));

    Contributor root = new Contributor(
        "R",
        "主创",
        true,
        "鸽了鸽了，臭写代码的",
        Core.atlas.find("crystal-r"), "瑟瑟的始发点");

    Seq<Contributor> others = Seq.with(
        new Contributor("白小惜", "贴图画师", false, "群主老婆，负责画画。", Core.atlas.find("crystal-bai"), "小惜老婆"),
        new Contributor("花开了吗", "贴图画师", false, "画画外加提供猎奇想法", Core.atlas.find("crystal-hua"), "合晶"),
        new Contributor("ZXS", "eve的父母", false, "eve真好用", Core.atlas.find("crystal-zxs"), "猫娘"),
        new Contributor("小凡", "bus", false, "天天戏弄群主的臭杂鱼~", Core.atlas.find("crystal-fan"), "小凡大人"),
        new Contributor("RC-C814", "测试伙伴", false, "群主好朋友，天天聊天，交换想法", Core.atlas.find("crystal-rc"), "望星"),
        new Contributor("CN方柠喵FNM ~喵", "可rua猫", false, "陪群主玩", Core.atlas.find("crystal-js"), "随便rua"),
        new Contributor("?!人人!?", "留有遗产", false, "我直接偷吃遗产（", Core.atlas.find("crystal-qin"), "高人红红~"));

    relativeDialog.setContributors(root, others);

    Vars.ui.menufrag.addButton("群主关系网", Icon.chat, () -> relativeDialog.show());
  }
}
