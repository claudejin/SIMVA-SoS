package simsos.scenario.thesis;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.distribution.NormalDistribution;
import simsos.scenario.thesis.ThesisScenario.SoSType;
import simsos.scenario.thesis.entity.Ambulance;
import simsos.scenario.thesis.entity.FireFighter;
import simsos.scenario.thesis.entity.Hospital;
import simsos.scenario.thesis.entity.RationalEconomicCS;
import simsos.scenario.thesis.util.*;
import simsos.simulation.analysis.PropertyValue;
import simsos.simulation.analysis.Snapshot;
import simsos.simulation.component.*;

import java.util.*;

// Map, Comm. Channel, The wounded

// - Maintain CSs’ current locations (for simulation log only)
// - Maintain reported wounded persons (for simulation log only)

public class ThesisWorld extends World {

    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_BLACK = "\u001B[30m";
    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_YELLOW = "\u001B[33m";
    public static final String ANSI_BLUE = "\u001B[34m";
    public static final String ANSI_PURPLE = "\u001B[35m";
    public static final String ANSI_CYAN = "\u001B[36m";
    public static final String ANSI_WHITE = "\u001B[37m";

    public SoSType type = null;
    public final int nPatient;

    public static final Pair<Integer, Integer> MAP_SIZE = new Pair<Integer, Integer>(19, 19);
    public final Maptrix<Integer> expectedPatientsMap = new Maptrix<Integer>(Integer.TYPE, MAP_SIZE.getLeft(), MAP_SIZE.getRight());
    public final Maptrix<ArrayList> patientsMap = new Maptrix<ArrayList>(ArrayList.class, MAP_SIZE.getLeft(), MAP_SIZE.getRight());

    public ArrayList<Patient> patients = new ArrayList<Patient>();

    public ThesisWorld(SoSType type, int nPatient) {
        super(new Random().nextLong());

        this.setSoSType(type);
        this.nPatient = nPatient;

        for (int i = 0; i < nPatient; i++)
            patients.add(new Patient(this.random, "Patient" + (i+1)));

        this.reset();
    }

    public void setSoSType(SoSType type) {
        this.type = type;
    }

    @Override
    public void reset() {
        super.reset();

        for (Patient patient : this.patients)
            patient.reset();

        // Adjust severity of patients

        // Adjust geographical distribution of patients
        patientsMap.reset();
        for (Patient patient : this.patients) {
            patient.setLocation(getRandomPatientLocation());
            patientsMap.getValue(patient.getLocation()).add(patient);
        }
        generateExpectedPatientsMap();

        HashMap<String, Location> hospitalLocations = new HashMap<String, Location>();
        for (Agent agent : this.agents)
            if (agent instanceof Hospital)
                hospitalLocations.put(agent.getName(), (Location) agent.getProperties().get("Location"));

        for (Agent agent : this.agents)
            if (agent instanceof Ambulance)
                ((Ambulance) agent).setHospitalLocations(hospitalLocations);
    }

    @Override
    public void progress(int time) {
        super.progress(time);

        for (Agent agent : this.agents)
            if (agent instanceof RationalEconomicCS)
                ((RationalEconomicCS) agent).progress();
    }

    private boolean checkValidLocation(Location location) {
        return checkValidLocation(location.getX(), location.getY());
    }

    private boolean checkValidLocation(int x, int y) {
        if (x < 0 || x > MAP_SIZE.getLeft() - 1)
            return false;
        if (y < 0 || y > MAP_SIZE.getRight() - 1)
            return false;

        return true;
    }

    private boolean checkValidPatientLocation(Location location) {
        for (Agent agent : this.agents) {
            if (agent instanceof Hospital) {
                Location hospitalLocation = (Location) agent.getProperties().get("Location");
                if (hospitalLocation.equals(location))
                    return false;
            }
        }

        return true;
    }

    private void generateExpectedPatientsMap() {
        NormalDistribution xND = new NormalDistribution(MAP_SIZE.getLeft() / 2, 1.5);
        NormalDistribution yND = new NormalDistribution(MAP_SIZE.getLeft() / 2, 1.5);

        for (int x = 0; x < MAP_SIZE.getLeft(); x++)
            for (int y = 0; y < MAP_SIZE.getRight(); y++) {
                double xProb = xND.cumulativeProbability(x) - xND.cumulativeProbability(x - 1);
                double nX = xProb * this.nPatient;

                double yProb = xND.cumulativeProbability(y) - xND.cumulativeProbability(y - 1);
                int nXY = (int) Math.round(yProb * nX);

                this.expectedPatientsMap.setValue(x, y, nXY);
            }
    }

