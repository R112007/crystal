package crystal.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import arc.util.Log;

public class Time {
  public static void logTime() {
    DateTimeFormatter matter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH:mm:ss");
    LocalDateTime now = LocalDateTime.now();
    Log.info("时间" + now.format(matter));
  }
}
