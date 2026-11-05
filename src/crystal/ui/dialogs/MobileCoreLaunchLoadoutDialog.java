package crystal.ui.dialogs;

import arc.Core;
import arc.func.Cons;
import arc.graphics.Color;
import arc.math.Mathf;
import arc.scene.ui.Button;
import arc.scene.ui.ButtonGroup;
import arc.scene.ui.layout.Scl;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Align;
import crystal.type.CoreUnit;
import crystal.type.CoreUnitType;
import crystal.type.MobileCoreSectorPreset;
import crystal.world.blocks.stroage.MobileCoreControlSystem;
import mindustry.content.Blocks;
import mindustry.game.Schematic;
import mindustry.gen.Icon;
import mindustry.type.Item;
import mindustry.type.ItemSeq;
import mindustry.type.ItemStack;
import mindustry.type.Sector;
import mindustry.type.UnitType;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.LaunchLoadoutDialog;
import mindustry.ui.dialogs.LoadoutDialog;
import mindustry.ui.dialogs.SchematicsDialog.SchematicImage;
import mindustry.world.blocks.storage.CoreBlock;

import static mindustry.Vars.*;

/**
 * Launch loadout replacement for mobile-core sectors.
 *
 * <p>Marked sectors go directly to mobile-core selection. Ordinary
 * {@link mindustry.type.SectorPreset}s can choose between a regular core and a
 * mobile core; the mobile-core-only control restriction remains exclusive to
 * marked sectors.</p>
 */
public class MobileCoreLaunchLoadoutDialog extends LaunchLoadoutDialog {
  private final LoadoutDialog resourceEditor = new LoadoutDialog();
  private final ItemSeq mobileTotal = new ItemSeq();
  private final ItemSeq mobileRequirements = new ItemSeq();
  private final ItemSeq entityTotal = new ItemSeq();
  private final Seq<UnitType> mobileCores = new Seq<>();

  private UnitType selectedCore;
  private Schematic selectedSchematic;
  private boolean mobileLaunch;
  private int mobileCapacity;
  private boolean mobileValid;
  private int entityCapacity;
  private boolean entityValid;

  public MobileCoreLaunchLoadoutDialog() {
    super();
    refreshCoreTypes();
  }

  @Override
  public void show(CoreBlock core, Sector from, Sector destination, Runnable confirm) {
    mobileLaunch = false;
    selectedSchematic = null;
    selectedCore = null;
    refreshCoreTypes();

    if (MobileCoreSectorPreset.isMarked(destination)) {
      mobileLaunch = true;
      selectedCore = selectDefaultCore(destination);
      buildMobileDialog(core, from, destination, confirm);
      return;
    }

    if (destination.preset != null) {
      showEntityCoreDialog(core, from, destination, confirm);
      return;
    }

    super.show(core, from, destination, confirm);
  }

  private void showEntityCoreDialog(CoreBlock core, Sector from, Sector destination, Runnable confirm) {
    mobileLaunch = false;
    buildEntityCoreDialog(core, from, destination, confirm);
  }

  public boolean isMobileCoreLaunch() {
    return mobileLaunch;
  }

  public UnitType selectedMobileCore() {
    return selectedCore;
  }

  private void addModeSwitch(CoreBlock core, Sector from, Sector destination, Runnable confirm, boolean mobileMode) {
    boolean marked = MobileCoreSectorPreset.isMarked(destination);
    ButtonGroup<Button> group = new ButtonGroup<>();
    cont.table(modes -> {
      modes.defaults().height(52f).pad(3f);
      modes.button(Core.bundle.get("waves.launch.blockcore"), Icon.boxSmall, Styles.togglet, () -> {
        if (mobileMode) {
          Core.settings.put(MobileCoreControlSystem.launchCoreSetting, "");
          showEntityCoreDialog(core, from, destination, confirm);
        }
      }).group(group).checked(b -> !mobileMode).disabled(marked).width(190f)
          .tooltip(marked ? Core.bundle.get("waves.launch.mobileonly") : "");
      modes.button(Core.bundle.get("waves.launch.mobilecore"), Icon.units, Styles.togglet, () -> {
        if (!mobileMode) {
          selectedCore = selectDefaultCore(destination);
          buildMobileDialog(core, from, destination, confirm);
        }
      }).group(group).checked(b -> mobileMode).width(190f);
    }).left().padBottom(6f).row();
  }

