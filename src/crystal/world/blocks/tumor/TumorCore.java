package crystal.world.blocks.tumor;

import arc.Core;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import crystal.world.tumor.TumorNetwork;
import crystal.world.tumor.TumorRender;
import mindustry.gen.Building;
import mindustry.graphics.Layer;
import mindustry.type.Item;
import mindustry.type.Liquid;
import mindustry.world.Block;
import mindustry.world.meta.BuildVisibility;

import static mindustry.Vars.*;

/**
 * The only permanent organ of a tumor network. Its visual stage changes at every evolution level.
 */
public class TumorCore extends Block {
  public static final int maxLevel = 4;
  public static final float evolutionTicks = 45f * 60f;
  public final TextureRegion[][] frames = new TextureRegion[maxLevel][4];

  public TumorCore(String name) {
    super(name);
    size = 3;
    health = 9000;
    armor = 10f;
    solid = true;
    update = true;
    destructible = true;
    hasItems = true;
    itemCapacity = 1200;
    hasLiquids = true;
    liquidCapacity = 1000f;
    buildVisibility = BuildVisibility.sandboxOnly;
    alwaysUnlocked = true;
  }

  @Override
  public void load() {
    super.load();
    for (int level = 0; level < maxLevel; level++) {
      for (int frame = 0; frame < frames[level].length; frame++) {
        frames[level][frame] = Core.atlas.find(name + "-" + (level + 1) + "-" + frame, region);
      }
    }
    region = frames[0][0];
    fullIcon = region;
    uiIcon = region;
  }

  @Override
  public boolean canPlaceOn(mindustry.world.Tile tile, mindustry.game.Team team, int rotation) {
    return TumorNetwork.allowsManualPlacement()
        && super.canPlaceOn(tile, team, rotation);
  }

  public class TumorCoreBuild extends Building {
    public int level = 1;
    public float evolution;
    public float growthTimer;
    public float pulse;
    public boolean nurseryCreated;
    public int appliedLevel;
    public int growthCursor;
    public int defenseBuildings;
    public boolean defenseCountInitialized;
    public float sharedPower;
    public float sharedHeat;
    public float sharedPowerCapacity = 1000f;
    public float sharedHeatCapacity = 1000f;
    public float growthCredits;

    public int maxDefenseBuildings() {
      return 64 + level * 16;
    }

    public boolean consumeGrowthCredit() {
      if (growthCredits < 1f) return false;
      growthCredits -= 1f;
      return true;
    }

    @Override
    public void updateTile() {
      pulse += Time.delta;
      if (net.client()) return;

      int previous = level;
      evolution += Time.delta + 0.01f;
      level = Math.min(maxLevel, 1 + (int) (evolution / evolutionTicks));
      if (level != appliedLevel) {
        appliedLevel = level;
        float ratio = maxHealth <= 0f ? 1f : Math.min(1f, health / maxHealth);
        maxHealth = block.health * (1f + (level - 1) * 0.55f);
        health = maxHealth * ratio;
      }
      if (level > previous) {
        if (level >= 2) {
          nurseryCreated = true;
          TumorNetwork.ensureNursery(this);
        }
      }

      if (!defenseCountInitialized) {
        defenseBuildings = TumorNetwork.countDefenses(this);
        defenseCountInitialized = true;
      }

      float creditRate = 1f / (150f - (level - 1) * 15f);
      growthCredits = Math.min(2f, growthCredits + Time.delta * creditRate);
      growthTimer += Time.delta * (1f + (level - 1) * 0.18f);
      float interval = 240f - (level - 1) * 15f;
      if (growthTimer >= interval) {
        growthTimer = 0f;
        if (arc.math.Mathf.chance(0.08f + level * 0.04f)) TumorNetwork.tryBud(this);
      }

    }

    @Override
    public boolean acceptItem(Building source, Item item) {
      return items != null && items.total() < itemCapacity;
    }

    @Override
    public int getMaximumAccepted(Item item) {
      return Math.max(0, itemCapacity - items.total());
    }

    @Override
    public void handleItem(Building source, Item item) {
      if (items.total() >= itemCapacity) return;
      items.add(item, 1);
      // Ore delivered by the network also accelerates mutation, but time alone is enough.
      evolution += 3.5f;
    }

    @Override
    public boolean acceptLiquid(Building source, Liquid liquid) {
      return liquids != null && liquids.currentAmount() < liquidCapacity;
    }

    @Override
    public void handleLiquid(Building source, Liquid liquid, float amount) {
      if (liquids != null) liquids.add(liquid, amount);
    }

    public float addSharedPower(float amount) {
      float accepted = Math.min(Math.max(0f, amount), sharedPowerCapacity - sharedPower);
      sharedPower += accepted;
      return accepted;
    }

    public float consumeSharedPower(float amount) {
      float used = Math.min(Math.max(0f, amount), sharedPower);
      sharedPower -= used;
      return used;
    }

    public void addSharedHeat(float amount) {
      sharedHeat = Math.min(sharedHeatCapacity, sharedHeat + Math.max(0f, amount));
    }

    public void coolSharedHeat(float amount) {
      sharedHeat = Math.max(0f, sharedHeat - Math.max(0f, amount));
    }

    @Override
    public void onDestroyed() {
      TumorNetwork.collapseFromCore(this);
      super.onDestroyed();
    }

    @Override
    public void draw() {
      Draw.z(Layer.block);
      float pulseScale = 1f + 0.018f * (float) Math.sin(pulse * 0.055f);
      TextureRegion current = frames[level - 1][(int) (pulse / 10f) % frames[level - 1].length];
      TumorRender.draw(current, x, y, 0f, pulseScale);
      drawCracks();
    }

    @Override
    public byte version() {
      return 4;
    }

    @Override
    public void write(Writes write) {
      super.write(write);
      write.i(level);
      write.f(evolution);
      write.f(growthTimer);
      write.f(pulse);
      write.bool(nurseryCreated);
      write.i(growthCursor);
      write.i(defenseBuildings);
      write.f(sharedPower);
      write.f(sharedHeat);
      write.f(growthCredits);
    }

    @Override
    public void read(Reads read, byte revision) {
      super.read(read, revision);
      level = Math.max(1, Math.min(maxLevel, read.i()));
      evolution = read.f();
      growthTimer = read.f();
      pulse = read.f();
      nurseryCreated = read.bool();
      if (revision >= 2) {
        growthCursor = read.i();
        defenseBuildings = read.i();
      }
      if (revision >= 3) {
        sharedPower = read.f();
        sharedHeat = read.f();
      }
      if (revision >= 4) growthCredits = read.f();
      appliedLevel = 0;
    }
  }
}
