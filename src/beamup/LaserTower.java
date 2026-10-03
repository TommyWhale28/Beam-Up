package beamup;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.struct.*;
import arc.util.Time;
import arc.util.io.*;
import mindustry.gen.Building;
import mindustry.graphics.*;
import mindustry.type.Category;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import mindustry.world.Block;

import static mindustry.Vars.*;

public class LaserTower extends Block{
    public enum Role{ input, transit, output }

    public final Role role;
    public float laserRange = 9.5f;
    public int maxLinks = 3;
    public float itemsPerSecond = 10f;
    public TextureRegion laser, laserEnd;
    public Color beamColor = Pal.accent;
    public float laserScale = 0.25f;

    public LaserTower(String name, Role role, ItemStack[] cost){
        super(name);
        this.role = role;
        size = 1;
        update = true;
        solid = true;
        hasItems = true;
        itemCapacity = 10;
        configurable = makesLinks();
        requirements(Category.distribution, cost);

        buildType = LaserTowerBuild::new;

        config(Integer.class, (LaserTowerBuild tower, Integer pos) -> {
            int index = tower.links.indexOf(pos);
            if(index != -1){
                tower.links.removeIndex(index);
            }else if(tower.links.size < maxLinks){
                tower.links.add(pos);
            }
        });
    }

    @Override
    public void load(){
        super.load();
        laser = Core.atlas.find("laser");
        laserEnd = Core.atlas.find("laser-end");
    }

    @Override
    public void init(){
        super.init();
        updateClipRadius(laserRange * tilesize);
    }

    public boolean makesLinks(){ return role != Role.output; }

    public boolean acceptsLinks(){ return role != Role.input; }

    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid){
        super.drawPlace(x, y, rotation, valid);
        Drawf.circles(x * tilesize + offset, y * tilesize + offset, laserRange * tilesize, Pal.accent);
    }

    public class LaserTowerBuild extends Building{
        public IntSeq links = new IntSeq();
        float[] flash = new float[maxLinks];
        int nextLink = 0;
        int lastItem = 0;

        @Override
        public boolean acceptItem(Building source, Item item){
            if(items.get(item) >= itemCapacity) return false;
            if(source instanceof LaserTowerBuild){
                return acceptsLinks() && ((LaserTowerBuild)source).links.indexOf(pos()) != -1;
            }
            return role == Role.input;
        }

        @Override
        public boolean canDump(Building to, Item item){
            return !(to instanceof LaserTowerBuild);
        }

        @Override
        public boolean onConfigureBuildTapped(Building other){
            if(other == this || !makesLinks()) return false;
            if(links.indexOf(other.pos()) != -1){
                configure(other.pos());
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

        float charge;

        @Override
        public void updateTile(){
            float interval = 60f / itemsPerSecond;
            charge = Math.min(charge + Time.delta, interval * 2f);

            if(role == Role.output){
                while(charge >= interval && dump()) charge -= interval;
                return;
            }

            for(int i = 0; i < flash.length; i++){
                flash[i] = Math.max(0f, flash[i] - Time.delta / 20f);
            }

            for(int i = links.size - 1; i >= 0; i--){
                Building b = world.build(links.get(i));
                if(b == null || !b.isValid()) links.removeIndex(i);
            }

            while(charge >= interval && sendOne()) charge -= interval;
        }

        private boolean sendOne(){
            if(links.size == 0) return false;
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
                        flash[linkIndex] = 1f;
                        return true;
                    }
                }
            }
            return false;
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
            for(int i = 0; i < links.size; i++){
                Building target = world.build(links.get(i));
                if(target == null) continue;

                Draw.color(beamColor, 0.45f + 0.55f * flash[i]);
                Drawf.laser(laser, laserEnd, x, y, target.x, target.y, laserScale);
            }
            Draw.reset();
        }

        @Override
        public byte version(){
            return 1;
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.b(links.size);
            for(int i = 0; i < links.size; i++) write.i(links.get(i));
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            links.clear();
            if(revision >= 1){
                int count = read.b();
                for(int i = 0; i < count; i++) links.add(read.i());
            }
        }
    }
}