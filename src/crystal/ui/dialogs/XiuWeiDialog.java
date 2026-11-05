package crystal.ui.dialogs;

import crystal.core.CultivationState;
import arc.Core;
import arc.graphics.Color;
import arc.math.Mathf;
import arc.scene.ui.Button;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.Label;
import arc.scene.ui.layout.Scl;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Scaling;
import arc.util.Time;
import crystal.CVars;
import crystal.content.GongFas;
import crystal.core.CSettings;
import crystal.core.FaBaoSystem;
import crystal.core.PlayerXiuWeiSystem;
import crystal.core.ScrollSystem;
import crystal.core.ShenWuSystem;
import crystal.entities.units.UnitEnum.JingJie;
import crystal.entities.units.UnitEnum.XiuWei;
import crystal.magic.FaBao;
import crystal.magic.ShenWu;
import crystal.magic.Skill;
import crystal.magic.SkillUnlockable;
import crystal.type.GongFa;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.ui.Bar;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;

/**
 * 修为面板 v2：分区卡片式布局。
 * 头部（修为/境界/道号）→ 灵力进度条 → 渡劫目标 → 功法 → 法宝槽位 → 神武槽位。
 * 法宝已实装：槽位内容从 FaBaoSystem 读取（图标 + 名字 + 持有数）。
 * 神武仍为预留位：内容从 CultivationState.shenwuHave 读取，系统实装后只需往里填数据。
 */
public class XiuWeiDialog extends BaseDialog {
  /** 神武预留槽位数 */
  private static final int SHENWU_SLOTS = 4;
  /** 内容区宽度（决定文本换行宽度） */
  private static final float CONTENT_WIDTH = 460f;
  private static final Color SECTION_COLOR = Color.gold;
  private static final Color EMPTY_COLOR = Color.valueOf("555577");
  private float contentWidth = CONTENT_WIDTH;

  public XiuWeiDialog() {
    super("", Styles.fullDialog);
    shown(() -> {
      rebuild();
      toFront();
    });
    onResize(this::rebuild);
    addCloseButton();
  }

  private void rebuild() {
    float screenWidth = Core.graphics.getWidth() / Scl.scl(1f);
    float screenHeight = Core.graphics.getHeight() / Scl.scl(1f);
    contentWidth = Math.max(240f, Math.min(CONTENT_WIDTH, screenWidth - (Core.graphics.isPortrait() ? 28f : 72f)));

    cont.clear();
    cont.margin(8f);
    cont.pane(t -> {
      t.defaults().pad(4f);
      buildHeader(t);
      buildHudVisibility(t);
      buildPowerBar(t);
      buildRealmProgress(t);
      buildDuJie(t);
      buildGongFa(t);
      buildMagicSkills(t);
      buildShenWuSkills(t);
      buildScrollInventory(t);
      buildFaBaoSlots(t);
      buildShenWuSlots(t);
    }).grow()
        .width(contentWidth + 20f)
        .maxHeight(Math.max(260f, screenHeight - 150f))
        .scrollX(false);
  }

  /** The same checkboxes exposed in SettingsMenuDialog, kept close to gameplay. */
  private void buildHudVisibility(Table t) {
    sectionTitle(t, Core.bundle.get("hud.buttons", "界面按钮"));
    t.table(Styles.black6, s -> {
      s.check(
          Core.bundle.get("setting.showSkillHUD.name", "显示技能呼出按钮"),
          CSettings.showSkillHUD(),
          CSettings::setShowSkillHUD)
          .left()
          .height(42f)
          .pad(4f, 10f, 4f, 10f)
          .growX();
      s.row();
      s.check(
          Core.bundle.get("setting.showFaBaoHUD.name", "显示法宝呼出按钮"),
          CSettings.showFaBaoHUD(),
          CSettings::setShowFaBaoHUD)
          .left()
          .height(42f)
          .pad(4f, 10f, 6f, 10f)
          .growX();
    }).growX().padBottom(6f);
    t.row();
  }

