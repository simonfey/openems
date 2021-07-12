package io.openems.edge.batteryinverter.refu100k.statemachine;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.batteryinverter.api.SymmetricBatteryInverter;
import io.openems.edge.batteryinverter.refu100k.RefuStore100k;
import io.openems.edge.batteryinverter.refu100k.statemachine.StateMachine.State;
import io.openems.edge.common.channel.IntegerReadChannel;
import io.openems.edge.common.startstop.StartStop;
import io.openems.edge.common.statemachine.StateHandler;

public class RunningHandler extends StateHandler<State, Context> {

	@Override
	protected State runAndGetNextState(Context context) throws OpenemsNamedException {
		RefuStore100k inverter = context.getParent();

		if (inverter.hasFaults()) {
			return State.UNDEFINED;
		}

		switch (inverter.getSystemState()) {
		case START:
			// Mark as started
			inverter._setStartStop(StartStop.START);
			// Apply Active and Reactive Power Set-Points
			this.applyPower(context);
			return State.RUNNING;
		case UNDEFINED:
			return State.UNDEFINED;
		case INIT:
		case PRE_OPERATION:
		case STANDBY:
			return State.GO_RUNNING;
		case FAULT:
			return State.ERROR;
		case STOP:
			return State.GO_STOPPED;
		}
		return State.UNDEFINED;
	}

	public void applyPower(Context context) throws OpenemsNamedException {

		RefuStore100k inverter = context.getParent();

		IntegerReadChannel maxApparentPowerChannel = inverter
				.channel(SymmetricBatteryInverter.ChannelId.MAX_APPARENT_POWER);
		int maxApparentPower = maxApparentPowerChannel.value().getOrError();

		int wSetPct = 0;
		int varSetPct = 0;

		// Calculate Active Power as a percentage of WMAX
		wSetPct = (((1000 * context.setActivePower) / maxApparentPower)) / 3;
		// Calculate Reactive Power as a percentage of WMAX
		varSetPct = (((100 * context.setReactivePower) / maxApparentPower)) / 3;
		

		inverter.getSetActivePowerL1Channel().setNextWriteValue(wSetPct);
		inverter.getSetActivePowerL2Channel().setNextWriteValue(wSetPct);
		inverter.getSetActivePowerL3Channel().setNextWriteValue(wSetPct);
		inverter.getSetReactivePowerL1Channel().setNextWriteValue(varSetPct);
		inverter.getSetReactivePowerL2Channel().setNextWriteValue(varSetPct);
		inverter.getSetReactivePowerL3Channel().setNextWriteValue(varSetPct);

	}

}
