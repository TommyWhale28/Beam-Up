package beamup;

import arc.graphics.g2d.*;
import arc.struct.*;
import mindustry.content.Items;
import mindustry.gen.Building;
import mindustry.graphics.*;
import mindustry.type.Category;
import mindustry.type.Item;
import mindustry.world.Block;

import static mindustry.Vars.*;
import static mindustry.type.ItemStack.with;

public class LaserTower extends Block{
    public enum Role{ input, transit, output }

    public final Role role;
    public float laserRange = 9.5f; // in tiles, center to center
    public int maxLinks = 3;

    public LaserTower(String name, Role role){
        super(name);
        this.role = role;
        size = 1;
        update = true;
        solid = true;
        hasItems = true;
        itemCapacity = 10;
        configurable = makesLinks(); // output towers have nothing to configure
        requirements(Category.distribution, with(Items.copper, 1));

        buildType = LaserTowerBuild::new;

        // Configuring with a position toggles that link on or off.
        config(Integer.class, (LaserTowerBuild tower, Integer pos) -> {
            int index = tower.links.indexOf(pos);
            if(index != -1){
                tower.links.removeIndex(index);
            }else if(tower.links.size < maxLinks){
                tower.links.add(pos);
            }
        });
    }

    /** Can this type create outgoing links? */
    public boolean makesLinks(){ return role != Role.output; }

    /** Can this type be the target of a link? */
    public boolean acceptsLinks(){ return role != Role.input; }

    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid){
        super.drawPlace(x, y, rotation, valid);
        if(makesLinks()){
            Drawf.circles(x * tilesize + offset, y * tilesize + offset, laserRange * tilesize, Pal.accent);
        }
    }

    public class LaserTowerBuild extends Building{
        public IntSeq links = new IntSeq();
        int nextLink = 0; // round-robin position over destinations
        int lastItem = 0; // round-robin position over item types

        @Override
        public boolean acceptItem(Building source, Item item){
            if(items.get(item) >= itemCapacity) return false;
            if(source instanceof LaserTowerBuild){
                // towers may only push along a link that points at this tower
                return acceptsLinks() && ((LaserTowerBuild)source).links.indexOf(pos()) != -1;
            }
            return role == Role.input; // everything else only feeds input towers
        }

        // output towers must not dump into neighbouring towers
        @Override
        public boolean canDump(Building to, Item item){
            return !(to instanceof LaserTowerBuild);
        }

        @Override
        public boolean onConfigureBuildTapped(Building other){
            if(other == this || !makesLinks()) return false;
            if(links.indexOf(other.pos()) != -1){
                configure(other.pos()); // already linked: toggles it off
            }else if(links.size < maxLinks
                    && other instanceof LaserTowerBuild
                    && ((LaserTower)other.block).acceptsLinks()
                    && other.team == team
                    && within(other, laserRange * tilesize)
                    && !createsLoop(other)){
                configure(other.pos());
            }
            return false;
        }

        /** Searches everything reachable from 'start'; true if it leads back to this tower. */
        private boolean createsLoop(Building start){
            Seq<Building> stack = new Seq<>();
            IntSet seen = new IntSet();
            stack.add(start);
            while(stack.size > 0){
                Building cur = stack.pop();
                if(cur == this) return true;
                if(!(cur instanceof LaserTowerBuild)) continue;
                if(seen.contains(cur.pos())) continue;
                seen.add(cur.pos());
                LaserTowerBuild t = (LaserTowerBuild)cur;
                for(int i = 0; i < t.links.size; i++){
                    Building next = world.build(t.links.get(i));
                    if(next != null) stack.add(next);
                }
            }
            return false;
        }

        @Override
        public void updateTile(){
            if(role == Role.output){
                dump(); // hand buffered items to whatever is next to the tower
                return;
            }

            // drop links to destroyed buildings
            for(int i = links.size - 1; i >= 0; i--){
                Building b = world.build(links.get(i));
                if(b == null || !b.isValid()) links.removeIndex(i);
            }
            if(links.size == 0) return;

            var all = content.items();
            for(int l = 0; l < links.size; l++){
                int linkIndex = (nextLink + l) % links.size;
                Building target = world.build(links.get(linkIndex));
                for(int i = 0; i < all.size; i++){
                    Item item = all.get((lastItem + i) % all.size);
                    if(items.get(item) > 0 && target.acceptItem(this, item)){
                        target.handleItem(this, item);
                        items.remove(item, 1);
                        lastItem = (lastItem + i + 1) % all.size;
                        nextLink = (linkIndex + 1) % links.size;
                        return; // one item per tick for now; the rate cap comes next
                    }
                }
            }
        }

        @Override
        public void drawConfigure(){
            super.drawConfigure();
            Drawf.circles(x, y, laserRange * tilesize, Pal.accent);
        }

        @Override
        public void draw(){
            super.draw();
            Draw.z(Layer.power);
            Lines.stroke(1.5f);
            Draw.color(Pal.accent);
            for(int i = 0; i < links.size; i++){
                Building target = world.build(links.get(i));
                if(target != null) Lines.line(x, y, target.x, target.y);
            }
            Draw.reset();
        }
    }
}