package io.openems.edge.batteryinverter.refu100k.statemachine;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.batteryinverter.refu100k.RefuStore100k;
import io.openems.edge.batteryinverter.refu100k.statemachine.StateMachine.State;
import io.openems.edge.common.statemachine.StateHandler;

public class GoRunningHandler extends StateHandler<State, Context> {

	@Override
	protected void onEntry(Context context) throws OpenemsNamedException {
		RefuStore100k inverter = context.getParent();

		inverter._setMaxStartAttempts(false);
	}

	@Override
	protected State runAndGetNextState(Context context) throws OpenemsNamedException {
		RefuStore100k inverter = context.getParent();

		if (inverter.hasFaults()) {
			return State.UNDEFINED;
		}

		switch (inverter.getSystemState()) {
		// Undefined case
		case UNDEFINED:
			return State.UNDEFINED;

		// Towards go running cases
		case INIT:
		case PRE_OPERATION:
		case STANDBY:
			return State.GO_RUNNING;

		// Start or operational cases
		case START:
			return State.RUNNING;
		// Error cases
		case FAULT:
			return State.ERROR;
		// Towards Stopping case
		case STOP:
			return State.GO_STOPPED;
		}
		return State.UNDEFINED;
	}

}