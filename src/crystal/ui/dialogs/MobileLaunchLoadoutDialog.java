package crystal.ui.dialogs;

import arc.Core;
import arc.Events;
import arc.audio.Music;
import arc.func.Cons;
import arc.math.Mathf;
import arc.scene.ui.Button;
import arc.scene.ui.ButtonGroup;
import arc.scene.ui.layout.Scl;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Align;
import arc.util.Time;
import crystal.type.CoreUnit;
import crystal.world.blocks.stroage.CoreInjector;
import crystal.world.blocks.stroage.MobileCoreLaunch;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.game.Schematic;
import mindustry.game.EventType.WorldLoadEndEvent;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.gen.Icon;
import mindustry.gen.Player;
import mindustry.gen.Unit;
import mindustry.type.Item;
import mindustry.type.ItemSeq;
import mindustry.type.ItemStack;
import mindustry.type.Sector;
import mindustry.type.UnitType;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.ui.dialogs.LoadoutDialog;
import mindustry.ui.dialogs.SchematicsDialog.SchematicImage;
import mindustry.world.blocks.storage.CoreBlock;
import mindustry.world.modules.ItemModule;

import static mindustry.Vars.*;

import java.lang.reflect.Field;

public class MobileLaunchLoadoutDialog extends BaseDialog {
  LoadoutDialog loadout = new LoadoutDialog();
  // 总需求物品
  ItemSeq total = new ItemSeq();
  // 当前选中的蓝图（方块核心模式用）
  Schematic selectedSchem;
  // 当前选中的移动核心类型（移动核心模式用）
  UnitType selectedMobileCore;
  // 当前核心模式：true = 移动核心，false = 方块核心
  boolean mobileMode = false;
  // 资源是否有效
  boolean valid;
  // 上次计算的容量
  int lastCapacity;
  // 可用的移动核心类型列表，你自己往里加
  public static Seq<UnitType> mobileCores = new Seq<>();

  public MobileLaunchLoadoutDialog() {
    super("@configure");
    for (var unit : content.units()) {
      if (unit instanceof CoreUnit) {
        mobileCores.add(unit);
      }
    }
  }

