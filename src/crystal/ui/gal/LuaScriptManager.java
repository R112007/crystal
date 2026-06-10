package crystal.ui.gal;

import java.io.Reader;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

import arc.Core;
import arc.files.Fi;
import arc.struct.ObjectMap;
import arc.util.Log;
import mindustry.Vars;

public class LuaScriptManager {
  public static final LuaScriptManager instance = new LuaScriptManager();
  private Globals globals;
  private final ObjectMap<String, DialogueModule> moduleCache = new ObjectMap<>();
  private static final String SCRIPT_FOLDER = "lua";
  private Fi scriptDir;

  private long lastModifiedTime = 0;
  private boolean watcherRunning = false;

  private LuaScriptManager() {
  }

  public void init() {
    // 初始化脚本目录
    scriptDir = Core.files.internal(SCRIPT_FOLDER);
    if (!scriptDir.exists()) {
      scriptDir.mkdirs();
      Log.info("[Lua] 创建Lua脚本目录: " + scriptDir.absolutePath());
    }

    // 初始化Lua环境并加载所有脚本
    reloadAllScripts();

    // 启动自动热重载监听线程
    if (!watcherRunning) {
      startScriptWatcher();
      watcherRunning = true;
    }
  }

  public void reloadAllScripts() {
    // 清空缓存
    moduleCache.clear();

    // 创建全新的Lua环境，避免旧脚本残留
    globals = JsePlatform.standardGlobals();

    // 注册所有Java API到Lua环境
    registerAllJavaAPIs();

    // 加载所有.lua脚本文件
    if (scriptDir.exists() && scriptDir.isDirectory()) {
      Fi[] files = scriptDir.list();
      int loadedCount = 0;

      for (Fi file : files) {
        if (file.extEquals("lua")) {
          loadSingleScript(file);
          loadedCount++;
        }
      }

      Log.info("[Lua] 脚本加载完成，共加载 " + loadedCount + " 个模块");
      Vars.ui.showInfoFade("Lua脚本已刷新: " + loadedCount + " 个模块");
    } else {
      Log.warn("[Lua] 脚本目录不存在或为空");
    }
  }

  /**
   * 加载单个Lua脚本文件
   * 使用Arc原生Fi.reader()，完美兼容Android APK内资源
   */
  private void loadSingleScript(Fi file) {
    try (Reader reader = file.reader("UTF-8")) {
      // 编译并执行Lua脚本
      LuaValue chunk = globals.load(reader, file.name());
      chunk.call();
      Log.info("[Lua] 成功加载脚本: " + file.name());
    } catch (Exception e) {
      Log.err("[Lua] 加载脚本失败: " + file.name(), e);
      Vars.ui.showErrorMessage("脚本错误: " + file.name() + "\n" + e.getMessage());
    }
  }

