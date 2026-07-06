package crystal.type;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.math.Mathf;
import arc.scene.ui.Image;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Scaling;
import arc.util.Strings;
import crystal.entities.shentong.ShenTong;
import crystal.entities.units.UnitEnum.XiuWei;
import crystal.game.CEventType.MagicPowerChange;
import crystal.gen.Magicc;
import crystal.gen.MechMagicUnit;
import crystal.graphics.CPal;
import crystal.util.DLog;
import crystal.world.meta.CStat;
import crystal.world.meta.CStatValues;
import mindustry.Vars;
import mindustry.ai.types.LogicAI;
import mindustry.content.Blocks;
import mindustry.entities.abilities.Ability;
import mindustry.gen.Iconc;
import mindustry.gen.Payloadc;
import mindustry.gen.Unit;
import mindustry.graphics.Pal;
import mindustry.type.UnitType;
import mindustry.ui.Bar;
import mindustry.world.blocks.environment.Floor;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;
import mindustry.world.meta.StatValues;

import static mindustry.Vars.*;

public class MagicUnitType extends UnitType implements MagicUnitInterface {
  public float magicPower = 200f;
  public float magicPowerRegen = 1f;
  public float magicPowerRegenTime = 120f;
  public XiuWei xiuWei = XiuWei.yong;
  public Seq<ShenTong> shenTongs = new Seq<>();
  public boolean useCircleCoat = true;
  // 被打死获得的修为
  public float xiuWeiAmount = 1;

  public MagicUnitType(String name) {
    super(name);
    constructor = MechMagicUnit::create;
  }

  @Override
  public void init() {
    super.init();
  }

  @Override
  public void load() {
    super.load();
  }

  @Override
  public float magicPower() {
    return magicPower;
  }

  @Override
  public float magicPowerRegen() {
    return magicPowerRegen;
  }

  @Override
  public float magicPowerRegenTime() {
    return magicPowerRegenTime;
  }

  @Override
  public XiuWei xiuWei() {
    return xiuWei;
  }

  @Override
  public Seq<ShenTong> shenTongs() {
    return shenTongs;
  }

  @Override
  public float xiuWeiAmount() {
    return xiuWeiAmount;
  }

  @Override
  public boolean useCircleCoat() {
    return useCircleCoat;
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
    stats.add(CStat.xiuWei, this.xiuWei.str);
    if (this.magicPower > 0) {
      stats.add(CStat.magicPower, this.magicPower);
      stats.add(CStat.magicPowerRegen, this.magicPowerRegen * 60);
      stats.add(CStat.magicPowerRegenTime, this.magicPowerRegenTime / 60);
    }
    if (abilities.any()) {
      stats.add(Stat.abilities, StatValues.abilities(abilities));
    }
    if (shenTongs.any()) {
      stats.add(CStat.shenTong, CStatValues.shenTongs(this, shenTongs));
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
      stats.add(Stat.weapons, StatValues.weapons(this, weapons));
    }

    if (immunities.size > 0) {
      stats.add(Stat.immunities, StatValues.statusEffects(immunities.toSeq().sort()));
    }
  }

  @Override
  public void display(Unit unit, Table table) {
    table.table(t -> {
      t.left();
      t.add(new Image(uiIcon)).size(iconMed).scaling(Scaling.fit);
      t.labelWrap(unit.isPlayer() ? unit.getPlayer().coloredName() + "\n[lightgray]" + localizedName : localizedName)
          .left().width(190f).padLeft(5);
    }).growX().left();
    table.row();
    table.table(bars -> {
      bars.defaults().growX().height(20f).pad(4);
      bars.add(new Bar("stat.health", Pal.health, unit::healthf).blink(Color.white));
      bars.row();
      if (unit instanceof Magicc magic) {
        bars.add(
            new Bar(magic.xiuWei().str + Core.bundle.get("magicpower"), CPal.magicColor1,
                () -> magic.magicPower() / magic.maxMagicPower()).blink(Color.white));
        bars.row();
        for (var s : magic.shenTongs()) {
          s.setBar(magic, bars);
        }
      }
      for (Ability ability : unit.abilities) {
        ability.displayBars(unit, bars);
      }

      if (payloadCapacity > 0 && unit instanceof Payloadc payload) {
        bars.add(new Bar("stat.payloadcapacity", Pal.items, () -> payload.payloadUsed() / payloadCapacity));
        bars.row();

        var count = new float[] { -1 };
        bars.table().update(t -> {
          if (count[0] != payload.payloadUsed()) {
            payload.contentInfo(t, 8 * 2, 270);
            count[0] = payload.payloadUsed();
          }
        }).growX().left().height(0f).pad(0f);
      }
    }).growX();

    if (unit.controller() instanceof LogicAI ai) {
      table.row();
      table.add(Blocks.microProcessor.emoji() + " " + Core.bundle.get("units.processorcontrol")).growX().wrap().left();
      if (ai.controller != null && (Core.settings.getBool("mouseposition") || Core.settings.getBool("position"))) {
        table.row();
        table.add("[lightgray](" + ai.controller.tileX() + ", " + ai.controller.tileY() + ")").growX().wrap().left();
      }
      table.row();
      table.label(() -> Iconc.settings + " " + (long) unit.flag + "").color(Color.lightGray).growX().wrap().left();
      if (net.active() && ai.controller != null && ai.controller.lastAccessed != null) {
        table.row();
        table.add(Core.bundle.format("lastaccessed", ai.controller.lastAccessed)).growX().wrap().left();
      }
    } else if (net.active() && unit.lastCommanded != null) {
      table.row();
      table.add(Core.bundle.format("lastcommanded", unit.lastCommanded)).growX().wrap().left();
    }

    table.row();
  }

  @Override
  public void killed(Unit unit) {
    if (unit.team != Vars.player.team() && Vars.state.isCampaign())
      Events.fire(new MagicPowerChange(
          xiuWeiAmount * (Math.max(1f, XiuWei.xiuWeiMultiplier(xiuWei)))));
  }
}
