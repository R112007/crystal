package crystal.type;

import arc.Core;
import arc.graphics.Color;
import arc.util.Log;
import arc.util.Scaling;
import crystal.content.CUnitCommands;
import crystal.world.meta.CStat;
import mindustry.content.UnitTypes;
import mindustry.entities.TargetPriority;
import mindustry.type.UnitType;
import mindustry.ui.Styles;
import mindustry.world.blocks.storage.CoreBlock;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;
import static mindustry.Vars.*;

public class CoreUnitType extends UnitType implements CoreUnit {
  public int storageCapacity = 3000; // 存储容量
  public float suckRange = 160f, auxiliaryRange = 200f; // 自动吸取范围
  public int unitCapBonus = 10; // 单位容量加成
  public CoreBlock core;
  public UnitType unit = UnitTypes.alpha;

  public CoreUnitType(String name) {
    super(name);
    useUnitCap = false;
    targetPriority = TargetPriority.core;
    core = new CoreBlock(this.name + "Core") {
      {
        health = (int) CoreUnitType.this.health;
        itemCapacity = CoreUnitType.this.storageCapacity;
        unitCapModifier = CoreUnitType.this.unitCapBonus;
      }
    };
  }

  @Override
  public void init() {
    super.init();
    core.health = (int) this.health;
    core.itemCapacity = this.storageCapacity;
    core.unitCapModifier = this.unitCapBonus;
    core.unitType = this.unit;
    commands.add(CUnitCommands.coreAuxiliaryCommand);
    Log.info("Commands for " + name + ": " + commands);
  }

  @Override
  public void setStats() {
    super.setStats();
    stats.add(CStat.storageCapacity, storageCapacity);
    stats.add(Stat.range, suckRange / 8, StatUnit.blocks);
    stats.add(Stat.unitType, table -> {
      table.row();
      table.table(Styles.grayPanel, b -> {
        b.image(unit.uiIcon).size(40).pad(10f).left().scaling(Scaling.fit);
        b.table(info -> {
          info.add(unit.localizedName).left();
          if (Core.settings.getBool("console")) {
            info.row();
            info.add(unit.name).left().color(Color.lightGray);
          }
        });
        b.button("?", Styles.flatBordert, () -> ui.content.show(unit)).size(40f).pad(10).right().grow()
            .visible(() -> unit.unlockedNow());
      }).growX().pad(5).row();
    });
  }

  public CoreBlock core() {
    return core;
  }

  public float auxiliaryRange() {
    return auxiliaryRange;
  }

  public int storageCapacity() {
    return storageCapacity;
  }

  public float suckRange() {
    return suckRange;
  }

  public int unitCapBonus() {
    return unitCapBonus;
  }
}
