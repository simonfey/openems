package io.openems.edge.ess.refu;

import org.osgi.service.metatype.annotations.AttributeDefinition;

import io.openems.edge.common.test.AbstractComponentConfig;

//@AttributeDefinition(name = "Component-ID", description = "Unique ID of this Component")
//String id() default "ess0";
//
//@AttributeDefinition(name = "Alias", description = "Human-readable name of this Component; defaults to Component-ID")
//String alias() default "";
//
//@AttributeDefinition(name = "Is enabled?", description = "Is this Component enabled?")
//boolean enabled() default true;
//
//@AttributeDefinition(name = "Modbus-ID", description = "ID of Modbus bridge.")
//String modbus_id() default "modbus0";

@SuppressWarnings("all")
public class MyConfig extends AbstractComponentConfig implements Config {

	protected static class Builder {
		private String id;
		private String modbusId;

		private Builder() {
		}

		public Builder setId(String id) {
			this.id = id;
			return this;
		}

		public Builder setModbusId(String modbusId) {
			this.modbusId = modbusId;
			return this;
		}

		public MyConfig build() {
			return new MyConfig(this);
		}
	}

	/**
	 * Create a Config builder.
	 * 
	 * @return a {@link Builder}
	 */
	public static Builder create() {
		return new Builder();
	}

	private final Builder builder;

	private MyConfig(Builder builder) {
		super(Config.class, builder.id);
		this.builder = builder;
	}

	@Override
	public String modbus_id() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String Modbus_target() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public EssState essState() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public SetOperationMode operationState() {
		// TODO Auto-generated method stub
		return null;
	}



}