package io.openems.edge.batteryinverter.refu100k.statemachine;

import io.openems.edge.battery.api.Battery;
import io.openems.edge.common.statemachine.AbstractContext;
import io.openems.edge.batteryinverter.refu100k.Config;
import io.openems.edge.batteryinverter.refu100k.RefuStore100k;

public class Context extends AbstractContext<RefuStore100k> {

	protected final Battery battery;
	protected final Config config;
	protected final int setActivePower;
	protected final int setReactivePower;

	public Context(RefuStore100k parent, Battery battery, Config config, int setActivePower, int setReactivePower) {
		super(parent);
		this.battery = battery;
		this.config = config;
		this.setActivePower = setActivePower;
		this.setReactivePower = setReactivePower;
	}
}
