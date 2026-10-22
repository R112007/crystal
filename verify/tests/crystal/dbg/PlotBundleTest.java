package crystal.dbg;

import crystal.util.PlotBundle;
import crystal.util.PlotObfuscator;
import mindustry.Vars;

/** Verifies that the packaged plot.properties is obfuscated and decoded correctly at runtime. */
public class PlotBundleTest {
  private static boolean ok = true;

  public static void run(String[] args) {
    try {
      String sample = "科技文明，修仙文明。";
      String encoded = PlotObfuscator.obfuscate(sample);
      check("runtime obfuscator round-trips", sample, PlotObfuscator.deobfuscate(encoded));
      check("obfuscation actually changes CJK text", true, !sample.equals(encoded));

      var mod = Vars.mods.getMod("crystal");
      check("crystal mod is loaded", true, mod != null);
      if (mod == null) {
        finish();
        return;
      }

      PlotBundle bundle = PlotBundle.loadFromMod(mod.root);
      check("plot bundle has entries", true, bundle.size() > 0);
      check("known module title decodes", "初遇·降临点", bundle.get("gal.jianglindian_lanuch"));
      check("known dialogue decodes", true,
          bundle.get("jianglindian_lanuch-1").startsWith("科技文明与修仙文明"));
    } catch (Throwable t) {
      t.printStackTrace();
      ok = false;
    }
    finish();
  }

  private static void finish() {
    System.out.println("[PLOT] " + (ok ? "PASS" : "FAIL"));
    System.exit(ok ? 0 : 1);
  }

  private static void check(String label, boolean expected, boolean actual) {
    boolean pass = expected == actual;
    ok &= pass;
    System.out.println("[PLOT] " + label + ": " + actual + "/" + expected + (pass ? " OK" : " FAIL"));
  }

  private static void check(String label, String expected, String actual) {
    boolean pass = expected == null ? actual == null : expected.equals(actual);
    ok &= pass;
    System.out.println("[PLOT] " + label + ": " + actual + "/" + expected + (pass ? " OK" : " FAIL"));
  }
}
