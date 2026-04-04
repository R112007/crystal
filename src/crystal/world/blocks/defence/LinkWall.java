package crystal.world.blocks.defence;

import arc.scene.ui.layout.Table;
import arc.struct.ObjectSet;
import arc.struct.Queue;
import arc.struct.Seq;
import crystal.util.Collections;
import mindustry.Vars;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Call;
import mindustry.world.Edges;
import mindustry.world.Tile;
import mindustry.world.blocks.defense.Wall;

public class LinkWall extends Wall {

  public LinkWall(String name) {
    super(name);
    this.solid = true;
    this.update = true;
  }

  public class LinkWallBuild extends Building {
    public Seq<LinkWallBuild> links = Collections.seqFromObj(this);
    private boolean isUpdating = false;
    protected int seqSize;

    @Override
    public Building init(Tile tile, Team team, boolean shouldAdd, int rotation) {
      Building init = super.init(tile, team, shouldAdd, rotation);
      this.seqSize = this.links.size;
      return init;
    }

    @Override
    public void onProximityUpdate() {
      super.onProximityUpdate();
      updateConnectedBuildings();
      call();
      callHealth();
    }

    private void updateConnectedBuildings() {
      if (isUpdating || dead())
        return;
      isUpdating = true;
      Queue<Building> queue = new Queue<>();
      ObjectSet<LinkWallBuild> visited = new ObjectSet<>();
      queue.addLast(this);
      visited.add(this);
      while (!queue.isEmpty()) {
        Building current = queue.removeFirst();
        for (var edge : Edges.getEdges(current.block.size)) {
          Tile adjacentTile = Vars.world.tile(current.tileX() + edge.x, current.tileY() + edge.y);
          if (adjacentTile == null || adjacentTile.build == null)
            continue;
          Building adjacentBuild = adjacentTile.build;
          if (!(adjacentBuild instanceof LinkWallBuild)) {
            continue;
          }
          if (!visited.contains(((LinkWallBuild) adjacentBuild))
              && adjacentBuild.block == LinkWall.this
              && !adjacentBuild.dead()) {
            visited.add(((LinkWallBuild) adjacentBuild));
            queue.addLast(adjacentBuild);
          }
        }
      }
      links = new Seq<>();
      links.addAll(visited);
      isUpdating = false;
    }

    public void call() {
      Seq<LinkWallBuild> tmp = this.links;
      for (var b : links) {
        if (b.links.size > tmp.size) {
          tmp = b.links.copy();
        }
      }
      for (var b : links) {
        b.links = tmp.copy();
      }
    }

    public void callHealth() {
      float allHealth = 0f;
      for (var b : links) {
        allHealth += b.health;
      }
      for (var b : links) {
        b.health = allHealth / links.size;
      }
    }

    @Override
    public void heal(float amount) {
      health: {
        health += amount;
        clampHealth();
        for (var b : links) {
          b.health = this.health;
          b.clampHealth();
        }
      }
      building: {
        healthChanged();
        for (var b : links) {
          b.healthChanged();
        }
      }
    }

    @Override
    public void updateTile() {
      super.updateTile();
      if (seqSize != links.size)
        seqSize = links.size;
    }

    /*
     * @Override
     * public void displayBars(Table table) {
     * super.displayBars(table);
     * table.add(new Bar("" + Mathf.round(health) + '/' + Mathf.round(maxHealth),
     * Pal.health,
     * () -> health / maxHealth));
     * }
     */
    @Override
    public void damage(float damage) {
      if (dead())
        return;
      if (!Vars.net.client()) {
        health -= damage / seqSize;
        if (health <= 0) {
          this.kill();
          for (LinkWallBuild b : this.links) {
            if (!b.dead()) {
              b.kill();
            }
          }
        } else {
          for (LinkWallBuild b : this.links) {
            b.health = health;
          }
        }
      }
      healthChanged();
      if (health <= 0) {
        for (LinkWallBuild b : this.links) {
          Call.buildDestroyed(b);
        }
        Call.buildDestroyed(this);
      }
    }

    @Override
    public void remove() {
      links.remove(this);
      for (var b : links) {
        b.links = new Seq<>();
      }
      for (var b : links) {
        b.updateConnectedBuildings();
      }
      super.remove();
    }

    @Override
    public void placed() {
      super.placed();
      updateConnectedBuildings();
      call();
      callHealth();
    }

    @Override
    public void display(Table table) {
      super.display(table);
      if (Vars.player.team() == this.team) {
        table.row();
        table.label(() -> {
          return "链接数量" + this.seqSize;
        }).pad(4).wrap().width(200f).left();
      }
    }
  }
}
