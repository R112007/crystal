package crystal.content;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import arc.Core;
import arc.func.Boolp;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Nullable;
import arc.util.Timer;
import arc.util.Timer.Task;
import crystal.Crystal;
import crystal.CVars;
import crystal.entities.units.UnitEnum.XiuWei;
import crystal.type.MagicUnitType;
import crystal.util.DLog;
import mindustry.Vars;
import mindustry.entities.abilities.Ability;
import mindustry.type.UnitType;
import mindustry.type.Weapon;

public class MuchLoadUnit {
  public static ObjectMap<MagicUnitType, ObjectMap<XiuWei, MagicUnitType>> magicUnitTypes = new ObjectMap<>();

  public static @Nullable MagicUnitType getUnitByXiuWei(MagicUnitType source, XiuWei xiuWei) {
    if (source == null || xiuWei == null)
      return source;
    if (!magicUnitTypes.containsKey(source)) {
      DLog.warn("单位[" + source.name + "]未找到修为克隆映射，返回原始单位");
      return source;
    }
    var unitMap = magicUnitTypes.get(source);
    MagicUnitType target = unitMap.get(xiuWei);
    if (target == null) {
      DLog.warn("单位[" + source.name + "]未找到修为[" + xiuWei.str + "]的克隆体，返回原始单位");
      return source;
    }
    return target;
  }

  public static MagicUnitType getPlayerCurrentXiuWeiUnit(MagicUnitType source) {
    return getUnitByXiuWei(source, CVars.playerXiuWei);
  }

  public static void load() throws IllegalAccessException {
    Seq<MagicUnitType> units = new Seq<>();
    for (UnitType unit : Vars.content.units()) {
      if (unit instanceof MagicUnitType magic) {
        DLog.info(magic.name);
        units.add(magic);
      }
    }
    for (MagicUnitType magic : units) {
      cloneUnitType(magic);
    }
  }

  public static void cloneUnitType(MagicUnitType sourceType) throws IllegalAccessException {
    ObjectMap<XiuWei, MagicUnitType> unitMap = new ObjectMap<>();
    unitMap.put(sourceType.xiuWei, sourceType);
    magicUnitTypes.put(sourceType, unitMap);
    for (int i = 0; i < XiuWei.all.length; i++) {
      int index = i;
      XiuWei targetXiuWei = XiuWei.all[index];
      MagicUnitType newUnit = new MagicUnitType(sourceType.name + index) {
        {
          this.xiuWei = targetXiuWei;
          this.localizedName = sourceType.localizedName + " · " + this.xiuWei.str;
          this.description = sourceType.description;
          this.constructor = sourceType.constructor;
        }

        @Override
        public void loadIcon() {
          sourceType.loadIcon();
          this.fullIcon = sourceType.fullIcon;
          this.uiIcon = sourceType.uiIcon;
        }

        @Override
        public void load() {
          sourceType.load();
          super.load();
          this.region = sourceType.region;
          this.baseRegion = sourceType.baseRegion;
          this.legRegion = sourceType.legRegion;
          this.previewRegion = sourceType.previewRegion;
          this.shadowRegion = sourceType.shadowRegion;
          this.cellRegion = sourceType.cellRegion;
          this.itemCircleRegion = sourceType.itemCircleRegion;
          this.softShadowRegion = sourceType.softShadowRegion;
          this.jointRegion = sourceType.jointRegion;
          this.footRegion = sourceType.footRegion;
          this.legBaseRegion = sourceType.legBaseRegion;
          this.baseJointRegion = sourceType.baseJointRegion;
          this.outlineRegion = sourceType.outlineRegion;
          this.treadRegion = sourceType.treadRegion;
          this.mineLaserRegion = sourceType.mineLaserRegion;
          this.mineLaserEndRegion = sourceType.mineLaserEndRegion;
          this.wreckRegions = sourceType.wreckRegions;
          this.segmentRegions = sourceType.segmentRegions;
          this.segmentCellRegions = sourceType.segmentCellRegions;
          this.segmentOutlineRegions = sourceType.segmentOutlineRegions;
          this.treadRegions = sourceType.treadRegions;
        }
      };

      Field[] unitTypeFields = UnitType.class.getDeclaredFields();
      for (Field field : unitTypeFields) {
        field.setAccessible(true);
        String fieldName = field.getName();
        if (Modifier.isFinal(field.getModifiers()))
          continue;
        switch (fieldName) {
          case "name", "xiuWei", "localizedName", "weapons", "abilities":
            continue;
          default: {
            Object value = field.get(sourceType);
            if (value != null)
              field.set(newUnit, value);
          }
        }
      }

      Field[] magicUnitFields = MagicUnitType.class.getDeclaredFields();
      for (Field field : magicUnitFields) {
        field.setAccessible(true);
        String fieldName = field.getName();
        if (Modifier.isFinal(field.getModifiers()) || fieldName.equals("xiuWei"))
          continue;
        Object value = field.get(sourceType);
        if (value != null)
          field.set(newUnit, value);
      }

      for (int j = 0; j < sourceType.weapons.size; j++) {
        Weapon originWeapon = sourceType.weapons.get(j);
        Weapon copiedWeapon = originWeapon.copy();
        copiedWeapon.bullet = originWeapon.bullet.copy();
        newUnit.weapons.add(copiedWeapon);
      }

      for (int j = 0; j < sourceType.abilities.size; j++) {
        Ability originAbility = sourceType.abilities.get(j);
        Ability copiedAbility = originAbility.copy();
        newUnit.abilities.add(copiedAbility);
      }

      // Vars.content.units().add(newUnit);
      unitMap.put(newUnit.xiuWei, newUnit);
    }
  }
}
