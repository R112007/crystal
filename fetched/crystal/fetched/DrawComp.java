package crystal.fetched;

import ent.anno.Annotations.*;
import mindustry.gen.*;

@EntityComponent(vanilla = true)
abstract class DrawComp implements Posc{

    float clipSize(){
        return Float.MAX_VALUE;
    }

    void draw(){

    }
}
