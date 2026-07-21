package crystal.type;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.TextureRegion;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Scaling;
import crystal.content.CUnitCommands;
import crystal.gen.Corec;
import crystal.gen.RetractableLegsCoreUnit;
import crystal.world.blocks.stroage.CoreUnitFactory;
import crystal.world.blocks.stroage.MCoreBlock;
import crystal.world.blocks.stroage.CoreUnitFactory.CoreUnitPlan;
import crystal.world.meta.CStat;
import mindustry.Vars;
import mindustry.content.UnitTypes;
import mindustry.entities.TargetPriority;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.gen.Unit;
import mindustry.type.ItemStack;
import mindustry.type.UnitType;
import mindustry.type.Weapon;
import mindustry.type.weapons.MineWeapon;
import mindustry.ui.Styles;
import mindustry.world.Block;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;
import static mindustry.Vars.*;

public class CoreUnitType extends UnitType implements CoreUnit {
  public int storageCapacity = 3000; // 存储容量
  public float suckRange = 160f, auxiliaryRange = 200f; // 自动吸取范围
  public int unitCapBonus = 10; // 单位容量加成
  public MCoreBlock core;
  public UnitType unit = UnitTypes.gamma;
  public boolean deployDrawWeapon = true;
  public int deployBlockSize;
  public TextureRegion rightTopBase, rightUnderBase, leftTopBase, leftUnderBase;
  public String entity = "legs";
  public static Seq<CoreUnitType> coreTypes = new Seq<>();

  public CoreUnitType(String name) {
    super(name);
    useUnitCap = false;
    targetPriority = TargetPriority.core;
    core = new MCoreBlock(this.name + "Core") {
      {
        health = (int) CoreUnitType.this.health;
        itemCapacity = CoreUnitType.this.storageCapacity;
        unitCapModifier = CoreUnitType.this.unitCapBonus;
      }
    };
  }

  @Override
  public ItemStack[] getRequirements(UnitType[] prevReturn, float[] timeReturn) {
    // 1. 先执行父类原版逻辑（原生UnitFactory/重构器/组装台）
    ItemStack[] base = super.getRequirements(prevReturn, timeReturn);
    if (base != null)
      return base;

    // 2. 遍历全局所有方块，检索自定义CoreUnitFactory
    for (Block block : Vars.content.blocks()) {
      if (block instanceof CoreUnitFactory factory) {
        // 匹配当前单位的plan
        CoreUnitPlan match = factory.plans.find(p -> p.unit == this);
        if (match != null) {
          // 回传生产时长（可选，用于统计）
          if (timeReturn != null) {
            timeReturn[0] = match.buildTime;
          }
          // 返回当前plan的耗材，作为研究花费计算基数
          return match.requirements;
        }
      }
    }
    // 无任何工厂配方时返回空
    return null;
  }

  @Override
  public void init() {
    coreTypes.add(this);
    core.health = (int) this.health;
    core.itemCapacity = this.storageCapacity;
    core.unitCapModifier = this.unitCapBonus;
    core.unitType = this.unit;
    if (weapons.contains(w -> w instanceof MineWeapon)) {
      drawMineBeam = false;
    }
    if (allowedInPayloads) {
      allowedInPayloads = false;
    }
    switch (entity) {
      case "legs":
        this.constructor = RetractableLegsCoreUnit::create;
        break;

      default:
        throw new RuntimeException(name + "has not entity,please add entity for it");
    }
    super.init();
    commands.add(CUnitCommands.coreAuxiliaryCommand);
    /*
     * weapons.add(new Weapon() {
     * {
     * x = y = 0;
     * top = false;
     * display = false;
     * bullet = new BasicBulletType(0, 0) {
     * {
     * width = height = 0.01f;
     * lifetime = 0;
     * }
     * };
     * reload = Float.MAX_VALUE;
     * }
     * });
     */
  }

  @Override
  public void setStats() {
    super.setStats();
    stats.add(CStat.storageCapacity, storageCapacity);
    stats.add(CStat.suckRange, suckRange / 8, StatUnit.blocks);
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

  @Override
  public MCoreBlock core() {
    return core;
  }

  @Override
  public float auxiliaryRange() {
    return auxiliaryRange;
  }

  @Override
  public int storageCapacity() {
    return storageCapacity;
  }

  @Override
  public float suckRange() {
    return suckRange;
  }

  @Override
  public int unitCapBonus() {
    return unitCapBonus;
  }

  @Override
  public void drawWeapons(Unit unit) {
    Corec corec = (Corec) unit;
    if (corec.deployed() && deployDrawWeapon == false)
      return;
    super.drawWeapons(unit);
  }

  @Override
  public void drawWeaponOutlines(Unit unit) {
    Corec corec = (Corec) unit;
    if (corec.deployed() && deployDrawWeapon == false)
      return;
    super.drawWeapons(unit);
  }
}