  public void show(CoreBlock core, Sector sector, Sector destination, Runnable confirm) {
    cont.clear();
    buttons.clear();
    buttons.defaults().size(160f, 64f);
    buttons.button("@back", Icon.left, this::hide);
    addCloseListener();
    ItemSeq sitems = sector.items();
    // 隐藏不在该星球的物品
    ItemSeq launch = universe.getLaunchResources();
    if (sector.planet.allowLaunchLoadout) {
      for (var item : content.items()) {
        if (!item.isOnPlanet(sector.planet)) {
          launch.set(item, 0);
        }
      }
      universe.updateLaunchResources(launch);
    }
    // 更新总需求和有效性
    Runnable update = () -> {
      int cap;
      if (mobileMode && selectedMobileCore instanceof CoreUnit cu) {
        // 移动核心模式：容量用移动核心的存储容量
        cap = lastCapacity = (int) (sector.planet.launchCapacityMultiplier * cu.storageCapacity());
      } else if (selectedSchem != null) {
        // 方块核心模式：容量用核心方块容量
        cap = lastCapacity = (int) (sector.planet.launchCapacityMultiplier * selectedSchem.findCore().itemCapacity);
      } else {
        cap = lastCapacity = 0;
      }
      ItemSeq schems = mobileMode ? new ItemSeq() : selectedSchem.requirements();
      ItemSeq resources = universe.getLaunchResources();
      resources.min(cap);
      int capacity = lastCapacity;
      if (!destination.allowLaunchLoadout()) {
        resources.clear();
        if (destination.preset != null) {
          var rules = destination.preset.generator.map.rules();
          for (var stack : rules.loadout) {
            if (stack.item.isOnPlanet(sector.planet)) {
              resources.add(stack.item, stack.amount);
            }
          }
        }
      } else if (getMax()) {
        for (Item item : content.items()) {
          resources.set(item, Mathf.clamp(sitems.get(item) - schems.get(item), 0, capacity));
        }
      }
      universe.updateLaunchResources(resources);
      total.clear();
      if (!mobileMode && selectedSchem != null) {
        selectedSchem.requirements().each(total::add);
      }
      universe.getLaunchResources().each(total::add);
      valid = sitems.has(total) || CPlanetDialog.debugSelect;
    };
    Cons<Table> rebuild = table -> {
      table.clearChildren();
      int i = 0;
      ItemSeq schems = mobileMode ? new ItemSeq() : selectedSchem.requirements();
      ItemSeq launches = universe.getLaunchResources();
      for (ItemStack s : total) {
        int as = schems.get(s.item), al = launches.get(s.item);
        if (as + al == 0)
          continue;
        table.image(s.item.uiIcon).left().size(iconSmall);
        String amountStr = (al + as) + (destination.allowLaunchLoadout() ? "[gray] (" + (al + " + " + as + ")") : "");
        table.add(
            sitems.has(s.item, s.amount) ? amountStr
                : "[scarlet]" + (Math.min(sitems.get(s.item), s.amount) + "[lightgray]/" + amountStr))
            .padLeft(2).left().padRight(4);
        if (++i % 4 == 0) {
          table.row();
        }
      }
    };
    Table items = new Table();
    Runnable rebuildItems = () -> rebuild.get(items);
    // 资源配置按钮
    if (destination.allowLaunchLoadout()) {
      buttons.button("@resources.max", Icon.add, Styles.togglet, () -> {
        setMax(!getMax());
        update.run();
        rebuildItems.run();
      }).checked(b -> getMax());
      buttons.button("@resources", Icon.edit, () -> {
        ItemSeq stacks = universe.getLaunchResources();
        Seq<ItemStack> out = stacks.toSeq();
        ItemSeq realItems = sitems.copy();
        if (!mobileMode && selectedSchem != null) {
          selectedSchem.requirements().each(realItems::remove);
        }
        loadout.show(lastCapacity, realItems, out, i -> i.unlocked() && i.isOnPlanet(sector.planet), out::clear, () -> {
        }, () -> {
          universe.updateLaunchResources(new ItemSeq(out));
          update.run();
          rebuildItems.run();
        });
      }).disabled(b -> getMax());
    }
    boolean rows = Core.graphics.isPortrait() && mobile;
    if (rows)
      buttons.row();
    var cell = buttons.button("@launch.text", Icon.ok, () -> {
      if (mobileMode) {
        // 移动核心模式：用 Core.settings 保存选择的移动核心类型
        Core.settings.put("mobile_core_type", selectedMobileCore.name);
      } else {
        // 方块核心模式：原版逻辑
        universe.updateLoadout(core, selectedSchem);
        Core.settings.put("mobile_core_type", "");
      }
      confirm.run();
      hide();
    }).disabled(b -> !valid);
    if (rows) {
      cell.colspan(2).size(160f + 160f + 4f, 64f);
    }
    Table contentTable = new Table();
    int cols = Math.max((int) (Core.graphics.getWidth() / Scl.scl(230)), 1);
    Runnable rebuildContent = () -> {
      contentTable.clear();
      if (mobileMode) {
        // ===== 移动核心模式：显示移动核心类型列表 =====
        ButtonGroup<Button> group = new ButtonGroup<>();
        contentTable.pane(t -> {
          int[] i = { 0 };
          for (UnitType type : mobileCores) {
            if (!(type instanceof CoreUnit))
              continue;
            if (!type.unlocked())
              continue; // 只显示已解锁的
            CoreUnit cu = (CoreUnit) type;
            t.button(b -> {
              b.left();
              b.image(type.uiIcon).size(iconMed).padRight(8f);
              b.table(info -> {
                info.add(type.localizedName).left().row();
                info.add("[lightgray]容量: " + cu.storageCapacity()).left().fontScale(0.8f).row();
                info.add("[lightgray]单位上限: +" + cu.unitCapBonus()).left().fontScale(0.8f);
              }).left();
            }, Styles.togglet, () -> {
              selectedMobileCore = type;
              update.run();
              rebuildItems.run();
            }).group(group).pad(4).checked(type == selectedMobileCore)
                .size(280f, 70f).left();
            if (++i[0] % cols == 0) {
              t.row();
            }
          }
        }).growX().scrollX(false);
      } else if (destination.allowLaunchSchematics()) {
        // ===== 方块核心模式：原版蓝图选择 =====
        ButtonGroup<Button> group = new ButtonGroup<>();
        if (selectedSchem == null)
          selectedSchem = schematics.getLoadouts().get((CoreBlock) Blocks.coreShard).first();
        contentTable.pane(t -> {
          int[] i = { 0 };
          Cons<Schematic> handler = s -> {
            if (s.tiles.contains(tile -> !tile.block.supportsEnv(sector.planet.defaultEnv) ||
                !tile.block.isOnPlanet(sector.planet))) {
              return;
            }
            t.button(b -> b.add(new SchematicImage(s)), Styles.togglet, () -> {
              selectedSchem = s;
              update.run();
              rebuildItems.run();
            }).group(group).pad(4).checked(s == selectedSchem).size(200f);
            if (++i[0] % cols == 0) {
              t.row();
            }
          };
          if (destination.allowLaunchSchematics() || schematics.getDefaultLoadout(core) == null) {
            for (var entry : schematics.getLoadouts()) {
              if (entry.key.size <= core.size) {
                for (Schematic s : entry.value) {
                  handler.get(s);
                }
              }
            }
          } else {
            handler.get(schematics.getDefaultLoadout(core));
          }
        }).growX().scrollX(false);
      } else if (destination.preset != null && destination.preset.description != null) {
        contentTable.pane(p -> {
          p.add(destination.preset.description).grow().wrap().labelAlign(Align.center);
        }).pad(10f).grow();
      }
    };
    // ===== 核心模式切换 =====
    cont.table(t -> {
      ButtonGroup<Button> modeGroup = new ButtonGroup<>();
      t.button("方块核心", Icon.boxSmall, Styles.togglet, () -> {
        mobileMode = false;
        rebuildContent.run();
        update.run();
        rebuildItems.run();
      }).group(modeGroup).checked(b -> !mobileMode).size(140f, 50f).padRight(8f);
      t.button("移动核心", Icon.units, Styles.togglet, () -> {
        mobileMode = true;
        // 默认选第一个已解锁的移动核心
        if (selectedMobileCore == null) {
          for (UnitType unit : mobileCores) {
            if (unit.unlocked()) {
              selectedMobileCore = unit;
              break;
            }
          }
        }
        rebuildContent.run(); // 加上这行
        update.run();
        rebuildItems.run();
      }).group(modeGroup).checked(b -> mobileMode).size(140f, 50f);
    }).left().padBottom(8f).row();
    cont.add(Core.bundle.format("launch.from", sector.name())).left().row();
    // ===== 内容区：根据模式显示不同内容 =====
    cont.add(contentTable).growX().row();
    rebuildContent.run();
    cont.label(() -> Core.bundle.format("launch.capacity", lastCapacity)).left().padTop(4f).row();
    cont.pane(items).growX().row();
    cont.add("@sector.missingresources").visible(() -> !valid).left();
    update.run();
    rebuildItems.run();
    show();
  }

