package crystal.mod;

import crystal.ai.type.CoreAuxiliaryAI;
import crystal.entities.abilities.ContinueRepairField;
import crystal.entities.abilities.DeathItemDrop;
import crystal.entities.abilities.FlashAbility;
import crystal.entities.abilities.ReduceBoostAbility;
import crystal.entities.bullet.GravityBullet;
import crystal.entities.bullet.ReduceBoostBullet;
import crystal.entities.shentong.FaTianXiangDi;
import crystal.type.BuildShieldUnitType;
import crystal.type.CItemType;
import crystal.type.MagicUnitType;
import crystal.world.blocks.defence.HealWall;
import crystal.world.blocks.defence.LinkWall;
import crystal.world.blocks.defence.ReduceBoost;
import crystal.world.blocks.defence.towers.PowerAttackTower;
import crystal.world.blocks.defence.towers.Tower;
import crystal.world.blocks.distribution.DropDrillItem;
import crystal.world.blocks.effect.CoverFloorMachine;
import crystal.world.blocks.effect.ReplaceFloor;
import crystal.world.blocks.effect.SCUnloader;
import crystal.world.blocks.payloads.UnitLaunchPad;
import crystal.world.blocks.payloads.UnitReceivePad;
import crystal.world.blocks.power.LiquidFloorGenerator;
import crystal.world.blocks.production.AttributeRandCrafter;
import crystal.world.blocks.production.DrillTurret;
import mindustry.mod.ClassMap;

public class ClassMapLoader {
  public static void load() {
    put("CoreAuxiliaryAI", CoreAuxiliaryAI.class);
    put("ContinueRepairField", ContinueRepairField.class);
    put("DeathItemDrop", DeathItemDrop.class);
    put("FlashAbility", FlashAbility.class);
    put("ReduceBoostAbility", ReduceBoostAbility.class);
    put("GravityBullet", GravityBullet.class);
    put("ReduceBoostBullet", ReduceBoostBullet.class);
    put("FaTianXiangDi", FaTianXiangDi.class);
    put("BuildShieldUnitType", BuildShieldUnitType.class);
    put("CItemType", CItemType.class);
    put("MagicUnitType", MagicUnitType.class);
    put("HealWall", HealWall.class);
    put("LinkWall", LinkWall.class);
    put("ReduceBoost", ReduceBoost.class);
    put("Tower", Tower.class);
    put("PowerAttackTower", PowerAttackTower.class);
    put("DropDrillItem", DropDrillItem.class);
    put("LiquidFloorGenerator", LiquidFloorGenerator.class);
    put("AttributeRandCrafter", AttributeRandCrafter.class);
    put("DrillTurret", DrillTurret.class);
    put("UnitLaunchPad", UnitLaunchPad.class);
    put("UnitReceivePad", UnitReceivePad.class);
    put("CoverFloorMachine", CoverFloorMachine.class);
    put("ReplaceFloor", ReplaceFloor.class);
    put("SCUnloader", SCUnloader.class);
    ClassMap.classes.put("CoreUnitType", crystal.type.CoreUnitType.class);
  }

  public static void put(String name, Class<?> clazz) {
    ClassMap.classes.put(name, clazz);
  }
}