    private Location getRandomPatientLocation() {
        int x = -1, y = -1;
        Location location = null;

        boolean stoppingCondition = false;
        while (!stoppingCondition) {
            x = (int) Math.round(this.random.nextGaussian() * 1.5 + MAP_SIZE.getLeft() / 2);
            y = (int) Math.round(this.random.nextGaussian() * 1.5 + MAP_SIZE.getLeft() / 2);
            location = new Location(x, y);

            if (!checkValidLocation(location))
                continue;

            if (!checkValidPatientLocation(location))
                continue;

            stoppingCondition = true;
        }

        return location;
    }

    @Override
    public HashMap<String, Object> getResources() {
        HashMap<String, Object> resources = new HashMap<String, Object>();
        resources.put("Time", this.time);
        resources.put("Type", this.type);
        resources.put("Agents", this.agents);
        resources.put("Patients", this.patients);
        resources.put("ExpectedPatientsMap", this.expectedPatientsMap);

        return resources;
    }

    public void sendMessage(Message message) {
        System.out.println("Messages: " + message.sender + " - " + message.getName());

        // Send the message to the receiver(s)
        for (Agent agent : this.agents)
            if (agent instanceof RationalEconomicCS) {
                if (agent.getName().equals(message.sender))
                    continue;

                if (agent.getName().startsWith(message.receiver)) {
                    if (message.location == null)
                        ((RationalEconomicCS) agent).receiveMessage(message);
                    else {
                        Map<String, Object> props = agent.getProperties();
                        if (props.containsKey("Location"))
                            if (message.location.equals(props.get("Location")))
                                ((RationalEconomicCS) agent).receiveMessage(message);
                    }
                }
            }
    }

    @Override
    public ArrayList<Action> generateExogenousActions() {
        ArrayList<Action> exo = new ArrayList<Action>();
        exo.add(new Action(0) {

            @Override
            public void execute() {
                for (Patient patient : ThesisWorld.this.patients)
                    patient.bleed();
            }

            @Override
            public String getName() {
                return "World: Patients bleed";
            }
        });

        return exo;
    }

    @Override
    public Snapshot getCurrentSnapshot() {
        Snapshot snapshot = super.getCurrentSnapshot();

        LinkedHashMap<String, Object> worldProperties = new LinkedHashMap<String, Object>();

        worldProperties.put("Time", this.time);
        snapshot.addProperties(null, worldProperties);

        for (Patient patient : this.patients)
            snapshot.addProperty(patient, "Location", patient.getLocation());

        System.out.println("Time: " + this.time);
//        printExpectedPatientsMap();
        printCurrentMap(snapshot);
        printBeliefMap(snapshot);
        return snapshot;
    }

    private void printExpectedPatientsMap() {
        System.out.println("Expected Patients Map");

        String [][] map = new String[MAP_SIZE.getLeft()][MAP_SIZE.getRight()];
        int maximalLength = 0;

        for (int x = 0; x < MAP_SIZE.getLeft(); x++)
            for (int y = 0; y < MAP_SIZE.getRight(); y++) {
                map[x][y] = "" + expectedPatientsMap.getValue(x, y);
                maximalLength = Math.max(maximalLength, map[x][y].length());
            }

        maximalLength = (maximalLength + 1) / 2; // roundup for division by 2

        String horizontal = String.join("", Collections.nCopies(maximalLength, "─"));

        System.out.println("┌" + String.join("┬", Collections.nCopies(MAP_SIZE.getLeft(), horizontal)) + "┐");

        for (int y = 0; y < MAP_SIZE.getRight(); y++) {
            System.out.print("│");
            for (int x = 0; x < MAP_SIZE.getLeft(); x++) {
                if (map[x][y] == null)
                    System.out.print(StringUtils.repeat(" ", 2 * maximalLength));
                else
                    System.out.print(map[x][y] + StringUtils.repeat(" ", 2 * maximalLength - map[x][y].length()));
                System.out.print("│");
            }
            System.out.println("");
            if (y < MAP_SIZE.getRight() - 1)
                System.out.println("├" + String.join("┼", Collections.nCopies(MAP_SIZE.getLeft(), horizontal)) + "┤");
        }

        System.out.println("└" + String.join("┴", Collections.nCopies(MAP_SIZE.getLeft(), horizontal)) + "┘");
    }

