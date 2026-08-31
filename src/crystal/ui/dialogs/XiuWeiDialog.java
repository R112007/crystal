package crystal.ui.dialogs;

import arc.Core;
import arc.graphics.Color;
import arc.math.Mathf;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Scaling;
import crystal.CVars;
import crystal.content.GongFas;
import crystal.core.FaBaoSystem;
import crystal.core.PlayerXiuWeiSystem;
import crystal.entities.units.UnitEnum.JingJie;
import crystal.magic.FaBao;
import crystal.type.GongFa;
import mindustry.gen.Tex;
import mindustry.ui.Bar;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;

/**
 * 修为面板 v2：分区卡片式布局。
 * 头部（修为/境界/道号）→ 灵力进度条 → 渡劫目标 → 功法 → 法宝槽位 → 神武槽位。
 * 法宝已实装：槽位内容从 FaBaoSystem 读取（图标 + 名字 + 持有数）。
 * 神武仍为预留位：内容从 CVars.shenwuHave 读取，系统实装后只需往里填数据。
 */
public class XiuWeiDialog extends BaseDialog {
  /** 法宝/神武各预留的槽位数 */
  private static final int FABAO_SLOTS = 4;
  private static final int SHENWU_SLOTS = 4;
  /** 内容区宽度（决定文本换行宽度） */
  private static final float CONTENT_WIDTH = 460f;
  private static final Color SECTION_COLOR = Color.gold;
  private static final Color EMPTY_COLOR = Color.valueOf("555577");

  public XiuWeiDialog() {
    super("", Styles.fullDialog);
    shown(this::rebuild);
    addCloseButton();
  }

  private void rebuild() {
    cont.clear();
    cont.pane(t -> {
      t.defaults().pad(4f);
      buildHeader(t);
      buildPowerBar(t);
      buildDuJie(t);
      buildGongFa(t);
      buildFaBaoSlots(t);
      buildSlots(t, Core.bundle.get("shenwu", "神武"), CVars.shenwuHave, SHENWU_SLOTS,
          Core.bundle.get("shenwu.empty", "尚未炼化任何神武"));
    }).growX().width(CONTENT_WIDTH + 20f);
  }

  /** 头部：修为标签 + 大号境界名（随修为档位变色）+ 道号 */
  private void buildHeader(Table t) {
    JingJie cur = CVars.playerJingJie == null ? JingJie.fan : CVars.playerJingJie;
    Color rankColor = CVars.playerXiuWei == null ? Color.white : CVars.playerXiuWei.color;

    t.table(Styles.black6, head -> {
      head.add(Core.bundle.get("stat.xiuwei") + " · " +
          (CVars.playerXiuWei == null ? "" : CVars.playerXiuWei.str))
          .color(rankColor).padTop(4f);
      head.row();
      head.add(cur.str).color(rankColor).get().setFontScale(2.2f);
      head.row();
      if (CVars.playerName != null && !CVars.playerName.isEmpty()) {
        head.add(Core.bundle.get("daohao", "道号") + "：" + CVars.playerName)
            .color(Color.lightGray).padBottom(4f);
      }
    }).growX().padBottom(6f);
    t.row();
  }

  /** 灵力进度条：当前境界到下一境界的进度，颜色取修为档位色 */
  private void buildPowerBar(Table t) {
    JingJie cur = CVars.playerJingJie == null ? JingJie.fan : CVars.playerJingJie;
    JingJie next = PlayerXiuWeiSystem.getNextJingJie();
    float from = cur.amount;
    float to = next.amount;
    boolean maxed = to <= from;

    t.table(Styles.black6, p -> {
      Bar bar = new Bar(
          () -> String.format("%.1f", CVars.playerMagicPower) + " / " + (maxed ? "MAX" : String.valueOf((long) to)),
          () -> (CVars.playerXiuWei == null ? Color.white : CVars.playerXiuWei.color)
              .cpy().lerp(Color.white, 0.2f),
          () -> maxed ? 1f : Mathf.clamp((CVars.playerMagicPower - from) / (to - from)));
      bar.blink(Color.white);
      p.add(bar).growX().height(46f).pad(6f);
      p.row();
      p.add(Core.bundle.get("nextjingjie") + "：" + next.str + (maxed ? "（MAX）" : ""))
          .color(Color.lightGray).padBottom(4f);
    }).growX().padBottom(6f);
    t.row();
  }

