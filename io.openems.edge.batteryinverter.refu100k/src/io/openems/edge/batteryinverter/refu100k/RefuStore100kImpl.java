package io.openems.edge.batteryinverter.refu100k;

import java.util.concurrent.atomic.AtomicReference;

import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.exceptions.OpenemsException;
import io.openems.edge.battery.api.Battery;
import io.openems.edge.batteryinverter.api.BatteryInverterConstraint;
import io.openems.edge.batteryinverter.api.ManagedSymmetricBatteryInverter;
import io.openems.edge.batteryinverter.api.SymmetricBatteryInverter;
import io.openems.edge.batteryinverter.refu100k.enums.SystemState;
import io.openems.edge.batteryinverter.refu100k.statemachine.Context;
import io.openems.edge.batteryinverter.refu100k.statemachine.StateMachine;
import io.openems.edge.batteryinverter.refu100k.statemachine.StateMachine.State;
import io.openems.edge.batteryinverter.refu88k.RefuStore88kChannelId;
import io.openems.edge.bridge.modbus.api.AbstractOpenemsModbusComponent;
import io.openems.edge.bridge.modbus.api.ElementToChannelConverter;
import io.openems.edge.bridge.modbus.api.ModbusProtocol;
import io.openems.edge.bridge.modbus.api.element.BitsWordElement;
import io.openems.edge.bridge.modbus.api.element.DummyRegisterElement;
import io.openems.edge.bridge.modbus.api.element.SignedDoublewordElement;
import io.openems.edge.bridge.modbus.api.element.SignedWordElement;
import io.openems.edge.bridge.modbus.api.element.UnsignedWordElement;
import io.openems.edge.bridge.modbus.api.element.WordOrder;
import io.openems.edge.bridge.modbus.api.task.FC16WriteRegistersTask;
import io.openems.edge.bridge.modbus.api.task.FC3ReadRegistersTask;
import io.openems.edge.bridge.modbus.api.task.FC4ReadInputRegistersTask;
import io.openems.edge.common.channel.IntegerWriteChannel;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.event.EdgeEventConstants;
import io.openems.edge.common.startstop.StartStop;
import io.openems.edge.common.startstop.StartStoppable;
import io.openems.edge.common.taskmanager.Priority;
import io.openems.edge.ess.api.AsymmetricEss;
import io.openems.edge.ess.api.ManagedSymmetricEss;
import io.openems.edge.ess.api.SymmetricEss;
import io.openems.edge.ess.power.api.Phase;
import io.openems.edge.ess.power.api.Pwr;
import io.openems.edge.ess.power.api.Relationship;
import io.openems.edge.timedata.api.Timedata;
import io.openems.edge.timedata.api.TimedataProvider;
import io.openems.edge.timedata.api.utils.CalculateEnergyFromPower;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "io.openems.edge.batteryinverter.refu100k", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE, //
		property = { //
				EventConstants.EVENT_TOPIC + "=" + EdgeEventConstants.TOPIC_CYCLE_BEFORE_PROCESS_IMAGE //
		} //
)
public class RefuStore100kImpl extends AbstractOpenemsModbusComponent
		implements RefuStore100k, ManagedSymmetricBatteryInverter, EventHandler, TimedataProvider, StartStoppable {

	private final Logger log = LoggerFactory.getLogger(RefuStore100kImpl.class);
	private Config config = null;

	public static final int DEFAULT_UNIT_ID = 1;
	private int MAX_APPARENT_POWER = 100000;
	protected static final double EFFICIENCY_FACTOR = 0.98;

	private final CalculateEnergyFromPower calculateChargeEnergy = new CalculateEnergyFromPower(this,
			SymmetricBatteryInverter.ChannelId.ACTIVE_CHARGE_ENERGY);
	private final CalculateEnergyFromPower calculateDischargeEnergy = new CalculateEnergyFromPower(this,
			SymmetricBatteryInverter.ChannelId.ACTIVE_DISCHARGE_ENERGY);

	@Reference
	protected ConfigurationAdmin cm;

	@Reference(policy = ReferencePolicy.DYNAMIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.OPTIONAL)
	private volatile Timedata timedata = null;

	/**
	 * Manages the {@link State}s of the StateMachine.
	 */
	private final StateMachine stateMachine = new StateMachine(State.UNDEFINED);

	public RefuStore100kImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				SymmetricEss.ChannelId.values(), //
				ManagedSymmetricEss.ChannelId.values(), //
				StartStoppable.ChannelId.values(), RefuStore88kChannelId.values() // ,
		);
		this._setMaxApparentPower(MAX_APPARENT_POWER);
	}

	@Activate
	void activate(ComponentContext context, Config config) {
		super.activate(context, config.id(), config.alias(), config.enabled());
		this.config = config;
	}

	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}

	@Override
	public void handleEvent(Event event) {
		if (!this.isEnabled()) {
			return;
		}
		switch (event.getTopic()) {
		case EdgeEventConstants.TOPIC_CYCLE_AFTER_PROCESS_IMAGE:
			// this.errorHandler.run();
			break;
		}
	}

	@Override
	public void run(Battery battery, int setActivePower, int setReactivePower) throws OpenemsNamedException {

		// Store the current State
		this.channel(RefuStore100k.ChannelId.STATE_MACHINE).setNextValue(this.stateMachine.getCurrentState());

		// Initialize 'Start-Stop' Channel
		this._setStartStop(StartStop.UNDEFINED);

		// Set Battery Limits
		this.setBatteryLimits(battery);

		// Calculate the Energy values from ActivePower.
		this.calculateEnergy();

		// Prepare Context
		Context context = new Context(this, battery, this.config, setActivePower, setReactivePower);

		// Call the StateMachine
		try {
			this.stateMachine.run(context);

			this.channel(RefuStore100k.ChannelId.RUN_FAILED).setNextValue(false);

		} catch (OpenemsNamedException e) {
			this.channel(RefuStore100k.ChannelId.RUN_FAILED).setNextValue(true);
			this.logError(this.log, "StateMachine failed: " + e.getMessage());
		}

	}

	private void setBatteryLimits(Battery battery) throws OpenemsNamedException {

		int maxBatteryChargeValue = battery.getChargeMaxCurrent().orElse(0);
		int maxBatteryDischargeValue = battery.getDischargeMaxCurrent().orElse(0);

		IntegerWriteChannel maxBatAChaChannel = this.channel(RefuStore100kChannelId.ALLOWED_CHARGE_CURRENT);
		maxBatAChaChannel.setNextWriteValue(maxBatteryChargeValue);

		IntegerWriteChannel maxBatADischaChannel = this.channel(RefuStore100kChannelId.ALLOWED_DISCHARGE_CURRENT);
		maxBatADischaChannel.setNextWriteValue(maxBatteryDischargeValue);
	}

	/**
	 * Calculate the Energy values from ActivePower.
	 */
	private void calculateEnergy() {
		// Calculate Energy
		Integer activePower = this.getActivePower().get();
		if (activePower == null) {
			// Not available
			this.calculateChargeEnergy.update(null);
			this.calculateDischargeEnergy.update(null);
		} else if (activePower > 0) {
			// Buy-From-Grid
			this.calculateChargeEnergy.update(0);
			this.calculateDischargeEnergy.update(activePower);
		} else {
			// Sell-To-Grid
			this.calculateChargeEnergy.update(activePower * -1);
			this.calculateDischargeEnergy.update(0);
		}
	}

	@Override
	public int getPowerPrecision() {
		return MAX_APPARENT_POWER / 1000;
	}

	@Override
	protected void logInfo(Logger log, String message) {
		super.logInfo(log, message);
	}

	@Override
	protected void logError(Logger log, String message) {
		super.logError(log, message);
	}

	public BatteryInverterConstraint[] getStaticConstraints() throws OpenemsException {
		SystemState systemState = this.channel(RefuStore100kChannelId.SYSTEM_STATE).value().asEnum();
		switch (systemState) {
		case INIT:
		case PRE_OPERATION:
		case STANDBY:
		case UNDEFINED:
			return new BatteryInverterConstraint[] {
					new BatteryInverterConstraint("Refu State: " + systemState, Phase.L1, Pwr.ACTIVE,
							Relationship.EQUALS, 0),

					new BatteryInverterConstraint("Refu State: " + systemState, Phase.L2, Pwr.ACTIVE,
							Relationship.EQUALS, 0),

					new BatteryInverterConstraint("Refu State: " + systemState, Phase.L3, Pwr.ACTIVE,
							Relationship.EQUALS, 0),

					new BatteryInverterConstraint("Refu State: " + systemState, Phase.L1, Pwr.REACTIVE,
							Relationship.EQUALS, 0),

					new BatteryInverterConstraint("Refu State: " + systemState, Phase.L2, Pwr.REACTIVE,
							Relationship.EQUALS, 0),

					new BatteryInverterConstraint("Refu State: " + systemState, Phase.L3, Pwr.REACTIVE,
							Relationship.EQUALS, 0),

			};

		case START:
			break;
		}
		return BatteryInverterConstraint.NO_CONSTRAINTS;
	}

