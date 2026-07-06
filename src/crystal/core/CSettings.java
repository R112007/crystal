package crystal.core;

import arc.Core;
import arc.struct.ObjectMap;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import crystal.CVars;
import crystal.content.CIcons;
import crystal.ui.gal.DialogueHistory;
import crystal.ui.gal.DialogueLine;
import crystal.ui.gal.DialogueModule;
import crystal.ui.gal.GalgameDialogueManager;
import mindustry.Vars;
import mindustry.gen.Icon;
import mindustry.io.JsonIO;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.SettingsMenuDialog;
import mindustry.ui.dialogs.SettingsMenuDialog.SettingsTable;

import static mindustry.Vars.*;

public class CSettings {
  public static final CSettings instance = new CSettings();
  private static final String KEY_PLAYER_NAME = "crystal_player_name";
  private static final String KEY_DIALOGUE_HISTORY = "crystal_gal_history";

  static {
    mindustry.io.JsonIO.json.addClassTag("DialogueHistory", crystal.ui.gal.DialogueHistory.class);
  }

  private CSettings() {
  }

  private boolean isBlank(String str) {
    return str == null || str.trim().isEmpty();
  }

  public String getPlayerName() {
    return Core.settings.getString(KEY_PLAYER_NAME, "");
  }

  /** 保存玩家名字，自动同步到剧情角色 */
  public void setPlayerName(String name) {
    String finalName = name == null ? "" : name.trim();
    Core.settings.put(KEY_PLAYER_NAME, finalName);
    Core.settings.autosave();
  }

  /** 判断是否已有名字存档 */
  public boolean hasPlayerName() {
    return !isBlank(getPlayerName());
  }

  public ObjectMap<String, Object> getAllSettings() {
    ObjectMap<String, Object> result = new ObjectMap<>();
    for (String key : Core.settings.keys()) {
      result.put(key, Core.settings.get(key, null));
    }
    return result;
  }

  /** 清除模块所有分支（修复并发修改异常） */
  public static void load() {
    ui.settings.addCategory(Core.bundle.get("settings.crystal"), CIcons.crystalCore, table -> {
      table.checkPref("showXiuWei", true);
      table.row();
      table.checkPref("showContinueButton", true);
      table.row();
      table.checkPref("showgongfabuquan", true);
      table.row();

      // --- 清空所有历史 ---
      table.pref(new SettingsMenuDialog.SettingsTable.Setting("crystal.clearallhistory") {
        @Override
        public void add(SettingsTable t) {
          t.button(Core.bundle.get("clearallhistory"), Icon.trash, Styles.flatt, () -> {
            ui.showConfirm(Core.bundle.get("confirm"), Core.bundle.get("clearallhistory.confirm"), () -> {
              instance.clearAllDialogueHistory();
              ui.showInfo(Core.bundle.get("clearallhistory.done"));
            });
          }).size(300f, 60f).left().marginLeft(4f).padTop(8f);
          t.row();
        }
      });

      // --- 修改玩家名字 ---
      table.pref(new SettingsMenuDialog.SettingsTable.Setting("crystal.changeplayername") {
        @Override
        public void add(SettingsTable t) {
          t.button(Core.bundle.get("changeplayername"), Icon.edit, Styles.flatt, () -> {
            Vars.ui.showTextInput(
                Core.bundle.get("changeplayername.title"),
                Core.bundle.get("changeplayername.content"),
                16,
                CSettings.instance.getPlayerName(),
                false,
                newName -> {
                  String trimName = newName == null ? "" : newName.trim();
                  if (CSettings.instance.isBlank(trimName)) {
                    Vars.ui.showErrorMessage(Core.bundle.get("changeplayername.nonull"));
                    return;
                  }
                  CSettings.instance.setPlayerName(trimName);
                  CVars.playerName = trimName;
                  Storys.inst.player.name = trimName;
                  Vars.ui.showInfo(Core.bundle.format("changeplayername.done", trimName));
                  Core.app.exit();
                },
                () -> {
                });
          }).size(300f, 60f).left().marginLeft(4f).padTop(8f);
          t.row();
        }
      });

      // --- 清空所有模块进度 ---
      table.pref(new SettingsMenuDialog.SettingsTable.Setting("crystal.clearallmodel") {
        @Override
        public void add(SettingsTable t) {
          t.button(Core.bundle.get("clearallmodel"), Icon.trash, Styles.flatt, () -> {
            ui.showConfirm(Core.bundle.get("confirm"), Core.bundle.get("clearallmodel.confirm"), () -> {
              GalgameDialogueManager.instance.resetAllProgress();
              ui.showInfo(Core.bundle.get("clearallmodel.done"));
            });
          }).size(300f, 60f).left().marginLeft(4f).padTop(8f);
          t.row();
        }
      });
    });
  }

  // 清除所有对话历史（含本地存储）
  public void clearAllDialogueHistory() {
    GalgameDialogueManager.instance.clearAllHistory();
    Core.settings.remove(KEY_DIALOGUE_HISTORY);
    Core.settings.forceSave();
  }

  public void clearModuleHistory(String moduleId) {
    DialogueModule module = GalgameDialogueManager.instance.getModule(moduleId);
    if (module != null) {
      module.history.clear();
      module.playedNodeSet.clear();
    }
    Core.settings.forceSave();
  }

}
