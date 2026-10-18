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
import crystal.ui.gal.GalgameDialogueManager;
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
        Texture dummyTexture;
        try {
            var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            var unsafe = (sun.misc.Unsafe) unsafeField.get(null);
            dummyTexture = (Texture) unsafe.allocateInstance(Texture.class);
            dummyTexture.width = 1;
            dummyTexture.height = 1;
        } catch (Throwable t) {
            throw new RuntimeException("cannot allocate dummy texture", t);
        }

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
        replayCopy.onComplete.run();
        replayCopy.beforePlay.run();
        replayCopy.options[0].onSelect.get(replayCopy.options[0]);
        replayCalls[0] = historyCalls[0];
        check("replay copy keeps callbacks", 3, replayCalls[0]);
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
        manager.isPlayingModule = true;
        manager.conditionalWaitingIds.add(dependent.moduleId);
        prerequisite.isCompleted = true;
        manager.checkConditionalTriggers(prerequisite.moduleId);
        check("completed prerequisite releases dependent", false, manager.conditionalWaitingIds.contains(dependent.moduleId));
        check("released dependent enters play queue", true, manager.waitingModuleIds.contains(dependent.moduleId));

        manager.waitingModuleIds.add("probe-gal-missing");
        manager.saveWaitingQueue();
        manager.waitingModuleIds.clear();
        manager.loadWaitingQueue();
        check("invalid waiting id is discarded", false, manager.waitingModuleIds.contains("probe-gal-missing"));
        check("valid waiting id is restored", true, manager.waitingModuleIds.contains(dependent.moduleId));
    }

    private static DialogueLine line(String text) {
        return new DialogueLine("npc", null, text);
    }

    private static void check(String label, int expected, int actual) {
        boolean pass = expected == actual;
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