//	@Override
//	public ModbusSlaveTable getModbusSlaveTable(AccessMode accessMode) {
//		return new ModbusSlaveTable( //
//				OpenemsComponent.getModbusSlaveNatureTable(accessMode), //
//				SymmetricEss.getModbusSlaveNatureTable(accessMode), //
//				ManagedSymmetricEss.getModbusSlaveNatureTable(accessMode), //
//				AsymmetricEss.getModbusSlaveNatureTable(accessMode), //
//				ManagedAsymmetricEss.getModbusSlaveNatureTable(accessMode), //
//				ModbusSlaveNatureTable.of(RefuEss.class, accessMode, 300) //
//						.build());
//	}

	@Override
	public String debugLog() {
		return "P:" + this.getActivePower().asString() //
				+ "|Q:" + this.getReactivePower().asString() //
				+ "|" + this.stateMachine.getCurrentState().asCamelCase();
	}

	private final AtomicReference<StartStop> startStopTarget = new AtomicReference<StartStop>(StartStop.UNDEFINED);

	@Override
	public void setStartStop(StartStop value) {
		if (this.startStopTarget.getAndSet(value) != value) {
			// Set only if value changed
			this.stateMachine.forceNextState(State.UNDEFINED);
		}
	}

	@Override
	public StartStop getStartStopTarget() {
		switch (this.config.startStop()) {
		case AUTO:
			// read StartStop-Channel
			return this.startStopTarget.get();

		case START:
			// force START
			return StartStop.START;

		case STOP:
			// force STOP
			return StartStop.STOP;
		}

		assert false;
		return StartStop.UNDEFINED; // can never happen
	}

	@Override
	protected ModbusProtocol defineModbusProtocol() throws OpenemsException {
		return new ModbusProtocol(this, //
				new FC3ReadRegistersTask(0x100, Priority.LOW, //
						m(RefuStore100kChannelId.SYSTEM_STATE, new UnsignedWordElement(0x100)),
						m(new BitsWordElement(0x101, this)//
								.bit(0, RefuStore100kChannelId.SYSTEM_ERROR_STATE_0)
								.bit(1, RefuStore100kChannelId.SYSTEM_ERROR_STATE_1)
								.bit(2, RefuStore100kChannelId.SYSTEM_ERROR_STATE_2)
								.bit(3, RefuStore100kChannelId.SYSTEM_ERROR_STATE_3)
								.bit(4, RefuStore100kChannelId.SYSTEM_ERROR_STATE_4)
								.bit(5, RefuStore100kChannelId.SYSTEM_ERROR_STATE_5)
								.bit(6, RefuStore100kChannelId.SYSTEM_ERROR_STATE_6)
								.bit(7, RefuStore100kChannelId.SYSTEM_ERROR_STATE_7)
								.bit(8, RefuStore100kChannelId.SYSTEM_ERROR_STATE_8)
								.bit(9, RefuStore100kChannelId.SYSTEM_ERROR_STATE_9)
								.bit(10, RefuStore100kChannelId.SYSTEM_ERROR_STATE_10)),

						m(new BitsWordElement(0x102, this).bit(1, RefuStore100kChannelId.COMMUNCATION_INFO_1)
								.bit(2, RefuStore100kChannelId.COMMUNCATION_INFO_2)
								.bit(3, RefuStore100kChannelId.COMMUNCATION_INFO_3)
								.bit(4, RefuStore100kChannelId.COMMUNCATION_INFO_4)
								.bit(5, RefuStore100kChannelId.COMMUNCATION_INFO_5)),

						m(new BitsWordElement(0x103, this).bit(1, RefuStore100kChannelId.STATUS_INVERTER_0)
								.bit(2, RefuStore100kChannelId.STATUS_INVERTER_1)
								.bit(4, RefuStore100kChannelId.STATUS_INVERTER_2)
								.bit(8, RefuStore100kChannelId.STATUS_INVERTER_3)
								.bit(256, RefuStore100kChannelId.STATUS_INVERTER_4)
								.bit(512, RefuStore100kChannelId.STATUS_INVERTER_5)
								.bit(1024, RefuStore100kChannelId.STATUS_INVERTER_6)
								.bit(2048, RefuStore100kChannelId.STATUS_INVERTER_7)
								.bit(4096, RefuStore100kChannelId.STATUS_INVERTER_8)
								.bit(8192, RefuStore100kChannelId.STATUS_INVERTER_9)
								.bit(16384, RefuStore100kChannelId.STATUS_INVERTER_10)),

						m(RefuStore100kChannelId.ERROR_CODE, new UnsignedWordElement(0x104)),

						m(new BitsWordElement(0x105, this).bit(0, RefuStore100kChannelId.STATUS_DCDC_0)
								.bit(1, RefuStore100kChannelId.STATUS_DCDC_1)
								.bit(2, RefuStore100kChannelId.STATUS_DCDC_2)
								.bit(3, RefuStore100kChannelId.STATUS_DCDC_3)
								.bit(4, RefuStore100kChannelId.STATUS_DCDC_4)
								.bit(5, RefuStore100kChannelId.STATUS_DCDC_5)
								.bit(6, RefuStore100kChannelId.STATUS_DCDC_6)),

						m(RefuStore100kChannelId.ERROR_DCDC, new SignedWordElement(0x106)),

						m(RefuStore100kChannelId.BATTERY_CURRENT_PCS, new SignedWordElement(0x107)),

						m(RefuStore100kChannelId.BATTERY_VOLTAGE_PCS, new SignedWordElement(0x108)),

						m(RefuStore100kChannelId.CURRENT, new SignedWordElement(0x109)),

						m(RefuStore100kChannelId.CURRENT_L1, new SignedWordElement(0x10A),
								ElementToChannelConverter.SCALE_FACTOR_2), //
						m(RefuStore100kChannelId.CURRENT_L2, new SignedWordElement(0x10B),
								ElementToChannelConverter.SCALE_FACTOR_2), //
						m(RefuStore100kChannelId.CURRENT_L3, new SignedWordElement(0x10C),
								ElementToChannelConverter.SCALE_FACTOR_2),
						m(SymmetricBatteryInverter.ChannelId.ACTIVE_POWER, new SignedWordElement(0x10D),
								ElementToChannelConverter.SCALE_FACTOR_2), //
						m(AsymmetricEss.ChannelId.ACTIVE_POWER_L1, new SignedWordElement(0x10E),
								ElementToChannelConverter.SCALE_FACTOR_2), //
						m(AsymmetricEss.ChannelId.ACTIVE_POWER_L2, new SignedWordElement(0x10F),
								ElementToChannelConverter.SCALE_FACTOR_2), //
						m(AsymmetricEss.ChannelId.ACTIVE_POWER_L3, new SignedWordElement(0x110),
								ElementToChannelConverter.SCALE_FACTOR_2), //
						m(SymmetricEss.ChannelId.REACTIVE_POWER, new SignedWordElement(0x111),
								ElementToChannelConverter.SCALE_FACTOR_2), //
						m(AsymmetricEss.ChannelId.REACTIVE_POWER_L1, new SignedWordElement(0x112),
								ElementToChannelConverter.SCALE_FACTOR_2), //
						m(AsymmetricEss.ChannelId.REACTIVE_POWER_L2, new SignedWordElement(0x113),
								ElementToChannelConverter.SCALE_FACTOR_2), //
						m(AsymmetricEss.ChannelId.REACTIVE_POWER_L3, new SignedWordElement(0x114),
								ElementToChannelConverter.SCALE_FACTOR_2), //
						m(RefuStore100kChannelId.COS_PHI_3P, new SignedWordElement(0x115)),
						m(RefuStore100kChannelId.COS_PHI_L1, new SignedWordElement(0x116)),
						m(RefuStore100kChannelId.COS_PHI_L2, new SignedWordElement(0x117)),
						m(RefuStore100kChannelId.COS_PHI_L3, new SignedWordElement(0x118))),
				new FC16WriteRegistersTask(0x203, //
						m(RefuStore100kChannelId.SET_ACTIVE_POWER, new SignedWordElement(0x203),
								ElementToChannelConverter.SCALE_FACTOR_2)),

				new FC16WriteRegistersTask(0x204, //
						m(RefuStore100kChannelId.SET_ACTIVE_POWER_L1, new SignedWordElement(0x204),
								ElementToChannelConverter.SCALE_FACTOR_2), //
						m(RefuStore100kChannelId.SET_ACTIVE_POWER_L2, new SignedWordElement(0x205),
								ElementToChannelConverter.SCALE_FACTOR_2),
						m(RefuStore100kChannelId.SET_ACTIVE_POWER_L3, new SignedWordElement(0x206),
								ElementToChannelConverter.SCALE_FACTOR_2)), //

				new FC16WriteRegistersTask(0x207, //
						m(RefuStore100kChannelId.SET_REACTIVE_POWER, new SignedWordElement(0x207),
								ElementToChannelConverter.SCALE_FACTOR_2)),
				new FC16WriteRegistersTask(0x208, //
						m(RefuStore100kChannelId.SET_REACTIVE_POWER_L1, new SignedWordElement(0x208),
								ElementToChannelConverter.SCALE_FACTOR_2),
						m(RefuStore100kChannelId.SET_REACTIVE_POWER_L2, new SignedWordElement(0x209),
								ElementToChannelConverter.SCALE_FACTOR_2),
						m(RefuStore100kChannelId.SET_REACTIVE_POWER_L3, new SignedWordElement(0x20A),
								ElementToChannelConverter.SCALE_FACTOR_2)),

				new FC4ReadInputRegistersTask(0x11A, Priority.LOW, //
						m(RefuStore100kChannelId.PCS_ALLOWED_CHARGE, new SignedWordElement(0x11A),
								ElementToChannelConverter.SCALE_FACTOR_2_AND_INVERT),
						m(RefuStore100kChannelId.PCS_ALLOWED_DISCHARGE, new SignedWordElement(0x11B),
								ElementToChannelConverter.SCALE_FACTOR_2),
						m(RefuStore100kChannelId.BATTERY_STATE, new UnsignedWordElement(0x11C)), //
						m(RefuStore100kChannelId.BATTERY_MODE, new UnsignedWordElement(0x11D)), //
						m(RefuStore100kChannelId.BATTERY_VOLTAGE, new UnsignedWordElement(0x11E)), //
						m(RefuStore100kChannelId.BATTERY_CURRENT, new SignedWordElement(0x11F)), //
						m(RefuStore100kChannelId.BATTERY_POWER, new SignedWordElement(0x120)), //
						m(SymmetricEss.ChannelId.SOC, new UnsignedWordElement(0x121)), //
						m(RefuStore100kChannelId.ALLOWED_CHARGE_CURRENT, new UnsignedWordElement(0x122),
								ElementToChannelConverter.SCALE_FACTOR_2_AND_INVERT),
						m(RefuStore100kChannelId.ALLOWED_DISCHARGE_CURRENT, new UnsignedWordElement(0x123),
								ElementToChannelConverter.SCALE_FACTOR_2), //
						m(ManagedSymmetricEss.ChannelId.ALLOWED_CHARGE_POWER, new UnsignedWordElement(0x124),
								ElementToChannelConverter.SCALE_FACTOR_2_AND_INVERT), //
						m(ManagedSymmetricEss.ChannelId.ALLOWED_DISCHARGE_POWER, new UnsignedWordElement(0x125),
								ElementToChannelConverter.SCALE_FACTOR_2),
						m(RefuStore100kChannelId.BATTERY_CHARGE_ENERGY,
								new SignedDoublewordElement(0x126).wordOrder(WordOrder.LSWMSW)), //
						m(RefuStore100kChannelId.BATTERY_DISCHARGE_ENERGY,
								new SignedDoublewordElement(0x128).wordOrder(WordOrder.LSWMSW)), //
						m(new BitsWordElement(0x12A, this) //
								.bit(0, RefuStore100kChannelId.BATTERY_ON_GRID_STATE_0) //
								.bit(1, RefuStore100kChannelId.BATTERY_ON_GRID_STATE_1) //
								.bit(2, RefuStore100kChannelId.BATTERY_ON_GRID_STATE_2) //
								.bit(3, RefuStore100kChannelId.BATTERY_ON_GRID_STATE_3) //
								.bit(4, RefuStore100kChannelId.BATTERY_ON_GRID_STATE_4) //
								.bit(5, RefuStore100kChannelId.BATTERY_ON_GRID_STATE_5) //
								.bit(6, RefuStore100kChannelId.BATTERY_ON_GRID_STATE_6) //
								.bit(7, RefuStore100kChannelId.BATTERY_ON_GRID_STATE_7) //
								.bit(8, RefuStore100kChannelId.BATTERY_ON_GRID_STATE_8) //
								.bit(9, RefuStore100kChannelId.BATTERY_ON_GRID_STATE_9) //
								.bit(10, RefuStore100kChannelId.BATTERY_ON_GRID_STATE_10) //
								.bit(11, RefuStore100kChannelId.BATTERY_ON_GRID_STATE_11) //
								.bit(12, RefuStore100kChannelId.BATTERY_ON_GRID_STATE_12) //
								.bit(13, RefuStore100kChannelId.BATTERY_ON_GRID_STATE_13) //
								.bit(14, RefuStore100kChannelId.BATTERY_ON_GRID_STATE_14) //
								.bit(15, RefuStore100kChannelId.BATTERY_ON_GRID_STATE_15)),
						m(RefuStore100kChannelId.BATTERY_HIGHEST_VOLTAGE, new UnsignedWordElement(0x12B)), //
						m(RefuStore100kChannelId.BATTERY_LOWEST_VOLTAGE, new UnsignedWordElement(0x12C)), //
						m(RefuStore100kChannelId.BATTERY_HIGHEST_TEMPERATURE, new SignedWordElement(0x12D)), //
						m(RefuStore100kChannelId.BATTERY_LOWEST_TEMPERATURE, new SignedWordElement(0x12E)), //
						m(RefuStore100kChannelId.BATTERY_STOP_REQUEST, new UnsignedWordElement(0x12F)), //
						m(new BitsWordElement(0x130, this) //
								.bit(0, RefuStore100kChannelId.STATE_16) //
								.bit(1, RefuStore100kChannelId.STATE_17) //
								.bit(2, RefuStore100kChannelId.STATE_18) //
								.bit(3, RefuStore100kChannelId.STATE_19) //
								.bit(4, RefuStore100kChannelId.STATE_20) //
								.bit(5, RefuStore100kChannelId.STATE_21) //
								.bit(6, RefuStore100kChannelId.STATE_22) //
								.bit(7, RefuStore100kChannelId.STATE_23) //
								.bit(8, RefuStore100kChannelId.STATE_24) //
								.bit(9, RefuStore100kChannelId.STATE_25) //
								.bit(10, RefuStore100kChannelId.STATE_26) //
								.bit(11, RefuStore100kChannelId.STATE_27) //
								.bit(12, RefuStore100kChannelId.STATE_28) //
								.bit(13, RefuStore100kChannelId.STATE_29) //
								.bit(14, RefuStore100kChannelId.STATE_30)),
						m(new BitsWordElement(0x131, this) //
								.bit(0, RefuStore100kChannelId.STATE_31) //
								.bit(1, RefuStore100kChannelId.STATE_32) //
								.bit(5, RefuStore100kChannelId.STATE_33) //
								.bit(7, RefuStore100kChannelId.STATE_34)),
						m(new BitsWordElement(0x132, this) //
								.bit(0, RefuStore100kChannelId.STATE_35) //
								.bit(1, RefuStore100kChannelId.STATE_36) //
								.bit(2, RefuStore100kChannelId.STATE_37) //
								.bit(3, RefuStore100kChannelId.STATE_38)),
						m(new BitsWordElement(0x133, this) //
								.bit(0, RefuStore100kChannelId.STATE_39) //
								.bit(1, RefuStore100kChannelId.STATE_40) //
								.bit(2, RefuStore100kChannelId.STATE_41) //
								.bit(3, RefuStore100kChannelId.STATE_42)),
						new DummyRegisterElement(0x134), //
						m(new BitsWordElement(0x135, this) //
								.bit(0, RefuStore100kChannelId.STATE_43) //
								.bit(1, RefuStore100kChannelId.STATE_44) //
								.bit(2, RefuStore100kChannelId.STATE_45) //
								.bit(3, RefuStore100kChannelId.STATE_46)),
						m(new BitsWordElement(0x136, this) //
								.bit(0, RefuStore100kChannelId.STATE_47) //
								.bit(1, RefuStore100kChannelId.STATE_48) //
								.bit(2, RefuStore100kChannelId.STATE_49) //
								.bit(3, RefuStore100kChannelId.STATE_50)),
						m(new BitsWordElement(0x137, this) //
								.bit(0, RefuStore100kChannelId.STATE_51) //
								.bit(1, RefuStore100kChannelId.STATE_52) //
								.bit(2, RefuStore100kChannelId.STATE_53) //
								.bit(3, RefuStore100kChannelId.STATE_54) //
								.bit(4, RefuStore100kChannelId.STATE_55) //
								.bit(5, RefuStore100kChannelId.STATE_56) //
								.bit(10, RefuStore100kChannelId.STATE_57) //
								.bit(11, RefuStore100kChannelId.STATE_58) //
								.bit(12, RefuStore100kChannelId.STATE_59) //
								.bit(13, RefuStore100kChannelId.STATE_60)),
						m(new BitsWordElement(0x138, this) //
								.bit(0, RefuStore100kChannelId.STATE_61) //
								.bit(1, RefuStore100kChannelId.STATE_62) //
								.bit(2, RefuStore100kChannelId.STATE_63) //
								.bit(3, RefuStore100kChannelId.STATE_64)),
						m(new BitsWordElement(0x139, this) //
								.bit(0, RefuStore100kChannelId.STATE_65) //
								.bit(1, RefuStore100kChannelId.STATE_66) //
								.bit(2, RefuStore100kChannelId.STATE_67) //
								.bit(3, RefuStore100kChannelId.STATE_68)),
						m(new BitsWordElement(0x13A, this) //
								.bit(0, RefuStore100kChannelId.STATE_69) //
								.bit(1, RefuStore100kChannelId.STATE_70) //
								.bit(2, RefuStore100kChannelId.STATE_71) //
								.bit(3, RefuStore100kChannelId.STATE_72)),
						m(new BitsWordElement(0x13B, this) //
								.bit(0, RefuStore100kChannelId.STATE_73) //
								.bit(1, RefuStore100kChannelId.STATE_74) //
								.bit(2, RefuStore100kChannelId.STATE_75) //
								.bit(3, RefuStore100kChannelId.STATE_76)),
						m(new BitsWordElement(0x13C, this) //
								.bit(0, RefuStore100kChannelId.STATE_77) //
								.bit(1, RefuStore100kChannelId.STATE_78) //
								.bit(2, RefuStore100kChannelId.STATE_79) //
								.bit(3, RefuStore100kChannelId.STATE_80)),
						new DummyRegisterElement(0x13D), //
						new DummyRegisterElement(0x13E), //
						m(new BitsWordElement(0x13F, this) //
								.bit(2, RefuStore100kChannelId.STATE_81) //
								.bit(3, RefuStore100kChannelId.STATE_82) //
								.bit(4, RefuStore100kChannelId.STATE_83) //
								.bit(6, RefuStore100kChannelId.STATE_84) //
								.bit(9, RefuStore100kChannelId.STATE_85) //
								.bit(10, RefuStore100kChannelId.STATE_86) //
								.bit(11, RefuStore100kChannelId.STATE_87) //
								.bit(12, RefuStore100kChannelId.STATE_88) //
								.bit(13, RefuStore100kChannelId.STATE_89) //
								.bit(14, RefuStore100kChannelId.STATE_90) //
								.bit(15, RefuStore100kChannelId.STATE_91)),
						m(new BitsWordElement(0x140, this) //
								.bit(2, RefuStore100kChannelId.STATE_92) //
								.bit(3, RefuStore100kChannelId.STATE_93) //
								.bit(7, RefuStore100kChannelId.STATE_94) //
								.bit(8, RefuStore100kChannelId.STATE_95) //
								.bit(10, RefuStore100kChannelId.STATE_96) //
								.bit(11, RefuStore100kChannelId.STATE_97) //
								.bit(12, RefuStore100kChannelId.STATE_98) //
								.bit(13, RefuStore100kChannelId.STATE_99) //
								.bit(14, RefuStore100kChannelId.STATE_100)),
						new DummyRegisterElement(0x141), //
						new DummyRegisterElement(0x142), //
						new DummyRegisterElement(0x143), //
						new DummyRegisterElement(0x144), //
						m(new BitsWordElement(0x145, this) //
								.bit(0, RefuStore100kChannelId.BATTERY_CONTROL_STATE_0) //
								.bit(1, RefuStore100kChannelId.BATTERY_CONTROL_STATE_1) //
								.bit(2, RefuStore100kChannelId.BATTERY_CONTROL_STATE_2) //
								.bit(3, RefuStore100kChannelId.BATTERY_CONTROL_STATE_3) //
								.bit(4, RefuStore100kChannelId.BATTERY_CONTROL_STATE_4) //
								.bit(5, RefuStore100kChannelId.BATTERY_CONTROL_STATE_5) //
								.bit(6, RefuStore100kChannelId.BATTERY_CONTROL_STATE_6) //
								.bit(7, RefuStore100kChannelId.BATTERY_CONTROL_STATE_7) //
								.bit(8, RefuStore100kChannelId.BATTERY_CONTROL_STATE_8) //
								.bit(9, RefuStore100kChannelId.BATTERY_CONTROL_STATE_9) //
								.bit(10, RefuStore100kChannelId.BATTERY_CONTROL_STATE_10) //
								.bit(11, RefuStore100kChannelId.BATTERY_CONTROL_STATE_11) //
								.bit(12, RefuStore100kChannelId.BATTERY_CONTROL_STATE_12) //
								.bit(13, RefuStore100kChannelId.BATTERY_CONTROL_STATE_13) //
								.bit(14, RefuStore100kChannelId.BATTERY_CONTROL_STATE_14) //
								.bit(15, RefuStore100kChannelId.BATTERY_CONTROL_STATE_15)),
						m(RefuStore100kChannelId.ERROR_LOG_0, new UnsignedWordElement(0x146)), //
						m(RefuStore100kChannelId.ERROR_LOG_1, new UnsignedWordElement(0x147)), //
						m(RefuStore100kChannelId.ERROR_LOG_2, new UnsignedWordElement(0x148)), //
						m(RefuStore100kChannelId.ERROR_LOG_3, new UnsignedWordElement(0x149)), //
						m(RefuStore100kChannelId.ERROR_LOG_4, new UnsignedWordElement(0x14A)), //
						m(RefuStore100kChannelId.ERROR_LOG_5, new UnsignedWordElement(0x14B)), //
						m(RefuStore100kChannelId.ERROR_LOG_6, new UnsignedWordElement(0x14C)), //
						m(RefuStore100kChannelId.ERROR_LOG_7, new UnsignedWordElement(0x14D)), //
						m(RefuStore100kChannelId.ERROR_LOG_8, new UnsignedWordElement(0x14E)), //
						m(RefuStore100kChannelId.ERROR_LOG_9, new UnsignedWordElement(0x14F)), //
						m(RefuStore100kChannelId.ERROR_LOG_10, new UnsignedWordElement(0x150)), //
						m(RefuStore100kChannelId.ERROR_LOG_11, new UnsignedWordElement(0x151)), //
						m(RefuStore100kChannelId.ERROR_LOG_12, new UnsignedWordElement(0x152)), //
						m(RefuStore100kChannelId.ERROR_LOG_13, new UnsignedWordElement(0x153)), //
						m(RefuStore100kChannelId.ERROR_LOG_14, new UnsignedWordElement(0x154)), //
						m(RefuStore100kChannelId.ERROR_LOG_15, new UnsignedWordElement(0x155)) //
				));

	}

	@Override
	public IntegerWriteChannel getSetActivePowerL1Channel() {
		return this.channel(RefuStore100kChannelId.SET_ACTIVE_POWER_L1);
	}

	@Override
	public IntegerWriteChannel getSetActivePowerL2Channel() {
		return this.channel(RefuStore100kChannelId.SET_ACTIVE_POWER_L2);
	}

	@Override
	public IntegerWriteChannel getSetActivePowerL3Channel() {
		return this.channel(RefuStore100kChannelId.SET_ACTIVE_POWER_L3);
	}

	@Override
	public IntegerWriteChannel getSetReactivePowerL1Channel() {
		return this.channel(RefuStore100kChannelId.SET_REACTIVE_POWER_L1);
	}

	@Override
	public IntegerWriteChannel getSetReactivePowerL2Channel() {
		return this.channel(RefuStore100kChannelId.SET_REACTIVE_POWER_L2);
	}

	@Override
	public IntegerWriteChannel getSetReactivePowerL3Channel() {
		return this.channel(RefuStore100kChannelId.SET_REACTIVE_POWER_L3);
	}

	@Override
	public Timedata getTimedata() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public SystemState getSystemState() {
		return this.channel(RefuStore100kChannelId.SYSTEM_STATE).value().asEnum();
	}

}
