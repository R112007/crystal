package crystal.ui.dialogs;

import arc.Core;
import arc.func.Boolp;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.input.KeyCode;
import arc.math.Mathf;
import arc.scene.Element;
import arc.scene.event.ElementGestureListener;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.event.Touchable;
import arc.scene.ui.Dialog;
import arc.scene.ui.Image;
import arc.scene.ui.Label;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import crystal.core.ShenWuSystem;
import crystal.magic.ShenWu;
import crystal.magic.ShenWu.Skill;
import mindustry.Vars;
import mindustry.gen.Icon;
import mindustry.graphics.Pal;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;

public class ShenWuSkillTreeDialog extends BaseDialog {

  private static final float NODE_SIZE = 72f;
  private static final float NODE_PAD = 32f;
  private static final float COL_GAP = 240f;

  private final View view;
  private final Seq<TreeNode> allNodes = new Seq<>();
  private static ShenWuSkillTreeDialog dialog;

  public ShenWuSkillTreeDialog() {
    super("神武技能树");
    shouldPause = true;

    view = new View();
    cont.add(view).grow().pad(20f);

    buttons.button("@close", Icon.left, this::hide).size(210f, 64f);

    addListener(new InputListener() {
      @Override
      public boolean scrolled(InputEvent event, float x, float y, float amountX, float amountY) {
        view.setScale(Mathf.clamp(view.scaleX - amountY / 10f * view.scaleX, 0.05f, 3f));
        view.setOrigin(1);
        view.setTransform(true);
        return true;
      }

      @Override
      public boolean mouseMoved(InputEvent event, float x, float y) {
        view.requestScroll();
        return super.mouseMoved(event, x, y);
      }
    });

    touchable = Touchable.enabled;
    addCaptureListener(new ElementGestureListener() {
      float lastZoom = -1f;

      @Override
      public void zoom(InputEvent event, float initialDistance, float distance) {
        if (lastZoom < 0f)
          lastZoom = view.scaleX;
        view.setScale(Mathf.clamp(distance / initialDistance * lastZoom, 0.05f, 3f));
        view.setOrigin(1);
        view.setTransform(true);
      }

      @Override
      public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode button) {
        lastZoom = -1f;
      }

      @Override
      public void pan(InputEvent event, float x, float y, float deltaX, float deltaY) {
        view.pan(deltaX, deltaY);
      }
    });
  }

  @Override
  public Dialog show() {
    rebuild();
    return super.show();
  }

  /** 重建树：显示所有神武，未获得的灰色+红边框 */
  private void rebuild() {
    allNodes.clear();
    view.clear();

    float cx = Core.graphics.getWidth() / 2f;
    float startY = Core.graphics.getHeight() / 2f + 180f;

    int col = 0;
    for (ShenWu sw : ShenWu.list) {
      if (sw.skills.isEmpty())
        continue;

      boolean obtained = ShenWuSystem.obtained.contains(sw);
      float colX = cx + (col - (ShenWu.list.size - 1) / 2f) * COL_GAP;

      // 神武根节点
      TreeNode swNode = new TreeNode(sw, null, colX, startY, obtained);
      allNodes.add(swNode);
      view.addChild(swNode);

      // 技能节点
      for (int i = 0; i < sw.skills.size; i++) {
        Skill skill = sw.skills.get(i);
        TreeNode sn = new TreeNode(sw, skill, colX,
            startY - (i + 1) * (NODE_SIZE + NODE_PAD), obtained);
        sn.parent = swNode;
        swNode.children.add(sn);
        allNodes.add(sn);
        view.addChild(sn);
      }
      col++;
    }
  }

  // ==================== 树节点 ====================

  public class TreeNode extends Table {
    public final ShenWu shenWu;
    public final Skill skill;
    public final Seq<TreeNode> children = new Seq<>();
    public TreeNode parent;
    /** 该节点所在的神武是否已获得 */
    public final boolean swObtained;

    public TreeNode(ShenWu sw, Skill sk, float x, float y, boolean obtained) {
      this.shenWu = sw;
      this.skill = sk;
      this.swObtained = obtained;
      setPosition(x, y);
      setSize(NODE_SIZE, NODE_SIZE);
      setTransform(true);

      if (skill == null) {
        // 神武节点
        buildShenWuNode(sw);
      } else {
        // 技能节点
        buildSkillNode(sk);
      }
    }

    private void buildShenWuNode(ShenWu sw) {
      if (swObtained) {
        // 已获得：正常显示
        Label label = new Label(sw.localizedName);
        label.setFontScale(0.85f);
        label.setColor(Color.gold);
        add(label);
        touchable = Touchable.enabled;
        clicked(() -> {
          // 可切换装备
          if (ShenWuSystem.equipped != sw)
            ShenWuSystem.equip(sw);
        });
      } else {
        // 未获得：灰色文字 + 灰色图标
        Image img = new Image(sw.uiIcon());
        img.setColor(Color.gray);
        add(img).size(40f).row();
        Label label = new Label(sw.localizedName);
        label.setFontScale(0.75f);
        label.setColor(Color.darkGray);
        add(label);
        touchable = Touchable.disabled;
      }
    }

    private void buildSkillNode(Skill sk) {
      if (!swObtained) {
        // 神武未获得：技能全部锁定显示
        add(sk.buildIconStack(NODE_SIZE)).size(NODE_SIZE);
        touchable = Touchable.disabled;
        return;
      }

      // 神武已获得：按技能状态
      add(sk.buildIconStack(NODE_SIZE)).size(NODE_SIZE);
      touchable = Touchable.enabled;

      clicked(() -> {
        if (!sk.unlocked())
          showUnlockDialog();
        else if (!sk.maxed())
          showUpgradeDialog();
        else
          Vars.ui.showInfo(sk.localizedName + " 已满级");
      });
    }

    @Override
    public void draw() {
      // 先画背景遮罩和边框
      if (!swObtained) {
        // 未获得神武：灰色表面 + 红色边框
        float pad = 4f;
        float rx = x - pad, ry = y - pad;
        float rw = width + pad * 2, rh = height + pad * 2;

        // 灰色半透明覆盖
        Draw.color(Color.gray, 0.35f);
        Fill.rect(rx + rw / 2f, ry + rh / 2f, rw, rh);
        Draw.color();

        // 红色边框
        Draw.color(Color.scarlet, 0.8f);
        Lines.stroke(2.5f);
        Lines.rect(rx, ry, rw, rh);
        Draw.color();
      } else if (skill != null && ShenWuSystem.equipped == shenWu) {
        // 已装备神武的技能：青色高亮边框
        float pad = 3f;
        Draw.color(Pal.accent, 0.5f);
        Lines.stroke(2f);
        Lines.rect(x - pad, y - pad, width + pad * 2, height + pad * 2);
        Draw.color();
      }

      super.draw();

      // 画连线到子节点
      for (TreeNode child : children) {
        boolean childActive = child.swObtained && child.skill != null && child.skill.unlocked();
        Draw.color(childActive ? Pal.accent : Color.gray, childActive ? 0.5f : 0.2f);
        Lines.stroke(childActive ? 2.5f : 1.5f);
        Lines.line(
            x + width / 2f, y,
            child.x + child.width / 2f, child.y + child.height);
      }
      Draw.color();
    }

    // ==================== 对话框 ====================

    private void showUnlockDialog() {
      BaseDialog d = new BaseDialog("解锁技能");
      Table t = d.cont;
      t.add(skill.localizedName).color(Color.gold).row();
      if (skill.description != null)
        t.add(skill.description).color(Color.lightGray).wrap().width(350f).row();

      t.add("解锁条件：").color(Color.orange).padTop(8f).row();

      final boolean[] allMet = { true };
      for (Boolp cond : skill.unlockConditions) {
        boolean met = cond.get();
        allMet[0] &= met;
        t.add(met ? "[green]✓ 满足" : "[scarlet]✗ 未满足").row();
      }

      TextButton btn = t.button("解锁", () -> {
        if (!allMet[0]) {
          Vars.ui.showInfo("条件未满足");
          return;
        }
        if (ShenWuSystem.unlockSkill(skill)) {
          d.hide();
          rebuild();
          Vars.ui.showInfo("[" + skill.localizedName + "] 已解锁");
        }
      }).size(150f, 50f).get();

      if (!allMet[0])
        btn.setDisabled(true);

      d.buttons.button("@close", d::hide).size(120f, 50f);
      d.show();
    }

    private void showUpgradeDialog() {
      BaseDialog d = new BaseDialog("升级技能");
      Table t = d.cont;
      t.add(skill.localizedName).color(Color.gold).row();
      t.add("当前等级：").color(Color.gray);
      t.add(String.valueOf(skill.level)).color(Pal.accent).row();
      t.add("下一级效果：").color(Color.gray).padTop(4f).row();
      t.add(getUpgradeDesc()).color(Color.lightGray).wrap().width(350f).row();

      boolean canUpgrade = skill.level < skill.maxLevel;
      TextButton btn = t.button("升级", () -> {
        if (ShenWuSystem.upgradeSkill(skill)) {
          d.hide();
          rebuild();
          Vars.ui.showInfo("[" + skill.localizedName + "] 升至 Lv." + skill.level);
        }
      }).size(150f, 50f).get();
      if (!canUpgrade)
        btn.setDisabled(true);

      d.buttons.button("@close", d::hide).size(120f, 50f);
      d.show();
    }

    private String getUpgradeDesc() {
      return "冷却 -10% / 伤害 +15%";
    }
  }

  // ==================== 视图 ====================

  public class View extends Table {
    public View() {
      setTransform(true);
      setClip(true);
    }

    public void pan(float dx, float dy) {
      for (TreeNode n : allNodes) {
        n.x += dx;
        n.y += dy;
      }
    }

    @Override
    public void draw() {
      Draw.color(Color.darkGray, 0.12f);
      Fill.rect(x + width / 2f, y + height / 2f, width, height);
      Draw.color();
      super.draw();
    }
  }

}
