package crystal.world.tumor;

/** Implemented by every building that belongs to a tumor's living network. */
public interface TumorTissue {
  TissueState tumor();
}
