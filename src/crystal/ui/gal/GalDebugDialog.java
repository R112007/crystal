package crystal.ui.gal;

import arc.Core;
import arc.graphics.Color;
import arc.scene.ui.Dialog;
import arc.scene.ui.Label;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.struct.ObjectSet;
import crystal.CVars;
import mindustry.gen.Tex;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;

public class GalDebugDialog extends BaseDialog {
    private final Table contentTable;
    private boolean autoRefresh = true;
    private float refreshInterval = 1f;
    private float refreshTimer = 0f;

    public GalDebugDialog() {
        super("剧情调试面板", Styles.defaultDialog);
        setResizable(true);
        setMovable(true);
        setModal(false);

        // ===== 核心修复：屏幕自适应尺寸，彻底解决缩放超出问题 =====
        float screenW = Core.graphics.getWidth();
        float screenH = Core.graphics.getHeight();
        // 限制面板最大为屏幕85%，最小尺寸兜底，适配所有设备
        float dialogW = Math.min(screenW * 0.85f, 900f);
        float dialogH = Math.min(screenH * 0.85f, 1200f);
        dialogW = Math.max(dialogW, 400f);
        dialogH = Math.max(dialogH, 300f);
        setSize(dialogW, dialogH);
        centerWindow(); // 强制居中，避免贴边超出

        addCloseButton();

        // 顶部控制栏
        Table controlTable = new Table();
        controlTable.left().background(Tex.button).margin(8f);
        controlTable.button("手动刷新", Styles.flatt, this::refreshDebugContent).size(120, 40).padRight(8f);
        controlTable.check("自动刷新", autoRefresh, val -> {
            autoRefresh = val;
            refreshTimer = 0f;
        }).padRight(12f);
        controlTable.add(new Label("刷新间隔(秒):", Styles.defaultLabel)).padRight(4f);
        controlTable.field(String.valueOf(refreshInterval), val -> {
            try {
                float newInterval = Float.parseFloat(val);
                if (newInterval > 0.1f)
                    refreshInterval = newInterval;
            } catch (Exception ignored) {
            }
        }).width(80f).padRight(12f);
        controlTable.button("清空历史", Styles.flatt, () -> {
            // ===== 适配分模块：遍历所有模块清空历史 =====
            GalgameDialogueManager.instance.modules.each(module -> {
                module.history.clear();
                module.playedNodeSet.clear();
            });
            refreshDebugContent();
        }).size(100, 40).padRight(6f);
        controlTable.button("重置所有进度", Styles.flatt, () -> {
            GalgameDialogueManager.instance.resetAllProgress();
            refreshDebugContent();
        }).size(140, 40);
        cont.add(controlTable).growX().padBottom(10f).row();

        // 内容容器+滚动面板，彻底解决内容超出屏幕
        contentTable = new Table();
        contentTable.left().top();
        ScrollPane scrollPane = new ScrollPane(contentTable);
        scrollPane.setFadeScrollBars(false);
        scrollPane.setScrollingDisabledX(true);
        scrollPane.setSmoothScrolling(true);
        cont.add(scrollPane).grow().pad(8f);

        // 自动刷新逻辑
        update(() -> {
            if (!autoRefresh || !isShown())
                return;
            refreshTimer += Core.graphics.getDeltaTime();
            if (refreshTimer >= refreshInterval) {
                refreshDebugContent();
                refreshTimer = 0f;
            }
        });

        // 窗口大小自适应
        resized(() -> {
            float w = Math.min(Core.graphics.getWidth() * 0.85f, 900f);
            float h = Math.min(Core.graphics.getHeight() * 0.85f, 1200f);
            setSize(w, h);
            centerWindow();
        });
        closeOnBack();
    }

    @Override
    public Dialog show() {
        refreshDebugContent();
        return super.show();
    }

