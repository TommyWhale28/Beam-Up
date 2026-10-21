package beamup;

import arc.Core;
import arc.func.Cons;
import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.math.geom.Point2;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.ButtonGroup;
import arc.scene.ui.ImageButton;
import arc.scene.ui.layout.Table;
import arc.struct.*;
import arc.util.Time;
import arc.util.io.*;
import mindustry.gen.Building;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.graphics.*;
import mindustry.type.Category;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import mindustry.ui.Styles;
import mindustry.world.Block;
import mindustry.world.blocks.ConstructBlock;

import static mindustry.Vars.*;

public class LinkNode extends Block{
    public enum Role{
        input("Input", Pal.heal),
        transit("Transit", Pal.accent),
        output("Output", Pal.ammo);

        public final String label;
        public final Color color;

        Role(String label, Color color){
            this.label = label;
            this.color = color;
        }

        public boolean makesLinks(){ return this != output; }

        public boolean acceptsLinks(){ return this != input; }

        public static Role fromOrdinal(int i, Role fallback){
            Role[] all = values();
            return i >= 0 && i < all.length ? all[i] : fallback;
        }

        public static Role fromName(String name, Role fallback){
            for(Role r : values()) if(r.name().equals(name)) return r;
            return fallback;
        }
    }

    public final Role defaultRole;
    public float laserRange = 9.5f;
    public int maxLinks = 3;
    public float itemsPerSecond = 10f;
    public TextureRegion laser, laserEnd;
    public Color beamColor = Pal.accent;
    public float laserScale = 0.25f;

    public LinkNode(String name, Role defaultRole, ItemStack[] cost){
        super(name);
        this.defaultRole = defaultRole;
        size = 1;
        update = true;
        solid = true;
        hasItems = true;
        itemCapacity = 10;
        configurable = true;
        requirements(Category.distribution, cost);

        buildType = LinkNodeBuild::new;

        config(Integer.class, (LinkNodeBuild tower, Integer pos) -> {
            if(!tower.role.makesLinks()) return;
            int index = tower.links.indexOf(pos);
            if(index != -1){
                tower.links.removeIndex(index);
                tower.confirmed.remove(pos);
            }else if(tower.links.size < maxLinks){
                tower.links.add(pos);
            }
        });

        config(String.class, (LinkNodeBuild tower, String roleName) -> {
            tower.setRole(Role.fromName(roleName, tower.role));
        });

        config(Object[].class, (LinkNodeBuild tower, Object[] value) -> {
            int first = 0;
            if(value.length > 0 && value[0] instanceof Integer){
                tower.setRole(Role.fromOrdinal((Integer)value[0], tower.role));
                first = 1;
            }
            tower.links.clear();
            tower.confirmed.clear();
            if(!tower.role.makesLinks()) return;
            for(int i = first; i < value.length; i++){
                if(value[i] instanceof Point2) addOffsetLink(tower, (Point2)value[i]);
            }
        });

        config(Point2[].class, (LinkNodeBuild tower, Point2[] offsets) -> {
            tower.links.clear();
            tower.confirmed.clear();
            if(!tower.role.makesLinks()) return;
            for(Point2 p : offsets) addOffsetLink(tower, p);
        });
    }

    private void addOffsetLink(LinkNodeBuild tower, Point2 p){
        if(tower.links.size >= maxLinks) return;
        if(p.x * p.x + p.y * p.y > laserRange * laserRange) return;
        tower.links.add(Point2.pack(p.x + tower.tileX(), p.y + tower.tileY()));
    }

    private static TextureRegionDrawable iconFor(Role r){
        switch(r){
            case input:  return Icon.download;
            case output: return Icon.upload;
            default:     return Icon.link;
        }
    }

    @Override
    public Object pointConfig(Object config, Cons<Point2> transformer){
        if(config instanceof Object[]){
            Object[] in = (Object[])config;
            Object[] out = new Object[in.length];
            for(int i = 0; i < in.length; i++){
                if(in[i] instanceof Point2){
                    Point2 p = ((Point2)in[i]).cpy();
                    transformer.get(p);
                    out[i] = p;
                }else{
                    out[i] = in[i];
                }
            }
            return out;
        }
        return config;
    }

    public TextureRegion[] roleRegions;

    @Override
    public void load(){
        super.load();
        laser = Core.atlas.find("laser");
        laserEnd = Core.atlas.find("laser-end");

        Role[] roles = Role.values();
        roleRegions = new TextureRegion[roles.length];
        for(int i = 0; i < roles.length; i++){
            roleRegions[i] = Core.atlas.find(name + "-" + roles[i].name());
        }
    }

