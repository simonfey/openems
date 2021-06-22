package io.openems.edge.batteryinverter.refu100k.enums;

import io.openems.common.types.OptionsEnum;

/**
 * This enum holds the System State of the Refu100k inverter *
 */
public enum SystemState implements OptionsEnum {
	UNDEFINED(-1, "Undefined"), //
	STOP(0, "Stop"), //
	INIT(1, "Init"), //
	PRE_OPERATION(2, "Pre-operation"), //
	STANDBY(3, "Stand by"), //
	START(4, "Start"), // operational mode is set as start
	FAULT(5, "Fault") //
	; //
	private final int value;
	private final String name;

	private SystemState(int value, String name) {
		this.value = value;
		this.name = name;
	}

	@Override
	public int getValue() {
		return value;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public OptionsEnum getUndefined() {
		return UNDEFINED;
	}

}
