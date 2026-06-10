package crystal.ui.dialogs;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.input.KeyCode;
import arc.math.Mathf;
import arc.math.geom.Rect;
import arc.scene.Element;
import arc.scene.Group;
import arc.scene.event.ElementGestureListener;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.event.Touchable;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.ImageButton;
import arc.scene.ui.layout.Scl;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Nullable;
import arc.util.Scaling;
import crystal.type.GongFa;
import mindustry.Vars;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.graphics.Pal;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;

@SuppressWarnings("unchecked")
public class GongFaDialog extends BaseDialog {
  // 基础尺寸配置
  public final float nodeSize = Scl.scl(60f);
  public final float colSpacing = Scl.scl(80f);
  public final float rowSpacing = Scl.scl(20f);

  // 功法节点与分组数据
  public Seq<GongFaNode> nodes = new Seq<>();
  public Seq<Seq<GongFa>> gongFaGroups = new Seq<>();
  public Rect bounds = new Rect();

  // 核心视图与选中状态
  public View view;
  public @Nullable ImageButton selectedNode;

  public GongFaDialog() {
    super("功法图鉴");
    // 初始化基础布局
    margin(0f).marginBottom(8f);
    cont.stack(view = new View()).grow();
    shouldPause = true;

    // 关闭按钮
    addCloseButton();

    // 滚轮缩放监听
    addListener(new InputListener() {
      @Override
      public boolean scrolled(InputEvent event, float x, float y, float amountX, float amountY) {
        view.setScale(Mathf.clamp(view.scaleX - amountY / 10f * view.scaleX, 0.25f, 3f));
        view.setOrigin(1);
        view.setTransform(true);
        return true;
      }
    });

    // 拖拽与捏合缩放监听
    addCaptureListener(new ElementGestureListener() {
      float lastZoom = -1f;

      @Override
      public void zoom(InputEvent event, float initialDistance, float distance) {
        if (lastZoom < 0)
          lastZoom = view.scaleX;
        view.setScale(Mathf.clamp(distance / initialDistance * lastZoom, 0.25f, 3f));
        view.setOrigin(1);
        view.setTransform(true);
      }

      @Override
      public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode button) {
        lastZoom = view.scaleX;
      }

      @Override
      public void pan(InputEvent event, float x, float y, float deltaX, float deltaY) {
        view.panX += deltaX / view.scaleX;
        view.panY += deltaY / view.scaleY;
        view.clamp();
      }
    });

    // 功法解锁事件监听（适配你的监听器解锁需求）
    Events.on(GongFaUnlockEvent.class, e -> Core.app.post(() -> {
      String message = Core.bundle.format("unlockgongfa", e.gongFa.localizedName);
      Vars.ui.hudfrag.showToast(Icon.ok, message);
      initGroups();
      layoutNodes();
      view.rebuildAll();
    }));

