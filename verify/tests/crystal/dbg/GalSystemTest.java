package crystal.dbg;

import arc.Core;
import arc.graphics.GL20;
import arc.graphics.Texture;
import arc.graphics.g2d.Font;
import arc.graphics.g2d.TextureAtlas;
import arc.graphics.g2d.TextureRegion;
import arc.scene.Scene;
import arc.scene.style.Drawable;
import arc.scene.style.TextureRegionDrawable;
import crystal.ui.gal.Branch;
import crystal.ui.gal.DialogueLine;
import crystal.ui.gal.DialogueLine.DialogueOption;
import crystal.ui.gal.DialogueModule;
import crystal.ui.gal.DialoguePlaybackState;
import crystal.ui.gal.GalgameDialogueManager;
import crystal.ui.gal.GalgameReplayManager;
import crystal.ui.gal.Side;
import arc.struct.Seq;
import mindustry.Vars;
import mindustry.core.GameState.State;
import mindustry.gen.Tex;
import mindustry.ui.Fonts;
import mindustry.ui.Styles;

/** Headless logic test for GAL module progress, branches and waiting queues. */
public class GalSystemTest {
    private static boolean ok = true;

    public static void run(String[] args) {
        try {
            installHeadlessUiStyles();
            testModuleProgressAndBranches();
            testPlaybackCopies();
            testSpriteContinuity();
            testPlaybackStateMachine();
            testReplaySession();
            testWaitingQueues();
        } catch (Throwable t) {
            t.printStackTrace();
            ok = false;
        }
        System.out.println("[GAL] " + (ok ? "PASS" : "FAIL"));
        System.exit(ok ? 0 : 1);
    }

