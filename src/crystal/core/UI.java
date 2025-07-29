package crystal.core;

import arc.ApplicationListener;
import crystal.ui.dialogs.CResearchDialog;

public class UI implements ApplicationListener {
  public CResearchDialog cresearch;

  @Override
  public void init() {
    cresearch = new CResearchDialog();
  }
}
