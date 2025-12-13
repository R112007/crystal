package crystal.content.blocks;

import crystal.content.CBullets;
import crystal.content.CItems;
import crystal.graphics.CPal;
import mindustry.content.Fx;
import mindustry.entities.part.HaloPart;
import mindustry.entities.part.ShapePart;
import mindustry.type.Category;
import mindustry.world.Block;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import mindustry.world.draw.DrawTurret;

import static mindustry.type.ItemStack.*;

public class CTurrets {
  public static Block qianfeng;

  public static void load() {
    qianfeng = new ItemTurret("qianfeng") {
      {
        drawer = new DrawTurret() {
          {
            parts.add(
                new HaloPart() {
                  {
                    shapeRotation = 0;
                    progress = PartProgress.life;
                    sides = 3;
                    shapes = 4;
                    y = -6;
                    color = CPal.sharedyellow;
                    layer = 110;
                    tri = true;
                    radius = 1f;
                    stroke = 2;
                    strokeTo = 0;
                    radiusTo = 1f;
                    triLength = 12;
                    triLengthTo = 12;
                    haloRadius = 0;
                    haloRadiusTo = 0;
                    haloRotateSpeed = -1f;
                  }
                });
          }
        };
        requirements(Category.turret, with(CItems.yellowcopper, 35));
        ammo(CItems.yellowcopper, CBullets.qianfengbullet);
        recoils = 2;
        recoil = 1.3f;
        shootY = 3f;
        reload = 20f;
        range = 160;
        size = 2;
        shootCone = 15f;
        ammoUseEffect = Fx.casing1;
        health = 500;
        inaccuracy = 2f;
        rotateSpeed = 10f;
        coolant = consumeCoolant(0.2f);
        researchCostMultiplier = 0.05f;
      }
    };
  }
}