    @Override
    public void init(){
        super.init();
        updateClipRadius(laserRange * tilesize);
    }

    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid){
        super.drawPlace(x, y, rotation, valid);
        Drawf.circles(x * tilesize + offset, y * tilesize + offset, laserRange * tilesize, Pal.accent);
    }

    public class LinkNodeBuild extends Building{
        public Role role = defaultRole;
        public IntSeq links = new IntSeq();
        IntSet confirmed = new IntSet();
        float[] flash = new float[maxLinks];
        float charge;
        int nextLink = 0;
        int lastItem = 0;

        void setRole(Role newRole){
            role = newRole;
            if(!role.makesLinks()){
                links.clear();
                confirmed.clear();
            }
        }

        @Override
        public boolean acceptItem(Building source, Item item){
            if(items.get(item) >= itemCapacity) return false;
            if(source instanceof LinkNodeBuild){
                return role.acceptsLinks() && ((LinkNodeBuild)source).links.indexOf(pos()) != -1;
            }
            return role == Role.input;
        }

        @Override
        public boolean canDump(Building to, Item item){
            return !(to instanceof LinkNodeBuild);
        }

        @Override
        public boolean onConfigureBuildTapped(Building other){
            if(other == this || !role.makesLinks() || !(other instanceof LinkNodeBuild)) return true;

            LinkNodeBuild target = (LinkNodeBuild)other;
            if(links.indexOf(other.pos()) != -1){
                configure(other.pos());
                return false;
            }
            if(links.size < maxLinks && target.role.acceptsLinks() && other.team == team
                    && within(other, laserRange * tilesize) && !createsLoop(other)){
                configure(other.pos());
                return false;
            }
            return true;
        }

        private boolean createsLoop(Building start){
            Seq<Building> stack = new Seq<>();
            IntSet seen = new IntSet();
            stack.add(start);
            while(stack.size > 0){
                Building cur = stack.pop();
                if(cur == this) return true;
                if(!(cur instanceof LinkNodeBuild)) continue;
                if(seen.contains(cur.pos())) continue;
                seen.add(cur.pos());
                LinkNodeBuild t = (LinkNodeBuild)cur;
                for(int i = 0; i < t.links.size; i++){
                    Building next = world.build(t.links.get(i));
                    if(next != null) stack.add(next);
                }
            }
            return false;
        }

        private void pruneLinks(){
            for(int i = links.size - 1; i >= 0; i--){
                int pos = links.get(i);
                Building b = world.build(pos);
                if(b instanceof LinkNodeBuild && b.isValid()){
                    if(((LinkNodeBuild)b).role.acceptsLinks()){
                        confirmed.add(pos);
                    }else{
                        links.removeIndex(i);
                        confirmed.remove(pos);
                    }
                }else if(b == null || !b.isValid()){
                    if(confirmed.contains(pos)){
                        links.removeIndex(i);
                        confirmed.remove(pos);
                    }
                }else if(!(b.block instanceof ConstructBlock)){
                    links.removeIndex(i);
                    confirmed.remove(pos);
                }
            }
        }

        private boolean sendOne(){
            if(links.size == 0) return false;
            var all = content.items();
            for(int l = 0; l < links.size; l++){
                int linkIndex = (nextLink + l) % links.size;
                Building target = world.build(links.get(linkIndex));
                if(target == null || !target.isValid()) continue;
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

        private boolean dumpOne(){
            var all = content.items();
            for(int i = 0; i < all.size; i++){
                Item item = all.get((lastItem + i) % all.size);
                if(items.get(item) > 0 && dump(item)){
                    lastItem = (lastItem + i + 1) % all.size;
                    return true;
                }
            }
            return false;
        }

        @Override
        public void updateTile(){
            float interval = 60f / itemsPerSecond;
            charge = Math.min(charge + Time.delta, interval * 2f);

            if(role == Role.output){
                while(charge >= interval && dumpOne()) charge -= interval;
                return;
            }

            for(int i = 0; i < flash.length; i++){
                flash[i] = Math.max(0f, flash[i] - Time.delta / 20f);
            }

            pruneLinks();

            while(charge >= interval && sendOne()) charge -= interval;
        }

        @Override
        public void buildConfiguration(Table table){
            ButtonGroup<ImageButton> group = new ButtonGroup<>();
            int columns = Role.values().length;

            Table modes = new Table().background(Styles.black6);
            for(Role r : Role.values()){
                ImageButton button = modes.button(iconFor(r), Styles.clearNoneTogglei, 40f, () -> {
                    if(role != r) configure(r.name());
                }).tooltip(r.label).group(group).size(40f).get();
                button.update(() -> button.setChecked(role == r));
            }
            table.top().add(modes);

            table.row();

            Table extras = new Table().background(Styles.black6);
            extras.image(Tex.whiteui, Pal.gray).height(4f).growX().colspan(columns).row();
            extras.button(Icon.cancel, Styles.cleari, 40f, () -> configure(new Point2[0]))
                .tooltip("Clear links").size(40f);
            for(int j = 1; j < columns; j++) extras.add().size(40f);

            table.row();
            table.add(extras).fillX().left();
        }

        @Override
        public Object config(){
            Object[] out = new Object[1 + links.size];
            out[0] = role.ordinal();
            for(int i = 0; i < links.size; i++){
                out[i + 1] = Point2.unpack(links.get(i)).sub(tile.x, tile.y);
            }
            return out;
        }

        @Override
        public void drawConfigure(){
            super.drawConfigure();
            Drawf.circles(x, y, laserRange * tilesize, Pal.accent);
        }

        @Override
        public void draw(){
            super.draw();

            TextureRegion overlay = roleRegions[role.ordinal()];
            if(Core.atlas.isFound(overlay)){
                Draw.rect(overlay, x, y);
            }

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
            return 2;
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.b(role.ordinal());
            write.b(links.size);
            for(int i = 0; i < links.size; i++) write.i(links.get(i));
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            if(revision >= 2){
                role = Role.fromOrdinal(read.b(), role);
            }
            links.clear();
            if(revision >= 1){
                int count = read.b();
                for(int i = 0; i < count; i++) links.add(read.i());
            }
        }
    }
}