package crystal.fetched;

import arc.util.*;
import ent.anno.Annotations.*;

@EntityComponent(vanilla = true)
abstract class TimerComp{
    transient Interval timer = new Interval(6);

    public boolean timer(int index, float time){
        if(Float.isInfinite(time)) return false;
        return timer.get(index, time);
    }
}
