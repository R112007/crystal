package crystal.core;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.math.Interp;
import arc.scene.actions.Actions;
import arc.scene.style.Drawable;
import arc.scene.ui.Button;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import crystal.CVars;
import crystal.content.WorldStuffs;
import crystal.game.UnitInfo;
import crystal.ui.dialogs.CPlanetDialog;
import crystal.ui.dialogs.CResearchDialog;
import crystal.ui.dialogs.WorldStuffDialog;
import mindustry.Vars;
import mindustry.content.UnitTypes;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.type.Sector;
import mindustry.type.UnitType;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.meta.StatValues;

public class UI {
    public CResearchDialog cresearch;
    public CPlanetDialog cplanet;
    public WorldStuffDialog stuff;
    float height = 100;

    public void init() {
        WorldStuffs.load();
        cresearch = new CResearchDialog();
        cplanet = new CPlanetDialog();
        stuff = new WorldStuffDialog();
        Vars.ui.hudGroup.fill(null, table -> {
            table.table(null, t -> {
                t.button("对话", () -> {
                })
                        .size(100, 70);
            }).size(100, 70);
            table.center().left().update(() -> {
                table.translation.set(0, height);
            });
        });
    }


}