  /** 头部：修为标签 + 大号境界名（随修为档位变色）+ 道号 */
  private void buildHeader(Table t) {
    JingJie cur = CultivationState.playerJingJie == null ? JingJie.fan : CultivationState.playerJingJie;
    XiuWei rank = CultivationState.currentXiuWei();
    Color rankColor = rank.color;

    t.table(Styles.black6, head -> {
      head.add(Core.bundle.get("stat.xiuwei") + " · " + rank.str)
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

  /** 灵力条：显示可动用灵力占灵力上限的比例，颜色取修为档位色。 */
  private void buildPowerBar(Table t) {
    JingJie next = PlayerXiuWeiSystem.getNextJingJie();
    boolean maxed = next == CultivationState.currentJingJie();

    t.table(Styles.black6, p -> {
      p.table(head -> {
        Label title = head.add("✦  灵 力 潮 汐  ✦").color(SECTION_COLOR).fontScale(1.05f).get();
        title.update(() -> title.setColor(SECTION_COLOR.cpy().lerp(
            Color.white, Mathf.absin(Time.time, 3.5f, 0.28f))));
        head.add().growX();
        Label regen = head.add("").color(Color.valueOf("9dffc4")).fontScale(1f).get();
        regen.update(() -> regen.setText(String.format(
            "凝聚  +%.2f / 秒", PlayerXiuWeiSystem.magicRegenPerSecond())));
      }).growX().pad(8f, 12f, 3f, 12f);
      p.row();

      Bar bar = new Bar(
          () -> String.format("%.1f / %.1f",
              Mathf.clamp(CultivationState.availableMagicPower, 0f, CultivationState.playerMagicPower),
              CultivationState.playerMagicPower),
          () -> CultivationState.currentXiuWei().color.cpy().lerp(
              Color.white, 0.16f + Mathf.absin(Time.time, 6f, 0.12f)),
          () -> CultivationState.playerMagicPower <= 0f ? 0f
              : Mathf.clamp(CultivationState.availableMagicPower / CultivationState.playerMagicPower));
      bar.blink(Color.white);
      bar.outline(CultivationState.currentXiuWei().color.cpy().a(0.9f), 3f);
      p.add(bar).growX().height(62f).pad(8f, 10f, 8f, 10f);
      p.row();
      p.add(Core.bundle.get("nextjingjie") + "：" + next.str
          + (maxed ? "（MAX）" : ""))
          .color(Color.lightGray).fontScale(0.9f).padBottom(6f);
    }).growX().padBottom(6f);
    t.row();
  }

  /** 境界进度：从当前境界门槛到下一境界门槛，显示总修为与尚差数值。 */
  private void buildRealmProgress(Table t) {
    JingJie current = CultivationState.currentJingJie();
    JingJie next = PlayerXiuWeiSystem.getNextJingJie();
    boolean maxed = next == current;
    float power = Math.max(0f, CultivationState.playerMagicPower);
    float currentThreshold = current.amount;
    float nextThreshold = maxed ? currentThreshold : next.amount;
    float ratio = maxed ? 1f : Mathf.clamp(
        (power - currentThreshold) / Math.max(1f, nextThreshold - currentThreshold));
    float remaining = maxed ? 0f : Math.max(0f, nextThreshold - power);
    String progressText = formatRealmValue(power) + " / " + formatRealmValue(nextThreshold);
    String detail = maxed
        ? "已达当前修行路线最高境界"
        : "距离「" + next.str + "」还差 " + formatRealmValue(remaining) + " 修为";
    Color progressColor = CultivationState.currentXiuWei().color;

    t.table(Styles.black6, p -> {
      p.table(head -> {
        head.add("境 界 进 度").color(SECTION_COLOR).fontScale(1.05f);
        head.add().growX();
        head.add(maxed ? "MAX" : "下一境界：" + next.str)
            .color(maxed ? Color.gold : Color.lightGray).fontScale(0.9f);
      }).growX().pad(8f, 12f, 3f, 12f);
      p.row();

      Bar bar = new Bar(
          () -> progressText,
          () -> progressColor.cpy().lerp(Color.white, 0.12f + Mathf.absin(Time.time, 6f, 0.10f)),
          () -> ratio);
      bar.outline(progressColor.cpy().a(0.9f), 3f);
      p.add(bar).growX().height(54f).pad(8f, 10f, 5f, 10f);
      p.row();
      p.add(detail).color(maxed ? Color.gold : Color.lightGray)
          .wrap().width(contentWidth - 28f).fontScale(0.9f)
          .pad(0f, 10f, 8f, 10f);
    }).growX().padBottom(6f);
    t.row();
  }

  private static String formatRealmValue(float value) {
    return String.format("%,.1f", value);
  }

  /** 渡劫目标：渡劫中显示当前目标（猩红），否则显示下一境界目标（金）；神途渡劫附带实时击杀进度 */
  private void buildDuJie(Table t) {
    String text = null;
    Color color = Color.gold;

    if (CultivationState.isInDuJie && CultivationState.pendingDuJieJingJie != null
        && CultivationState.pendingDuJieJingJie.duJieCondition != null) {
      text = Core.bundle.get("dujie.ing", "[渡] 当前目标") + "："
          + CultivationState.pendingDuJieJingJie.duJieCondition.str;
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
      d.add(ftext).color(fcolor).wrap().width(contentWidth - 28f).pad(6f);
      // 神途渡劫：实时击杀进度（条件为"击杀100个敌方单位"）
      if (CultivationState.isInDuJie && CultivationState.pendingDuJieJingJie == JingJie.shentu) {
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

    Seq<GongFa> list = CultivationState.gongfaHave.toSeq().select(g -> g != GongFas.none).sort(g -> g.id);
    t.table(Styles.black6, g -> {
      if (list.isEmpty()) {
        g.add(Core.bundle.get("gongfa.empty", "尚未习得功法")).color(EMPTY_COLOR).pad(8f);
        return;
      }
      int columns = contentWidth < 340f ? 1 : 2;
      float cardWidth = (contentWidth - 28f) / columns;
      int i = 0;
      for (GongFa gf : list) {
        g.table(Tex.button, card -> {
          card.image(gf.uiIcon()).size(30f).scaling(Scaling.fit);
          card.add(gf.localizedName).padLeft(5f).wrap().left().growX();
        }).width(cardWidth).pad(3f).left();
        if (++i % columns == 0)
          g.row();
      }
    }).growX().padBottom(6f);
    t.row();
  }

  /** Standalone magic skills. ShenWu skills intentionally stay in their own section. */
  private void buildMagicSkills(Table t) {
    sectionTitle(t, Core.bundle.get("skills.unlocked.title", "已解锁技能"));

    Seq<SkillUnlockable> unlocked = new Seq<>();
    for (Skill skill : Skill.list) {
      if (skill.unlocked()) {
        unlocked.add(skill);
      }
    }
    buildSkillGrid(t, unlocked, "skills.unlocked.empty");
  }

  /** Skills belonging to ShenWu, displayed separately from standalone magic skills. */
  private void buildShenWuSkills(Table t) {
    sectionTitle(t, Core.bundle.get("shenwu.skills.unlocked.title", "已解锁神武技能"));

    Seq<SkillUnlockable> unlocked = new Seq<>();
    for (ShenWu shenWu : ShenWu.list) {
      for (ShenWu.Skill skill : shenWu.skills) {
        if (skill.unlocked()) {
          unlocked.add(skill);
        }
      }
    }
    buildSkillGrid(t, unlocked, "shenwu.skills.unlocked.empty");
  }

  private void buildSkillGrid(Table t, Seq<SkillUnlockable> skills, String emptyKey) {
    t.table(Styles.black6, grid -> {
      if (skills.isEmpty()) {
        grid.add(Core.bundle.get(emptyKey, "尚未解锁任何技能"))
            .color(EMPTY_COLOR)
            .pad(8f);
        return;
      }

      int columns = contentWidth < 360f ? 1 : 2;
      float cardWidth = (contentWidth - 28f) / columns;
      int index = 0;
      for (SkillUnlockable skill : skills) {
        Button card = new Button(Tex.button);
        card.margin(6f);
        card.image(skill.unlockIcon()).size(38f).scaling(Scaling.fit).padRight(7f);
        card.add(skill.displayName()).wrap().left().growX().get().setFontScale(0.9f);
        card.clicked(() -> new SkillDetailDialog(skill, false).show());
        grid.add(card).width(cardWidth).height(62f).pad(3f).left();
        if (++index % columns == 0) {
          grid.row();
        }
      }
    }).growX().padBottom(6f);
    t.row();
  }

  private void buildScrollInventory(Table t) {
    sectionTitle(t, Core.bundle.get("scroll.inventory.section", "卷轴"));
    t.table(Styles.black6, s -> {
      s.button(
          Core.bundle.format(
              "scroll.inventory.button",
              ScrollSystem.owned().size),
          Icon.bookOpen,
          Styles.flatt,
          () -> new ScrollInventoryDialog().show())
          .size(Math.min(300f, contentWidth - 24f), 54f)
          .pad(8f);
    }).growX().padBottom(6f);
    t.row();
  }

  /** 法宝槽位区：栏位数量与持有类型数一致，最多显示两行，超出后纵向滚动。 */
  private void buildFaBaoSlots(Table t) {
    sectionTitle(t, Core.bundle.get("fabao", "法宝"));

    Seq<FaBao> owned = FaBaoSystem.owned();
    int columns = contentWidth < 430f ? 2 : 3;
    float slotWidth = Math.max(92f, Math.min(136f, (contentWidth - 24f) / columns));
    t.table(Styles.black6, s -> {
      if (owned.isEmpty()) {
        s.add(Core.bundle.get("fabao.empty", "尚无法宝（一次性消耗品，从右侧法宝栏拖出使用）"))
            .color(Color.darkGray).wrap().width(contentWidth - 28f).pad(4f);
        return;
      }

      Table grid = new Table();
      grid.center();
      for (int i = 0; i < owned.size; i++) {
        FaBao f = owned.get(i);
        grid.table(Tex.button, slot -> {
          slot.image(f.uiIcon()).size(40f).scaling(Scaling.fit)
              .color(f.usingFallbackIcon() ? f.rangeColor : Color.white);
          slot.row();
          slot.add(f.localizedName).wrap().width(slotWidth - 12f)
              .get().setFontScale(0.9f);
          slot.row();
          slot.add("×" + FaBaoSystem.count(f)).color(Color.lightGray).get().setFontScale(0.8f);
        }).size(slotWidth, 108f).pad(4f);
        if ((i + 1) % columns == 0)
          grid.row();
      }

      int rows = (owned.size + columns - 1) / columns;
      ScrollPane scroll = new ScrollPane(grid, Styles.smallPane);
      scroll.setScrollingDisabled(true, rows <= 2);
      scroll.setFadeScrollBars(false);
      s.add(scroll).growX().height(Math.min(rows, 2) * 116f);
    }).growX().padBottom(6f);
    t.row();
  }

  /** Obtained ShenWu, replacing the former placeholder-string slots. */
  private void buildShenWuSlots(Table t) {
    sectionTitle(t, Core.bundle.get("shenwu", "神武"));
    t.table(Styles.black6, s -> {
      Seq<ShenWu> owned = ShenWuSystem.obtained;
      for (int i = 0; i < SHENWU_SLOTS; i++) {
        int index = i;
        if (index >= owned.size) {
          s.add("?").color(EMPTY_COLOR).pad(8f).size(76f)
              .get().setFontScale(1.6f);
          continue;
        }

        ShenWu shenWu = owned.get(index);
        Button slot = new Button(Tex.button);
        slot.image(shenWu.uiIcon()).size(34f).scaling(Scaling.fit);
        slot.row();
        slot.add(shenWu.localizedName).get().setFontScale(0.75f);
        slot.clicked(() -> ShenWuSystem.equip(shenWu));
        s.add(slot).size(92f, 82f).pad(4f);
      }
      s.row();
      if (owned.isEmpty()) {
        s.add(Core.bundle.get("shenwu.empty", "尚未炼化任何神武"))
            .colspan(SHENWU_SLOTS)
            .color(Color.darkGray)
            .wrap()
            .width(contentWidth - 28f)
            .padBottom(4f);
      } else {
        Label equipped = s.add("").colspan(SHENWU_SLOTS)
            .color(Color.lightGray)
            .padBottom(5f)
            .get();
        equipped.update(() -> equipped.setText(
            ShenWuSystem.equipped == null
                ? Core.bundle.get("shenwu.equipped.none", "当前未装备神武")
                : Core.bundle.format(
                    "shenwu.equipped",
                    ShenWuSystem.equipped.localizedName)));
      }
    }).growX().padBottom(6f);
    t.row();
  }

  private void sectionTitle(Table t, String text) {
    t.add("—— " + text + " ——").color(SECTION_COLOR).padTop(4f);
    t.row();
  }
}