  /**
   * 注册所有Java API到Lua环境
   * 这里添加你需要在Lua中调用的所有功能
   */
  private void registerAllJavaAPIs() {
    // ==================== 1. 核心模块定义函数 ====================
    globals.set("module", LuaValue.valueOf((LuaValue[] args) -> {
      if (args.length < 2) {
        Log.err("[Lua] module函数需要两个参数: moduleId, moduleName");
        return LuaValue.NIL;
      }

      String moduleId = args[0].tojstring();
      String moduleName = args[1].tojstring();
      DialogueModule module = new DialogueModule(moduleId, moduleName);

      // 加入缓存
      moduleCache.put(moduleId, module);

      // 设置为当前模块，后续的say/choice都会添加到这个模块
      globals.set("currentModule", LuaValue.userdataOf(module));

      return LuaValue.NIL;
    }));

    // ==================== 2. 对话函数 ====================
    globals.set("say", LuaValue.valueOf((LuaValue[] args) -> {
      DialogueModule module = (DialogueModule) globals.get("currentModule").touserdata();
      if (module == null) {
        Log.err("[Lua] 没有当前模块，请先调用module()函数");
        return LuaValue.NIL;
      }

      if (args.length < 3) {
        Log.err("[Lua] say函数需要三个参数: 角色, 表情, 内容");
        return LuaValue.NIL;
      }

      Character character = (Character) args[0].touserdata();
      Expression expression = Expression.valueOf(args[1].tojstring());
      String content = args[2].tojstring();

      DialogueLine line = new DialogueLine(character, expression, content);

      // 可选参数：onComplete回调
      if (args.length >= 4 && !args[3].isnil()) {
        line.onComplete = () -> args[3].call();
      }

      module.addNode(line);
      return LuaValue.NIL;
    }));

    // ==================== 3. 选项分支函数 ====================
    globals.set("choice", LuaValue.valueOf((LuaValue[] args) -> {
      DialogueModule module = (DialogueModule) globals.get("currentModule").touserdata();
      if (module == null) {
        Log.err("[Lua] 没有当前模块，请先调用module()函数");
        return LuaValue.NIL;
      }

      DialogueLine.DialogueOption[] options = new DialogueLine.DialogueOption[args.length];
      for (int i = 0; i < args.length; i++) {
        LuaValue option = args[i];
        if (!option.istable()) {
          Log.err("[Lua] choice参数必须是表: {\"选项文本\", 回调函数}");
          continue;
        }

        String text = option.get(1).tojstring();
        LuaValue callback = option.get(2);

        options[i] = new DialogueLine.DialogueOption(text, opt -> callback.call());
      }

      // 添加一个空的对话行来承载选项
      module.addNode(new DialogueLine(null, null, "", options));
      return LuaValue.NIL;
    }));

    // ==================== 4. 全局角色对象 ====================
    // 确保Storys已经初始化了这些角色
    globals.set("yi", LuaValue.userdataOf(crystal.core.Storys.inst.yi));
    globals.set("player", LuaValue.userdataOf(crystal.core.Storys.inst.player));
    globals.set("background", LuaValue.userdataOf(crystal.core.Storys.inst.background));

    // ==================== 5. 表情枚举 ====================
    for (Expression exp : Expression.values()) {
      globals.set(exp.name(), LuaValue.valueOf(exp.name()));
    }

    // ==================== 6. 好感度系统 ====================
    globals.set("affection", LuaValue.tableOf(new LuaValue[] {
        LuaValue.valueOf("increase"), LuaValue.valueOf((LuaValue[] args) -> {
          int amount = args.length > 0 ? args[0].toint() : 1;
          Affection.affection.increaseAffection(amount);
          Log.info("[Lua] 好感度增加: " + amount + "，当前: " + Affection.affection.getYiAffection());
          return LuaValue.NIL;
        }),
        LuaValue.valueOf("decrease"), LuaValue.valueOf((LuaValue[] args) -> {
          int amount = args.length > 0 ? args[0].toint() : 1;
          Affection.affection.decreaseAffection(amount);
          Log.info("[Lua] 好感度减少: " + amount + "，当前: " + Affection.affection.getYiAffection());
          return LuaValue.NIL;
        }),
        LuaValue.valueOf("get"), LuaValue.valueOf(() -> LuaValue.valueOf(Affection.affection.getYiAffection()))
    }));

    // ==================== 7. 功法系统 ====================
    globals.set("gongfa", LuaValue.tableOf(new LuaValue[] {
        LuaValue.valueOf("unlock"), LuaValue.valueOf((LuaValue[] args) -> {
          if (args.length < 1) {
            Log.err("[Lua] gongfa.unlock需要功法ID参数");
            return LuaValue.NIL;
          }

          String id = args[0].tojstring();
          // 在这里添加你所有的功法解锁逻辑
          switch (id) {
            case "taiXuanTianGong1":
              GongFas.taiXuanTianGong1.unlock();
              Log.info("[Lua] 解锁功法: 太玄天宫第一层");
              break;
            // 在这里添加更多功法
            default:
              Log.warn("[Lua] 未知功法ID: " + id);
              break;
          }
          return LuaValue.NIL;
        })
    }));

    // ==================== 8. 完整角色动画系统 ====================
    globals.set("action", LuaValue.tableOf(new LuaValue[] {
        LuaValue.valueOf("shake"), LuaValue.valueOf(() -> {
          CharacterActions.shake(GalgameDialogueManager.instance.ui);
          return LuaValue.NIL;
        }),
        LuaValue.valueOf("shakeHard"), LuaValue.valueOf(() -> {
          CharacterActions.shakeHard(GalgameDialogueManager.instance.ui);
          return LuaValue.NIL;
        }),
        LuaValue.valueOf("floatUpDown"), LuaValue.valueOf(() -> {
          CharacterActions.floatUpDown(GalgameDialogueManager.instance.ui);
          return LuaValue.NIL;
        }),
        LuaValue.valueOf("runIn"), LuaValue.valueOf(() -> {
          CharacterActions.runIn(GalgameDialogueManager.instance.ui);
          return LuaValue.NIL;
        }),
        LuaValue.valueOf("runOut"), LuaValue.valueOf(() -> {
          CharacterActions.runOut(GalgameDialogueManager.instance.ui);
          return LuaValue.NIL;
        }),
        LuaValue.valueOf("fallDown"), LuaValue.valueOf(() -> {
          CharacterActions.fallDown(GalgameDialogueManager.instance.ui);
          return LuaValue.NIL;
        }),
        LuaValue.valueOf("hitBack"), LuaValue.valueOf(() -> {
          CharacterActions.hitBack(GalgameDialogueManager.instance.ui);
          return LuaValue.NIL;
        }),
        LuaValue.valueOf("jump"), LuaValue.valueOf(() -> {
          CharacterActions.jump(GalgameDialogueManager.instance.ui);
          return LuaValue.NIL;
        }),
        LuaValue.valueOf("shyShake"), LuaValue.valueOf(() -> {
          CharacterActions.shyShake(GalgameDialogueManager.instance.ui);
          return LuaValue.NIL;
        }),
        LuaValue.valueOf("nod"), LuaValue.valueOf(() -> {
          CharacterActions.nod(GalgameDialogueManager.instance.ui);
          return LuaValue.NIL;
        }),
        LuaValue.valueOf("shakeHead"), LuaValue.valueOf(() -> {
          CharacterActions.shakeHead(GalgameDialogueManager.instance.ui);
          return LuaValue.NIL;
        }),
        LuaValue.valueOf("heartbeat"), LuaValue.valueOf(() -> {
          CharacterActions.heartbeat(GalgameDialogueManager.instance.ui);
          return LuaValue.NIL;
        }),
        LuaValue.valueOf("shySteam"), LuaValue.valueOf(() -> {
          CharacterActions.shySteam(GalgameDialogueManager.instance.ui);
          return LuaValue.NIL;
        })
    }));

    // ==================== 9. 调试工具 ====================
    globals.set("log", LuaValue.valueOf((LuaValue[] args) -> {
      StringBuilder sb = new StringBuilder();
      for (LuaValue arg : args) {
        sb.append(arg.tojstring()).append(" ");
      }
      Log.info("[Lua] " + sb.toString().trim());
      return LuaValue.NIL;
    }));
  }

  public ObjectMap<String, DialogueModule> getLoadedModules() {
    return moduleCache;
  }

  /**
   * 启动脚本文件自动监听线程
   * 跨平台通用，避免Android FileObserver权限问题
   */
  private void startScriptWatcher() {
    Core.app.post(() -> {
      Log.info("[Lua] 自动热重载监听已启动");

      while (true) {
        try {
          Thread.sleep(1000); // 每秒检查一次

          if (scriptDir.exists() && scriptDir.isDirectory()) {
            long currentMaxModified = 0;

            // 找到最新修改的脚本文件时间
            for (Fi file : scriptDir.list()) {
              if (file.extEquals("lua")) {
                long modified = file.lastModified();
                if (modified > currentMaxModified) {
                  currentMaxModified = modified;
                }
              }
            }

            // 如果有文件更新，自动重载
            if (currentMaxModified > lastModifiedTime && lastModifiedTime != 0) {
              Log.info("[Lua] 检测到脚本变化，自动重载");
              reloadAllScripts();
            }

            lastModifiedTime = currentMaxModified;
          }
        } catch (InterruptedException e) {
          Log.info("[Lua] 自动热重载监听已停止");
          break;
        }
      }
    });
  }
}
