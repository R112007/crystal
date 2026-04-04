package crystal.type;

import arc.Core;
import arc.math.Mathf;
import arc.struct.Seq;
import arc.util.Strings;
import crystal.entities.units.MultiStageMechUnit;
import crystal.entities.units.UnitEnum.Mode;
import crystal.type.weapons.StageWeapon;
import crystal.world.meta.CStatValues;
import mindustry.entities.abilities.Ability;
import mindustry.gen.Payloadc;
import mindustry.type.UnitType;
import mindustry.world.blocks.environment.Floor;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;
import mindustry.world.meta.StatValues;

import static arc.Core.bundle;
import static mindustry.Vars.*;

public class MultiStageUnitType extends UnitType {
  public Mode mode;
  public Seq<Ability> abStage2 = new Seq<>(), abStage3 = new Seq<>(), abStage4 = new Seq<>();

  public MultiStageUnitType(String name) {
    super(name);
    this.constructor = MultiStageMechUnit::create;
  }

  @Override
  public void load() {
    super.load();
  }

  @Override
  public void setStats() {
    stats.add(Stat.health, health);
    stats.add(Stat.armor, armor);
    stats.add(Stat.speed, speed * 60f / tilesize, StatUnit.tilesSecond);
    stats.add(Stat.size, StatValues.squared(hitSize / tilesize, StatUnit.blocks));
    stats.add(Stat.itemCapacity, itemCapacity);
    stats.add(Stat.range, Strings.autoFixed(maxRange / tilesize, 1), StatUnit.blocks);

    if (crushDamage > 0) {
      stats.add(Stat.crushDamage, crushDamage * 60f * 5f, StatUnit.perSecond);
    }
    if (legSplashDamage > 0 && legSplashRange > 0) {
      stats.add(Stat.legSplashDamage, table -> {
        table.add((Core.bundle.format("bullet.splashdamage", Strings.autoFixed(legSplashDamage, 2),
            Strings.autoFixed(legSplashRange / tilesize, 2))).replace("[stat]", "[white]") + " "
            + StatUnit.perLeg.localized());
      });
    }

    stats.add(Stat.targetsAir, targetAir);
    stats.add(Stat.targetsGround, targetGround);

    if (abilities.any()) {
      stats.add(Stat.abilities, CStatValues.abilities(abilities, bundle.get("stage.ability" + "1")));
    }
    if (abStage2.any()) {
      stats.add(Stat.abilities, CStatValues.abilities(abStage2, bundle.get("stage.ability") + "2"));
    }
    if (abStage3.any()) {
      stats.add(Stat.abilities, CStatValues.abilities(abStage3, bundle.get("stage.ability") + "3"));
    }
    if (abStage4.any()) {
      stats.add(Stat.abilities, CStatValues.abilities(abStage4, bundle.get("stage.ability") + "4"));
    }

    stats.add(Stat.flying, flying);

    if (!flying) {
      stats.add(Stat.canBoost, canBoost);
    }

    if (mineTier >= 1) {
      stats.addPercent(Stat.mineSpeed, mineSpeed);
      stats.add(Stat.mineTier, StatValues.drillables(mineSpeed, 1f, 1, null, b -> b.itemDrop != null &&
          (b instanceof Floor f && (((f.wallOre && mineWalls) || (!f.wallOre && mineFloor))) ||
              (!(b instanceof Floor) && mineWalls))
          &&
          b.itemDrop.hardness <= mineTier && (!b.playerUnmineable || Core.settings.getBool("doubletapmine"))));
    }
    if (buildSpeed > 0) {
      stats.addPercent(Stat.buildSpeed, buildSpeed);
    }
    if (sample instanceof Payloadc) {
      stats.add(Stat.payloadCapacity,
          StatValues.squared(Mathf.sqrt(payloadCapacity / (tilesize * tilesize)), StatUnit.blocks));
    }

    var reqs = getFirstRequirements();

    if (reqs != null) {
      stats.add(Stat.buildCost, StatValues.items(reqs));
    }

    if (weapons.any()) {
      stats.add(Stat.weapons, CStatValues.stageWeapons(this, weapons));
    }

    if (immunities.size > 0) {
      stats.add(Stat.immunities, StatValues.statusEffects(immunities.toSeq().sort()));
    }
  }

  @Override
  public void init() {
    super.init();
    for (Ability a : abStage2) {
      a.init(this);
    }
    for (Ability a : abStage3) {
      a.init(this);
    }
    for (Ability a : abStage4) {
      a.init(this);
    }
    for (var w : weapons) {
      if (!(w instanceof StageWeapon))
        throw new IllegalArgumentException("MultitStageUnitType must use StageWeapon……");
    }
    for (var w : weapons) {
      if (w instanceof StageWeapon s)
        if (s.weappnStage > 4)
          throw new IllegalArgumentException("StageWeapon:" + s.name + "hava a high stage" + s.weappnStage);
    }
  }
}