  private void buildEntityCoreDialog(CoreBlock core, Sector from, Sector destination, Runnable confirm) {
    cont.clear();
    buttons.clear();
    buttons.defaults().size(160f, 64f);
    buttons.button("@back", Icon.left, this::hide);
    addCloseListener();
    addModeSwitch(core, from, destination, confirm, false);

    ItemSeq sectorItems = from.items();
    ItemSeq launchItems = universe.getLaunchResources();
    if (from.planet.allowLaunchLoadout) {
      for (Item item : content.items()) {
        if (!item.isOnPlanet(from.planet)) {
          launchItems.set(item, 0);
        }
      }
      universe.updateLaunchResources(launchItems);
    }

    Runnable update = () -> {
      if (selectedSchematic == null || selectedSchematic.findCore() == null) {
        entityCapacity = 0;
        entityTotal.clear();
        entityValid = false;
        return;
      }

      entityCapacity = (int) (from.planet.launchCapacityMultiplier
          * selectedSchematic.findCore().itemCapacity);
      ItemSeq schematicRequirements = selectedSchematic.requirements();
      ItemSeq resources = universe.getLaunchResources();
      resources.min(entityCapacity);

      if (!destination.allowLaunchLoadout()) {
        resources.clear();
        if (destination.preset != null && destination.preset.generator != null) {
          var rules = destination.preset.generator.map.rules();
          for (ItemStack stack : rules.loadout) {
            if (stack.item.isOnPlanet(from.planet)) {
              resources.add(stack.item, stack.amount);
            }
          }
        }
      } else if (getMax()) {
        for (Item item : content.items()) {
          resources.set(item, Mathf.clamp(
              sectorItems.get(item) - schematicRequirements.get(item), 0, entityCapacity));
        }
      }

      universe.updateLaunchResources(resources);
      entityTotal.clear();
      schematicRequirements.each(entityTotal::add);
      resources.each(entityTotal::add);
      entityValid = sectorItems.has(entityTotal) || CPlanetDialog.debugSelect;
    };

    Table resourceTable = new Table();
    Runnable rebuildResources = () -> {
      resourceTable.clearChildren();
      if (selectedSchematic == null) {
        return;
      }
      ItemSeq requirements = selectedSchematic.requirements();
      ItemSeq launched = universe.getLaunchResources();
      int index = 0;
      for (ItemStack stack : entityTotal) {
        int schematicAmount = requirements.get(stack.item);
        int launchAmount = launched.get(stack.item);
        if (schematicAmount + launchAmount <= 0) {
          continue;
        }
        resourceTable.image(stack.item.uiIcon).left().size(iconSmall);
        String amount = launchAmount + schematicAmount
            + (destination.allowLaunchLoadout() ? "[gray] (" + launchAmount + " + " + schematicAmount + ")" : "");
        resourceTable.add(
            sectorItems.has(stack.item, stack.amount)
                ? amount
                : "[scarlet]" + Math.min(sectorItems.get(stack.item), stack.amount) + "[lightgray]/" + amount)
            .padLeft(2f).left().padRight(4f);
        if (++index % 4 == 0) {
          resourceTable.row();
        }
      }
    };

    if (destination.allowLaunchLoadout()) {
      buttons.button("@resources.max", Icon.add, Styles.togglet, () -> {
        setMax(!getMax());
        update.run();
        rebuildResources.run();
      }).checked(b -> getMax());

      buttons.button("@resources", Icon.edit, () -> {
        if (selectedSchematic == null) {
          return;
        }
        ItemSeq available = sectorItems.copy();
        selectedSchematic.requirements().each(available::remove);
        Seq<ItemStack> selected = universe.getLaunchResources().toSeq();
        resourceEditor.show(
            entityCapacity,
            available,
            selected,
            item -> item.unlocked() && item.isOnPlanet(from.planet),
            selected::clear,
            () -> {
            },
            () -> {
              universe.updateLaunchResources(new ItemSeq(selected));
              update.run();
              rebuildResources.run();
            });
      }).disabled(b -> getMax());
    }

    boolean portrait = Core.graphics.isPortrait() && mobile;
    if (portrait) {
      buttons.row();
    }

    var launchButton = buttons.button("@launch.text", Icon.ok, () -> {
      if (selectedSchematic == null || !entityValid) {
        return;
      }
      universe.updateLoadout(core, selectedSchematic);
      Core.settings.put(MobileCoreControlSystem.launchCoreSetting, "");
      confirm.run();
      hide();
    }).disabled(b -> !entityValid);

    if (portrait) {
      launchButton.colspan(3).size(484f, 64f);
    }

    if (selectedSchematic == null) {
      selectedSchematic = universe.getLoadout(core);
      if (selectedSchematic == null) {
        var defaults = schematics.getLoadouts().get((CoreBlock) Blocks.coreShard);
        selectedSchematic = defaults == null || defaults.isEmpty() ? null : defaults.first();
      }
    }

    cont.add(Core.bundle.format("launch.from", from.name())).left().row();

    int columns = Math.max((int) (Core.graphics.getWidth() / Scl.scl(230f)), 1);
    ButtonGroup<Button> schematicGroup = new ButtonGroup<>();
    if (destination.allowLaunchSchematics()) {
      cont.pane(table -> {
        int[] index = { 0 };
        Cons<Schematic> handler = schematic -> {
          if (schematic.tiles.contains(tile -> !tile.block.supportsEnv(from.planet.defaultEnv)
              || !tile.block.isOnPlanet(from.planet))) {
            return;
          }
          table.button(button -> button.add(new SchematicImage(schematic)), Styles.togglet, () -> {
            selectedSchematic = schematic;
            update.run();
            rebuildResources.run();
          }).group(schematicGroup).pad(4f).checked(schematic == selectedSchematic).size(200f);
          if (++index[0] % columns == 0) {
            table.row();
          }
        };

        if (destination.allowLaunchSchematics() || schematics.getDefaultLoadout(core) == null) {
          for (var entry : schematics.getLoadouts()) {
            if (entry.key.size <= core.size) {
              for (Schematic schematic : entry.value) {
                handler.get(schematic);
              }
            }
          }
        } else {
          handler.get(schematics.getDefaultLoadout(core));
        }
      }).growX().scrollX(false).row();
    } else if (destination.preset != null && destination.preset.description != null) {
      cont.pane(table -> table.add(destination.preset.description)
          .grow().wrap().labelAlign(Align.center)).pad(10f).grow().row();
    }

    cont.label(() -> Core.bundle.format("launch.capacity", entityCapacity))
        .left().row();
    cont.pane(resourceTable).growX().row();
    cont.add("@sector.missingresources").visible(() -> !entityValid)
        .color(Color.scarlet).left().row();

    update.run();
    rebuildResources.run();
    show();
  }

