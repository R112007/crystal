package crystal.world.blocks.effect;

import arc.Events;
import arc.math.Mathf;
import arc.scene.ui.layout.Table;
import crystal.content.CFx;
import crystal.game.CEventType.SummonUnitEvent;
import crystal.ui.CStyles;
import crystal.world.blocks.environment.SpawnBossFloor;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.content.UnitTypes;
import mindustry.entities.Damage;
import mindustry.entities.Effect;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Icon;
import mindustry.type.Item;
import mindustry.type.UnitType;
import mindustry.ui.Styles;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.environment.Floor;

import static mindustry.Vars.*;

public class SummonUnitBlock extends Block {
  public int seq = 0;
  public Effect deadEffect = Fx.none;
  public UnitType type;
  public float delay;

  public SummonUnitBlock(String name) {
    super(name);
    solid = true;
    update = true;
    configurable = true;
    createRubble = false;
    destroyEffect = Fx.none;
    rebuildable = false;
  }

  @Override
  public boolean canPlaceOn(Tile tile, Team team, int rotation) {
    Floor floor = tile.floor();
    if (floor instanceof SpawnBossFloor boss) {
      if (boss.seq == this.seq)
        return true;
    }
    return false;
  }

  public class SummonUnitBuild extends Building {
    public boolean ready = true;

    public Building init(Tile tile, Team team, boolean shouldAdd, int rotation) {
      return super.init(tile, team, shouldAdd, rotation);
    }

    @Override
    public void updateTile() {
    }

    @Override
    public void killed() {
      deadEffect.at(x, y);
      super.killed();
    }

    @Override
    public void onDestroyed() {
      float flammability = 0.0F;
      float power = 0.0F;
      if (block.hasItems) {
        for (Item item : content.items()) {
          int amount = Math.min(items.get(item), explosionItemCap());
          flammability += item.flammability * amount;
          power += item.charge * Mathf.pow(amount, 1.1F) * 150.0F;
        }
      }
      if (block.hasLiquids) {
        flammability += liquids.sum((liquid, amount) -> liquid.flammability * amount / 2.0F);
      }
      if (block.consPower != null && block.consPower.buffered) {
        power += this.power.status * block.consPower.capacity;
      }
      if (block.hasLiquids && state.rules.damageExplosions) {
        liquids.each(this::splashLiquid);
      }
      Damage.dynamicExplosion(x, y, flammability * block.flammabilityScale,
          0, power, tilesize * block.size / 2.0F,
          state.rules.damageExplosions, block.destroyEffect, 0);
      if (block.createRubble && !floor().solid && !floor().isLiquid) {
        Effect.rubble(x, y, block.size);
      }
    }

    @Override
    public void buildConfiguration(Table table) {
      if (ready) {
        table.button(Icon.upOpen, CStyles.lightBlueButton, () -> {
          Events.fire(
              new SummonUnitEvent(UnitTypes.dagger, x + offset, y + offset, CFx.spawn1, Vars.state.rules.waveTeam, 3));
          killed();
          deselect();
        }).size(50, 40);
      } else {
        table.button(Icon.upOpen, Styles.cleari, () -> {
          deselect();
        }).size(40f);
      }
    }
  }
}
