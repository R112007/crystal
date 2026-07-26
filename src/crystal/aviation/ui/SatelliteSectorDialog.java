package crystal.aviation.ui;

import arc.*;
import arc.math.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;

import crystal.aviation.*;

/**
 * 在卫星上选择要移动到的目标区块。
 */
public class SatelliteSectorDialog extends BaseDialog{
    public SatelliteSectorDialog(Satellite satellite){
        super("选择目标区块");

        Planet planet = satellite.planet;
        if(planet == null || planet.sectors == null){
            cont.add("没有可用区块");
            return;
        }

        Seq<Sector> available = planet.sectors.select(s -> s.hasBase() || s.unlocked() || s.preset != null);

        ScrollPane pane = new ScrollPane(new Table(), Styles.defaultPane);
        Table table = (Table)pane.getWidget();

        for(Sector sector : available){
            String label = sector.id + ": " + (sector.preset != null ? sector.preset.localizedName : "Sector " + sector.id);
            table.button(label, Styles.cleart, () -> {
                // 计算目标角度：指向该 sector 的方向
                float targetAngle = Mathf.atan2(sector.tile.v.x, sector.tile.v.y);
                // 先解除旧绑定再重新绑定，确保可以移动
                satellite.unbindSector();
                satellite.startMove(targetAngle, 120f); // 2秒动画
                satellite.bindToSector(sector.id);      // 移动结束后静止在区块上方
                hide();
            }).width(280f).pad(4f).row();
        }

        cont.add(pane).size(320f, 400f);
        buttons.button("@close", this::hide).size(120f, 50f);
    }
}
