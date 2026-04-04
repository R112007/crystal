package crystal.world.meta;

import java.util.ArrayList;

import arc.Core;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.scene.ui.Image;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Scaling;
import arc.util.Strings;
import crystal.entities.shentong.ShenTong;
import crystal.gen.Magicc;
import crystal.type.MagicUnitType;
import crystal.type.weapons.StageWeapon;
import crystal.util.DLog;
import mindustry.Vars;
import mindustry.content.StatusEffects;
import mindustry.ctype.UnlockableContent;
import mindustry.entities.abilities.Ability;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Tex;
import mindustry.type.UnitType;
import mindustry.type.Weapon;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.blocks.defense.turrets.Turret;
import mindustry.world.meta.StatUnit;
import mindustry.world.meta.StatValue;
import mindustry.world.meta.StatValues;
import static mindustry.Vars.*;

public class CStatValues {
  public static <T extends UnlockableContent> StatValue ammo(ObjectMap<T, BulletType[]> map) {
    return ammo(map, 0, false);
  }

  public static <T extends UnlockableContent> StatValue ammo(ObjectMap<T, BulletType[]> map, boolean showUnit) {
    return ammo(map, 0, showUnit);
  }

  public static <T extends UnlockableContent> StatValue ammo(ObjectMap<T, BulletType[]> map, int indent,
      boolean showUnit) {
    return table -> {

      table.row();

      Seq<T> orderedKeys = map.keys().toSeq();
      orderedKeys.sort();

      for (T t : orderedKeys) {
        boolean compact = t instanceof UnitType && !showUnit || indent > 0;
        if (!compact && !(t instanceof Turret)) {
          table.image(icon(t)).size(3 * 8).padRight(4).right().top();
          table.add(t.localizedName).padRight(10).left().top();
        }
        table.row();
        for (BulletType type : map.get(t)) {
          if (type.spawnUnit != null && type.spawnUnit.weapons.size > 0) {
            ammo(ObjectMap.of(t, type.spawnUnit.weapons.first().bullet), indent, false).display(table);
            return;
          }

          // no point in displaying unit icon twice

          table.table(bt -> {
            bt.left().defaults().padRight(3).left();

            if (type.damage > 0 && (type.collides || type.splashDamage <= 0)) {
              if (type.continuousDamage() > 0) {
                bt.add(Core.bundle.format("bullet.damage", type.continuousDamage()) + StatUnit.perSecond.localized());
              } else {
                bt.add(Core.bundle.format("bullet.damage", type.damage));
              }
            }

            if (type.buildingDamageMultiplier != 1) {
              sep(bt, Core.bundle.format("bullet.buildingdamage", (int) (type.buildingDamageMultiplier * 100)));
            }

            if (type.rangeChange != 0 && !compact) {
              sep(bt, Core.bundle.format("bullet.range",
                  (type.rangeChange > 0 ? "+" : "-") + Strings.autoFixed(type.rangeChange / tilesize, 1)));
            }

            if (type.splashDamage > 0) {
              sep(bt, Core.bundle.format("bullet.splashdamage", (int) type.splashDamage,
                  Strings.fixed(type.splashDamageRadius / tilesize, 1)));
            }

            if (!compact && !Mathf.equal(type.ammoMultiplier, 1f) && type.displayAmmoMultiplier
                && (!(t instanceof Turret) || ((Turret) t).displayAmmoMultiplier)) {
              sep(bt, Core.bundle.format("bullet.multiplier", (int) type.ammoMultiplier));
            }

            if (!compact && !Mathf.equal(type.reloadMultiplier, 1f)) {
              sep(bt, Core.bundle.format("bullet.reload", Strings.autoFixed(type.reloadMultiplier, 2)));
            }

            if (type.knockback > 0) {
              sep(bt, Core.bundle.format("bullet.knockback", Strings.autoFixed(type.knockback, 2)));
            }

            if (type.healPercent > 0f) {
              sep(bt, Core.bundle.format("bullet.healpercent", Strings.autoFixed(type.healPercent, 2)));
            }

            if (type.healAmount > 0f) {
              sep(bt, Core.bundle.format("bullet.healamount", Strings.autoFixed(type.healAmount, 2)));
            }

            if (type.pierce || type.pierceCap != -1) {
              sep(bt, type.pierceCap == -1 ? "@bullet.infinitepierce"
                  : Core.bundle.format("bullet.pierce", type.pierceCap));
            }

            if (type.incendAmount > 0) {
              sep(bt, "@bullet.incendiary");
            }

            if (type.homingPower > 0.01f) {
              sep(bt, "@bullet.homing");
            }

            if (type.lightning > 0) {
              sep(bt, Core.bundle.format("bullet.lightning", type.lightning,
                  type.lightningDamage < 0 ? type.damage : type.lightningDamage));
            }

            if (type.pierceArmor) {
              sep(bt, "@bullet.armorpierce");
            }

            if (type.status != StatusEffects.none) {
              sep(bt,
                  (type.status.minfo.mod == null ? type.status.emoji() : "") + "[stat]" + type.status.localizedName
                      + "[lightgray] ~ [stat]" + ((int) (type.statusDuration / 60f)) + "[lightgray] "
                      + Core.bundle.get("unit.seconds"));
            }

            if (type.fragBullet != null) {
              sep(bt, Core.bundle.format("bullet.frags", type.fragBullets));
              bt.row();

              StatValues.ammo(ObjectMap.of(t, type.fragBullet), true, false).display(bt);
            }
          }).padTop(compact ? 0 : -9).padLeft(indent * 8).left().get().background(compact ? null : Tex.underline);

          table.row();
        }
      }
    };
  }

