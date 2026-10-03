package beamup;

import arc.util.Log;
import mindustry.content.*;
import mindustry.mod.*;
import mindustry.type.ItemStack;
import mindustry.world.Block;

import static mindustry.type.ItemStack.with;

public class BeamUp extends Mod{
    @Override
    public void loadContent(){
        LaserTower serpInput   = new LaserTower("input-tower",   LaserTower.Role.input,   with(Items.copper, 10, Items.lead, 10));
        LaserTower serpTransit = new LaserTower("transit-tower", LaserTower.Role.transit, with(Items.copper, 10, Items.lead, 10));
        LaserTower serpOutput  = new LaserTower("output-tower",  LaserTower.Role.output,  with(Items.copper, 10, Items.lead, 10));

        LaserTower erekInput   = new LaserTower("erekir-input-tower",   LaserTower.Role.input,   with(Items.beryllium, 15, Items.graphite, 15));
        LaserTower erekTransit = new LaserTower("erekir-transit-tower", LaserTower.Role.transit, with(Items.beryllium, 15, Items.graphite, 15));
        LaserTower erekOutput  = new LaserTower("erekir-output-tower",  LaserTower.Role.output,  with(Items.beryllium, 15, Items.graphite, 15));

        attach(Blocks.itemBridge, serpInput, serpTransit, serpOutput,
            with(Items.copper, 100, Items.lead, 100),
            with(Items.copper, 100, Items.lead, 100),
            with(Items.copper, 100, Items.lead, 100));

        attach(Blocks.ductBridge, erekInput, erekTransit, erekOutput,
            with(Items.beryllium, 150, Items.graphite, 150),
            with(Items.beryllium, 150, Items.graphite, 150),
            with(Items.beryllium, 150, Items.graphite, 150));
    }

    private void attach(Block parentBlock, LaserTower input, LaserTower transit, LaserTower output,
                        ItemStack[] inCost, ItemStack[] transitCost, ItemStack[] outCost){
        TechTree.TechNode parent = TechTree.all.find(n -> n.content == parentBlock);
        if(parent == null){
            Log.warn("beamup: no tech node found for @", parentBlock.name);
            return;
        }
        TechTree.TechNode in = new TechTree.TechNode(parent, input, inCost);
        TechTree.TechNode tr = new TechTree.TechNode(in, transit, transitCost);
        new TechTree.TechNode(tr, output, outCost);
    }
}