package crystal.core;

import arc.ApplicationListener;
import crystal.ui.dialogs.CPlanetDialog;
import crystal.ui.dialogs.CResearchDialog;

public class UI implements ApplicationListener {
  public CResearchDialog cresearch;
  public CPlanetDialog cplanet;

  @Override
  public void init() {
    cresearch = new CResearchDialog();
    cplanet = new CPlanetDialog();
  }
}
