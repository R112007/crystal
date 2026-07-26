package crystal.ui.dialogs;

import arc.Core;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.input.KeyCode;
import arc.math.*;
import arc.scene.event.*;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.Align;
import arc.util.Scaling;
import crystal.type.Contributor;
import mindustry.graphics.Pal;
import mindustry.ui.Fonts;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;

/**
 * 展示模组制作组成员的关系图。
 *
 * - root=true 的成员居中显示。
 * - 其余成员围绕在四周，并用直线与 root 相连，线上方显示 relative。
 * - 点击头像按钮弹出详情对话框。
 *
 * 缩放/拖动逻辑参考 mindustry.ui.dialogs.ResearchDialog 的 View 实现。
 */
public class RelativeDialog extends BaseDialog {
    public Contributor root;
    public Seq<Contributor> relatives = new Seq<>();

    private float iconSize = 64f;
    private View view;

    public RelativeDialog(String title) {
        super(title);
        addCloseButton();
        setup();
    }

    public void setContributors(Contributor root, Seq<Contributor> relatives) {
        this.root = root;
        this.relatives = relatives;
        setup();
    }

    void setup() {
        cont.clear();
        if (root == null) {
            cont.add("No contributors").center();
            return;
        }

        touchable = Touchable.enabled;

        // 桌面滚轮缩放，与 ResearchDialog 一致
        addListener(new InputListener() {
            @Override
            public boolean scrolled(InputEvent event, float x, float y, float amountX, float amountY) {
                if (view == null)
                    return false;
                view.setScale(Mathf.clamp(view.scaleX - amountY / 10f * view.scaleX, 0.25f, 2f));
                view.setOrigin(Align.center);
                view.setTransform(true);
                return true;
            }

            @Override
            public boolean mouseMoved(InputEvent event, float x, float y) {
                if (view != null)
                    view.requestScroll();
                return super.mouseMoved(event, x, y);
            }
        });

        // 移动端双指缩放/单指拖动，与 ResearchDialog 一致
        addCaptureListener(new ElementGestureListener() {
            @Override
            public void touchDown(InputEvent event, float x, float y, int pointer, KeyCode button) {
                if (view != null)
                    view.moved = false;
            }

            @Override
            public void zoom(InputEvent event, float initialDistance, float distance) {
                if (view == null)
                    return;
                if (view.lastZoom < 0)
                    view.lastZoom = view.scaleX;

                view.setScale(Mathf.clamp(distance / initialDistance * view.lastZoom, 0.25f, 2f));
                view.setOrigin(Align.center);
                view.setTransform(true);
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode button) {
                if (view == null)
                    return;
                view.lastZoom = view.scaleX;
            }

            @Override
            public void pan(InputEvent event, float x, float y, float deltaX, float deltaY) {
                if (view == null)
                    return;
                view.panX += deltaX / view.scaleX;
                view.panY += deltaY / view.scaleY;
                view.moved = true;
            }
        });

        cont.add(view = new View()).size(520f).center().pad(20f);
    }

    /**
     * 关系图主视图，仿 ResearchDialog.View。
     */
    public class View extends WidgetGroup {
        public float panX = 0, panY = 0, lastZoom = -1;
        public boolean moved = false;
        Seq<ContributorButton> buttons = new Seq<>();

        public View() {
            transform = true;
            setOrigin(Align.center);
            setTransform(true);
            touchable = Touchable.enabled;
            rebuild();
            released(() -> moved = false);
        }

        void rebuild() {
            clear();
            buttons.clear();

            buttons.add(new ContributorButton(root, true, 0));
            for (int i = 0; i < relatives.size; i++) {
                buttons.add(new ContributorButton(relatives.get(i), false, i));
            }

            for (ContributorButton b : buttons) {
                addChild(b);
            }
        }

        @Override
        public void act(float delta) {
            super.act(delta);

            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            int count = relatives.size;
            float radius = Math.min(getWidth(), getHeight()) / 2.8f;

            for (ContributorButton b : buttons) {
                if (b.isRoot) {
                    b.baseX = cx;
                    b.baseY = cy;
                } else {
                    float angle = 360f * b.index / Math.max(1, count);
                    b.baseX = cx + Angles.trnsx(angle, radius);
                    b.baseY = cy + Angles.trnsy(angle, radius);
                }
                b.setPosition(b.baseX + panX, b.baseY + panY, Align.center);
            }
        }

        @Override
        public void draw() {
            validate();
            if (transform)
                applyTransform(computeTransform());

            float cx = getWidth() / 2f + panX;
            float cy = getHeight() / 2f + panY;
            int count = relatives.size;
            float radius = Math.min(getWidth(), getHeight()) / 2.8f;

            // 先画连线
            Lines.stroke(2f, Pal.accent);
            for (int i = 0; i < count; i++) {
                float angle = 360f * i / Math.max(1, count);
                float px = cx + Angles.trnsx(angle, radius);
                float py = cy + Angles.trnsy(angle, radius);
                Lines.line(cx, cy, px, py);
            }
            Draw.reset();

            // 再画关系文字（线上方）
            for (int i = 0; i < count; i++) {
                Contributor c = relatives.get(i);
                float angle = 360f * i / Math.max(1, count);
                float px = cx + Angles.trnsx(angle, radius);
                float py = cy + Angles.trnsy(angle, radius);
                float mx = (cx + px) / 2f;
                float my = (cy + py) / 2f + 8f;
                Fonts.outline.draw(c.relative, mx, my, Color.white, 0.8f, false, Align.center);
            }

            // 画按钮（头像）
            drawChildren();

            // 最后画名字（在按钮下方）
            for (ContributorButton b : buttons) {
                float px = b.x + b.getWidth() / 2f;
                float py = b.y - 8f;
                Fonts.outline.draw(b.contributor.name, px, py, Color.white, 0.8f, false, Align.center);
            }

            if (transform)
                resetTransform();
        }
    }

    /**
     * 成员头像按钮，使用 ImageButton 以支持点击。
     */
    public class ContributorButton extends ImageButton {
        Contributor contributor;
        boolean isRoot;
        int index;
        float baseX, baseY;

        public ContributorButton(Contributor contributor, boolean isRoot, int index) {
            super(new TextureRegionDrawable(
                    (contributor.icon != null && contributor.icon.found()) ? contributor.icon
                            : Core.atlas.find("whiteui")),
                    Styles.cleari);

            this.contributor = contributor;
            this.isRoot = isRoot;
            this.index = index;

            setSize(iconSize, iconSize);
            getImage().setScaling(Scaling.fit);
            touchable = Touchable.enabled;

            clicked(() -> {
                if (view.moved)
                    return;
                showDetail(contributor);
            });
        }
    }

    void showDetail(Contributor c) {
        BaseDialog detail = new BaseDialog(c.name);
        detail.cont.defaults().pad(8f).center();

        if (c.icon != null && c.icon.found()) {
            detail.cont.image(c.icon).size(128f).row();
        }
        detail.cont.add(c.name).row();
        detail.cont.add("群头衔: " + (c.title != null ? c.title : "暂无头衔")).row();
        detail.cont.add(c.description != null ? c.description : "").wrap().width(320f).row();

        detail.addCloseButton();
        detail.show();
    }
}
