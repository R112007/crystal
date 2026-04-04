package crystal.type.weapons;

import arc.Core;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Mathf;
import arc.scene.ui.layout.Table;
import mindustry.entities.units.WeaponMount;
import mindustry.gen.Unit;
import mindustry.type.UnitType;
import mindustry.type.Weapon;

/**
 * StageWeapon
 */
public class StageWeapon extends Weapon {
  public TextureRegion topIcon = null;
  public boolean active = false, hasDrop = false;
  public static final String topTex = "-tex";
  public int weappnStage;

  public StageWeapon(String name, boolean load) {
    this.name = name;
    if (load)
      loadTopTex();
  }

  public StageWeapon(boolean load) {
    this("", load);
  }

  public StageWeapon() {
    this(false);
  }

  public void loadTopTex() {
    topIcon = Core.atlas.find("crystal-" + name + topTex);
  }

  public void drawTopTex(Unit unit, WeaponMount mount) {
    if (topIcon == null || active || !hasDrop)
      return;

    float rotation = unit.rotation - 90,
        realRecoil = Mathf.pow(mount.recoil, recoilPow) * recoil,
        weaponRotation = rotation + (rotate ? mount.rotation : baseRotation),
        wx = unit.x + Angles.trnsx(rotation, x, y) + Angles.trnsx(weaponRotation, 0, -realRecoil),
        wy = unit.y + Angles.trnsy(rotation, x, y) + Angles.trnsy(weaponRotation, 0, -realRecoil);

    Draw.rect(topIcon, wx, wy, weaponRotation);
  }

  public StageWeapon copy() {
    try {
      return (StageWeapon) clone();
    } catch (CloneNotSupportedException suck) {
      throw new RuntimeException("very good language design", suck);
    }
  }

  @Override
  public void addStats(UnitType u, Table t) {
    super.addStats(u, t);
  }

  @Override
  public void draw(Unit unit, WeaponMount mount) {
    // drawTopTex(unit, mount);

    super.draw(unit, mount);
  }
}
