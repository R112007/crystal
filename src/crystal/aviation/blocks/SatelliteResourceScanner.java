package crystal.aviation.blocks;

import arc.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.world.*;
import mindustry.world.meta.*;

import crystal.aviation.*;

import static mindustry.Vars.*;

/**
 * 卫星资源扫描仪。
 * 选择星球上任意区块进行扫描，标记为已扫描并显示基础信息。
 */
public class SatelliteResourceScanner extends Block{
    /** 扫描冷却时间（秒） */
    public float scanCooldown = 30f;

    public SatelliteResourceScanner(String name){
        super(name);
        update = true;
        solid = true;
        destructible = true;
        configurable = true;
        hasPower = true;
        consumePower(0.5f);
        requirements(Category.effect, BuildVisibility.shown, new ItemStack[]{
            new ItemStack(mindustry.content.Items.silicon, 100),
            new ItemStack(mindustry.content.Items.titanium, 80),
            new ItemStack(mindustry.content.Items.thorium, 30)
        });
    }

    public class SatelliteResourceScannerBuild extends Building{
        public int satelliteId = -1;
        public float cooldown = 0f;

        @Override
        public void created(){
            super.created();
            // 在卫星地图中新建造时绑定当前卫星
            if(SatelliteManager.currentSatelliteId >= 0 && satelliteId < 0){
                satelliteId = SatelliteManager.currentSatelliteId;
            }
        }

        @Override
        public void updateTile(){
            if(cooldown > 0f) cooldown -= edelta() / 60f;
        }

        @Override
        public void buildConfiguration(Table table){
            Satellite s = SatelliteManager.get(satelliteId);
            if(s == null || s.planet == null || s.planet.sectors == null){
                table.add("未绑定卫星");
                return;
            }

            if(cooldown > 0f){
                table.add("扫描冷却中：" + Strings.fixed(cooldown, 1) + "s");
                return;
            }

            table.add("选择扫描目标").row();
            ScrollPane pane = new ScrollPane(new Table(), Styles.defaultPane);
            Table list = (Table)pane.getWidget();
            for(Sector sector : s.planet.sectors){
                if(sector.preset == null && !sector.hasBase() && !sector.unlocked()) continue;
                boolean scanned = s.scannedSectors.contains(sector.id);
                String label = (scanned ? "[已扫描] " : "") + sector.id + ": " +
                    (sector.preset != null ? sector.preset.localizedName : "Sector " + sector.id);
                list.button(label, Styles.cleart, () -> {
                    scanSector(sector);
                    configure(sector.id);
                }).width(260f).pad(2f).row();
            }
            table.add(pane).size(300f, 280f);
        }

        void scanSector(Sector sector){
            if(sector == null) return;
            configure(sector.id);
        }

        @Override
        public void configured(Unit builder, Object value){
            if(!(value instanceof Integer)) return;
            int sid = (Integer)value;
            Satellite s = SatelliteManager.get(satelliteId);
            if(s == null || s.planet == null) return;
            Sector sector = null;
            for(Sector sec : s.planet.sectors){
                if(sec.id == sid){
                    sector = sec;
                    break;
                }
            }
            if(sector == null) return;
            if(!s.scannedSectors.contains(sector.id)){
                s.scannedSectors.add(sector.id);
            }
            cooldown = scanCooldown;
            if(ui != null){
                String info = "扫描完成：" + sector.id;
                if(sector.preset != null) info += "\n" + sector.preset.localizedName;
                info += "\n威胁度：" + (int)(sector.threat * 100f) + "%";
                ui.showInfo(info);
            }
            SatelliteManager.save();
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.i(satelliteId);
            write.f(cooldown);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            satelliteId = read.i();
            cooldown = read.f();
        }
    }
}