    private static void installHeadlessUiStyles() {
        if (Styles.defaultDialog != null) {
            return;
        }
        if (Core.scene == null) {
            Core.gl = (GL20) java.lang.reflect.Proxy.newProxyInstance(
                    GL20.class.getClassLoader(), new Class<?>[] { GL20.class }, (proxy, method, args) -> {
                        Class<?> type = method.getReturnType();
                        if (type == boolean.class)
                            return false;
                        if (type == int.class)
                            return 0;
                        if (type == long.class)
                            return 0L;
                        if (type == float.class)
                            return 0f;
                        if (type == double.class)
                            return 0d;
                        return null;
                    });
            Core.scene = new Scene();
            Core.scene.resize(Core.graphics.getWidth(), Core.graphics.getHeight());
        }
        Texture dummyTexture = allocateDummyTexture();

        Core.atlas = new TextureAtlas() {
            @Override
            public AtlasRegion find(String name) {
                AtlasRegion region = new AtlasRegion();
                region.texture = dummyTexture;
                region.name = name;
                region.width = 1;
                region.height = 1;
                region.packedWidth = 1;
                region.packedHeight = 1;
                region.u = 0f;
                region.v = 0f;
                region.u2 = 1f;
                region.v2 = 1f;
                region.splits = new int[] { 0, 0, 0, 0 };
                return region;
            }

            @Override
            public AtlasRegion white() {
                return find("white");
            }
        };

        TextureRegion dummyRegion = new TextureRegion(dummyTexture, 1, 1);
        if (Fonts.def == null) {
            Font.FontData data = new Font.FontData();
            data.lineHeight = 1f;
            data.capHeight = 1f;
            data.ascent = 1f;
            data.descent = 1f;
            data.down = 1f;
            data.spaceXadvance = 1f;
            data.xHeight = 1f;
            data.missingGlyph = new Font.Glyph();
            Fonts.def = new Font(data, dummyRegion, false);
            Fonts.outline = Fonts.def;
            Fonts.icon = Fonts.def;
        }

        Drawable dummy = new TextureRegionDrawable(dummyRegion);
        try {
            for (var field : Tex.class.getFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())
                        && field.getType() == Drawable.class) {
                    field.set(null, dummy);
                }
            }
            Styles.load();
            Tex.loadStyles();
        } catch (Throwable t) {
            throw new RuntimeException("cannot initialize headless UI styles", t);
        }
    }

    private static void testModuleProgressAndBranches() {
        String moduleId = "probe-gal-module";
        DialogueModule module = new DialogueModule(moduleId, "probe")
                .addNode(line("main-1"), line("main-2"), line("main-3"));
        check("node ids are generated", 3, module.dialogueNodes.size);
        check("node id uses one-based index", moduleId + "-1", module.dialogueNodes.first().nodeId);

        Branch branch = new Branch("probe-gal-branch")
                .addNode(line("branch-1"))
                .addNode(line("branch-2"));
        module.addBranch(branch);
        check("branch nodes are appended", 5, module.dialogueNodes.size);
        check("branch ids are recorded", 1, module.branchIds.size);

        module.progressIndex = 3;
        module.saveModuleData();
        module.loadModuleData();
        check("branch history is restored", 1, module.branchIds.size);
        check("branch nodes are rebuilt", 5, module.dialogueNodes.size);
        check("module progress is restored", 3, module.progressIndex);
        check("partial progress rebuilds history", 3, module.history.size);

        module.progressIndex = module.dialogueNodes.size;
        module.isCompleted = true;
        module.saveModuleData();
        module.loadModuleData();
        check("completion is restored", true, module.isCompleted);
        check("completed module rebuilds full history", module.dialogueNodes.size, module.history.size);

        module.resetProgress();
        check("reset restores only main nodes", 3, module.dialogueNodes.size);
        check("reset clears selected branches", 0, module.branchIds.size);
        check("reset clears completion", false, module.isCompleted);
    }

    private static void testPlaybackCopies() {
        int[] historyCalls = { 0 };
        int[] replayCalls = { 0 };
        Branch branch = new Branch("probe-gal-replay-branch").addNode(line("replay-branch"));
        DialogueOption option = new DialogueOption("option", branch, ignored -> historyCalls[0]++);
        DialogueLine source = new DialogueLine("npc", null, "replay-line", option)
                .complete(() -> historyCalls[0]++)
                .beforePlay(() -> historyCalls[0]++);

        DialogueLine historyCopy = source.copyForHistory();
        check("history copy removes completion callback", (Object) null, historyCopy.onComplete);
        check("history copy removes before-play callback", (Object) null, historyCopy.beforePlay);
        check("history copy removes callbacks", 0, historyCalls[0]);
        check("history copy keeps branch reference", branch, historyCopy.options[0].branch);
        check("history copy removes option callback", (Object) null, historyCopy.options[0].onSelect);

        DialogueLine replayCopy = source.copyForReplay();
        check("replay copy removes completion callback", (Object) null, replayCopy.onComplete);
        check("replay copy removes before-play callback", (Object) null, replayCopy.beforePlay);
        check("replay copy removes option callback", (Object) null, replayCopy.options[0].onSelect);
        check("replay copy cannot mutate game state", 0, historyCalls[0]);
        replayCalls[0] = historyCalls[0];
        check("replay remains side-effect free", 0, replayCalls[0]);
        check("replay copy keeps branch reference", branch, replayCopy.options[0].branch);
    }

    private static void testWaitingQueues() {
        GalgameDialogueManager manager = GalgameDialogueManager.instance;
        manager.modules.clear();
        DialogueModule prerequisite = new DialogueModule("probe-gal-pre", "pre")
                .addNode(line("pre-1"));
        DialogueModule dependent = new DialogueModule("probe-gal-dependent", "dependent")
                .withPrerequisite(prerequisite.moduleId)
                .addNode(line("dependent-1"));
        manager.modules.addAll(prerequisite, dependent);

        check("unfinished prerequisite blocks dependent", false, dependent.prerequisiteMet());
        manager.playQueue(Seq.with(line("queue-busy")));
        manager.conditionalWaitingIds().add(dependent.moduleId);
        prerequisite.isCompleted = true;
        manager.checkConditionalTriggers(prerequisite.moduleId);
        check("completed prerequisite releases dependent", false,
                manager.conditionalWaitingIds().contains(dependent.moduleId));
        check("released dependent enters play queue", true,
                manager.waitingModuleIds().contains(dependent.moduleId));
        manager.hide();

        manager.waitingModuleIds().add("probe-gal-missing");
        manager.saveWaitingQueue();
        manager.waitingModuleIds().clear();
        manager.loadWaitingQueue();
        check("invalid waiting id is discarded", false,
                manager.waitingModuleIds().contains("probe-gal-missing"));
        check("valid waiting id is restored", true,
                manager.waitingModuleIds().contains(dependent.moduleId));
    }

    private static void testPlaybackStateMachine() {
        GalgameDialogueManager manager = GalgameDialogueManager.instance;
        manager.resetAllProgress();
        Vars.state.set(State.playing);
        manager.playQueue(Seq.with(line("state-1"), line("state-2")));
        check("playback enters visible typing state", true, manager.isShowing());
        check("playback reports typing", DialoguePlaybackState.typing, manager.playbackState());

        manager.fastForward();
        check("empty playback returns to idle", DialoguePlaybackState.idle,
                manager.playbackState());
        check("idle playback is not visible", false, manager.isShowing());

        DialogueLine choice = line("state-choice");
        choice.options = new DialogueOption[] {
            new DialogueOption("choice", ignored -> {
            })
        };
        manager.playQueue(Seq.with(line("before-choice"), choice, line("after-choice")));
        manager.fastForward();
        check("fast forward stops on choice line", choice, manager.currentLine);
        check("choice options are visible", true, manager.ui.optionTable.visible);
        check("choice playback waits for selection", DialoguePlaybackState.typingChoice,
                manager.playbackState());
        manager.fastForward();
        check("fast forward does not pass an active choice", choice, manager.currentLine);
        manager.hide();
    }

    private static void testSpriteContinuity() {
        GalgameDialogueManager manager = GalgameDialogueManager.instance;
        manager.resetAllProgress();
        Vars.state.set(State.playing);
        Drawable sprite = new TextureRegionDrawable(
                new TextureRegion(allocateDummyTexture(), 1, 1));

        DialogueLine first = new DialogueLine("left-speaker", sprite, "sprite-1");
        first.rightSprite = sprite;
        first.activeSide = Side.left;
        DialogueLine second = new DialogueLine("right-speaker", sprite, "sprite-2");
        second.rightSprite = sprite;
        second.activeSide = Side.right;

        manager.playQueue(Seq.with(first, second));
        check("both sprites are visible together", true,
                manager.ui.leftSprite.visible && manager.ui.rightSprite.visible);

        manager.ui.leftSprite.setTranslation(17f, -9f);
        manager.ui.rightSprite.setTranslation(-13f, 8f);
        manager.ui.finishTyping();
        manager.nextLine();
        check("left sprite keeps its transform across lines", 17f,
                manager.ui.leftSprite.translation.x);
        check("right sprite keeps its transform across lines", -13f,
                manager.ui.rightSprite.translation.x);
        check("both sprites remain visible after speaker switch", true,
                manager.ui.leftSprite.visible && manager.ui.rightSprite.visible);
        manager.hide();
    }

    private static void testReplaySession() {
        GalgameReplayManager replay = GalgameReplayManager.instance;
        replay.hide();
        Vars.state.set(State.playing);

        DialogueModule module = new DialogueModule("probe-gal-replay", "replay")
                .addNode(line("replay-1"), line("replay-2"));
        module.isCompleted = true;
        module.progressIndex = module.dialogueNodes.size;
        module.saveModuleData();

        boolean[] hostClosed = { false };
        replay.hostExternally(() -> hostClosed[0] = true);
        if (replay.ui.getScene() == null && !replay.ui.hasParent()) {
            Core.scene.root.addChild(replay.ui);
        }
        replay.replayModule(module);
        check("starting replay keeps its external host open", false, hostClosed[0]);
        check("replay session becomes visible", true, replay.isShowing());
        check("replay session enters typing state", DialoguePlaybackState.typing,
                replay.playbackState());
        check("replay starts from the first node", "replay-1",
                replay.currentLine == null ? null : replay.currentLine.content);
        check("replay does not change module progress",
                module.dialogueNodes.size, module.progressIndex);
        replay.hide();
        check("replay cleanup closes its external host", true, hostClosed[0]);
        check("replay cleanup returns to idle", DialoguePlaybackState.idle,
                replay.playbackState());
    }

    private static Texture allocateDummyTexture() {
        try {
            var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            var unsafe = (sun.misc.Unsafe) unsafeField.get(null);
            Texture texture = (Texture) unsafe.allocateInstance(Texture.class);
            texture.width = 1;
            texture.height = 1;
            return texture;
        } catch (Throwable t) {
            throw new RuntimeException("cannot allocate dummy texture", t);
        }
    }

    private static DialogueLine line(String text) {
        return new DialogueLine("npc", null, text);
    }

    private static void check(String label, int expected, int actual) {
        boolean pass = expected == actual;
        ok &= pass;
        System.out.println("[GAL] " + label + ": " + actual + "/" + expected + (pass ? " OK" : " FAIL"));
    }

    private static void check(String label, float expected, float actual) {
        boolean pass = Math.abs(expected - actual) < 0.001f;
        ok &= pass;
        System.out.println("[GAL] " + label + ": " + actual + "/" + expected + (pass ? " OK" : " FAIL"));
    }

    private static void check(String label, String expected, String actual) {
        boolean pass = expected == null ? actual == null : expected.equals(actual);
        ok &= pass;
        System.out.println("[GAL] " + label + ": " + actual + "/" + expected + (pass ? " OK" : " FAIL"));
    }

    private static void check(String label, Object expected, Object actual) {
        boolean pass = expected == actual;
        ok &= pass;
        System.out.println("[GAL] " + label + ": " + actual + "/" + expected + (pass ? " OK" : " FAIL"));
    }

    private static void check(String label, boolean expected, boolean actual) {
        boolean pass = expected == actual;
        ok &= pass;
        System.out.println("[GAL] " + label + ": " + actual + "/" + expected + (pass ? " OK" : " FAIL"));
    }
}
