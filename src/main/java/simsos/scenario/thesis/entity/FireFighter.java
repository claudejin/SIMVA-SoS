package simsos.scenario.thesis.entity;

import simsos.scenario.thesis.ThesisScenario.SoSType;
import simsos.scenario.thesis.ThesisWorld;
import simsos.scenario.thesis.util.*;
import simsos.simulation.component.Action;
import simsos.simulation.component.World;

import java.util.*;

public class FireFighter extends RationalEconomicCS {

    Maptrix<Integer> expectedPatientsMap = (Maptrix<Integer>) this.world.getResources().get("ExpectedPatientsMap");
    Maptrix<HashSet> beliefMap = new Maptrix<HashSet>(HashSet.class, ThesisWorld.MAP_SIZE.getLeft(), ThesisWorld.MAP_SIZE.getRight());

//    private Location headingLocation = ((ThesisWorld) world).getRandomPatientLocation();

    private Location location;

    private enum Direction {NONE, LEFT, RIGHT, UP, DOWN}
    private Direction lastDirection;

    private Patient discoveredPatient = null;

    public FireFighter(World world, String name) {
        super(world);

        this.name = name;
        this.reset();
    }

    @Override
    protected void observeEnvironment() {
        // FireFighter observe current location and update already discovered patients
        Set<Patient> localBelief = this.beliefMap.getValue(this.location);
        Set<Patient> discoveredPatients = ((ThesisWorld) this.world).getDiscoveredPatients(this.location);
        localBelief.addAll(discoveredPatients);

//        // FireFighter observe global location and update already discovered patients
//        Set<Patient> localBelief = null;
//        Set<Patient> discoveredPatients = null;
//        for (int x = 0; x < ThesisWorld.MAP_SIZE.getLeft(); x++)
//            for (int y = 0; y < ThesisWorld.MAP_SIZE.getRight(); y++) {
//                localBelief = this.beliefMap.getValue(x, y);
//                discoveredPatients = ((ThesisWorld) this.world).getDiscoveredPatients(new Location(x, y));
//                localBelief.addAll(discoveredPatients);
//            }
    }

    @Override
    protected void consumeInformation() {
        // Process this.incomingInformation
    }

    @Override
    protected void generateActiveImmediateActions() {
        // I-Discover
        switch ((SoSType) this.world.getResources().get("Type")) {
            default:
            this.immediateActionList.add(new ABCItem(this.discoverPatient, 10, 1));
        }

        // I-Share
        switch ((SoSType) this.world.getResources().get("Type")) {
            case Acknowledged:
            case Collaborative:

                break;
        }
    }

    @Override
    protected void generatePassiveImmediateActions() {
        for (Message message : this.incomingRequests) {
            // I-ReportLocation
            if (message.sender.equals("ControlTower") && message.purpose == Message.Purpose.ReqInfo && message.data.containsKey("Location")) {
                Message locationReport = new Message();
                locationReport.name = "Respond location report";
                locationReport.sender = this.getName();
                locationReport.receiver = "ControlTower";
                locationReport.purpose = Message.Purpose.Response;
                locationReport.data.put("Location", this.location);

                switch ((SoSType) this.world.getResources().get("Type")) {
                    case Directed:
                    case Acknowledged:

                        this.immediateActionList.add(new ABCItem(new SendMessage(locationReport), 0, 0));
                        break;
                }
            }

            // I-ReportDiscovery
            if (message.sender.equals("ControlTower") && message.purpose == Message.Purpose.ReqInfo && message.data.containsKey("Discovered")) {
                Message discoveryReport = new Message();
                discoveryReport.name = "Respond discovery report";
                discoveryReport.sender = this.getName();
                discoveryReport.receiver = "ControlTower";
                discoveryReport.purpose = Message.Purpose.Response;
                discoveryReport.data.put("Discovered", this.discoveredPatient);

                switch ((SoSType) this.world.getResources().get("Type")) {
                    case Directed:
                    case Acknowledged:

                        this.immediateActionList.add(new ABCItem(new SendMessage(discoveryReport), 0, 0));
                        break;
                }
            }
        }
    }

