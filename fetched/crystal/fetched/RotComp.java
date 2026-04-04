package crystal.fetched;

import ent.anno.Annotations.*;
import mindustry.gen.*;

@EntityComponent(vanilla = true)
abstract class RotComp implements Entityc{
    @SyncField(false) @SyncLocal float rotation;
}
