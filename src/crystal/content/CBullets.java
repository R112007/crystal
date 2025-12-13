package crystal.content;

import arc.graphics.Color;
import crystal.graphics.CPal;
import mindustry.content.Fx;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.bullet.ShrapnelBulletType;
import mindustry.entities.part.HaloPart;
import mindustry.entities.part.ShapePart;
import mindustry.graphics.Pal;

public class CBullets {
  public static BulletType qianfengbullet = new BasicBulletType(3, 25) {
    {
      width = 7f;
      height = 7f;
      lifetime = 60f;
      ammoMultiplier = 2;
      hitEffect = despawnEffect = Fx.hitBulletColor;
      hitColor = backColor = trailColor = Pal.copperAmmoBack;
      frontColor = Pal.copperAmmoFront;
      trailLength = 8;
      trailWidth = 1.5f;
      trailColor = CPal.sharedyellow;
      parts.add(
          new ShapePart() {
            {
              progress = PartProgress.life;
              color = CPal.sharedyellow;
              sides = 0;
              stroke = 1;
              strokeTo = 1f;
              circle = false;
              hollow = true;
              radius = 2.5f;
              radiusTo = 2.5f;
              layer = 110;
            }
          },
          new ShapePart() {
            {
              progress = PartProgress.life;
              color = CPal.sharedyellow;
              stroke = 1;
              strokeTo = 0.8f;
              circle = true;
              hollow = true;
              radius = 5;
              radiusTo = 5;
              layer = 110;
            }
          },
          new HaloPart() {
            {
              shapeRotation = 0;// 图形方向
              progress = PartProgress.life;// 更新方式
              sides = 3;// 几边形
              shapes = 5;// 几个图形
              color = CPal.sharedyellow;
              layer = 110;
              tri = true;// 是否是三角形
              radius = 1f;//
              radiusTo = 1f;//
              triLength = 2.5f;// 长度
              triLengthTo = 2.5f;// 末长度
              haloRadius = 5;// 环绕半径
              haloRadiusTo = 5;// 末环绕半径
              haloRotateSpeed = 3f;// 转速
            }
          });
    }
  };
  public static BulletType taichu1 = new BasicBulletType(5.0f, 28f) {
    {
      this.splashDamage = 58;
      this.splashDamageRadius = 12f;
      this.width = 7f;
      this.height = 14f;
      this.lifetime = 40;
      this.homingPower = 6f;
      this.trailChance = 0.4f;
      this.trailColor = CPal.light_blue2;
      this.frontColor = CPal.blue1;
      this.hitEffect = this.despawnEffect = Fx.smokeCloud;
    }
  };;
  public static BulletType taichu2 = new ShrapnelBulletType() {
    {
      this.damage = 48;
      this.length = 138;
      this.reloadMultiplier = 1.5f;
      this.ammoMultiplier = 3;
      this.knockback = 0.8f;
      this.toColor = Color.valueOf("#79C5C5FF");
    }
  };;
}
