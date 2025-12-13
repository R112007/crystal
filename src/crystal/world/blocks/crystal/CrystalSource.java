package crystal.world.blocks.crystal;

import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.io.Reads;
import arc.util.io.Writes;
import crystal.world.interfaces.ConsumeCrystalInterface;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.world.Block;

public class CrystalSource extends Block {
  public CrystalSource(String name) {
    super(name);
    update = true;
    solid = true;
  }

  public class CrystalSourceBuild extends Building {
    public boolean shouldUpdate = true;
    public ObjectMap<Integer, Building> crys = new ObjectMap<>();

    @Override
    public void updateTile() {
      if (shouldUpdate) {
        updateProximity();
        for (var entity : this.proximity) {
          if (entity instanceof ConsumeCrystalInterface cry) {
            if (!crys.containsKey(entity.pos())) {
              crys.put(entity.id, entity);
              cry.getConsumer().tickImprove(10000f);
            }
          }
        }
        shouldUpdate = false;
      }
    }

    @Override
    public void remove() {
      super.remove();
      for (var entity : this.proximity) {
        if (entity instanceof ConsumeCrystalInterface cry) {
          cry.getConsumer().tick -= 10000f;
        }
      }
    }

    @Override
    public void onProximityUpdate() {
      super.onProximityUpdate();
      shouldUpdate = true;
    }

    @Override
    public void write(Writes write) {
      super.write(write);
      write.i(crys.size);
      for (var i : crys.keys()) {
        write.i(i);
      }
    }

    @Override
    public void read(Reads read, byte revision) {
      super.read(read, revision);
      int size = read.i();
      for (int i = 0; i < size; i++) {
        int id = read.i();
        crys.put(id, Groups.build.getByID(id));
      }
    }
  }
}
