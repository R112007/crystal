package crystal.ui.gal;

/**
 * Dedicated replay host. Playback logic lives in the base manager; replay mode only changes
 * whether integrations and persistence are enabled.
 */
public final class GalgameReplayManager extends GalgameDialogueManager {
  public static final GalgameReplayManager instance = new GalgameReplayManager();

  private GalgameReplayManager() {
    super(true);
  }
}
