package io.openems.edge.batteryinverter.refu100k;

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
import io.openems.edge.batteryinverter.api.ManagedSymmetricBatteryInverter;
import io.openems.edge.batteryinverter.api.SymmetricBatteryInverter;
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
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.IntegerWriteChannel;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.event.EdgeEventConstants;
import io.openems.edge.common.startstop.StartStop;
import io.openems.edge.common.taskmanager.Priority;
import io.openems.edge.ess.api.AsymmetricEss;
import io.openems.edge.ess.api.ManagedSymmetricEss;
import io.openems.edge.ess.api.SymmetricEss;
import io.openems.edge.ess.power.api.Power;
import io.openems.edge.timedata.api.Timedata;
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
public class RefuStore100kImpl extends AbstractOpenemsModbusComponent implements RefuStore100k,
		ManagedSymmetricBatteryInverter, SymmetricBatteryInverter, OpenemsComponent, EventHandler {

	private final Logger log = LoggerFactory.getLogger(RefuStore100kImpl.class);
	private Config config = null;

	public static final int DEFAULT_UNIT_ID = 1;
	private int MAX_APPARENT_POWER = 100000;
	protected static final double EFFICIENCY_FACTOR = 0.98;

//	private final CalculateEnergyFromPower calculateChargeEnergy = new CalculateEnergyFromPower(this,
//			SymmetricBatteryInverter.ChannelId.ACTIVE_CHARGE_ENERGY);
//	private final CalculateEnergyFromPower calculateDischargeEnergy = new CalculateEnergyFromPower(this,
//			SymmetricBatteryInverter.ChannelId.ACTIVE_DISCHARGE_ENERGY);

	@Reference
	private Power power;

	@Reference
	protected ConfigurationAdmin cm;

	@Reference(policy = ReferencePolicy.DYNAMIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.OPTIONAL)
	private volatile Timedata timedata = null;

//	/**
//	 * Manages the {@link State}s of the StateMachine.
//	 */
//	private final StateMachine stateMachine = new StateMachine(State.UNDEFINED);

	public RefuStore100kImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				SymmetricEss.ChannelId.values(), //
				ManagedSymmetricEss.ChannelId.values(), //
				RefuStore100k.ChannelId.values() //
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
			//this.errorHandler.run();
			break;
		}
	}

	@Override
	public String debugLog() {
		return "Hello World";
	}

	@Override
	public void setStartStop(StartStop value) throws OpenemsNamedException {
		// TODO Auto-generated method stub

	}

	@Override
	public void run(Battery battery, int setActivePower, int setReactivePower) throws OpenemsNamedException {
		// TODO Auto-generated method stub

	}

	@Override
	public int getPowerPrecision() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public Power getPower() {
		return this.power;
	}

	@Override
	public int getPowerPrecision() {
		return 100;
	}



	@Override
	protected void logInfo(Logger log, String message) {
		super.logInfo(log, message);
	}

	@Override
	protected void logError(Logger log, String message) {
		super.logError(log, message);
	}

	public Constraint[] getStaticConstraints() throws OpenemsException {
		SystemState systemState = this.channel(ChannelId.SYSTEM_STATE).value().asEnum();
		switch (systemState) {
		case ERROR:
		case INIT:
		case OFF:
		case PRE_OPERATION:
		case STANDBY:
		case UNDEFINED:
			return new Constraint[] {
					this.power.createSimpleConstraint("Refu State: " + systemState, this, Phase.L1, Pwr.ACTIVE,
							Relationship.EQUALS, 0),
					this.power.createSimpleConstraint("Refu State: " + systemState, this, Phase.L2, Pwr.ACTIVE,
							Relationship.EQUALS, 0),
					this.power.createSimpleConstraint("Refu State: " + systemState, this, Phase.L3, Pwr.ACTIVE,
							Relationship.EQUALS, 0),
					this.power.createSimpleConstraint("Refu State: " + systemState, this, Phase.L3, Pwr.REACTIVE,
							Relationship.EQUALS, 0),
					this.power.createSimpleConstraint("Refu State: " + systemState, this, Phase.L3, Pwr.REACTIVE,
							Relationship.EQUALS, 0),
					this.power.createSimpleConstraint("Refu State: " + systemState, this, Phase.L3, Pwr.REACTIVE,
							Relationship.EQUALS, 0) };

		case OPERATION:
			break;
		}
		return Power.NO_CONSTRAINTS;
	}

	@Override
	public ModbusSlaveTable getModbusSlaveTable(AccessMode accessMode) {
		return new ModbusSlaveTable( //
				OpenemsComponent.getModbusSlaveNatureTable(accessMode), //
				SymmetricEss.getModbusSlaveNatureTable(accessMode), //
				ManagedSymmetricEss.getModbusSlaveNatureTable(accessMode), //
				AsymmetricEss.getModbusSlaveNatureTable(accessMode), //
				ManagedAsymmetricEss.getModbusSlaveNatureTable(accessMode), //
				ModbusSlaveNatureTable.of(RefuEss.class, accessMode, 300) //
						.build());
	}
	
	@Override
	protected ModbusProtocol defineModbusProtocol() throws OpenemsException {		
		return new ModbusProtocol(this, //
				new FC3ReadRegistersTask(0x100, Priority.LOW, //
						m(RefuStore100k.ChannelId.SYSTEM_STATE, new UnsignedWordElement(0x100)),
						m(new BitsWordElement(0x101, this)//
								.bit(0, RefuStore100k.ChannelId.SYSTEM_ERROR_STATE_0)
								.bit(1, RefuStore100k.ChannelId.SYSTEM_ERROR_STATE_1)
								.bit(2, RefuStore100k.ChannelId.SYSTEM_ERROR_STATE_2)
								.bit(3, RefuStore100k.ChannelId.SYSTEM_ERROR_STATE_3)
								.bit(4, RefuStore100k.ChannelId.SYSTEM_ERROR_STATE_4)
								.bit(5, RefuStore100k.ChannelId.SYSTEM_ERROR_STATE_5)
								.bit(6, RefuStore100k.ChannelId.SYSTEM_ERROR_STATE_6)
								.bit(7, RefuStore100k.ChannelId.SYSTEM_ERROR_STATE_7)
								.bit(8, RefuStore100k.ChannelId.SYSTEM_ERROR_STATE_8)
								.bit(9, RefuStore100k.ChannelId.SYSTEM_ERROR_STATE_9)
								.bit(10, RefuStore100k.ChannelId.SYSTEM_ERROR_STATE_10)),

						m(new BitsWordElement(0x102, this).bit(1, RefuStore100k.ChannelId.COMMUNCATION_INFO_1)
								.bit(2, RefuStore100k.ChannelId.COMMUNCATION_INFO_2)
								.bit(3, RefuStore100k.ChannelId.COMMUNCATION_INFO_3)
								.bit(4, RefuStore100k.ChannelId.COMMUNCATION_INFO_4)
								.bit(5, RefuStore100k.ChannelId.COMMUNCATION_INFO_5)),

						m(new BitsWordElement(0x103, this).bit(1, RefuStore100k.ChannelId.STATUS_INVERTER_0)
								.bit(2, RefuStore100k.ChannelId.STATUS_INVERTER_1)
								.bit(4, RefuStore100k.ChannelId.STATUS_INVERTER_2)
								.bit(8, RefuStore100k.ChannelId.STATUS_INVERTER_3)
								.bit(256, RefuStore100k.ChannelId.STATUS_INVERTER_4)
								.bit(512, RefuStore100k.ChannelId.STATUS_INVERTER_5)
								.bit(1024, RefuStore100k.ChannelId.STATUS_INVERTER_6)
								.bit(2048, RefuStore100k.ChannelId.STATUS_INVERTER_7)
								.bit(4096, RefuStore100k.ChannelId.STATUS_INVERTER_8)
								.bit(8192, RefuStore100k.ChannelId.STATUS_INVERTER_9)
								.bit(16384, RefuStore100k.ChannelId.STATUS_INVERTER_10)),

						m(RefuStore100k.ChannelId.ERROR_CODE, new UnsignedWordElement(0x104)),

						m(new BitsWordElement(0x105, this).bit(0, RefuStore100k.ChannelId.STATUS_DCDC_0)
								.bit(1, RefuStore100k.ChannelId.STATUS_DCDC_1)
								.bit(2, RefuStore100k.ChannelId.STATUS_DCDC_2)
								.bit(3, RefuStore100k.ChannelId.STATUS_DCDC_3)
								.bit(4, RefuStore100k.ChannelId.STATUS_DCDC_4)
								.bit(5, RefuStore100k.ChannelId.STATUS_DCDC_5)
								.bit(6, RefuStore100k.ChannelId.STATUS_DCDC_6)),

						m(RefuStore100k.ChannelId.ERROR_DCDC, new SignedWordElement(0x106)),

						m(RefuStore100k.ChannelId.BATTERY_CURRENT_PCS, new SignedWordElement(0x107)),

						m(RefuStore100k.ChannelId.BATTERY_VOLTAGE_PCS, new SignedWordElement(0x108)),

						m(RefuStore100k.ChannelId.CURRENT, new SignedWordElement(0x109)),

						m(RefuStore100k.ChannelId.CURRENT_L1, new SignedWordElement(0x10A),
								ElementToChannelConverter.SCALE_FACTOR_2), //
						m(RefuStore100k.ChannelId.CURRENT_L2, new SignedWordElement(0x10B),
								ElementToChannelConverter.SCALE_FACTOR_2), //
						m(RefuStore100k.ChannelId.CURRENT_L3, new SignedWordElement(0x10C),
								ElementToChannelConverter.SCALE_FACTOR_2),
						m(SymmetricEss.ChannelId.ACTIVE_POWER, new SignedWordElement(0x10D),
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
						m(RefuStore100k.ChannelId.COS_PHI_3P, new SignedWordElement(0x115)),
						m(RefuStore100k.ChannelId.COS_PHI_L1, new SignedWordElement(0x116)),
						m(RefuStore100k.ChannelId.COS_PHI_L2, new SignedWordElement(0x117)),
						m(RefuStore100k.ChannelId.COS_PHI_L3, new SignedWordElement(0x118))),
				new FC16WriteRegistersTask(0x203, //
						m(RefuStore100k.ChannelId.SET_ACTIVE_POWER, new SignedWordElement(0x203),
								ElementToChannelConverter.SCALE_FACTOR_2)),

				new FC16WriteRegistersTask(0x204, //
						m(RefuStore100k.ChannelId.SET_ACTIVE_POWER_L1, new SignedWordElement(0x204),
								ElementToChannelConverter.SCALE_FACTOR_2), //
						m(RefuStore100k.ChannelId.SET_ACTIVE_POWER_L2, new SignedWordElement(0x205),
								ElementToChannelConverter.SCALE_FACTOR_2),
						m(RefuStore100k.ChannelId.SET_ACTIVE_POWER_L3, new SignedWordElement(0x206),
								ElementToChannelConverter.SCALE_FACTOR_2)), //

				new FC16WriteRegistersTask(0x207, //
						m(RefuStore100k.ChannelId.SET_REACTIVE_POWER, new SignedWordElement(0x207),
								ElementToChannelConverter.SCALE_FACTOR_2)),
				new FC16WriteRegistersTask(0x208, //
						m(RefuStore100k.ChannelId.SET_REACTIVE_POWER_L1, new SignedWordElement(0x208),
								ElementToChannelConverter.SCALE_FACTOR_2),
						m(RefuStore100k.ChannelId.SET_REACTIVE_POWER_L2, new SignedWordElement(0x209),
								ElementToChannelConverter.SCALE_FACTOR_2),
						m(RefuStore100k.ChannelId.SET_REACTIVE_POWER_L3, new SignedWordElement(0x20A),
								ElementToChannelConverter.SCALE_FACTOR_2)),

				new FC4ReadInputRegistersTask(0x11A, Priority.LOW, //
						m(RefuStore100k.ChannelId.PCS_ALLOWED_CHARGE, new SignedWordElement(0x11A),
								ElementToChannelConverter.SCALE_FACTOR_2_AND_INVERT),
						m(RefuStore100k.ChannelId.PCS_ALLOWED_DISCHARGE, new SignedWordElement(0x11B),
								ElementToChannelConverter.SCALE_FACTOR_2),
						m(RefuStore100k.ChannelId.BATTERY_STATE, new UnsignedWordElement(0x11C)), //
						m(RefuStore100k.ChannelId.BATTERY_MODE, new UnsignedWordElement(0x11D)), //
						m(RefuStore100k.ChannelId.BATTERY_VOLTAGE, new UnsignedWordElement(0x11E)), //
						m(RefuStore100k.ChannelId.BATTERY_CURRENT, new SignedWordElement(0x11F)), //
						m(RefuStore100k.ChannelId.BATTERY_POWER, new SignedWordElement(0x120)), //
						m(SymmetricEss.ChannelId.SOC, new UnsignedWordElement(0x121)), //
						m(RefuStore100k.ChannelId.ALLOWED_CHARGE_CURRENT, new UnsignedWordElement(0x122),
								ElementToChannelConverter.SCALE_FACTOR_2_AND_INVERT),
						m(RefuStore100k.ChannelId.ALLOWED_DISCHARGE_CURRENT, new UnsignedWordElement(0x123),
								ElementToChannelConverter.SCALE_FACTOR_2), //
						m(ManagedSymmetricEss.ChannelId.ALLOWED_CHARGE_POWER, new UnsignedWordElement(0x124),
								ElementToChannelConverter.SCALE_FACTOR_2_AND_INVERT), //
						m(ManagedSymmetricEss.ChannelId.ALLOWED_DISCHARGE_POWER, new UnsignedWordElement(0x125),
								ElementToChannelConverter.SCALE_FACTOR_2),
						m(RefuStore100k.ChannelId.BATTERY_CHARGE_ENERGY,
								new SignedDoublewordElement(0x126).wordOrder(WordOrder.LSWMSW)), //
						m(RefuStore100k.ChannelId.BATTERY_DISCHARGE_ENERGY,
								new SignedDoublewordElement(0x128).wordOrder(WordOrder.LSWMSW)), //
						m(new BitsWordElement(0x12A, this) //
								.bit(0, RefuStore100k.ChannelId.BATTERY_ON_GRID_STATE_0) //
								.bit(1, RefuStore100k.ChannelId.BATTERY_ON_GRID_STATE_1) //
								.bit(2, RefuStore100k.ChannelId.BATTERY_ON_GRID_STATE_2) //
								.bit(3, RefuStore100k.ChannelId.BATTERY_ON_GRID_STATE_3) //
								.bit(4, RefuStore100k.ChannelId.BATTERY_ON_GRID_STATE_4) //
								.bit(5, RefuStore100k.ChannelId.BATTERY_ON_GRID_STATE_5) //
								.bit(6, RefuStore100k.ChannelId.BATTERY_ON_GRID_STATE_6) //
								.bit(7, RefuStore100k.ChannelId.BATTERY_ON_GRID_STATE_7) //
								.bit(8, RefuStore100k.ChannelId.BATTERY_ON_GRID_STATE_8) //
								.bit(9, RefuStore100k.ChannelId.BATTERY_ON_GRID_STATE_9) //
								.bit(10, RefuStore100k.ChannelId.BATTERY_ON_GRID_STATE_10) //
								.bit(11, RefuStore100k.ChannelId.BATTERY_ON_GRID_STATE_11) //
								.bit(12, RefuStore100k.ChannelId.BATTERY_ON_GRID_STATE_12) //
								.bit(13, RefuStore100k.ChannelId.BATTERY_ON_GRID_STATE_13) //
								.bit(14, RefuStore100k.ChannelId.BATTERY_ON_GRID_STATE_14) //
								.bit(15, RefuStore100k.ChannelId.BATTERY_ON_GRID_STATE_15)),
						m(RefuStore100k.ChannelId.BATTERY_HIGHEST_VOLTAGE, new UnsignedWordElement(0x12B)), //
						m(RefuStore100k.ChannelId.BATTERY_LOWEST_VOLTAGE, new UnsignedWordElement(0x12C)), //
						m(RefuStore100k.ChannelId.BATTERY_HIGHEST_TEMPERATURE, new SignedWordElement(0x12D)), //
						m(RefuStore100k.ChannelId.BATTERY_LOWEST_TEMPERATURE, new SignedWordElement(0x12E)), //
						m(RefuStore100k.ChannelId.BATTERY_STOP_REQUEST, new UnsignedWordElement(0x12F)), //
						m(new BitsWordElement(0x130, this) //
								.bit(0, RefuStore100k.ChannelId.STATE_16) //
								.bit(1, RefuStore100k.ChannelId.STATE_17) //
								.bit(2, RefuStore100k.ChannelId.STATE_18) //
								.bit(3, RefuStore100k.ChannelId.STATE_19) //
								.bit(4, RefuStore100k.ChannelId.STATE_20) //
								.bit(5, RefuStore100k.ChannelId.STATE_21) //
								.bit(6, RefuStore100k.ChannelId.STATE_22) //
								.bit(7, RefuStore100k.ChannelId.STATE_23) //
								.bit(8, RefuStore100k.ChannelId.STATE_24) //
								.bit(9, RefuStore100k.ChannelId.STATE_25) //
								.bit(10, RefuStore100k.ChannelId.STATE_26) //
								.bit(11, RefuStore100k.ChannelId.STATE_27) //
								.bit(12, RefuStore100k.ChannelId.STATE_28) //
								.bit(13, RefuStore100k.ChannelId.STATE_29) //
								.bit(14, RefuStore100k.ChannelId.STATE_30)),
						m(new BitsWordElement(0x131, this) //
								.bit(0, RefuStore100k.ChannelId.STATE_31) //
								.bit(1, RefuStore100k.ChannelId.STATE_32) //
								.bit(5, RefuStore100k.ChannelId.STATE_33) //
								.bit(7, RefuStore100k.ChannelId.STATE_34)),
						m(new BitsWordElement(0x132, this) //
								.bit(0, RefuStore100k.ChannelId.STATE_35) //
								.bit(1, RefuStore100k.ChannelId.STATE_36) //
								.bit(2, RefuStore100k.ChannelId.STATE_37) //
								.bit(3, RefuStore100k.ChannelId.STATE_38)),
						m(new BitsWordElement(0x133, this) //
								.bit(0, RefuStore100k.ChannelId.STATE_39) //
								.bit(1, RefuStore100k.ChannelId.STATE_40) //
								.bit(2, RefuStore100k.ChannelId.STATE_41) //
								.bit(3, RefuStore100k.ChannelId.STATE_42)),
						new DummyRegisterElement(0x134), //
						m(new BitsWordElement(0x135, this) //
								.bit(0, RefuStore100k.ChannelId.STATE_43) //
								.bit(1, RefuStore100k.ChannelId.STATE_44) //
								.bit(2, RefuStore100k.ChannelId.STATE_45) //
								.bit(3, RefuStore100k.ChannelId.STATE_46)),
						m(new BitsWordElement(0x136, this) //
								.bit(0, RefuStore100k.ChannelId.STATE_47) //
								.bit(1, RefuStore100k.ChannelId.STATE_48) //
								.bit(2, RefuStore100k.ChannelId.STATE_49) //
								.bit(3, RefuStore100k.ChannelId.STATE_50)),
						m(new BitsWordElement(0x137, this) //
								.bit(0, RefuStore100k.ChannelId.STATE_51) //
								.bit(1, RefuStore100k.ChannelId.STATE_52) //
								.bit(2, RefuStore100k.ChannelId.STATE_53) //
								.bit(3, RefuStore100k.ChannelId.STATE_54) //
								.bit(4, RefuStore100k.ChannelId.STATE_55) //
								.bit(5, RefuStore100k.ChannelId.STATE_56) //
								.bit(10, RefuStore100k.ChannelId.STATE_57) //
								.bit(11, RefuStore100k.ChannelId.STATE_58) //
								.bit(12, RefuStore100k.ChannelId.STATE_59) //
								.bit(13, RefuStore100k.ChannelId.STATE_60)),
						m(new BitsWordElement(0x138, this) //
								.bit(0, RefuStore100k.ChannelId.STATE_61) //
								.bit(1, RefuStore100k.ChannelId.STATE_62) //
								.bit(2, RefuStore100k.ChannelId.STATE_63) //
								.bit(3, RefuStore100k.ChannelId.STATE_64)),
						m(new BitsWordElement(0x139, this) //
								.bit(0, RefuStore100k.ChannelId.STATE_65) //
								.bit(1, RefuStore100k.ChannelId.STATE_66) //
								.bit(2, RefuStore100k.ChannelId.STATE_67) //
								.bit(3, RefuStore100k.ChannelId.STATE_68)),
						m(new BitsWordElement(0x13A, this) //
								.bit(0, RefuStore100k.ChannelId.STATE_69) //
								.bit(1, RefuStore100k.ChannelId.STATE_70) //
								.bit(2, RefuStore100k.ChannelId.STATE_71) //
								.bit(3, RefuStore100k.ChannelId.STATE_72)),
						m(new BitsWordElement(0x13B, this) //
								.bit(0, RefuStore100k.ChannelId.STATE_73) //
								.bit(1, RefuStore100k.ChannelId.STATE_74) //
								.bit(2, RefuStore100k.ChannelId.STATE_75) //
								.bit(3, RefuStore100k.ChannelId.STATE_76)),
						m(new BitsWordElement(0x13C, this) //
								.bit(0, RefuStore100k.ChannelId.STATE_77) //
								.bit(1, RefuStore100k.ChannelId.STATE_78) //
								.bit(2, RefuStore100k.ChannelId.STATE_79) //
								.bit(3, RefuStore100k.ChannelId.STATE_80)),
						new DummyRegisterElement(0x13D), //
						new DummyRegisterElement(0x13E), //
						m(new BitsWordElement(0x13F, this) //
								.bit(2, RefuStore100k.ChannelId.STATE_81) //
								.bit(3, RefuStore100k.ChannelId.STATE_82) //
								.bit(4, RefuStore100k.ChannelId.STATE_83) //
								.bit(6, RefuStore100k.ChannelId.STATE_84) //
								.bit(9, RefuStore100k.ChannelId.STATE_85) //
								.bit(10, RefuStore100k.ChannelId.STATE_86) //
								.bit(11, RefuStore100k.ChannelId.STATE_87) //
								.bit(12, RefuStore100k.ChannelId.STATE_88) //
								.bit(13, RefuStore100k.ChannelId.STATE_89) //
								.bit(14, RefuStore100k.ChannelId.STATE_90) //
								.bit(15, RefuStore100k.ChannelId.STATE_91)),
						m(new BitsWordElement(0x140, this) //
								.bit(2, RefuStore100k.ChannelId.STATE_92) //
								.bit(3, RefuStore100k.ChannelId.STATE_93) //
								.bit(7, RefuStore100k.ChannelId.STATE_94) //
								.bit(8, RefuStore100k.ChannelId.STATE_95) //
								.bit(10, RefuStore100k.ChannelId.STATE_96) //
								.bit(11, RefuStore100k.ChannelId.STATE_97) //
								.bit(12, RefuStore100k.ChannelId.STATE_98) //
								.bit(13, RefuStore100k.ChannelId.STATE_99) //
								.bit(14, RefuStore100k.ChannelId.STATE_100)),
						new DummyRegisterElement(0x141), //
						new DummyRegisterElement(0x142), //
						new DummyRegisterElement(0x143), //
						new DummyRegisterElement(0x144), //
						m(new BitsWordElement(0x145, this) //
								.bit(0, RefuStore100k.ChannelId.BATTERY_CONTROL_STATE_0) //
								.bit(1, RefuStore100k.ChannelId.BATTERY_CONTROL_STATE_1) //
								.bit(2, RefuStore100k.ChannelId.BATTERY_CONTROL_STATE_2) //
								.bit(3, RefuStore100k.ChannelId.BATTERY_CONTROL_STATE_3) //
								.bit(4, RefuStore100k.ChannelId.BATTERY_CONTROL_STATE_4) //
								.bit(5, RefuStore100k.ChannelId.BATTERY_CONTROL_STATE_5) //
								.bit(6, RefuStore100k.ChannelId.BATTERY_CONTROL_STATE_6) //
								.bit(7, RefuStore100k.ChannelId.BATTERY_CONTROL_STATE_7) //
								.bit(8, RefuStore100k.ChannelId.BATTERY_CONTROL_STATE_8) //
								.bit(9, RefuStore100k.ChannelId.BATTERY_CONTROL_STATE_9) //
								.bit(10, RefuStore100k.ChannelId.BATTERY_CONTROL_STATE_10) //
								.bit(11, RefuStore100k.ChannelId.BATTERY_CONTROL_STATE_11) //
								.bit(12, RefuStore100k.ChannelId.BATTERY_CONTROL_STATE_12) //
								.bit(13, RefuStore100k.ChannelId.BATTERY_CONTROL_STATE_13) //
								.bit(14, RefuStore100k.ChannelId.BATTERY_CONTROL_STATE_14) //
								.bit(15, RefuStore100k.ChannelId.BATTERY_CONTROL_STATE_15)),
						m(RefuStore100k.ChannelId.ERROR_LOG_0, new UnsignedWordElement(0x146)), //
						m(RefuStore100k.ChannelId.ERROR_LOG_1, new UnsignedWordElement(0x147)), //
						m(RefuStore100k.ChannelId.ERROR_LOG_2, new UnsignedWordElement(0x148)), //
						m(RefuStore100k.ChannelId.ERROR_LOG_3, new UnsignedWordElement(0x149)), //
						m(RefuStore100k.ChannelId.ERROR_LOG_4, new UnsignedWordElement(0x14A)), //
						m(RefuStore100k.ChannelId.ERROR_LOG_5, new UnsignedWordElement(0x14B)), //
						m(RefuStore100k.ChannelId.ERROR_LOG_6, new UnsignedWordElement(0x14C)), //
						m(RefuStore100k.ChannelId.ERROR_LOG_7, new UnsignedWordElement(0x14D)), //
						m(RefuStore100k.ChannelId.ERROR_LOG_8, new UnsignedWordElement(0x14E)), //
						m(RefuStore100k.ChannelId.ERROR_LOG_9, new UnsignedWordElement(0x14F)), //
						m(RefuStore100k.ChannelId.ERROR_LOG_10, new UnsignedWordElement(0x150)), //
						m(RefuStore100k.ChannelId.ERROR_LOG_11, new UnsignedWordElement(0x151)), //
						m(RefuStore100k.ChannelId.ERROR_LOG_12, new UnsignedWordElement(0x152)), //
						m(RefuStore100k.ChannelId.ERROR_LOG_13, new UnsignedWordElement(0x153)), //
						m(RefuStore100k.ChannelId.ERROR_LOG_14, new UnsignedWordElement(0x154)), //
						m(RefuStore100k.ChannelId.ERROR_LOG_15, new UnsignedWordElement(0x155)) //
				));

	}
	
	private IntegerWriteChannel getSetActivePowerL1Channel() {
		return this.channel(RefuStore100k.ChannelId.SET_ACTIVE_POWER_L1);
	}

	private IntegerWriteChannel getSetActivePowerL2Channel() {
		return this.channel(RefuStore100k.ChannelId.SET_ACTIVE_POWER_L2);
	}

	private IntegerWriteChannel getSetActivePowerL3Channel() {
		return this.channel(RefuStore100k.ChannelId.SET_ACTIVE_POWER_L3);
	}

	private IntegerWriteChannel getSetReactivePowerL1Channel() {
		return this.channel(RefuStore100k.ChannelId.SET_REACTIVE_POWER_L1);
	}

	private IntegerWriteChannel getSetReactivePowerL2Channel() {
		return this.channel(RefuStore100k.ChannelId.SET_REACTIVE_POWER_L2);
	}

	private IntegerWriteChannel getSetReactivePowerL3Channel() {
		return this.channel(RefuStore100k.ChannelId.SET_REACTIVE_POWER_L3);
	}


	
}
