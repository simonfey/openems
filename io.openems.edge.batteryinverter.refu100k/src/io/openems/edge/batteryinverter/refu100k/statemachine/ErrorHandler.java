package io.openems.edge.batteryinverter.refu100k.statemachine;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.batteryinverter.refu100k.RefuStore100k;
import io.openems.edge.batteryinverter.refu100k.statemachine.StateMachine.State;
import io.openems.edge.common.statemachine.StateHandler;

public class ErrorHandler extends StateHandler<State, Context> {

	@Override
	protected State runAndGetNextState(Context context) throws OpenemsNamedException {
		RefuStore100k inverter = context.getParent();

		// Setting Active and Reactive power to 0
		inverter.getSetActivePowerL1Channel().setNextWriteValue(0);
		inverter.getSetActivePowerL2Channel().setNextWriteValue(0);
		inverter.getSetActivePowerL3Channel().setNextWriteValue(0);
		inverter.getSetReactivePowerL1Channel().setNextWriteValue(0);
		inverter.getSetReactivePowerL2Channel().setNextWriteValue(0);
		inverter.getSetReactivePowerL3Channel().setNextWriteValue(0);

		// Try again
		return State.UNDEFINED;
	}

}
