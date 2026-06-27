package crystal.type.weapons;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.weapons.MineWeapon;
import mindustry.entities.units.WeaponMount;
import mindustry.gen.Unit;

public class CMineWeapon extends MineWeapon {
  // 光束宽度，默认0.75与原版一致
  public float beamWidth = 0.75f;
  // 光束矿端抖动幅度
  public float swingMag = 4f;
  // 光束抖动频率
  public float swingScl = 12f;

  public CMineWeapon(String name) {
    super(name);
  }

  @Override
  public void draw(Unit unit, WeaponMount mount) {
    super.draw(unit, mount);
    if (unit.mining()) {
      float z = Draw.z();
      float rotation = unit.rotation - 90;
      float weaponRotation = rotation + (rotate ? mount.rotation : 0);
      float wx = unit.x + Angles.trnsx(rotation, x, y) + Angles.trnsx(weaponRotation, 0, -mount.recoil);
      float wy = unit.y + Angles.trnsy(rotation, x, y) + Angles.trnsy(weaponRotation, 0, -mount.recoil);
      float sY = shootY + Mathf.absin(Time.time, 1.1f, 0.5f);
      float px = wx + Angles.trnsx(weaponRotation, shootX, sY);
      float py = wy + Angles.trnsy(weaponRotation, shootX, sY);
      float ex = unit.mineTile.worldx() + Mathf.sin(Time.time + 48, swingScl, swingMag);
      float ey = unit.mineTile.worldy() + Mathf.sin(Time.time + 48, swingScl + 2f, swingMag);
      Draw.z(Layer.flyingUnit + 0.1f);
      Draw.color(Color.lightGray, Color.white, 0.7f + Mathf.absin(Time.time, 0.5f, 0.3f));
      Drawf.laser(unit.type.mineLaserRegion, unit.type.mineLaserEndRegion, px, py, ex, ey, beamWidth);
      if (unit.isLocal()) {
        Lines.stroke(1f, Pal.accent);
        Lines.poly(unit.mineTile.worldx(), unit.mineTile.worldy(), 4, 4f * Mathf.sqrt2, Time.time);
      }
      Draw.reset();
      Draw.z(z);
    }
  }
}
