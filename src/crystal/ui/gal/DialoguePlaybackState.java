package crystal.ui.gal;

/** Explicit lifecycle state for the dialogue player. */
public enum DialoguePlaybackState {
  idle,
  preparing,
  typing,
  typingChoice,
  awaitingAdvance,
  awaitingChoice;

  public boolean sessionActive() {
    return this != idle;
  }

  public boolean visible() {
    return this == typing || this == typingChoice
        || this == awaitingAdvance || this == awaitingChoice;
  }
}
