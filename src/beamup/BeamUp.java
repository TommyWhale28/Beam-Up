package beamup;

import arc.util.Log;
import mindustry.content.*;
import mindustry.mod.*;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import mindustry.world.meta.BuildVisibility;

import static mindustry.type.ItemStack.with;

public class BeamUp extends Mod{
    @Override
    public void loadContent(){
        LinkNode serpLink = new LinkNode("link-node", LinkNode.Role.transit, with(Items.copper, 10, Items.lead, 10));
        LinkNode erekLink = new LinkNode("erekir-link-node", LinkNode.Role.transit, with(Items.beryllium, 15, Items.graphite, 15));

        legacy(new LinkNode("input-tower",   LinkNode.Role.input,   with(Items.copper, 10, Items.lead, 10)));
        legacy(new LinkNode("transit-tower", LinkNode.Role.transit, with(Items.copper, 10, Items.lead, 10)));
        legacy(new LinkNode("output-tower",  LinkNode.Role.output,  with(Items.copper, 10, Items.lead, 10)));

        legacy(new LinkNode("erekir-input-tower",   LinkNode.Role.input,   with(Items.beryllium, 15, Items.graphite, 15)));
        legacy(new LinkNode("erekir-transit-tower", LinkNode.Role.transit, with(Items.beryllium, 15, Items.graphite, 15)));
        legacy(new LinkNode("erekir-output-tower",  LinkNode.Role.output,  with(Items.beryllium, 15, Items.graphite, 15)));

        attach(Blocks.itemBridge, serpLink, with(Items.copper, 300, Items.lead, 300));
        attach(Blocks.ductBridge, erekLink, with(Items.beryllium, 450, Items.graphite, 450));
    }

    private void legacy(LinkNode tower){
        tower.buildVisibility = BuildVisibility.hidden;
    }

    private void attach(Block parentBlock, LinkNode tower, ItemStack[] researchCost){
        TechTree.TechNode parent = TechTree.all.find(n -> n.content == parentBlock);
        if(parent == null){
            Log.warn("beamup: no tech node found for @", parentBlock.name);
            return;
        }
        new TechTree.TechNode(parent, tower, researchCost);
    }
}