  private void buildMobileDialog(CoreBlock core, Sector from, Sector destination, Runnable confirm) {
    boolean marked = MobileCoreSectorPreset.isMarked(destination);
    cont.clear();
    buttons.clear();
    buttons.defaults().size(160f, 64f);
    buttons.button("@back", Icon.left, this::hide);
    addCloseListener();
    addModeSwitch(core, from, destination, confirm, true);

    ItemSeq sectorItems = from.items();
    ItemSeq launchItems = universe.getLaunchResources();
    if (from.planet.allowLaunchLoadout) {
      for (Item item : content.items()) {
        if (!item.isOnPlanet(from.planet)) {
          launchItems.set(item, 0);
        }
      }
      universe.updateLaunchResources(launchItems);
    }

    Table requirementsTable = new Table();
    Runnable rebuildRequirements = () -> {
      requirementsTable.clearChildren();
      requirementsTable.add("[lightgray]核心建造：").left();
      if (isFreeStartLaunch(destination)) {
        requirementsTable.add("[green]起始区块免费").left();
      } else if (mobileRequirements.toSeq().isEmpty()) {
        requirementsTable.add("[lightgray]无").left();
      } else {
        for (ItemStack stack : mobileRequirements) {
          requirementsTable.image(stack.item.uiIcon).size(iconSmall).padLeft(5f);
          requirementsTable.add(String.valueOf(stack.amount)).color(Color.lightGray).padLeft(2f);
        }
      }
    };

    Runnable update = () -> {
      mobileRequirements.clear();
      if (!(selectedCore instanceof CoreUnitType coreType)
          || !selectedCore.unlockedNow()
          || !selectedCore.isOnPlanet(from.planet)) {
        mobileCapacity = 0;
        mobileTotal.clear();
        mobileValid = false;
        rebuildRequirements.run();
        return;
      }

      if (!isFreeStartLaunch(destination) && coreType.requirements != null) {
        for (ItemStack stack : coreType.requirements) {
          mobileRequirements.add(stack.item, stack.amount);
        }
      }

      mobileCapacity = (int) (from.planet.launchCapacityMultiplier * coreType.storageCapacity());
      ItemSeq resources = universe.getLaunchResources();
      resources.min(mobileCapacity);

      if (!destination.allowLaunchLoadout()) {
        resources.clear();
        if (destination.preset != null) {
          var rules = destination.preset.generator.map.rules();
          for (ItemStack stack : rules.loadout) {
            if (stack.item.isOnPlanet(from.planet)) {
              resources.add(stack.item, stack.amount);
            }
          }
        }
      } else if (getMax()) {
        ItemSeq available = sectorItems.copy();
        mobileRequirements.each(available::remove);
        for (Item item : content.items()) {
          resources.set(item, Mathf.clamp(available.get(item), 0, mobileCapacity));
        }
      }

      universe.updateLaunchResources(resources);
      mobileTotal.clear();
      resources.each(mobileTotal::add);
      ItemSeq requiredTotal = mobileTotal.copy();
      mobileRequirements.each(requiredTotal::add);
      mobileValid = sectorItems.has(requiredTotal) || CPlanetDialog.debugSelect;
      rebuildRequirements.run();
    };

    Table resourceTable = new Table();
    Runnable rebuildResources = () -> {
      resourceTable.clearChildren();
      int index = 0;
      ItemSeq launched = universe.getLaunchResources();
      for (ItemStack stack : mobileTotal) {
        int amount = launched.get(stack.item);
        if (amount <= 0) {
          continue;
        }
        resourceTable.image(stack.item.uiIcon).left().size(iconSmall);
        resourceTable.add(
            sectorItems.has(stack.item, amount)
                ? String.valueOf(amount)
                : "[scarlet]" + Math.min(sectorItems.get(stack.item), amount) + "[lightgray]/" + amount)
            .padLeft(2f).left().padRight(4f);
        if (++index % 4 == 0) {
          resourceTable.row();
        }
      }
    };

    if (destination.allowLaunchLoadout()) {
      buttons.button("@resources.max", Icon.add, Styles.togglet, () -> {
        setMax(!getMax());
        update.run();
        rebuildResources.run();
      }).checked(b -> getMax());

      buttons.button("@resources", Icon.edit, () -> {
        ItemSeq available = sectorItems.copy();
        mobileRequirements.each(available::remove);
        Seq<ItemStack> selected = universe.getLaunchResources().toSeq();
        resourceEditor.show(
            mobileCapacity,
            available,
            selected,
            item -> item.unlocked() && item.isOnPlanet(from.planet),
            selected::clear,
            () -> {
            },
            () -> {
              universe.updateLaunchResources(new ItemSeq(selected));
              update.run();
              rebuildResources.run();
            });
      }).disabled(b -> getMax());
    }

    boolean portrait = Core.graphics.isPortrait() && mobile;
    if (portrait) {
      buttons.row();
    }

    var launchButton = buttons.button("@launch.text", Icon.ok, () -> {
      if (selectedCore == null) {
        return;
      }
      Core.settings.put(
          marked ? MobileCoreControlSystem.preferredCoreSetting : MobileCoreControlSystem.launchCoreSetting,
          selectedCore.name);
      if (marked) {
        Core.settings.put(MobileCoreControlSystem.launchCoreSetting, "");
      }
      confirm.run();
      hide();
    }).disabled(b -> !mobileValid || selectedCore == null);

    if (portrait) {
      launchButton.colspan(3).size(484f, 64f);
    }

    cont.add("[accent]移动核心发射[]").left().row();
    cont.add(marked
        ? "[lightgray]玩家将直接控制移动核心本体，不召唤或附身其他单位。"
        : "[lightgray]移动核心将作为本区块核心，玩家控制其配置的核心机。")
        .left().row();

    int columns = Math.max((int) (Core.graphics.getWidth() / Scl.scl(230f)), 1);
    ButtonGroup<Button> group = new ButtonGroup<>();
    cont.pane(table -> {
      int index = 0;
      for (UnitType type : mobileCores) {
        if (!(type instanceof CoreUnit coreType)
            || !type.unlockedNow()
            || !type.isOnPlanet(from.planet)) {
          continue;
        }
        table.button(button -> {
          button.left();
          button.image(type.uiIcon).size(iconMed).padRight(8f);
          button.table(info -> {
            info.add(type.localizedName).left().row();
            info.add("[lightgray]容量: " + coreType.storageCapacity()).left().fontScale(0.8f).row();
            info.add("[lightgray]单位上限: +" + coreType.unitCapBonus()).left().fontScale(0.8f);
          }).left();
        }, Styles.togglet, () -> {
          selectedCore = type;
          update.run();
          rebuildResources.run();
        }).group(group).pad(4f).checked(type == selectedCore).size(280f, 72f);

        if (++index % columns == 0) {
          table.row();
        }
      }
    }).growX().scrollX(false).row();

    cont.add(requirementsTable).growX().left().row();
    cont.label(() -> Core.bundle.format("launch.capacity", mobileCapacity))
        .left().padTop(4f).row();
    cont.pane(resourceTable).growX().row();
    cont.add("@sector.missingresources").visible(() -> !mobileValid)
        .color(Color.scarlet).left().row();
    cont.add(Core.bundle.format("launch.from", from.name()))
        .color(Color.lightGray).left().padTop(6f).row();

    update.run();
    rebuildResources.run();
    show();
  }

  private void refreshCoreTypes() {
    mobileCores.clear();
    for (UnitType type : content.units()) {
      if (type instanceof CoreUnit) {
        mobileCores.add(type);
      }
    }
  }

  private UnitType selectDefaultCore(Sector destination) {
    UnitType remembered = MobileCoreControlSystem.preferredCoreType();
    if (isSelectable(remembered, destination)) {
      return remembered;
    }
    for (UnitType type : mobileCores) {
      if (isSelectable(type, destination)) {
        return type;
      }
    }
    return null;
  }

  private boolean isSelectable(UnitType type, Sector destination) {
    return type instanceof CoreUnit && type.unlockedNow() && type.isOnPlanet(destination.planet);
  }

  public static boolean isFreeStartLaunch(Sector destination) {
    return destination != null && destination.preset instanceof MobileCoreSectorPreset preset
        && preset.startSector;
  }

  private void setMax(boolean max) {
    Core.settings.put("maxresources", max);
  }

  private boolean getMax() {
    return Core.settings.getBool("maxresources", true);
  }
}