    @Override
    protected void generateNormalActions() {
        // N-Directed Moves
        if (this.world.getResources().get("Type") == SoSType.Directed) {

        }

        // N-Autonomous Moves
        if (this.world.getResources().get("Type") != SoSType.Directed) {
//                if (this.location.equals(this.headingLocation))
//                    updateHeadingLocation();

            if (FireFighter.this.location.getX() > 0 && lastDirection != Direction.RIGHT)
                normalActionList.add(new ABCItem(new Move(Direction.LEFT), 0, calculateMoveCost(-1, 0)));
            if (FireFighter.this.location.getX() < ThesisWorld.MAP_SIZE.getLeft() - 1 && lastDirection != Direction.LEFT)
                normalActionList.add(new ABCItem(new Move(Direction.RIGHT), 0, calculateMoveCost(1, 0)));
            if (FireFighter.this.location.getY() > 0 && lastDirection != Direction.DOWN)
                normalActionList.add(new ABCItem(new Move(Direction.UP), 0, calculateMoveCost(0, -1)));
            if (FireFighter.this.location.getY() < ThesisWorld.MAP_SIZE.getRight() - 1 && lastDirection != Direction.UP)
                normalActionList.add(new ABCItem(new Move(Direction.DOWN), 0, calculateMoveCost(0, 1)));
        }

    }

    public int calculateMoveCost(int deltaX, int deltaY) {
        Location nextLocation = this.location.add(deltaX, deltaY);
        // Uncertainty
        int totalCost = this.world.random.nextInt(2);

//        // Distance cost
//        totalCost += nextLocation.distanceTo(this.headingLocation);

//        // Belief cost
        totalCost += this.beliefMap.getValue(nextLocation.getX(), nextLocation.getY()).size();
        totalCost -= this.expectedPatientsMap.getValue(nextLocation.getX(), nextLocation.getY());

        return totalCost;
    }

//    private void updateHeadingLocation() {
//        while (this.location.equals(this.headingLocation))
//            this.headingLocation = ((ThesisWorld) world).getRandomPatientLocation();
//    }

    @Override
    public void reset() {
        this.beliefMap.reset();

        this.location = new Location(ThesisWorld.MAP_SIZE.getLeft() / 2, ThesisWorld.MAP_SIZE.getRight() / 2);
//        this.location = new Location(0, 0);
//        updateHeadingLocation();
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public String getSymbol() {
        return this.name.replace("FireFighter", "F");
    }

    @Override
    public HashMap<String, Object> getProperties() {
        HashMap<String, Object> properties = new HashMap<String, Object>();
        properties.put("location", this.location);
        properties.put("BeliefMap", this.beliefMap);
        return properties;
    }

    private final Action discoverPatient = new Action(0) {

        @Override
        public void execute() {
            FireFighter.this.discoveredPatient = ((ThesisWorld) FireFighter.this.world).getUndiscoveredPatient(FireFighter.this.location);

            if (FireFighter.this.discoveredPatient != null) {
                FireFighter.this.beliefMap.getValue(FireFighter.this.location).add(FireFighter.this.discoveredPatient);
            } else {
                // Fail to discover a patient
            }
        }

        @Override
        public String getName() {
            return FireFighter.this.getName() + ": Discover a patient";
        }
    };

    protected class Move extends Action {
        Direction direction = Direction.NONE;

        public Move(Direction direction) {
            super(1);

            this.direction = direction;
        }

        @Override
        public void execute() {
            switch (this.direction) {
                case LEFT:
                    FireFighter.this.location.moveX(-1);
                    break;
                case RIGHT:
                    FireFighter.this.location.moveX(1);
                    break;
                case UP:
                    FireFighter.this.location.moveY(-1);
                    break;
                case DOWN:
                    FireFighter.this.location.moveY(1);
                    break;
                default:
                    System.out.println("FireFighter: Move Error"); // Error
            }

            FireFighter.this.lastDirection = this.direction;
        }

        @Override
        public String getName() {
            return null;
        }
    }
}
