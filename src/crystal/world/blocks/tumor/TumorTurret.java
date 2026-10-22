package crystal.world.blocks.tumor;

import arc.graphics.g2d.Draw;
import arc.util.io.Reads;
import arc.util.io.Writes;
import crystal.world.tumor.TissueState;
import crystal.world.tumor.TumorNetwork;
import crystal.world.tumor.TumorTissue;
import mindustry.gen.Building;
import mindustry.type.Item;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import mindustry.world.meta.BuildVisibility;

import static mindustry.Vars.*;

/** Organic gun that can synthesize ammunition directly from the parent core's mineral stock. */
public class TumorTurret extends ItemTurret {
  public TumorTurret(String name) {
    super(name);
    size = 1;
    health = 620;
    armor = 4f;
    range = 185f;
    reload = 28f;
    maxAmmo = 40;
    rotateSpeed = 8f;
    targetAir = true;
    targetGround = true;
    update = true;
    destructible = true;
    buildVisibility = BuildVisibility.sandboxOnly;
    alwaysUnlocked = true;
  }

  @Override
  public boolean canPlaceOn(mindustry.world.Tile tile, mindustry.game.Team team, int rotation) {
    return TumorNetwork.allowsManualPlacement()
        && super.canPlaceOn(tile, team, rotation);
  }

  public class TumorTurretBuild extends ItemTurretBuild implements TumorTissue {
    public final TissueState tissue = new TissueState();
    public int appliedLevel;
    public float supplyTimer;

    @Override
    public TissueState tumor() {
      return tissue;
    }

    @Override
    public void updateTile() {
      if (TumorNetwork.updateTissue(this, tissue) || net.client()) return;

      int level = TumorNetwork.levelOf(this, tissue);
      if (level != appliedLevel) {
        appliedLevel = level;
        float ratio = maxHealth <= 0f ? 1f : Math.min(1f, health / maxHealth);
        maxHealth = block.health * (1f + (level - 1) * 0.48f);
        health = maxHealth * ratio;
      }

      supplyTimer += 1f;
      if (supplyTimer >= 15f) {
        supplyTimer = 0f;
        refillFromCore();
      }
      super.updateTile();
    }

    private void refillFromCore() {
      if (totalAmmo >= maxAmmo) return;
      var core = TumorNetwork.rootCore(this, tissue);
      if (core == null || core.items == null || core.items.empty()) return;

      int room = maxAmmo - totalAmmo;
      for (Item item : TumorNetwork.mineralDrops) {
        int stored = core.items.get(item);
        if (stored <= 0 || !ammoTypes.containsKey(item)) continue;
        int amount = Math.min(Math.min(stored, room), 8);
        if (amount <= 0) return;
        core.items.remove(item, amount);
        handleStack(item, amount, core);
        return;
      }
    }

    @Override
    public float range() {
      return super.range() * (1f + (Math.max(appliedLevel, 1) - 1) * 0.1f);
    }

    @Override
    protected float baseReloadSpeed() {
      return super.baseReloadSpeed() * (1f + (Math.max(appliedLevel, 1) - 1) * 0.12f);
    }

    @Override
    public void draw() {
      Draw.alpha(tissue.visualAlpha());
      super.draw();
      Draw.reset();
    }

    @Override
    public byte version() {
      return 3;
    }

    @Override
    public void write(Writes write) {
      super.write(write);
      tissue.write(write);
      write.i(appliedLevel);
      write.f(supplyTimer);
    }

    @Override
    public void read(Reads read, byte revision) {
      super.read(read, revision);
      tissue.read(read, revision);
      read.i();
      appliedLevel = 0;
      supplyTimer = read.f();
    }
  }
}
