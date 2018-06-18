public abstract class AbsenceChecker extends PropertyChecker{
    @Override
    protected abstract boolean evaluateState(Snapshot state, Property verificationProperty);

    @Override
    public boolean check(SimulationLog simLog, Property verificationProperty) {
        int logSize = simLog.getSize(); // 0 ... 10 => size: 11, endTime: 10

        for (int i = 0; i < logSize; i++) {
            if (evaluateState(simLog.getSnapshot(i), verificationProperty)) {
                return false;
            }
        }
        return true;
    }
}
