package crystal.world.meta;

import java.util.ArrayList;

import arc.Core;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.scene.ui.Image;
import arc.scene.ui.layout.Collapser;
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
import mindustry.entities.bullet.EmpBulletType;
import mindustry.entities.bullet.LaserBulletType;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.graphics.Pal;
import mindustry.type.UnitType;
import mindustry.type.Weapon;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.blocks.defense.turrets.Turret;
import mindustry.world.meta.Stat;
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

  public static StatValue weaponStats(Weapon weapon) {
    return table -> displayWeapon(weapon, table);
  }

  /** 主入口：把武器属性 + 子弹属性全部画进 table */
  public static void displayWeapon(Weapon weapon, Table table) {
    table.table(Styles.grayPanel, bt -> {
      bt.left().top().defaults().padRight(3).left();

      // 武器图标
      if (!weapon.name.isEmpty() && weapon.region != null && weapon.region.found() && weapon.showStatSprite) {
        bt.table(title -> {
          title.image(weapon.region).size(60).scaling(Scaling.bounded).left().top();
          title.add(weapon.name).padLeft(8).left().top().color(Pal.accent);
        });
        bt.row();
      }

      // === 武器本体属性 ===
      if (weapon.inaccuracy > 0) {
        sep(bt, "[lightgray]" + Stat.inaccuracy.localized() + ": [white]" + (int) weapon.inaccuracy + " "
            + StatUnit.degrees.localized());
      }

      if (!weapon.alwaysContinuous && weapon.reload > 0 && !weapon.bullet.killShooter) {
        sep(bt, "[lightgray]" + Stat.reload.localized() + ": " + (weapon.mirror ? "2x " : "") + "[white]"
            + Strings.autoFixed(60f / weapon.reload * weapon.shoot.shots, 2) + " " + StatUnit.perSecond.localized());
      }

      if (weapon.recoil > 0) {
        sep(bt, "[lightgray]后坐力: [white]" + Strings.autoFixed(weapon.recoil, 2));
      }

      // === 子弹属性 ===
      displayBullet(weapon.bullet, bt, false);

    }).padLeft(5).padTop(5).padBottom(5).growX().margin(10);
  }

  /** 绘制单种子弹属性（支持递归显示 frag/interval/spawn） */
  public static void displayBullet(BulletType type, Table bt, boolean compact) {
    if (type == null)
      return;

    // 直接伤害
    if (type.damage > 0 && (type.collides || type.splashDamage <= 0)) {
      sep(bt,
          Core.bundle.format("bullet.damage", type.damage) + (type.continuousDamage() > 0
              ? "[lightgray] ~ [stat]" + Core.bundle.format("bullet.damage", type.continuousDamage())
                  + StatUnit.perSecond.localized()
              : ""));
    }

    // 建筑/护盾伤害倍率
    if (type.buildingDamageMultiplier != 1) {
      sep(bt, Core.bundle.format("bullet.buildingdamage", ammoStat((int) (type.buildingDamageMultiplier * 100 - 100))));
    }
    if (type.shieldDamageMultiplier != 1) {
      sep(bt, Core.bundle.format("bullet.shielddamage", ammoStat((int) (type.shieldDamageMultiplier * 100 - 100))));
    }

    // 射程修正
    if (type.rangeChange != 0 && !compact) {
      sep(bt, Core.bundle.format("bullet.range", ammoStat(type.rangeChange / tilesize)));
    }

    // 溅射
    if (type.splashDamage > 0) {
      sep(bt, Core.bundle.format("bullet.splashdamage", (int) type.splashDamage,
          Strings.fixed(type.splashDamageRadius / tilesize, 1)));
    }

    // 弹药倍率 / 装填倍率
    if (type.statLiquidConsumed <= 0f && !compact && !Mathf.equal(type.ammoMultiplier, 1f)
        && type.displayAmmoMultiplier) {
      sep(bt, Core.bundle.format("bullet.multiplier", (int) type.ammoMultiplier));
    }
    if (!compact && !Mathf.equal(type.reloadMultiplier, 1f)) {
      sep(bt, Core.bundle.format("bullet.reload", ammoStat((int) (type.reloadMultiplier * 100 - 100))));
    }

    // 击退 / 治疗
    if (type.knockback > 0) {
      sep(bt, Core.bundle.format("bullet.knockback", Strings.autoFixed(type.knockback, 2)));
    }
    if (type.healPercent > 0f) {
      sep(bt, Core.bundle.format("bullet.healpercent", Strings.autoFixed(type.healPercent, 2)));
    }
    if (type.healAmount > 0f) {
      sep(bt, Core.bundle.format("bullet.healamount", Strings.autoFixed(type.healAmount, 2)));
    }

    // 穿透 / 燃烧 / 追踪 / 闪电
    if (type.pierce || type.pierceCap != -1) {
      sep(bt, type.pierceCap == -1 ? "@bullet.infinitepierce" : Core.bundle.format("bullet.pierce", type.pierceCap));
    }
    if (type.incendAmount > 0)
      sep(bt, "@bullet.incendiary");
    if (type.homingPower > 0.01f)
      sep(bt, "@bullet.homing");
    if (type.lightning > 0) {
      sep(bt, Core.bundle.format("bullet.lightning", type.lightning,
          type.lightningDamage < 0 ? type.damage : type.lightningDamage));
    }

    // 激光子弹
    if (type instanceof LaserBulletType b && b.lightningSpacing > 0) {
      int count = (int) (b.length / b.lightningSpacing) * 2 + 2;
      float damage = b.lightningDamage < 0 ? b.damage : b.lightningDamage;
      sep(bt, Core.bundle.format("bullet.lightning", count, damage));
      note(bt, Core.bundle.format("bullet.lightninginterval", Strings.autoFixed(b.lightningSpacing / tilesize, 2),
          Strings.autoFixed(b.lightningLength, 2)));
    }

    // EMP 子弹
    if (type instanceof EmpBulletType b && b.radius > 0f) {
      sep(bt, Core.bundle.format("bullet.empradius", Strings.fixed(b.radius / tilesize, 1)));
      if (b.timeDuration > 0f && b.timeIncrease > 1f) {
        sep(bt, Core.bundle.format("bullet.empboost", Strings.autoFixed(b.timeIncrease * 100f, 2),
            Strings.autoFixed(b.timeDuration / 60f, 1)) + " " + StatUnit.seconds.localized());
      }
      if (b.timeDuration > 0f && b.powerSclDecrease < 1f) {
        sep(bt,
            Core.bundle.format("bullet.empslowdown",
                (b.powerSclDecrease < 1f ? "[negstat]" : "") + Strings.autoFixed((b.powerSclDecrease - 1f) * 100f, 2),
                Strings.autoFixed(b.timeDuration / 60f, 1)) + " " + StatUnit.seconds.localized());
      }
      if (!Mathf.equal(b.powerDamageScl, 1f)) {
        sep(bt, Core.bundle.format("bullet.empdamage", Strings.autoFixed(b.powerDamageScl * 100f, 2)));
      }
      if (b.hitUnits) {
        sep(bt, Core.bundle.format("bullet.empunitdamage",
            (b.unitDamageScl < 1f ? "[negstat]" : "") + Strings.autoFixed(b.unitDamageScl * 100f, 2)));
      }
    }

    // 穿甲 / 护甲倍率
    if (type.pierceArmor) {
      sep(bt, "@bullet.armorpierce");
    } else {
      if (type.armorMultiplier != 1f) {
        if (type.armorMultiplier > 1f)
          sep(bt, Core.bundle.format("bullet.armorweakness", type.armorMultiplier));
        else if (Mathf.sign(type.armorMultiplier) == 1)
          sep(bt, Core.bundle.format("bullet.partialarmorpierce", (int) ((1 - type.armorMultiplier) * 100)));
        else
          sep(bt, Core.bundle.format("bullet.antiarmor", (-type.armorMultiplier)));
      }
      if (type.blockArmorMultiplier != 1f) {
        if (type.blockArmorMultiplier > 1f)
          sep(bt, Core.bundle.format("bullet.blockarmorweakness", type.blockArmorMultiplier));
        else if (Mathf.sign(type.blockArmorMultiplier) == 1)
          sep(bt, Core.bundle.format("bullet.blockpartialarmorpierce", (int) ((1 - type.blockArmorMultiplier) * 100)));
        else
          sep(bt, Core.bundle.format("bullet.blockantiarmor", (-type.blockArmorMultiplier)));
      }
    }

    // 最大伤害比例 / 压制 / 状态效果
    if (type.maxDamageFraction > 0) {
      sep(bt, Core.bundle.format("bullet.maxdamagefraction", (int) (type.maxDamageFraction * 100)));
    }
    if (type.suppressionRange > 0) {
      sep(bt, Core.bundle.format("bullet.suppression", Strings.autoFixed(type.suppressionDuration / 60f, 2),
          Strings.fixed(type.suppressionRange / tilesize, 1)));
    }
    if (type.status != StatusEffects.none) {
      sep(bt,
          (type.status.hasEmoji() ? type.status.emoji() : "") + "[stat]" + type.status.localizedName
              + (type.status.reactive ? ""
                  : "[lightgray] ~ [stat]" + Strings.autoFixed(type.statusDuration / 60f, 1) + "[lightgray] "
                      + Core.bundle.get("unit.seconds")));
    }

    if (!type.targetMissiles)
      sep(bt, "@bullet.notargetsmissiles");
    if (!type.targetBlocks)
      sep(bt, "@bullet.notargetsbuildings");

    // ===== 自定义扩展点：在这里插入你自己的 BulletType 数据 =====
    // 示例：
    // if(type instanceof MyBulletType mb){
    // sep(bt, "[lightgray]专属充能: [stat]" + Strings.autoFixed(mb.charge, 2));
    // }

    // 间隔子弹（可折叠）
    if (type.intervalBullet != null) {
      bt.row();
      Table ic = new Table();
      displayBullet(type.intervalBullet, ic, true);
      Collapser coll = new Collapser(ic, true);
      coll.setDuration(0.1f);
      bt.table(it -> {
        it.left().defaults().left();
        it.add(Core.bundle.format("bullet.interval",
            Strings.autoFixed(type.intervalBullets / type.bulletInterval * 60, 2)));
        it.button(Icon.downOpen, Styles.emptyi, () -> coll.toggle(false))
            .update(i -> i.getStyle().imageUp = (!coll.isCollapsed() ? Icon.upOpen : Icon.downOpen)).size(8)
            .padLeft(16f).expandX();
      });
      bt.row();
      bt.add(coll);
    }

    // 分裂子弹（可折叠）
    if (type.fragBullet != null) {
      bt.row();
      Table fc = new Table();
      displayBullet(type.fragBullet, fc, true);
      Collapser coll = new Collapser(fc, true);
      coll.setDuration(0.1f);
      bt.table(ft -> {
        ft.left().defaults().left();
        ft.add(Core.bundle.format("bullet.frags", type.fragBullets));
        ft.button(Icon.downOpen, Styles.emptyi, () -> coll.toggle(false))
            .update(i -> i.getStyle().imageUp = (!coll.isCollapsed() ? Icon.upOpen : Icon.downOpen)).size(8)
            .padLeft(16f).expandX();
      });
      bt.row();
      bt.add(coll);
    }

    // 生成子弹（可折叠）
    if (type.spawnBullets != null && type.spawnBullets.size > 0) {
      bt.row();
      Table sc = new Table();
      for (BulletType spawn : type.spawnBullets) {
        if (spawn.showStats)
          displayBullet(spawn, sc, true);
      }
      if (sc.getChildren().size > 0) {
        Collapser coll = new Collapser(sc, true);
        coll.setDuration(0.1f);
        bt.table(st -> {
          st.left().defaults().left();
          st.add(Core.bundle.format("bullet.spawnBullets", type.spawnBullets.size));
          st.button(Icon.downOpen, Styles.emptyi, () -> coll.toggle(false))
              .update(i -> i.getStyle().imageUp = (!coll.isCollapsed() ? Icon.upOpen : Icon.downOpen)).size(8)
              .padLeft(16f).expandX();
        });
        bt.row();
        bt.add(coll);
      }
    }
  }

  private static void note(Table table, String text) {
    table.row();
    table.table(t -> {
      t.image(Icon.arrowNoteSmall.getRegion()).size(15).color(Pal.stat).scaling(Scaling.fit).padRight(6).padLeft(12);
      t.add(text);
    });
  }

  private static String ammoStat(float val) {
    return (val > 0 ? "[stat]+" : "[negstat]") + Strings.autoFixed(val, 1);
  }
}
