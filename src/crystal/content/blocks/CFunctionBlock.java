package crystal.content.blocks;

import mindustry.entities.bullet.LaserBoltBulletType;
import mindustry.gen.Sounds;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import mindustry.world.blocks.defense.MendProjector;
import mindustry.world.blocks.defense.turrets.PowerTurret;

import static crystal.content.CItems.*;

import arc.graphics.Color;

public class CFunctionBlock {
  public static Block xiuliqi;
  public static Block xiuliqi2;
  public static Block repairturret1;

  public static void load() {
    CFunctionBlock.xiuliqi = new MendProjector("xiuliqi") {
      {
        this.size = 1;
        this.health = 200;
        this.reload = 330f;
        this.range = 45f;
        this.healPercent = 6.3f;
        this.itemCapacity = 10;
        this.phaseBoost = 10f;
        this.phaseRangeBoost = 15f;
        this.consumePower(0.48f);
        this.consumeItem(cuguijing, 1).optional(true, true);
        this.requirements(Category.effect, ItemStack.with(new Object[] { lv, 20, li, 30 }));
      }
    };
    CFunctionBlock.xiuliqi2 = new MendProjector("xiuliqi2") {
      {
        this.size = 2;
        this.health = 380;
        this.reload = 280f;
        this.range = 80f;
        this.healPercent = 8f;
        this.itemCapacity = 10;
        this.phaseBoost = 30f;
        this.phaseRangeBoost = 25f;
        this.consumePower(1.5f);
        this.consumeItem(chunguijing, 1).optional(true, true);
        this.requirements(Category.effect,
            ItemStack.with(new Object[] { lv, 55, li, 60, cuguijing, 65, xi, 60 }));
      }
    };
    CFunctionBlock.repairturret1 = new PowerTurret("repairturret1") {
      {
        this.health = 820;
        this.size = 2;
        this.reload = 20;
        this.range = 240;
        this.inaccuracy = 0;
        this.targetAir = false;
        this.targetGround = false;
        this.targetHealing = true;
        this.shootCone = 2;
        this.requirements(Category.effect,
            ItemStack.with(
                new Object[] { lv, 60, li, 60, xi, 50, chunguijing, 45 }));
        this.recoil = 0;
        this.rotateSpeed = 8;
        this.shootSound = Sounds.shootLaser;
        this.consumePower(4f);
        this.consumeCoolant(0.2f).optional(true, true);
        this.hasPower = true;
        this.liquidCapacity = 60f;
        this.shootType = new LaserBoltBulletType() {
          {
            this.speed = 8;
            this.lifetime = 48;
            this.width = 2;
            this.height = 8;
            this.ammoMultiplier = 1;
            this.healPercent = 2;
            this.backColor = Color.valueOf("#98FFA9FF");
            this.collidesAir = true;
            this.collidesGround = true;
            this.collidesTeam = true;
            this.damage = 0;
          }
        };
      }
    };
  }
}