  void setMax(boolean max) {
    Core.settings.put("maxresources", max);
  }

  boolean getMax() {
    return Core.settings.getBool("maxresources", true);
  }

  public static void init() {
    // 用 WorldLoadEvent，世界开始加载时就准备好
    Events.on(WorldLoadEvent.class, e -> {
      if (!Vars.state.isCampaign())
        return;

      String coreName = Core.settings.getString("mobile_core_type", "");
      if (coreName.isEmpty())
        return;

      UnitType type = Vars.content.unit(coreName);
      if (!(type instanceof crystal.type.CoreUnit))
        return;

      // 延迟到世界加载完成后再替换（WorldLoadEvent 时地图还没加载完）
      Time.runTask(2f, () -> {
        CoreBlock.CoreBuild originCore = Vars.player.team().core();
        if (originCore == null)
          return;

        ItemModule items = originCore.items.copy();
        float x = originCore.x();
        float y = originCore.y();

        // 移除原版核心
        originCore.tile.remove();

        // 创建移动核心（位置就在降落点，elevation 控制高度）
        Unit mobileCore = type.create(Vars.player.team());
        mobileCore.set(x, y);
        mobileCore.elevation = 1f;
        mobileCore.add();

        // 立即注入核心系统，确保 bestCore() 能找到
        if (mobileCore instanceof crystal.gen.Corec mc) {
          mc.items().set(items);
          CoreInjector.injectCore(Vars.player.team().data(), mc);
        }

        // 跳过动画
        if (Core.settings.getBool("skipcoreanimation") || Vars.state.rules.pvp) {
          mobileCore.elevation(0f);
          Core.camera.position.set(mobileCore);
          Core.settings.put("mobile_core_type", "");
          return;
        }

        // 启动降落动画
        float duration = 120f;
        MobileCoreLaunch animator = new MobileCoreLaunch(mobileCore, x, y, duration);

        try {
          java.lang.reflect.Field animField = Vars.renderer.getClass().getDeclaredField("launchAnimator");
          animField.setAccessible(true);
          animField.set(Vars.renderer, animator);

          java.lang.reflect.Field launchField = Vars.renderer.getClass().getDeclaredField("launching");
          launchField.setAccessible(true);
          launchField.setBoolean(Vars.renderer, false);

          Vars.renderer.landTime = duration;

          if (Core.settings.getInt("musicvol") > 0) {
            Music music = animator.landMusic();
            if (music != null) {
              music.stop();
              music.play();
              music.setVolume(Core.settings.getInt("musicvol") / 100f);
            }
          }

          Vars.player.deathTimer = Player.deathDelay - duration;
        } catch (Exception ex) {
          ex.printStackTrace();
        }

        Core.settings.put("mobile_core_type", "");
      });
    });

  }
}
