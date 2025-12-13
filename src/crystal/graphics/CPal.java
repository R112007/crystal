package crystal.graphics;

import arc.graphics.Color;

public class CPal {
  public static Color light_blue1;
  public static Color blue1;
  public static Color dark_blue1;
  public static Color light_blue2;
  public static Color blue2;
  public static Color dark_blue2;
  public static Color light_sharedyellow, sharedyellow, dark_sharedyellow;

  static {
    CPal.light_blue1 = Color.valueOf("#99F8FFFF");
    CPal.blue1 = Color.valueOf("#74C2E8FF");
    CPal.dark_blue1 = Color.valueOf("#5898F0FF");
    CPal.light_blue2 = Color.valueOf("#9CC2F0FF");
    CPal.blue2 = Color.valueOf("#1F84FFFF");
    CPal.dark_blue2 = Color.valueOf("#0A72F2FF");
    CPal.light_sharedyellow = Color.valueOf("#FFE18FFF");
    CPal.sharedyellow = Color.valueOf("#F8C266FF");
    CPal.dark_sharedyellow = Color.valueOf("#DE9458FF");
  }
}
