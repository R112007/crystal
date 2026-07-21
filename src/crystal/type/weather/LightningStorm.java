package crystal.type.weather;

import arc.graphics.Color;
import arc.math.Mathf;
import arc.math.Rand;
import arc.math.geom.Position;
import arc.struct.ObjectSet;
import crystal.Crystal;
import mindustry.Vars;
import mindustry.entities.Lightning;
import mindustry.gen.Building;
import mindustry.gen.WeatherState;
import mindustry.graphics.Pal;
import mindustry.type.Weather;
import mindustry.world.Tile;
import mindustry.world.blocks.environment.AirBlock;
import mindustry.world.blocks.environment.StaticWall;
import mindustry.world.blocks.environment.TiledWall;

public class LightningStorm extends Weather {
  public Color lightningColor = Pal.surge;
  public int lightning = 1;
  public int lightningLength = 5;
  public int lightningLengthRand = 0;
  public float lightningDamage = -1;
  public float delay = 60;
  public static final Rand rand = new Rand();

  public LightningStorm(String name) {
    super(name);
  }

  public Position getPos() {
    int h = Vars.state.map.height;
    int w = Vars.state.map.width;
    int maxAttempts = (int) (h * w / 3); // 防死循环

    for (int i = 0; i < maxAttempts; i++) {
      Tile tile = Vars.world.tile(rand.random(0, w), rand.random(0, h));

      // 安全检查：tile 非空 + block 非空 + 业务条件
      if (tile != null && tile.block() != null &&
          !((tile.block() instanceof TiledWall) || (tile.block() instanceof StaticWall))) {
        return tile;
      }
    }

    // 100次都失败的备用方案（避免返回 null）
    return Vars.world.tile(w / 2, h / 2);
  }

  @Override
  public void update(WeatherState state) {
    if (Crystal.timer % delay == 0) {
      for (int i = 0; i < lightning; i++) {
        Lightning.create(Vars.state.rules.waveTeam, lightningColor, lightningDamage, getPos().getX(),
            getPos().getY(), Mathf.random(360f),
            lightningLength + Mathf.random(lightningLengthRand));
      }
    }
  }
}
