package io.openems.edge.batteryinverter.refu100k;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

import io.openems.edge.common.startstop.StartStopConfig;

@ObjectClassDefinition(//
		name = "Battery-Inverter REFUstore100k", //
		description = "Implements the REFUstore 100K Battery Inverter")
public @interface Config {

	@AttributeDefinition(name = "Component-ID", description = "Unique ID of this Component")
	String id() default "batteryInverter0";

	@AttributeDefinition(name = "Alias", description = "Human-readable name of this Component; defaults to Component-ID")
	String alias() default "";

	@AttributeDefinition(name = "Is enabled?", description = "Is this Component enabled?")
	boolean enabled() default true;	
	
	@AttributeDefinition(name = "Start/stop behaviour?", description = "Should this Component be forced to start or stop?")
	StartStopConfig startStop() default StartStopConfig.AUTO;

	String webconsole_configurationFactory_nameHint() default "Battery-Inverter REFUstore100k [{id}]";

}