    private void printBeliefMap(Snapshot snapshot) {
        ArrayList<PropertyValue> prop = snapshot.getProperties();

        for (PropertyValue pv : prop) {
            if (pv.propertyName.endsWith("BeliefMap")) {
                System.out.println(pv.subjectName + ": " + pv.propertyName);

                String [][] map = new String[MAP_SIZE.getLeft()][MAP_SIZE.getRight()];
                int maximalLength = 0;

                Maptrix<Set<Patient>> beliefMap = (Maptrix<Set<Patient>>) pv.value;

                for (int x = 0; x < MAP_SIZE.getLeft(); x++)
                    for (int y = 0; y < MAP_SIZE.getRight(); y++) {
                        if (pv.propertyName.equals("PatientsBeliefMap"))
                            map[x][y] = "" + ((TimedValue) beliefMap.getValue(x, y)).toString();
                        else
                            map[x][y] = "" + beliefMap.getValue(x, y).size();
                        maximalLength = Math.max(maximalLength, map[x][y].length());
                    }

                maximalLength = (maximalLength + 1) / 2; // roundup for division by 2

                String horizontal = String.join("", Collections.nCopies(maximalLength, "─"));

                System.out.println("┌" + String.join("┬", Collections.nCopies(MAP_SIZE.getLeft(), horizontal)) + "┐");

                for (int y = 0; y < MAP_SIZE.getRight(); y++) {
                    System.out.print("│");
                    for (int x = 0; x < MAP_SIZE.getLeft(); x++) {
                        if (map[x][y] == null)
                            System.out.print(StringUtils.repeat(" ", 2 * maximalLength));
                        else
                            System.out.print(map[x][y] + StringUtils.repeat(" ", 2 * maximalLength - map[x][y].length()));
                        System.out.print("│");
                    }
                    System.out.println("");
                    if (y < MAP_SIZE.getRight() - 1)
                        System.out.println("├" + String.join("┼", Collections.nCopies(MAP_SIZE.getLeft(), horizontal)) + "┤");
                }

                System.out.println("└" + String.join("┴", Collections.nCopies(MAP_SIZE.getLeft(), horizontal)) + "┘");
            }
        }
    }
    private void printCurrentMap(Snapshot snapshot) {
        ArrayList<PropertyValue> prop = snapshot.getProperties();
        String [][] map = new String[MAP_SIZE.getLeft()][MAP_SIZE.getRight()];
        int maximalLength = 0;
        for (PropertyValue pv : prop) {
           if (pv.propertyName.equals("Location")) {
//               System.out.println(pv.subjectName + ": " + pv.value.toString());
               Location location = (Location) pv.value;
               if (map[location.getX()][location.getY()] == null)
                   map[location.getX()][location.getY()] = "";
               if (pv.subject instanceof Patient)
                   if (((Patient) pv.subject).getStatus() == Patient.Status.Discovered)
                       map[location.getX()][location.getY()] += ANSI_RED + pv.subject.getSymbol() + ANSI_RESET;
                   else
                       map[location.getX()][location.getY()] += pv.subject.getSymbol();
               else
                   map[location.getX()][location.getY()] += pv.subject.getSymbol();
               maximalLength = Math.max(maximalLength, map[location.getX()][location.getY()].replaceAll("\u001B\\[[;\\d]*m", "").length());
           }
        }

        maximalLength = (maximalLength + 1) / 2; // roundup for division by 2

        String horizontal = String.join("", Collections.nCopies(maximalLength, "─"));

        System.out.println("┌" + String.join("┬", Collections.nCopies(MAP_SIZE.getLeft(), horizontal)) + "┐");

        for (int y = 0; y < MAP_SIZE.getRight(); y++) {
            System.out.print("│");
            for (int x = 0; x < MAP_SIZE.getLeft(); x++) {
                if (map[x][y] == null)
                    System.out.print(StringUtils.repeat(" ", 2 * maximalLength));
                else
                    System.out.print(map[x][y] + StringUtils.repeat(" ", 2 * maximalLength - map[x][y].replaceAll("\u001B\\[[;\\d]*m", "").length()));
                System.out.print("│");
            }
            System.out.println("");
            if (y < MAP_SIZE.getRight() - 1)
                System.out.println("├" + String.join("┼", Collections.nCopies(MAP_SIZE.getLeft(), horizontal)) + "┤");
        }

        System.out.println("└" + String.join("┴", Collections.nCopies(MAP_SIZE.getLeft(), horizontal)) + "┘");
    }

    public Set<Patient> getDiscoveredPatients(Location location) {
        Set<Patient> discoveredPatients = new HashSet<Patient>();

        ArrayList<Patient> patients = this.patientsMap.getValue(location);
        for (Patient patient : patients) {
            if (patient.getStatus() == Patient.Status.Discovered) {
                discoveredPatients.add(patient);
            }
        }

        return discoveredPatients;
    }
    public Patient getUndiscoveredPatient(Location location) {
        ArrayList<Patient> patients = this.patientsMap.getValue(location);

        for (Patient patient : patients) {
            if (patient.getStatus() == Patient.Status.Initial) {
                patient.setStatus(Patient.Status.Discovered);

                return patient;
            }
        }

        return null;
    }
}
