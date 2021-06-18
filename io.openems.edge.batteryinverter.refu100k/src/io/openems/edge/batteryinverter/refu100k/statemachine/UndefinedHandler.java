package io.openems.edge.batteryinverter.refu100k.statemachine;

import io.openems.edge.batteryinverter.refu100k.statemachine.StateMachine.State;
import io.openems.edge.common.statemachine.StateHandler;

public class UndefinedHandler extends StateHandler<State, Context> {

	@Override
	public State runAndGetNextState(Context context) {
	return null;	
	}
	

}