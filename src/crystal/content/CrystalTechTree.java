package crystal.content;

import arc.struct.Seq;
import crystal.content.blocks.*;
import crystal.entities.units.UnitEnum.XiuWei;
import crystal.game.CObjectives.ArchieveXiuWei;
import mindustry.content.Liquids;
import mindustry.content.SectorPresets;
import mindustry.game.Objectives;
import mindustry.game.Objectives.Objective;
import mindustry.game.Objectives.Research;
import mindustry.type.ItemStack;

import static crystal.content.Tree.*;

public class CrystalTechTree {
  public static void load() {
    CPlanets.lx.techTree = nodeRoot("lx", CStroage.core1, () -> {
      node(CTurrets.duoguanpao, () -> {
        node(CWalls.lvwall1, () -> {
          node(CWalls.lvwall4, Seq.with(new ArchieveXiuWei(XiuWei.shen)), () -> {
            node(CWalls.xiwall4, () -> {
            });
          });
          node(CWalls.lvwall2, () -> {
            node(CWalls.lvwall3, () -> {
            });
          });
          node(CWalls.xiwall1, () -> {
            node(CWalls.xiwall2, () -> {
              node(CWalls.xiwall3, () -> {
              });
            });
          });
        });
        node(CTurrets.powerair1, () -> {
          node(CTurrets.chuantou, new Seq<Objective>().add(new ArchieveXiuWei(XiuWei.fan)), () -> {
          });
        });
        node(CTurrets.zhentian, () -> {
          node(CTurrets.liuxing, Seq.with(new ArchieveXiuWei(XiuWei.fan)), () -> {
            node(CTurrets.tujin1, Seq.with(new ArchieveXiuWei(XiuWei.fan)), () -> {
              node(CTurrets.tuxi, () -> {
              });
            });
          });
        });
      });
      node(CDistribution.lvconveyor, () -> {
        node(CDistribution.lvlianjieqi, () -> {
          node(CDistribution.lvluyouqi, () -> {
            node(CDistribution.lvbridge, () -> {
              node(CDistribution.xiconveyor, () -> {
                node(CDistribution.xibridge, () -> {
                  node(CDistribution.massdriver1, () -> {
                  });
                });
                node(CDistribution.dropdrill1, () -> {
                });
              });
              node(CDistribution.lvyiliumeng, () -> {
                node(CDistribution.lvfanxiangyiliumeng, () -> {
                });
              });
              node(CDistribution.lvfenleiqi, () -> {
                node(CDistribution.lvfanxiangfenleiqi, () -> {
                });
              });
              node(CDistribution.lvfenpeiqi, () -> {
                node(CDistribution.lvfenliuqi, () -> {
                });
              });
            });
          });
        });
      });
      node(CDrills.lvdrill, ItemStack.with(CItems.lv, 50), () -> {
        node(CPowerBlock.powernode1, () -> {
          node(CFunctionBlock.xiuliqi, () -> {
            node(CFunctionBlock.repairturret1, () -> {
            });
            node(CFunctionBlock.xiuliqi2, () -> {
            });
          });
          node(CPowerBlock.powernode2, () -> {
          });
          node(CPowerBlock.firepower1, Seq.with(new Research(CItems.tandanzhi)), () -> {
            node(CPowerBlock.firepower2, () -> {
            });
            node(CPowerBlock.qilunji, () -> {

            });
          });
        });
        node(CFactories.guicuzhiji, () -> {
          node(CFactories.cuzhiganguo, () -> {
          });
          node(CFactories.guitichunji, () -> {
          });
        });
        node(CLiquidBlocks.beng1, () -> {
          node(CLiquidBlocks.lvdaoguan, () -> {
            node(CLiquidBlocks.lvyetijiaochaqi, () -> {
              node(CLiquidBlocks.lvyetiluyouqi, () -> {
                node(CLiquidBlocks.yetichuguan, () -> {
                });
                node(CLiquidBlocks.lvdaoguanqiao, () -> {
                  node(CLiquidBlocks.xidaoguanqiao, () -> {
                  });
                });
              });
            });
            node(CLiquidBlocks.xidaoguan, () -> {
            });
          });
        });
        node(CDrills.guijingdrill, () -> {
        });
      });
      nodeProduce(CItems.lv, () -> {
        nodeProduce(Liquids.water, () -> {
        });
        nodeProduce(CItems.li, () -> {
          nodeProduce(CItems.boli, () -> {
          });
          nodeProduce(CItems.tandanzhi, () -> {
          });
          nodeProduce(CItems.cuguijing, () -> {
            nodeProduce(CItems.xi, () -> {
              nodeProduce(CItems.lvgang, () -> {
              });
            });
            nodeProduce(CItems.chunguijing, () -> {
            });
          });
        });
      });
    });
  }
}
