package crystal.dbg;

import crystal.content.CUnits;
import crystal.editor.MagicSpawnGroup;
import crystal.entities.shentong.FaTianXiangDi;
import crystal.entities.shentong.ShenTong;
import crystal.entities.shentong.ThunderSkyBlade;
import crystal.entities.units.UnitEnum.JingJie;
import mindustry.io.JsonIO;

import java.lang.reflect.Parameter;
import java.util.Arrays;

public class ShenTongConfigTest {
  private static boolean ok = true;

  public static void run(String[] args) {
    try {
      ShenTong fatian = new FaTianXiangDi(0f, 0f, 0f, 0f);
      Parameter[] fatianParams = fatian.editableParameters();
      check("fatian richest constructor has five parameters", true, fatianParams.length == 5);
      check("fatian parameter names retained",
          Arrays.asList("baseMultiplier", "baseSizeMultiplier", "regenAmount", "restoreTime", "createMagicCost"),
          Arrays.stream(fatianParams).map(Parameter::getName).toList());
      ShenTong configured = fatian.createConfigured(new float[] { 2f, 3f, 4f, 5f, 6f });
      check("configured construction type", true, configured instanceof FaTianXiangDi);
      FaTianXiangDi cast = (FaTianXiangDi) configured;
      check("configured base multiplier", 2f, cast.baseMultiplier);
      check("configured create magic cost", 6f, cast.createMagicCost);

      ShenTong thunder = new ThunderSkyBlade();
      check("thunder richest constructor has five parameters", true,
          thunder.editableParameters().length == 5);

      MagicSpawnGroup group = new MagicSpawnGroup(CUnits.chujia1);
      group.jingJie = JingJie.huayuan;
      group.shenTongId = fatian.id;
      group.shenTongValues = new float[] { 7f, 8f, 9f, 10f, 11f };
      MagicSpawnGroup copy = group.copy();
      check("copy preserves shen tong values", true,
          Arrays.equals(group.shenTongValues, copy.shenTongValues));
      MagicSpawnGroup roundTrip = JsonIO.copy(group);
      check("json round-trip preserves shen tong values", true,
          Arrays.equals(group.shenTongValues, roundTrip.shenTongValues));

      float[] defaults = group.defaultShenTongValues(fatian);
      check("fatian defaults come from selected realm", true, defaults[0] > 1f && defaults[2] > 0f);
    } catch (Throwable t) {
      t.printStackTrace();
      ok = false;
    }

    System.out.println("[SHENTONG] " + (ok ? "PASS" : "FAIL"));
    System.exit(ok ? 0 : 1);
  }

  private static void check(String label, Object expected, Object actual) {
    boolean pass = expected == null ? actual == null : expected.equals(actual);
    ok &= pass;
    System.out.println("[SHENTONG] " + label + ": " + actual + "/" + expected
        + (pass ? " OK" : " FAIL"));
  }
}
