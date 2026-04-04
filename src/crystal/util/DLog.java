package crystal.util;

import arc.util.Log;
import arc.util.Log.LogLevel;
import crystal.CVars;

public class DLog {
  public static void log(LogLevel level, String text, Object... args) {
    if (CVars.debug)
      Log.log(level, text, args);
  }

  public static void debug(String text, Object... args) {
    if (CVars.debug)
      Log.debug(text, args);
  }

  public static void debug(Object object) {
    if (CVars.debug)
      Log.debug(object);
  }

  public static void infoList(Object... args) {
    if (CVars.debug)
      Log.infoList(args);
  }

  public static void infoTag(String tag, String text) {
    if (CVars.debug)
      Log.infoTag(tag, text);
  }

  public static void info(String text, Object... args) {
    if (CVars.debug)
      Log.info(text, args);
  }

  public static void info(Object object) {
    if (CVars.debug)
      Log.info(object);
  }

  public static void warn(String text, Object... args) {
    if (CVars.debug)
      Log.warn(text, args);
  }

  public static void errTag(String tag, String text) {
    if (CVars.debug)
      Log.errTag(tag, text);
  }

  public static void err(String text, Object... args) {
    if (CVars.debug)
      Log.err(text, args);
  }

  public static void err(Throwable th) {
    if (CVars.debug)
      Log.err(th);
  }

  public static void err(String text, Throwable th) {
    if (CVars.debug)
      Log.err(text, th);
  }
}