  private static void sep(Table table, String text) {
    table.row();
    table.add(text);
  }

  private static TextureRegion icon(UnlockableContent t) {
    return t.uiIcon;
  }

  public static StatValue text(String text) {
    return table -> {
      table.row();
      table.add(text + ":");
      table.row();
    };
  }

  public static StatValue contents(UnlockableContent[] contents) {
    return table -> {
      int i = 0;
      for (UnlockableContent content : contents) {
        table.add(new Image(content.uiIcon)).size(Vars.iconSmall).padRight(3);
        table.add(content.localizedName).padRight(3);
        i++;
        if (i % 4 == 0) {
          table.row();
          i = 0;
        }
      }
    };
  }

  public static StatValue contents(ArrayList<? extends UnlockableContent> contents) {
    return table -> {
      int i = 0;
      for (UnlockableContent content : contents) {
        table.add(new Image(content.uiIcon)).size(Vars.iconSmall).padRight(3);
        table.add(content.localizedName).padRight(3);
        i++;
        if (i % 4 == 0) {
          table.row();
          i = 0;
        }
      }
    };
  }

  public static StatValue contents(Seq<? extends UnlockableContent> contents) {
    return table -> {
      int i = 0;
      for (UnlockableContent content : contents) {
        table.add(new Image(content.uiIcon)).size(Vars.iconSmall).padRight(3);
        table.add(content.localizedName).padRight(3);
        i++;
        if (i % 4 == 0) {
          table.row();
          i = 0;
        }
      }
    };
  }

  public static StatValue stageWeapons(UnitType unit, Seq<Weapon> weapons) {
    return table -> {
      table.row();
      Seq<Weapon> w1 = new Seq<>(), w2 = new Seq<>(), w3 = new Seq<>(), w4 = new Seq<>();
      for (int i = 0; i < weapons.size; i++) {
        if (weapons.get(i) instanceof StageWeapon s1) {
          if (s1.weappnStage == 1)
            w1.add(s1);
        }
        if (weapons.get(i) instanceof StageWeapon s2) {
          if (s2.weappnStage == 2)
            w2.add(s2);
        }
        if (weapons.get(i) instanceof StageWeapon s3) {
          if (s3.weappnStage == 3)
            w3.add(s3);
        }
        if (weapons.get(i) instanceof StageWeapon s4) {
          if (s4.weappnStage == 4)
            w4.add(s4);
        }
      }
      DLog.info("w1.size" + w1.size);
      DLog.info("w2.size" + w2.size);
      DLog.info("w3.size" + w3.size);
      DLog.info("w4.size" + w4.size);
      if (w1.any()) {
        table.add(Core.bundle.get("stage.weapon") + 1);
        table.row();
        showWeapon(unit, w1, table);
        DLog.info("w1run");
      }
      if (w2.any()) {
        table.add(Core.bundle.get("stage.weapon") + 2);
        table.row();
        showWeapon(unit, w2, table);
        DLog.info("w2run");
      }
      if (w3.any()) {
        table.add(Core.bundle.get("stage.weapon") + 3);
        table.row();
        showWeapon(unit, w3, table);
        DLog.info("w3run");
      }
      if (w4.any()) {
        table.add(Core.bundle.get("stage.weapon") + 4);
        table.row();
        showWeapon(unit, w4, table);
        DLog.info("w4run");
      }
    };
  }

  public static void showWeapon(UnitType unit, Seq<Weapon> weapons, Table table) {
    for (int i = 0; i < weapons.size; i++) {
      Weapon weapon = weapons.get(i);

      if (weapon.flipSprite || !weapon.hasStats(unit)) {
        // flipped weapons are not given stats
        continue;
      }

      TextureRegion region = !weapon.name.isEmpty() ? Core.atlas.find(weapon.name + "-preview", weapon.region) : null;

      table.table(Styles.grayPanel, w -> {
        w.left().top().defaults().padRight(3).left();
        if (region != null && region.found() && weapon.showStatSprite)
          w.image(region).size(60).scaling(Scaling.bounded).left().top();
        w.row();

        weapon.addStats(unit, w);
      }).growX().pad(5).margin(10);
      table.row();
    }
  }

  public static StatValue abilities(Seq<Ability> abilities, String str) {
    return table -> {
      table.row();
      table.table(t -> {
        t.add(str);
        int count = 0;
        for (Ability ability : abilities) {
          if (ability.display) {
            ability.display(t);

            if ((++count) == 2) {
              count = 0;
              t.row();
            }
          }
        }
      });
    };
  }

  public static StatValue shenTongs(MagicUnitType magic, Seq<ShenTong> shengTongs) {
    return table -> {
      table.row();
      table.table(t -> {
        int count = 0;
        for (ShenTong shenTong : shengTongs) {
          t.button(shenTong.name(), Styles.black5, () -> {
            var d = new BaseDialog(shenTong.name());
            d.cont.pane(ta -> {
              shenTong.setStat(magic, ta);
              ta.row();
              ta.add(shenTong.description());
              ta.row();
            });
            d.addCloseButton();
            d.show();
          }).size(100f, 64f);
          if ((++count) == 2) {
            count = 0;
            t.row();
          }
        }
      });
    };
  }
}
