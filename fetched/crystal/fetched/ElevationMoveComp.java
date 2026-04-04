package crystal.fetched;

import ent.anno.Annotations.*;
import mindustry.entities.*;
import mindustry.entities.EntityCollisions.*;
import mindustry.gen.*;

@EntityComponent(vanilla = true)
abstract class ElevationMoveComp implements Velc, Posc, Hitboxc, Unitc{
    @Import float x, y;

    @Replace
    @Override
    public SolidPred solidity(){
        return isFlying() || ignoreSolids() ? null : EntityCollisions::solid;
    }

}
