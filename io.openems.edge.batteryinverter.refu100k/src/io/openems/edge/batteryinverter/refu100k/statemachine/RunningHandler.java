package io.openems.edge.batteryinverter.refu100k.statemachine;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.batteryinverter.refu100k.RefuStore100k;
import io.openems.edge.batteryinverter.refu100k.statemachine.StateMachine.State;
import io.openems.edge.common.statemachine.StateHandler;

public class RunningHandler extends StateHandler<State, Context> {

	@Override
	protected State runAndGetNextState(Context context) throws OpenemsNamedException {
		RefuStore100k inverter = context.getParent();
		
		if (inverter.hasFaults()) {
			return State.UNDEFINED;
		}
		
		//switch(inverter.getOperatingState())
		
		return null;
	}

}
