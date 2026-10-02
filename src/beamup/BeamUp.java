package beamup;

import mindustry.mod.*;

public class BeamUp extends Mod{

    @Override
    public void loadContent(){
        new LaserTower("input-tower", LaserTower.Role.input);
        new LaserTower("transit-tower", LaserTower.Role.transit);
        new LaserTower("output-tower", LaserTower.Role.output);
    }

}