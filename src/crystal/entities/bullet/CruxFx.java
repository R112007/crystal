package crystal.entities.bullet;

import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.Vec2;
import arc.util.*;
import mindustry.entities.Effect;
import mindustry.graphics.*;

import static mindustry.Vars.*;
import static mindustry.content.Fx.rand;

public class CruxFx {

    public static Effect chainLightning;

    public static void load() {
        chainLightning = new Effect(15f, 300f, e -> {
            if (!(e.data instanceof VisualLightningHolder))
                return;
            VisualLightningHolder p = (VisualLightningHolder) e.data;

            float tx = p.start().x, ty = p.start().y;
            float dst = Mathf.dst(p.end().x, p.end().y, tx, ty);

            Tmp.v3.set(p.end()).sub(p.start()).nor();
            float normx = Tmp.v3.x, normy = Tmp.v3.y;

            rand.setSeed(e.id);

            float arcWidth = rand.range(dst * p.arc());

            int links = Mathf.ceil(dst / p.segLength());
            float spacing = dst / links;

            Lines.stroke(p.width() * e.fout());
            Draw.color(Color.white, e.color, e.finpow());

            Lines.beginLine();
            Lines.linePoint(tx, ty);

            float lastx = tx, lasty = ty;

            for (int i = 0; i < links; i++) {
                float nx, ny;
                if (i == links - 1) {
                    nx = p.end().x;
                    ny = p.end().y;
                } else {
                    float len = (i + 1) * spacing;
                    rand.setSeed(e.id + i);
                    float angle = rand.random(360f);
                    float offset = rand.random(p.segLength() / 2f);
                    float percent = ((float) (i + 1)) / links;
                    float arcOffset = Mathf.sinDeg(percent * 180) * arcWidth;

                    // ★ 修复：Tmp.v3.angle() → 计算方向角度
                    float dirAngle = Mathf.atan2(normy, normx);
                    nx = tx + normx * len + Angles.trnsx(angle, offset) + Angles.trnsx(dirAngle - 90f, arcOffset);
                    ny = ty + normy * len + Angles.trnsy(angle, offset) + Angles.trnsy(dirAngle - 90f, arcOffset);
                }

                Lines.linePoint(nx, ny);
                lastx = nx;
                lasty = ny;
            }

            Lines.endLine();
            Draw.color();
        });

    }

    public interface VisualLightningHolder {
        Vec2 start();

        Vec2 end();

        float width();

        float segLength();

        float arc();
    }
}
