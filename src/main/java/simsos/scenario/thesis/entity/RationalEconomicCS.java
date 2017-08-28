package simsos.scenario.thesis.entity;

import simsos.scenario.thesis.ThesisWorld;
import simsos.scenario.thesis.util.ABCItem;
import simsos.scenario.thesis.util.Message;
import simsos.simulation.component.Action;
import simsos.simulation.component.Agent;
import simsos.simulation.component.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

public abstract class RationalEconomicCS extends Agent {

    // ImmediateActiveStep: Send messages to others (voluntarily)
    // ImmediatePassiveStep: Respond to others' requests
    // NormalStep: Act something
    public enum Phase {ActiveImmediateStep, PassiveImmediateStep, NormalStep}

    protected Phase phase = Phase.ActiveImmediateStep;

    protected ArrayList<Message> incomingRequests = new ArrayList<Message>();
    protected ArrayList<Message> incomingInformation = new ArrayList<Message>();

    protected ArrayList<ABCItem> immediateActionList = new ArrayList<ABCItem>();
    protected ArrayList<ABCItem> normalActionList = new ArrayList<ABCItem>();

    protected Comparator<ABCItem> utilityComparator = new Comparator<ABCItem>() {
        @Override
        public int compare(ABCItem o1, ABCItem o2) {
            return o2.utility() - o1.utility();
        }
    };

    public RationalEconomicCS(World world) {
        super(world);
    }

    @Override
    public Action step() {
        updateBelief();
        generateActionList();

        return selectBestAction();
    }

    protected void updateBelief() {
        // Observe environment
        observeEnvironment();

        if (this.incomingInformation.size() > 0) {
            // Consume information from others
            consumeInformation();
        }

        // If I have messages to respond, then set my phase as passive step (reacting to requests)
        if (this.phase == Phase.PassiveImmediateStep && this.incomingRequests.size() == 0) {
            this.phase = Phase.NormalStep;
        } else if (this.phase == Phase.NormalStep && this.incomingRequests.size() > 0) {
            this.phase = Phase.PassiveImmediateStep;
        }
    }

    protected abstract void observeEnvironment();
    protected abstract void consumeInformation();

    protected void generateActionList() {
        this.normalActionList.clear();

        if (this.phase == Phase.ActiveImmediateStep) {
            generateActiveImmediateActions();
            this.phase = Phase.PassiveImmediateStep;
        } else if (this.phase == Phase.PassiveImmediateStep) {
            generatePassiveImmediateActions();
            this.phase = Phase.NormalStep;
        } else {
            generateNormalActions();
            this.phase = Phase.ActiveImmediateStep;
        }
    }

    protected abstract void generateActiveImmediateActions(); // Send messages to others (voluntarily)
    protected abstract void generatePassiveImmediateActions(); // Respond to others' requests
    protected abstract void generateNormalActions(); // Act something

    protected Action selectBestAction() {
        Action res = null;

        if (immediateActionList.size() > 0) {
            Collections.shuffle(immediateActionList, this.world.random);
            Collections.sort(immediateActionList, utilityComparator);
            res = immediateActionList.remove(0).action;
        } else if (normalActionList.size() > 0) {
            Collections.shuffle(normalActionList, this.world.random);
            Collections.sort(normalActionList, utilityComparator);
            res = normalActionList.remove(0).action;
        } else {
            res = Action.getNullAction(1, this.getName() + ": Null action");
        }

        return res;
    }

    public abstract void reset();
    public abstract String getName();

    public abstract HashMap<String, Object> getProperties();

    protected class SendMessage extends Action {
        private final Message message;
        public SendMessage(Message message) {
            super(0);
            this.message = message;
        }
        @Override
        public void execute() {
            ((ThesisWorld) RationalEconomicCS.this.world).sendMessage(message);
        }

        @Override
        public String getName() {
            return RationalEconomicCS.this.getName() + ": Send messages";
        }
    };

    public void receiveMessage(Message message) {
        if (message.purpose == Message.Purpose.ReqInfo || message.purpose == Message.Purpose.ReqAction)
            this.incomingRequests.add(message);
        else
            this.incomingInformation.add(message);
    }
}