  /** 渡劫目标：渡劫中显示当前目标（猩红），否则显示下一境界目标（金）；神途渡劫附带实时击杀进度 */
  private void buildDuJie(Table t) {
    String text = null;
    Color color = Color.gold;

    if (CVars.isInDuJie && CVars.pendingDuJieJingJie != null
        && CVars.pendingDuJieJingJie.duJieCondition != null) {
      text = Core.bundle.get("dujie.ing", "[渡] 当前目标") + "："
          + CVars.pendingDuJieJingJie.duJieCondition.str;
      color = Color.scarlet;
    } else {
      JingJie next = PlayerXiuWeiSystem.getNextJingJie();
      if (next.needDuJie && next.duJieCondition != null) {
        text = Core.bundle.get("dujie.next", "[渡] 下一境界目标") + "：" + next.duJieCondition.str;
      }
    }
    if (text == null)
      return;

    String ftext = text;
    Color fcolor = color;
    t.table(Styles.black6, d -> {
      d.add(ftext).color(fcolor).wrap().width(CONTENT_WIDTH - 20f).pad(6f);
      // 神途渡劫：实时击杀进度（条件为"击杀100个敌方单位"）
      if (CVars.isInDuJie && CVars.pendingDuJieJingJie == JingJie.shentu) {
        d.row();
        d.add(Core.bundle.get("dujie.kills", "击杀进度") + "："
            + PlayerXiuWeiSystem.getDuJieKillCount() + "/100")
            .color(Color.lightGray).padBottom(4f);
      }
    }).growX().padBottom(6f);
    t.row();
  }

  /** 功法区：图标卡片流式排列（无功法 sentinel "none" 不展示） */
  private void buildGongFa(Table t) {
    sectionTitle(t, Core.bundle.get("gongfahave"));

    Seq<GongFa> list = CVars.gongfaHave.toSeq().select(g -> g != GongFas.none).sort(g -> g.id);
    t.table(Styles.black6, g -> {
      if (list.isEmpty()) {
        g.add(Core.bundle.get("gongfa.empty", "尚未习得功法")).color(EMPTY_COLOR).pad(8f);
        return;
      }
      int i = 0;
      for (GongFa gf : list) {
        g.table(Tex.button, card -> {
          card.image(gf.uiIcon()).size(30f).scaling(Scaling.fit);
          card.add(gf.localizedName).padLeft(5f);
        }).pad(3f);
        if (++i % 2 == 0)
          g.row();
      }
    }).growX().padBottom(6f);
    t.row();
  }

  /** 法宝槽位区：读 FaBaoSystem 真实持有数，竖排卡片（图标在上，名字/数量在下），3 列排列，空槽 "?" */
  private void buildFaBaoSlots(Table t) {
    sectionTitle(t, Core.bundle.get("fabao", "法宝"));

    Seq<FaBao> owned = FaBaoSystem.owned();
    int total = Math.max(FABAO_SLOTS, owned.size);
    t.table(Styles.black6, s -> {
      for (int i = 0; i < total; i++) {
        int idx = i;
        s.table(Tex.button, slot -> {
          if (idx < owned.size) {
            FaBao f = owned.get(idx);
            slot.image(f.uiIcon()).size(40f).scaling(Scaling.fit)
                .color(f.usingFallbackIcon() ? f.rangeColor : Color.white);
            slot.row();
            slot.add(f.localizedName).get().setFontScale(0.9f);
            slot.row();
            slot.add("×" + FaBaoSystem.count(f)).color(Color.lightGray).get().setFontScale(0.8f);
          } else {
            slot.add("?").color(EMPTY_COLOR).get().setFontScale(1.6f);
          }
        }).size(136f, 108f).pad(4f);
        if ((i + 1) % 3 == 0)
          s.row();
      }
      if (owned.isEmpty()) {
        s.row();
        s.add(Core.bundle.get("fabao.empty", "尚无法宝（一次性消耗品，从右侧法宝栏拖出使用）"))
            .color(Color.darkGray).padBottom(4f);
      }
    }).growX().padBottom(6f);
    t.row();
  }

  /** 神武槽位区：固定槽位，有内容显示名字，空槽显示 "?" */
  private void buildSlots(Table t, String title, Seq<String> owned, int slots, String emptyTip) {
    sectionTitle(t, title);

    t.table(Styles.black6, s -> {
      for (int i = 0; i < slots; i++) {
        int idx = i;
        s.table(Tex.button, slot -> {
          if (idx < owned.size) {
            slot.add(owned.get(idx)).pad(8f);
          } else {
            slot.add("?").color(EMPTY_COLOR).pad(8f).get().setFontScale(1.6f);
          }
        }).size(76f).pad(4f);
      }
      s.row();
      if (owned.isEmpty()) {
        s.add(emptyTip).color(Color.darkGray).padBottom(4f);
      }
    }).growX().padBottom(6f);
    t.row();
  }

  private void sectionTitle(Table t, String text) {
    t.add("—— " + text + " ——").color(SECTION_COLOR).padTop(4f);
    t.row();
  }
}