    private void refreshDebugContent() {
        contentTable.clear();
        GalgameDialogueManager manager = GalgameDialogueManager.instance;

        // 1. 核心运行状态
        contentTable.add(new Label("=== 核心运行状态 ===", Styles.defaultLabel))
                .fontScale(1.2f).left().padBottom(8f).row();
        addDebugRow("当前模块ID", manager.currentModuleId == null ? "无" : manager.currentModuleId);
        addDebugRow("面板是否显示", String.valueOf(manager.isShowing));
        addDebugRow("是否正在播放", String.valueOf(manager.isPlaying));
        addDebugRow("是否自动播放", String.valueOf(manager.isAutoPlay));
        addDebugRow("是否正在打字", String.valueOf(manager.isTyping));
        addDebugRow("上一个角色ID", manager.lastPlayedCharacterId == null ? "无" : manager.lastPlayedCharacterId);
        addDebugRow("自动播放间隔", manager.autoPlayInterval + "s");
        addDebugRow("打字速度", manager.typingSpeed + " 字符/秒");
        contentTable.add().padTop(12f).row();

        // 2. 模块列表详情
        contentTable.add(new Label("=== 模块列表详情 ===", Styles.defaultLabel))
                .fontScale(1.2f).left().padBottom(8f).row();
        if (manager.modules.isEmpty()) {
            contentTable.add(new Label("暂无注册模块", Styles.defaultLabel)).left().pad(4f).row();
        } else {
            manager.modules.each(module -> {
                String moduleInfo = String.format(
                        "模块ID: %s | 名称: %s | 进度: %d/%d | 已完成: %s | 历史条数: %d",
                        module.moduleId,
                        module.moduleName,
                        module.progressIndex,
                        module.dialogueNodes.size,
                        module.isCompleted,
                        module.history.size);
                Label moduleLabel = new Label(moduleInfo, Styles.defaultLabel);
                moduleLabel.setColor(module.isCompleted ? Color.gray : Color.white);
                contentTable.add(moduleLabel).left().pad(2f).growX().wrap().row();
            });
        }
        contentTable.add().padTop(12f).row();

        // 3. 已播放节点ID集合（适配分模块：合并所有模块的已播放节点）
        contentTable.add(new Label("=== 已播放节点ID集合 ===", Styles.defaultLabel))
                .fontScale(1.2f).left().padBottom(8f).row();
        ObjectSet<String> allPlayedNodes = new ObjectSet<>();
        manager.modules.each(module -> allPlayedNodes.addAll(module.playedNodeSet));

        if (allPlayedNodes.isEmpty()) {
            contentTable.add(new Label("暂无已播放节点", Styles.defaultLabel)).left().pad(4f).row();
        } else {
            Seq<String> sortedNodes = allPlayedNodes.toSeq().sort();
            Table nodeTable = new Table();
            nodeTable.left();
            sortedNodes.each(nodeId -> {
                nodeTable.add(new Label(nodeId, Styles.defaultLabel)).left().pad(2f).row();
            });
            ScrollPane nodeScroll = new ScrollPane(nodeTable);
            nodeScroll.setFadeScrollBars(false);
            nodeScroll.setScrollingDisabledX(true);
            contentTable.add(nodeScroll).maxHeight(Core.graphics.getHeight() * 0.15f).growX().left().row();
        }
        contentTable.add().padTop(12f).row();

        // 4. 对话历史记录（倒序，适配分模块：合并所有模块的历史）
        contentTable.add(new Label("=== 对话历史记录（倒序） ===", Styles.defaultLabel))
                .fontScale(1.2f).left().padBottom(8f).row();
        Seq<DialogueHistory> allHistory = new Seq<>();
        manager.modules.each(module -> {
            if (module.history != null && !module.history.isEmpty()) {
                allHistory.addAll(module.history);
            }
        });

        if (allHistory.isEmpty()) {
            contentTable.add(new Label("暂无对话历史", Styles.defaultLabel)).left().pad(4f).row();
        } else {
            Table historyTable = new Table();
            historyTable.left();
            // 倒序显示
            for (int i = allHistory.size - 1; i >= 0; i--) {
                DialogueHistory item = allHistory.get(i);
                String historyInfo = String.format(
                        "[%s | %s] %s: %s",
                        item.moduleId,
                        item.nodeId,
                        item.characterName,
                        item.content);
                Label historyLabel = new Label(historyInfo, Styles.defaultLabel);
                historyLabel.setWrap(true);
                historyTable.add(historyLabel).growX().left().pad(3f).row();
            }
            ScrollPane historyScroll = new ScrollPane(historyTable);
            historyScroll.setFadeScrollBars(false);
            historyScroll.setScrollingDisabledX(true);
            contentTable.add(historyScroll).maxHeight(Core.graphics.getHeight() * 0.35f).growX().left().row();
        }
        contentTable.add().padTop(12f).row();
        contentTable.add().padTop(12f).row();
        // 新增：等待队列显示
        contentTable.add(new Label("=== 模块等待队列 ===", Styles.defaultLabel))
                .fontScale(1.2f).left().padBottom(8f).row();
        Seq<String> waitingIds = GalgameDialogueManager.instance.waitingModuleIds;
        if (waitingIds.isEmpty()) {
            contentTable.add(new Label("暂无等待模块", Styles.defaultLabel)).left().pad(4f).row();
        } else {
            Table queueTable = new Table();
            queueTable.left();
            for (int i = 0; i < waitingIds.size; i++) {
                String moduleId = waitingIds.get(i);
                DialogueModule module = GalgameDialogueManager.instance.getModule(moduleId);
                String info = String.format("%d. %s | %s", i + 1, moduleId,
                        module == null ? "无效模块" : module.moduleName);
                queueTable.add(new Label(info, Styles.defaultLabel)).left().pad(2f).row();
            }
            ScrollPane queueScroll = new ScrollPane(queueTable);
            queueScroll.setFadeScrollBars(false);
            queueScroll.setScrollingDisabledX(true);
            contentTable.add(queueScroll).maxHeight(Core.graphics.getHeight() * 0.1f).growX().left().row();
        }
        // 5. 玩家名字
        contentTable.add(new Label("=== 玩家名字 ===", Styles.defaultLabel))
                .fontScale(1.2f).left().padBottom(8f).row();
        addDebugRow("当前玩家名", CVars.playerName);
        contentTable.add().padTop(12f).row();

        // 6. 所有模块已触发分支（适配分模块：遍历所有模块显示）
        contentTable.add(new Label("=== 模块已触发分支 ===", Styles.defaultLabel))
                .fontScale(1.2f).left().padBottom(8f).row();
        if (manager.modules.isEmpty()) {
            contentTable.add(new Label("暂无模块", Styles.defaultLabel)).left().pad(4f).row();
        } else {
            manager.modules.each(module -> {
                String branchText = module.branchIds.isEmpty() ? "[无分支]" : module.branchIds.toString();
                Label label = new Label(module.moduleName + "：" + branchText, Styles.defaultLabel);
                label.setWrap(true);
                contentTable.add(label).growX().left().pad(2f).row();
            });
        }
        contentTable.invalidateHierarchy();
    }

    // 调试行辅助方法
    private void addDebugRow(String key, String value) {
        Table rowTable = new Table();
        rowTable.left();
        Label keyLabel = new Label(key + ": ", Styles.defaultLabel);
        keyLabel.setColor(Color.valueOf("ffd37f"));
        rowTable.add(keyLabel).width(160f).left();
        rowTable.add(new Label(value, Styles.defaultLabel)).left().growX();
        contentTable.add(rowTable).growX().left().pad(2f).row();
    }
}