    // 对话框显示时刷新数据
    shown(() -> {
      initGroups();
      layoutNodes();
      view.rebuildAll();
      selectedNode = null;
    });
  }

  // 按ID分组：同ID功法为一组，ID升序排列（实现从左到右、同ID并排）
  private void initGroups() {
    gongFaGroups.clear();
    Seq<GongFa> allGongFa = GongFa.gongFas.values().toSeq();
    allGongFa.sort(g -> g.id); // 按ID从小到大排序

    int currentId = -1;
    Seq<GongFa> currentGroup = new Seq<>();
    for (GongFa g : allGongFa) {
      if (g.id != currentId) {
        if (currentGroup.any())
          gongFaGroups.add(currentGroup);
        currentId = g.id;
        currentGroup = new Seq<>();
      }
      currentGroup.add(g);
    }
    if (currentGroup.any())
      gongFaGroups.add(currentGroup);
  }

  // 计算节点坐标布局
  private void layoutNodes() {
    nodes.clear();
    float currentX = 0;
    // 遍历每一列（ID组）
    for (Seq<GongFa> group : gongFaGroups) {
      // 计算组内总高度，实现垂直居中
      float groupHeight = group.size * nodeSize + (group.size - 1) * rowSpacing;
      float currentY = -groupHeight / 2f;
      // 同ID功法上下并排
      for (GongFa g : group) {
        nodes.add(new GongFaNode(g, currentX, currentY));
        currentY += nodeSize + rowSpacing;
      }
      currentX += colSpacing; // 下一列
    }
    // 计算拖拽边界
    float minX = -nodeSize, minY = -nodeSize, maxX = currentX, maxY = 0;
    for (GongFaNode node : nodes) {
      minX = Math.min(minX, node.x - nodeSize / 2f);
      maxX = Math.max(maxX, node.x + nodeSize / 2f);
      minY = Math.min(minY, node.y - nodeSize / 2f);
      maxY = Math.max(maxY, node.y + nodeSize / 2f);
    }
    bounds.set(minX, minY, maxX - minX, maxY - minY);
  }

  // 功法节点封装类
  public static class GongFaNode {
    public GongFa gongFa;
    public float x, y;
    public final float width, height;

    public GongFaNode(GongFa gongFa, float x, float y) {
      this.gongFa = gongFa;
      this.x = x;
      this.y = y;
      this.width = this.height = Scl.scl(60f);
    }
  }

  // 功法解锁事件（用于监听器解锁自动刷新）
  public static class GongFaUnlockEvent {
    public final GongFa gongFa;

    public GongFaUnlockEvent(GongFa gongFa) {
      this.gongFa = gongFa;
    }
  }

  // 核心渲染视图
  public class View extends Group {
    public float panX = 0, panY = 0;
    public boolean moved = false;
    public final Table infoTable = new Table();

    public View() {
      setTransform(true);
      setOrigin(1);
      infoTable.touchable = Touchable.enabled;
    }

    // 重建所有功法节点按钮
    public void rebuildAll() {
      clear();
      selectedNode = null;
      infoTable.clear();

      for (GongFaNode node : nodes) {
        ImageButton button = new ImageButton(node.gongFa.uiIcon(), Styles.nodei);
        button.setSize(nodeSize);
        button.userObject = node;

        // 点击显示详情
        button.clicked(() -> {
          if (!moved) {
            selectedNode = button;
            rebuildInfo();
          }
        });

        // 实时更新边框颜色：解锁绿/未解锁红
        button.update(() -> {
          float offsetX = panX + width / 2f;
          float offsetY = panY + height / 2f;
          button.setPosition(node.x + offsetX, node.y + offsetY, 1);

          boolean unlocked = node.gongFa.unlocked();
          // 只修改按钮自身的背景，不修改共享style
          button.getStyle().up = unlocked ? Tex.buttonOver : Tex.buttonRed;
          // 直接更新按钮的图片，不修改共享的imageUp，避免影响其他按钮
          button.getImage().setDrawable(new TextureRegionDrawable(node.gongFa.uiIcon()));
          button.getImage().setColor(Color.white);
          button.getImage().setScaling(Scaling.bounded);
        });
        addChild(button);
      }

      // 点击空白处关闭详情
      tapped(() -> {
        Element hit = Core.scene.hit(Core.input.mouseX(), Core.input.mouseY(), true);
        if (hit == this) {
          selectedNode = null;
          rebuildInfo();
        }
      });

      released(() -> moved = false);
    }

    // 限制拖拽边界，防止拖出屏幕
    public void clamp() {
      float pad = nodeSize * 2;
      float ox = width / 2f;
      float oy = height / 2f;
      float rx = bounds.x + panX + ox;
      float ry = panY + oy + bounds.y;
      float rw = bounds.width;
      float rh = bounds.height;

      rx = Mathf.clamp(rx, -rw + pad, Core.graphics.getWidth() - pad);
      ry = Mathf.clamp(ry, -rh + pad, Core.graphics.getHeight() - pad);

      panX = rx - bounds.x - ox;
      panY = ry - bounds.y - oy;
    }

    // 重建功法详情面板
    public void rebuildInfo() {
      infoTable.remove();
      infoTable.clear();

      if (selectedNode != null) {
        GongFaNode node = ((GongFaNode) selectedNode.userObject);
        GongFa gongFa = node.gongFa;

        // 详情面板跟随按钮位置
        infoTable.update(() -> infoTable.setPosition(
            selectedNode.x + selectedNode.getWidth(),
            selectedNode.y + selectedNode.getHeight(),
            10));
        infoTable.left().background(Tex.button).margin(12f);

        // 功法名称
        infoTable.add(gongFa.localizedName).color(Pal.accent).fontScale(1.2f).left().row();
        // 分割线
        infoTable.image().color(Pal.accent).height(2f).fillX().padTop(4f).padBottom(8f).row();
        // 解锁状态
        infoTable.add(gongFa.unlocked() ? "已解锁" : "未解锁")
            .color(gongFa.unlocked() ? Color.green : Color.scarlet).left().row();
        // 功法描述
        if (gongFa.description != null) {
          infoTable.labelWrap(gongFa.description).color(Color.lightGray).width(400f).padTop(8f).left().row();
        }

        addChild(infoTable);
        infoTable.pack();
        infoTable.act(Core.graphics.getDeltaTime());
      }
    }

    // 绘制节点间连线（先画连线后画节点，保证节点在上层）
    @Override
    public void drawChildren() {
      clamp();
      float offsetX = panX + width / 2f;
      float offsetY = panY + height / 2f;

      Draw.sort(true);
      // 遍历相邻ID组，绘制列间连线
      for (int i = 0; i < gongFaGroups.size - 1; i++) {
        Seq<GongFa> currentGroup = gongFaGroups.get(i);
        Seq<GongFa> nextGroup = gongFaGroups.get(i + 1);

        Seq<GongFaNode> currentNodes = nodes.select(n -> currentGroup.contains(n.gongFa));
        Seq<GongFaNode> nextNodes = nodes.select(n -> nextGroup.contains(n.gongFa));

        float midX = (currentNodes.first().x + nextNodes.first().x) / 2f;

        // 绘制折线连线，和原版科技树风格一致
        for (GongFaNode curr : currentNodes) {
          for (GongFaNode next : nextNodes) {
            boolean allUnlocked = curr.gongFa.unlocked() && next.gongFa.unlocked();
            Draw.z(allUnlocked ? 2f : 1f);
            Lines.stroke(Scl.scl(3f), allUnlocked ? Pal.accent : Pal.gray);
            Draw.alpha(parentAlpha);

            // 三段式折线
            Lines.line(curr.x + offsetX, curr.y + offsetY, midX + offsetX, curr.y + offsetY);
            Lines.line(midX + offsetX, curr.y + offsetY, midX + offsetX, next.y + offsetY);
            Lines.line(midX + offsetX, next.y + offsetY, next.x + offsetX, next.y + offsetY);
          }
        }
      }
      Draw.sort(false);
      Draw.reset();

      super.drawChildren();
    }
  }
}
