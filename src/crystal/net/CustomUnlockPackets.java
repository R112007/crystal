package crystal.net;

import arc.struct.ObjectSet;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.net.Net;
import mindustry.net.Packet;

public class CustomUnlockPackets {
  public static void register() {
    Net.registerPacket(UnlockFullSyncPacket::new);
    Net.registerPacket(UnlockProgressPacket::new);
    Net.registerPacket(ClientResearchRequestPacket::new);
    Net.registerPacket(UnlockCompletedPacket::new);
  }

  public static class UnlockFullSyncPacket extends Packet {
    public ObjectSet<String> unlockedIds;

    @Override
    public void write(Writes w) {
      w.i(unlockedIds.size);
      unlockedIds.each(w::str);
    }

    @Override
    public void read(Reads r) {
      int size = r.i();
      unlockedIds = new ObjectSet<>(size);
      for (int i = 0; i < size; i++) {
        unlockedIds.add(r.str());
      }
    }
  }

  public static class UnlockProgressPacket extends Packet {
    public String unlockId;
    public int itemIndex;
    public int addedAmount;

    @Override
    public void write(Writes w) {
      w.str(unlockId);
      w.i(itemIndex);
      w.i(addedAmount);
    }

    @Override
    public void read(Reads r) {
      unlockId = r.str();
      itemIndex = r.i();
      addedAmount = r.i();
    }
  }

  // 3. 客户端研究请求包：客户端向服务端申请研究
  public static class ClientResearchRequestPacket extends Packet {
    public String unlockId;
    // 客户端请求消耗的物品数量
    public int amount;

    @Override
    public void write(Writes w) {
      w.str(unlockId);
      w.i(amount);
    }

    @Override
    public void read(Reads r) {
      unlockId = r.str();
      amount = r.i();
    }
  }

  // 4. 解锁完成包：服务端广播某个内容已解锁
  public static class UnlockCompletedPacket extends Packet {
    public String unlockId;

    @Override
    public void write(Writes w) {
      w.str(unlockId);
    }

    @Override
    public void read(Reads r) {
      unlockId = r.str();
    }
  }
}
