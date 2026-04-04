package crystal.fetched;

import ent.anno.Annotations.*;
import mindustry.gen.*;
import mindustry.world.blocks.power.*;

@EntityComponent(vanilla = true)
abstract class PowerGraphUpdaterComp implements Entityc{
    public transient PowerGraph graph;

    @Override
    public void update(){
        graph.update();
    }
}
