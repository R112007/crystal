package crystal.aviation.ui;

import arc.*;
import arc.files.*;
import arc.func.*;
import arc.math.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;

import static mindustry.Vars.*;

/**
 * 卫星发射前的名称输入对话框。
 * 支持选择自定义地图文件；不选择则使用默认生成的卫星地图。
 */
public class SatelliteNameDialog extends BaseDialog{
    private final Cons<LaunchResult> callback;
    private @Nullable Fi selectedMapFile;
    private Label mapLabel;
    private TextField heightField;
    private TextField angleField;

    public SatelliteNameDialog(Cons<LaunchResult> callback){
        super("发射卫星");
        this.callback = callback;

        cont.defaults().pad(4f);

        cont.add("卫星名称").left().row();
        TextField field = new TextField("");
        field.setMessageText("输入名称");
        field.setMaxLength(32);
        cont.add(field).width(320f).padBottom(12f).row();

        cont.add("地图文件").left().row();
        Table mapTable = new Table();
        mapLabel = new Label("[gray]使用默认生成地图");
        mapLabel.setWrap(true);
        mapTable.add(mapLabel).width(220f).left();
        mapTable.button("选择", Styles.cleart, () -> {
            showMapSelect(file -> {
                selectedMapFile = file;
                updateMapLabel();
            });
        }).size(80f, 40f).padLeft(8f);
        mapTable.button("清除", Styles.cleart, () -> {
            selectedMapFile = null;
            updateMapLabel();
        }).size(80f, 40f).padLeft(4f);
        cont.add(mapTable).row();

        // 轨道高度与初始角度
        cont.add("轨道高度（半径倍数，1.5~5.0）").left().padTop(8f).row();
        heightField = new TextField("2.5");
        heightField.setMessageText("2.5");
        heightField.setFilter((textField, c) -> Character.isDigit(c) || c == '.' || c == '-');
        cont.add(heightField).width(320f).padBottom(8f).row();

        cont.add("初始角度（度，0~360）").left().row();
        angleField = new TextField("0");
        angleField.setMessageText("0");
        angleField.setFilter((textField, c) -> Character.isDigit(c) || c == '.' || c == '-');
        cont.add(angleField).width(320f).padBottom(12f).row();

        buttons.defaults().size(120f, 50f);
        buttons.button("@cancel", this::hide);
        buttons.button("@ok", () -> {
            LaunchResult result = new LaunchResult();
            result.name = field.getText().trim();
            result.mapFile = selectedMapFile;
            result.orbitRadius = parseFloat(heightField.getText(), 2.5f, 1.5f, 5.0f);
            result.orbitAngleDeg = parseFloat(angleField.getText(), 0f, 0f, 360f);
            callback.get(result);
            hide();
        });

        shown(() -> Core.app.post(field::requestKeyboard));
    }

    float parseFloat(String text, float def, float min, float max){
        try{
            float v = Float.parseFloat(text.trim());
            return Mathf.clamp(v, min, max);
        }catch(Exception e){
            return def;
        }
    }

    void updateMapLabel(){
        if(selectedMapFile != null){
            mapLabel.setText(selectedMapFile.nameWithoutExtension());
        }else{
            mapLabel.setText("[gray]使用默认生成地图");
        }
    }

    void showMapSelect(Cons<Fi> callback){
        BaseDialog dialog = new BaseDialog("选择地图文件");
        Table table = new Table();
        ScrollPane pane = new ScrollPane(table, Styles.defaultPane);

        Seq<Fi> files = Seq.with();
        if(customMapDirectory != null && customMapDirectory.exists()){
            customMapDirectory.walk(file -> {
                if(file.extension().equalsIgnoreCase(mapExtension)){
                    files.add(file);
                }
            });
        }

        if(files.isEmpty()){
            table.add("未在 " + customMapDirectory + " 找到 ." + mapExtension + " 地图文件");
        }else{
            for(Fi file : files){
                table.button(file.nameWithoutExtension(), Styles.cleart, () -> {
                    callback.get(file);
                    dialog.hide();
                }).growX().height(40f).row();
            }
        }

        dialog.cont.add(pane).size(420f, 320f);
        dialog.buttons.button("@close", dialog::hide).size(120f, 50f);
        dialog.show();
    }

    public static class LaunchResult{
        public String name = "";
        public @Nullable Fi mapFile;
        /** 轨道半径（相对于星球半径的倍数） */
        public float orbitRadius = 2.5f;
        /** 初始轨道角度（度） */
        public float orbitAngleDeg = 0f;
    }
}
