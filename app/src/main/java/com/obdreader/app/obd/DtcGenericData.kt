package com.obdreader.app.obd

/**
 * Standard names for the generic trouble codes not written out by hand in [DtcGeneric]:
 * SAE J2012 P0, P2, P34 (cylinder deactivation), U0 and U3 codes. Each line is
 * `code|family|name`; the family picks the explanation, causes and checks.
 *
 * A code is only listed when at least two independent open transcriptions of the SAE list agree
 * on its name, letters, numbers and bank (python-OBD, AndrOBD and the Wal33D DTC database).
 * Codes they disagree on are left out, and fall back to the explanation from the code's structure.
 * Generated; kept in chunks because a single Kotlin string can't be this long.
 */
internal object DtcGenericData {
    val CHUNKS: List<String> by lazy { listOf(C0, C1, C2, C3, C4) }

    private const val C0 = """
P0001|FUEL_PRESSURE|Fuel volume regulator A control circuit/open
P0002|FUEL_PRESSURE|Fuel volume regulator A control circuit performance
P0003|FUEL_PRESSURE|Fuel volume regulator A control circuit low
P0004|FUEL_PRESSURE|Fuel volume regulator A control circuit high
P0005|FUEL_PRESSURE|Fuel shutoff valve A control circuit/open
P0006|FUEL_PRESSURE|Fuel shutoff valve A control circuit low
P0007|FUEL_PRESSURE|Fuel shutoff valve A control circuit high
P0008|GENERAL|Engine position system performance (bank 1)
P0009|GENERAL|Engine position system performance (bank 2)
P000A|POSITION_SENSOR|Camshaft A position slow response (bank 1)
P000B|POSITION_SENSOR|Camshaft B position slow response (bank 1)
P000C|POSITION_SENSOR|Camshaft A position slow response (bank 2)
P000D|POSITION_SENSOR|Camshaft B position slow response (bank 2)
P0026|CAM_TIMING|Intake valve control solenoid circuit range/performance (bank 1)
P0027|CAM_TIMING|Exhaust valve control solenoid circuit range/performance (bank 1)
P0028|CAM_TIMING|Intake valve control solenoid circuit range/performance (bank 2)
P0029|CAM_TIMING|Exhaust valve control solenoid circuit range/performance (bank 2)
P0033|TURBO|Turbocharger/supercharger bypass valve A control circuit
P0034|TURBO|Turbocharger/supercharger bypass valve A control circuit low
P0035|TURBO|Turbocharger/supercharger bypass valve A control circuit high
P0039|TURBO|Turbocharger/supercharger bypass valve A control circuit range/performance
P0040|O2|O2 sensor signals swapped bank 1 sensor 1/ (bank 2 sensor 1)
P0041|O2|O2 sensor signals swapped bank 1 sensor 2/ (bank 2 sensor 2)
P0042|O2_HEATER|HO2S heater control circuit (bank 1 sensor 3)
P0043|O2_HEATER|HO2S heater control circuit low (bank 1 sensor 3)
P0044|O2_HEATER|HO2S heater control circuit high (bank 1 sensor 3)
P0045|TURBO|Turbocharger/supercharger boost control A circuit/open
P0046|TURBO|Turbocharger/supercharger boost control A circuit range/performance
P0047|TURBO|Turbocharger/supercharger boost control A circuit low
P0048|TURBO|Turbocharger/supercharger boost control A circuit high
P0049|TURBO|Turbocharger/supercharger A turbine overspeed
P004A|TURBO|Turbocharger/supercharger boost control B circuit/open
P004B|TURBO|Turbocharger/supercharger boost control B circuit range/performance
P004C|TURBO|Turbocharger/supercharger boost control B circuit low
P004D|TURBO|Turbocharger/supercharger boost control B circuit high
P004E|TURBO|Turbocharger/supercharger boost control A circuit intermittent/erratic
P004F|TURBO|Turbocharger/supercharger boost control B circuit intermittent/erratic
P0053|O2_HEATER|HO2S heater resistance (bank 1 sensor 1)
P0054|O2_HEATER|HO2S heater resistance (bank 1 sensor 2)
P0055|O2_HEATER|HO2S heater resistance (bank 1 sensor 3)
P0059|O2_HEATER|HO2S heater resistance (bank 2 sensor 1)
P0060|O2_HEATER|HO2S heater resistance (bank 2 sensor 2)
P0061|O2_HEATER|HO2S heater resistance (bank 2 sensor 3)
P0062|O2_HEATER|HO2S heater control circuit (bank 2 sensor 3)
P0063|O2_HEATER|HO2S heater control circuit low (bank 2 sensor 3)
P0064|O2_HEATER|HO2S heater control circuit high (bank 2 sensor 3)
P0065|INJECTOR|Air assisted injector control range/performance
P0066|INJECTOR|Air assisted injector control circuit low
P0067|INJECTOR|Air assisted injector control circuit high
P006A|AIR_SENSOR|MAP - mass or volume air flow correlation (bank 1)
P006B|AIR_SENSOR|MAP - exhaust pressure correlation
P006C|TURBO|MAP - turbocharger/supercharger inlet pressure correlation
P006D|TURBO|Barometric pressure - turbocharger/supercharger inlet pressure correlation (bank 1)
P0070|GENERAL|Ambient air temperature sensor circuit A
P0071|GENERAL|Ambient air temperature sensor circuit A range/performance
P0072|GENERAL|Ambient air temperature sensor circuit A low
P0073|GENERAL|Ambient air temperature sensor circuit A high
P0074|GENERAL|Ambient air temperature sensor circuit A intermittent/erratic
P0075|CAM_TIMING|Intake valve control solenoid circuit (bank 1)
P0076|CAM_TIMING|Intake valve control solenoid circuit low (bank 1)
P0077|CAM_TIMING|Intake valve control solenoid circuit high (bank 1)
P0078|CAM_TIMING|Exhaust valve control solenoid circuit (bank 1)
P0079|CAM_TIMING|Exhaust valve control solenoid circuit low (bank 1)
P0080|CAM_TIMING|Exhaust valve control solenoid circuit high (bank 1)
P0081|CAM_TIMING|Intake valve control solenoid circuit (bank 2)
P0082|CAM_TIMING|Intake valve control solenoid circuit low (bank 2)
P0083|CAM_TIMING|Intake valve control solenoid circuit high (bank 2)
P0084|CAM_TIMING|Exhaust valve control solenoid circuit (bank 2)
P0085|CAM_TIMING|Exhaust valve control solenoid circuit low (bank 2)
P0086|CAM_TIMING|Exhaust valve control solenoid circuit high (bank 2)
P0091|FUEL_PRESSURE|Fuel pressure regulator 1 control circuit low
P0092|FUEL_PRESSURE|Fuel pressure regulator 1 control circuit high
P0093|FUEL_PRESSURE|Fuel system leak detected - large leak
P0094|FUEL_PRESSURE|Fuel system leak detected - small leak
P0095|AIR_SENSOR|Intake air temperature sensor 2 circuit (bank 1)
P0096|AIR_SENSOR|Intake air temperature sensor 2 circuit range/performance (bank 1)
P0097|AIR_SENSOR|Intake air temperature sensor 2 circuit low (bank 1)
P0098|AIR_SENSOR|Intake air temperature sensor 2 circuit high (bank 1)
P0099|AIR_SENSOR|Intake air temperature sensor 2 circuit intermittent/erratic (bank 1)
P009A|AIR_SENSOR|Intake air temperature/ambient air temperature correlation
P010A|AIR_SENSOR|Mass or volume air flow sensor B circuit
P010B|AIR_SENSOR|Mass or volume air flow sensor B circuit range/performance
P010C|AIR_SENSOR|Mass or volume air flow sensor B circuit low
P010D|AIR_SENSOR|Mass or volume air flow sensor B circuit high
P010E|AIR_SENSOR|Mass or volume air flow sensor B circuit intermittent/erratic
P010F|AIR_SENSOR|Mass or volume air flow sensor A/B correlation
P011A|COOLANT_SENSOR|Engine coolant temperature sensor 1/2 correlation
P0127|AIR_SENSOR|Intake air temperature too high
P0129|AIR_SENSOR|Barometric pressure too low
P012A|TURBO|Turbocharger/supercharger inlet pressure sensor A circuit
P012B|TURBO|Turbocharger/supercharger inlet pressure sensor A circuit range/performance
P012C|TURBO|Turbocharger/supercharger inlet pressure sensor A circuit low
P012D|TURBO|Turbocharger/supercharger inlet pressure sensor A circuit high
P012E|TURBO|Turbocharger/supercharger inlet pressure sensor A circuit intermittent/erratic
P0148|GENERAL|Fuel delivery error
P0149|GENERAL|Fuel timing error
P0162|O2|O2 sensor circuit (bank 2 sensor 3)
P0163|O2|O2 sensor circuit low voltage (bank 2 sensor 3)
P0164|O2|O2 sensor circuit high voltage (bank 2 sensor 3)
P0165|O2|O2 sensor circuit slow response (bank 2 sensor 3)
P0166|O2|O2 sensor circuit no activity detected (bank 2 sensor 3)
P0167|O2_HEATER|O2 sensor heater circuit (bank 2 sensor 3)
P0168|FUEL_PRESSURE|Fuel temperature too high
P0169|GENERAL|Incorrect fuel composition
P0176|GENERAL|Fuel composition sensor circuit
P0177|GENERAL|Fuel composition sensor circuit range/performance
P0178|GENERAL|Fuel composition sensor circuit low
P0179|GENERAL|Fuel composition sensor circuit high
P0180|FUEL_PRESSURE|Fuel temperature sensor A circuit
P0181|FUEL_PRESSURE|Fuel temperature sensor A circuit range/performance
P0182|FUEL_PRESSURE|Fuel temperature sensor A circuit low
P0183|FUEL_PRESSURE|Fuel temperature sensor A circuit high
P0184|FUEL_PRESSURE|Fuel temperature sensor A circuit intermittent
P0185|FUEL_PRESSURE|Fuel temperature sensor B circuit
P0186|FUEL_PRESSURE|Fuel temperature sensor B circuit range/performance
P0187|FUEL_PRESSURE|Fuel temperature sensor B circuit low
P0188|FUEL_PRESSURE|Fuel temperature sensor B circuit high
P0189|FUEL_PRESSURE|Fuel temperature sensor B circuit intermittent
P018A|FUEL_PRESSURE|Fuel pressure sensor B circuit
P018B|FUEL_PRESSURE|Fuel pressure sensor B circuit range/performance
P018C|FUEL_PRESSURE|Fuel pressure sensor B circuit low
P018D|FUEL_PRESSURE|Fuel pressure sensor B circuit high
P018E|FUEL_PRESSURE|Fuel pressure sensor B circuit intermittent/erratic
P0195|ENGINE_OIL|Engine oil temperature sensor A circuit
P0196|ENGINE_OIL|Engine oil temperature sensor A range/performance
P0197|ENGINE_OIL|Engine oil temperature sensor A circuit low
P0198|ENGINE_OIL|Engine oil temperature sensor A circuit high
P0199|ENGINE_OIL|Engine oil temperature sensor A circuit intermittent/erratic
P020A|INJECTOR|Cylinder 1 injection timing
P020B|INJECTOR|Cylinder 2 injection timing
P020C|INJECTOR|Cylinder 3 injection timing
P020D|INJECTOR|Cylinder 4 injection timing
P020E|INJECTOR|Cylinder 5 injection timing
P020F|INJECTOR|Cylinder 6 injection timing
P0213|INJECTOR|Cold start injector 1
P0214|INJECTOR|Cold start injector 2
P0215|GENERAL|Engine shutoff solenoid
P0216|INJECTOR|Injector/injection timing control circuit
P021A|INJECTOR|Cylinder 7 injection timing
P021B|INJECTOR|Cylinder 8 injection timing
P021C|INJECTOR|Cylinder 9 injection timing
P021D|INJECTOR|Cylinder 10 injection timing
P021E|INJECTOR|Cylinder 11 injection timing
P021F|INJECTOR|Cylinder 12 injection timing
P0224|THROTTLE_SENSOR|Throttle/pedal position sensor/switch B circuit intermittent
P0225|THROTTLE_SENSOR|Throttle/pedal position sensor/switch C circuit
P0226|THROTTLE_SENSOR|Throttle/pedal position sensor/switch C circuit range/performance
P0227|THROTTLE_SENSOR|Throttle/pedal position sensor/switch C circuit low
P0228|THROTTLE_SENSOR|Throttle/pedal position sensor/switch C circuit high
P0229|THROTTLE_SENSOR|Throttle/pedal position sensor/switch C circuit intermittent
P022A|TURBO|Charge air cooler bypass control A circuit /open
P022B|TURBO|Charge air cooler bypass control A circuit low
P022C|TURBO|Charge air cooler bypass control A circuit high
P022D|TURBO|Charge air cooler bypass control B circuit /open
P022E|TURBO|Charge air cooler bypass control B circuit low
P022F|TURBO|Charge air cooler bypass control B circuit high
P0231|FUEL_PRESSURE|Fuel pump secondary circuit low
P0232|FUEL_PRESSURE|Fuel pump secondary circuit high
P0233|FUEL_PRESSURE|Fuel pump secondary circuit intermittent
P0235|TURBO|Turbocharger/supercharger boost sensor A circuit
P0236|TURBO|Turbocharger/supercharger boost sensor A circuit range/performance
P0237|TURBO|Turbocharger/supercharger boost sensor A circuit low
P0238|TURBO|Turbocharger/supercharger boost sensor A circuit high
P0239|TURBO|Turbocharger/supercharger boost sensor B circuit
P023A|COOLING|Charge air cooler coolant pump A control circuit/open
P023B|COOLING|Charge air cooler coolant pump A control circuit low
P023C|COOLING|Charge air cooler coolant pump A control circuit high
P023D|TURBO|Manifold absolute pressure - turbocharger/supercharger boost sensor A correlation
P023E|TURBO|Manifold absolute pressure - turbocharger/supercharger boost sensor B correlation
P0240|TURBO|Turbocharger/supercharger boost sensor B circuit range/performance
P0241|TURBO|Turbocharger/supercharger boost sensor B circuit low
P0242|TURBO|Turbocharger/supercharger boost sensor B circuit high
P0243|TURBO|Turbocharger/supercharger wastegate actuator A
P0244|TURBO|Turbocharger/supercharger wastegate actuator A range/performance
P0245|TURBO|Turbocharger/supercharger wastegate actuator A low
P0246|TURBO|Turbocharger/supercharger wastegate actuator A high
P0247|TURBO|Turbocharger/supercharger wastegate actuator B
P0248|TURBO|Turbocharger/supercharger wastegate actuator B range/performance
P0249|TURBO|Turbocharger/supercharger wastegate actuator B low
P024A|TURBO|Charge air cooler bypass control A range/performance
P024B|TURBO|Charge air cooler bypass control A stuck
P024C|TURBO|Charge air cooler bypass position sensor A circuit
P024D|TURBO|Charge air cooler bypass position sensor A circuit range/performance
P024E|TURBO|Charge air cooler bypass position sensor A circuit low
P024F|TURBO|Charge air cooler bypass position sensor A circuit high
P0250|TURBO|Turbocharger/supercharger wastegate actuator B high
P0251|INJECTOR|Injection pump fuel metering control A (cam/rotor/injector)
P0252|INJECTOR|Injection pump fuel metering control A range/performance (cam/rotor/injector)
P0253|INJECTOR|Injection pump fuel metering control A low (cam/rotor/injector)
P0254|INJECTOR|Injection pump fuel metering control A high (cam/rotor/injector)
P0255|INJECTOR|Injection pump fuel metering control A intermittent (cam/rotor/injector)
P0256|INJECTOR|Injection pump fuel metering control B (cam/rotor/injector)
P0257|INJECTOR|Injection pump fuel metering control B range/performance (cam/rotor/injector)
P0258|INJECTOR|Injection pump fuel metering control B low (cam/rotor/injector)
P0259|INJECTOR|Injection pump fuel metering control B high (cam/rotor/injector)
P025A|FUEL_PRESSURE|Fuel pump module A control circuit/open
P025B|FUEL_PRESSURE|Fuel pump module A control circuit range/performance
P025C|FUEL_PRESSURE|Fuel pump module A control circuit low
P025D|FUEL_PRESSURE|Fuel pump module A control circuit high
P0260|INJECTOR|Injection pump fuel metering control B intermittent (cam/rotor/injector)
P0263|INJECTOR|Cylinder 1 contribution/balance
P0266|INJECTOR|Cylinder 2 contribution/balance
P0269|INJECTOR|Cylinder 3 contribution/balance
P0272|INJECTOR|Cylinder 4 contribution/balance
P0275|INJECTOR|Cylinder 5 contribution/balance
P0278|INJECTOR|Cylinder 6 contribution/balance
P0281|INJECTOR|Cylinder 7 contribution/balance
P0284|INJECTOR|Cylinder 8 contribution/balance
P0285|INJECTOR|Cylinder 9 injector A circuit low
P0286|INJECTOR|Cylinder 9 injector A circuit high
P0287|INJECTOR|Cylinder 9 contribution/balance
P0288|INJECTOR|Cylinder 10 injector A circuit low
P0289|INJECTOR|Cylinder 10 injector A circuit high
P0290|INJECTOR|Cylinder 10 contribution/balance
P0291|INJECTOR|Cylinder 11 injector A circuit low
P0292|INJECTOR|Cylinder 11 injector A circuit high
P0293|INJECTOR|Cylinder 11 contribution/balance
P0294|INJECTOR|Cylinder 12 injector A circuit low
P0295|INJECTOR|Cylinder 12 injector A circuit high
P0296|INJECTOR|Cylinder 12 contribution/balance
P0297|TEMPERATURE_LIMIT|Vehicle overspeed condition
P0298|ENGINE_OIL|Engine oil over temperature
P0315|POSITION_SENSOR|Crankshaft position system variation not learned
P0317|GENERAL|Rough road hardware not present
P0318|GENERAL|Rough road sensor A signal circuit
P0319|GENERAL|Rough road sensor B signal circuit
P0320|POSITION_SENSOR|Ignition/distributor engine speed input circuit
P0321|POSITION_SENSOR|Ignition/distributor engine speed input circuit range/performance
P0322|POSITION_SENSOR|Ignition/distributor engine speed input circuit no signal
P0323|POSITION_SENSOR|Ignition/distributor engine speed input circuit intermittent
P0324|KNOCK|Knock/combustion vibration control system error
P0331|KNOCK|Knock sensor 2 circuit range/performance (bank 2)
P0334|KNOCK|Knock sensor 2 circuit intermittent (bank 2)
P0346|POSITION_SENSOR|Camshaft position sensor A circuit range/performance (bank 2)
P0347|POSITION_SENSOR|Camshaft position sensor A circuit low (bank 2)
P0348|POSITION_SENSOR|Camshaft position sensor A circuit high (bank 2)
P0349|POSITION_SENSOR|Camshaft position sensor A circuit intermittent (bank 2)
P0363|MISFIRE|Misfire detected - fueling disabled
P0367|POSITION_SENSOR|Camshaft position sensor B circuit low (bank 1)
P0368|POSITION_SENSOR|Camshaft position sensor B circuit high (bank 1)
P0369|POSITION_SENSOR|Camshaft position sensor B circuit intermittent (bank 1)
P0370|GENERAL|Timing reference high resolution signal A
P0371|GENERAL|Timing reference high resolution signal A too many pulses
P0372|GENERAL|Timing reference high resolution signal A too few pulses
P0373|GENERAL|Timing reference high resolution signal A intermittent/erratic pulses
P0374|GENERAL|Timing reference high resolution signal A no pulses
P0375|GENERAL|Timing reference high resolution signal B
P0376|GENERAL|Timing reference high resolution signal B too many pulses
P0377|GENERAL|Timing reference high resolution signal B too few pulses
P0378|GENERAL|Timing reference high resolution signal B intermittent/erratic pulses
P0379|GENERAL|Timing reference high resolution signal B no pulses
P0381|GLOW_PLUG|Glow plug/heater indicator control circuit/open
P0382|GLOW_PLUG|Glow plug/heater circuit B
P0385|POSITION_SENSOR|Crankshaft position sensor B circuit
P0386|POSITION_SENSOR|Crankshaft position sensor B circuit range/performance
P0387|POSITION_SENSOR|Crankshaft position sensor B circuit low
P0388|POSITION_SENSOR|Crankshaft position sensor B circuit high
P0389|POSITION_SENSOR|Crankshaft position sensor B circuit intermittent
P0391|POSITION_SENSOR|Camshaft position sensor B circuit range/performance (bank 2)
P0392|POSITION_SENSOR|Camshaft position sensor B circuit low (bank 2)
P0393|POSITION_SENSOR|Camshaft position sensor B circuit high (bank 2)
P0394|POSITION_SENSOR|Camshaft position sensor B circuit intermittent (bank 2)
P0407|EGR|EGR sensor B circuit low
P0408|EGR|EGR sensor B circuit high
P0409|EGR|EGR sensor A circuit
P040A|EGR|EGR temperature sensor A circuit
P040B|EGR|EGR temperature sensor A circuit range/performance
P040C|EGR|EGR temperature sensor A circuit low
P040D|EGR|EGR temperature sensor A circuit high
P040E|EGR|EGR temperature sensor A circuit intermittent/erratic
P040F|EGR|EGR temperature sensor A/B correlation
P0413|SECONDARY_AIR|Secondary air injection system switching valve A circuit open
P0414|SECONDARY_AIR|Secondary air injection system switching valve A circuit shorted
P0415|SECONDARY_AIR|Secondary air injection system switching valve B circuit
P0416|SECONDARY_AIR|Secondary air injection system switching valve B circuit open
P0417|SECONDARY_AIR|Secondary air injection system switching valve B circuit shorted
P0418|SECONDARY_AIR|Secondary air injection system control A circuit
P0419|SECONDARY_AIR|Secondary air injection system control B circuit
P041A|EGR|EGR temperature sensor B circuit
P041B|EGR|EGR temperature sensor B circuit range/performance
P041C|EGR|EGR temperature sensor B circuit low
P041D|EGR|EGR temperature sensor B circuit high
P041E|EGR|EGR temperature sensor B circuit intermittent/erratic
P0422|CATALYST|Main catalyst efficiency below threshold (bank 1)
P0423|CATALYST|Heated catalyst efficiency below threshold (bank 1)
P0424|CATALYST|Heated catalyst temperature below threshold (bank 1)
P0425|CATALYST|Catalyst temperature sensor circuit (bank 1 sensor 1)
P0426|CATALYST|Catalyst temperature sensor circuit range/performance (bank 1 sensor 1)
P0427|CATALYST|Catalyst temperature sensor circuit low (bank 1 sensor 1)
P0428|CATALYST|Catalyst temperature sensor circuit high (bank 1 sensor 1)
P0429|CATALYST|Catalyst heater control circuit/open (bank 1)
P042A|CATALYST|Catalyst temperature sensor circuit (bank 1 sensor 2)
P042B|CATALYST|Catalyst temperature sensor circuit range/performance (bank 1 sensor 2)
P042C|CATALYST|Catalyst temperature sensor circuit low (bank 1 sensor 2)
P042D|CATALYST|Catalyst temperature sensor circuit high (bank 1 sensor 2)
P0432|CATALYST|Main catalyst efficiency below threshold (bank 2)
P0433|CATALYST|Heated catalyst efficiency below threshold (bank 2)
P0434|CATALYST|Heated catalyst temperature below threshold (bank 2)
P0435|CATALYST|Catalyst temperature sensor circuit (bank 2 sensor 1)
P0436|CATALYST|Catalyst temperature sensor circuit range/performance (bank 2 sensor 1)
P0437|CATALYST|Catalyst temperature sensor circuit low (bank 2 sensor 1)
P0438|CATALYST|Catalyst temperature sensor circuit high (bank 2 sensor 1)
P0439|CATALYST|Catalyst heater control circuit/open (bank 2)
P043A|CATALYST|Catalyst temperature sensor circuit (bank 2 sensor 2)
P043B|CATALYST|Catalyst temperature sensor circuit range/performance (bank 2 sensor 2)
P043C|CATALYST|Catalyst temperature sensor circuit low (bank 2 sensor 2)
P043D|CATALYST|Catalyst temperature sensor circuit high (bank 2 sensor 2)
P043E|EVAP|EVAP system leak detection reference orifice low flow
P043F|EVAP|EVAP system leak detection reference orifice high flow
P0444|EVAP|EVAP system purge control valve A circuit open
P0445|EVAP|EVAP system purge control valve A circuit shorted
P0447|EVAP|EVAP system vent control circuit open
P0448|EVAP|EVAP system vent control circuit shorted
P0450|EVAP|EVAP system pressure sensor/switch A circuit
P0454|EVAP|EVAP system pressure sensor/switch A circuit intermittent
P0458|EVAP|EVAP system purge control valve A circuit low
P0459|EVAP|EVAP system purge control valve A circuit high
P0465|EVAP|EVAP purge flow sensor circuit
P0466|EVAP|EVAP purge flow sensor circuit range/performance
P0467|EVAP|EVAP purge flow sensor circuit low
P0468|EVAP|EVAP purge flow sensor circuit high
P0469|EVAP|EVAP purge flow sensor circuit intermittent
P0470|EXHAUST_TEMP|Exhaust pressure sensor A circuit
P0471|EXHAUST_TEMP|Exhaust pressure sensor A circuit range/performance
P0472|EXHAUST_TEMP|Exhaust pressure sensor A circuit low
P0473|EXHAUST_TEMP|Exhaust pressure sensor A circuit high
P0474|EXHAUST_TEMP|Exhaust pressure sensor A circuit intermittent/erratic
P0475|GENERAL|Exhaust pressure control valve A
P0476|GENERAL|Exhaust pressure control valve A range/performance
P0477|GENERAL|Exhaust pressure control valve A low
P0478|GENERAL|Exhaust pressure control valve A high
P0479|GENERAL|Exhaust pressure control valve A intermittent
P047A|EXHAUST_TEMP|Exhaust pressure sensor B circuit
P047B|EXHAUST_TEMP|Exhaust pressure sensor B circuit range/performance
P047C|EXHAUST_TEMP|Exhaust pressure sensor B circuit low
P047D|EXHAUST_TEMP|Exhaust pressure sensor B circuit high
P047E|EXHAUST_TEMP|Exhaust pressure sensor B circuit intermittent/erratic
P0482|FAN|Fan 3 control circuit
P0483|FAN|Fan rationality check
P0484|FAN|Fan circuit current high
P0485|FAN|Fan power/ground circuit
P0486|EGR|EGR sensor B circuit
P0487|EGR|EGR throttle control circuit A /open
P0488|EGR|EGR throttle control circuit A range/performance
P0489|EGR|EGR A control circuit low
P0490|EGR|EGR A control circuit high
P0493|FAN|Fan overspeed
P0494|FAN|Fan speed low
P0495|FAN|Fan speed high
P0498|EVAP|EVAP system vent valve control circuit low
P0499|EVAP|EVAP system vent valve control circuit high
P0504|BRAKE_SWITCH|Brake switch A/B correlation
P0508|IDLE|Idle air control system circuit low
P0509|IDLE|Idle air control system circuit high
P050A|IDLE|Cold start idle control system performance
P050B|IGNITION_COIL|Cold start ignition timing performance
P050C|COOLANT_SENSOR|Cold start engine coolant temperature performance
P050D|IDLE|Cold start rough idle
P0511|IDLE|Idle air control circuit
P0512|STARTER|Starter request circuit
P0513|GENERAL|Incorrect immobilizer key
P0514|CHARGING|Battery temperature sensor circuit range/performance
P0515|CHARGING|Battery temperature sensor circuit
P0516|CHARGING|Battery temperature sensor circuit low
P0517|CHARGING|Battery temperature sensor circuit high
P0518|IDLE|Idle air control circuit intermittent
P0519|IDLE|Idle air control system performance
P0525|CRUISE|Cruise control servo control circuit range/performance
P0526|FAN|Fan speed sensor circuit
P0527|FAN|Fan speed sensor circuit range/performance
P0528|FAN|Fan speed sensor circuit no signal
P0529|FAN|Fan speed sensor circuit intermittent
P0531|AIRCON|A/C refrigerant pressure sensor A circuit range/performance
P0534|AIRCON|A/C refrigerant charge loss
P0535|AIRCON|A/C evaporator temperature sensor circuit
P0536|AIRCON|A/C evaporator temperature sensor circuit range/performance
P0537|AIRCON|A/C evaporator temperature sensor circuit low
P0538|AIRCON|A/C evaporator temperature sensor circuit high
P0539|AIRCON|A/C evaporator temperature sensor circuit intermittent
P053A|GENERAL|PCV heater A control circuit /open
P053B|GENERAL|PCV heater A control circuit low
P053C|GENERAL|PCV heater A control circuit high
P0540|GLOW_PLUG|Intake air heater A circuit
P0541|GLOW_PLUG|Intake air heater A circuit low
P0542|GLOW_PLUG|Intake air heater A circuit high
P0543|GLOW_PLUG|Intake air heater A circuit open
P0544|EXHAUST_TEMP|Exhaust gas temperature sensor circuit (bank 1 sensor 1)
P0545|EXHAUST_TEMP|Exhaust gas temperature sensor circuit low (bank 1 sensor 1)
P0546|EXHAUST_TEMP|Exhaust gas temperature sensor circuit high (bank 1 sensor 1)
P0547|EXHAUST_TEMP|Exhaust gas temperature sensor circuit (bank 2 sensor 1)
P0548|EXHAUST_TEMP|Exhaust gas temperature sensor circuit low (bank 2 sensor 1)
P0549|EXHAUST_TEMP|Exhaust gas temperature sensor circuit high (bank 2 sensor 1)
P0550|POWER_STEERING|Power steering pressure sensor/switch circuit
P0551|POWER_STEERING|Power steering pressure sensor/switch circuit range/performance
P0552|POWER_STEERING|Power steering pressure sensor/switch circuit low
P0553|POWER_STEERING|Power steering pressure sensor/switch circuit high
P0554|POWER_STEERING|Power steering pressure sensor/switch circuit intermittent
P0555|GENERAL|Brake booster pressure sensor circuit
P0556|GENERAL|Brake booster pressure sensor circuit range/performance
P0557|GENERAL|Brake booster pressure sensor circuit low
P0558|GENERAL|Brake booster pressure sensor circuit high
P0559|GENERAL|Brake booster pressure sensor circuit intermittent
P0561|CHARGING|System voltage unstable
P0564|CRUISE|Cruise control multi-function input A circuit
P0565|CRUISE|Cruise control on signal
P0566|CRUISE|Cruise control off signal
P0567|CRUISE|Cruise control resume signal
P0568|CRUISE|Cruise control set signal
P0569|CRUISE|Cruise control coast signal
P056A|CRUISE|Cruise control increase distance signal
P056B|CRUISE|Cruise control decrease distance signal
P0570|CRUISE|Cruise control accelerate signal
P0572|BRAKE_SWITCH|Brake switch A circuit low
P0573|BRAKE_SWITCH|Brake switch A circuit high
P0574|CRUISE|Cruise control system - vehicle speed too high
P0575|CRUISE|Cruise control input circuit
P0576|CRUISE|Cruise control input circuit low
P0577|CRUISE|Cruise control input circuit high
P0578|CRUISE|Cruise control multi-function input A circuit stuck
P0579|CRUISE|Cruise control multi-function input A circuit range/performance
P0580|CRUISE|Cruise control multi-function input A circuit low
P0581|CRUISE|Cruise control multi-function input A circuit high
P0582|CRUISE|Cruise control vacuum control circuit/open
P0583|CRUISE|Cruise control vacuum control circuit low
P0584|CRUISE|Cruise control vacuum control circuit high
P0585|CRUISE|Cruise control multi-function input A/B correlation
P0586|CRUISE|Cruise control vent control circuit/open
P0587|CRUISE|Cruise control vent control circuit low
P0588|CRUISE|Cruise control vent control circuit high
P0589|CRUISE|Cruise control multi-function input B circuit
P0590|CRUISE|Cruise control multi-function input B circuit stuck
P0591|CRUISE|Cruise control multi-function input B circuit range/performance
P0592|CRUISE|Cruise control multi-function input B circuit low
P0593|CRUISE|Cruise control multi-function input B circuit high
P0594|CRUISE|Cruise control servo control circuit/open
P0595|CRUISE|Cruise control servo control circuit low
P0596|CRUISE|Cruise control servo control circuit high
P0597|GENERAL|Thermostat heater control circuit/open
P0598|GENERAL|Thermostat heater control circuit low
P0599|GENERAL|Thermostat heater control circuit high
P0608|CONTROL_MODULE|Control module VSS output A
P0609|CONTROL_MODULE|Control module VSS output B
P060A|CONTROL_MODULE|Internal control module monitoring processor performance
P060B|CONTROL_MODULE|Internal control module A/D processing performance
P060C|CONTROL_MODULE|Internal control module main processor performance
P060D|CONTROL_MODULE|Internal control module accelerator pedal position performance
P060E|CONTROL_MODULE|Internal control module throttle position performance
P060F|CONTROL_MODULE|Internal control module coolant temperature performance
P0610|CONTROL_MODULE|Control module vehicle options error
P0611|INJECTOR|Fuel injector control module performance
P0612|INJECTOR|Fuel injector control module relay control
P0613|CONTROL_MODULE|TCM processor
P0614|GENERAL|ECM/TCM incompatible
P0616|STARTER|Starter relay A circuit low
P0617|STARTER|Starter relay A circuit high
P0618|CONTROL_MODULE|Alternative fuel control module KAM error
P0619|CONTROL_MODULE|Alternative fuel control module RAM/ROM error
P061A|CONTROL_MODULE|Internal control module torque performance
P061B|CONTROL_MODULE|Internal control module torque calculation performance
P061C|CONTROL_MODULE|Internal control module engine RPM performance
P061D|CONTROL_MODULE|Internal control module engine air mass performance
P061E|CONTROL_MODULE|Internal control module brake signal performance
P061F|CONTROL_MODULE|Internal control module throttle actuator controller performance
P0621|CHARGING|Generator lamp/l terminal circuit
P0623|CHARGING|Generator lamp control circuit
P0624|EVAP|Fuel cap lamp control circuit
P0625|CHARGING|Generator field/f terminal circuit low
P0626|CHARGING|Generator field/f terminal circuit high
P0628|FUEL_PRESSURE|Fuel pump A control circuit low
P0629|FUEL_PRESSURE|Fuel pump A control circuit high
P062A|FUEL_PRESSURE|Fuel pump A control circuit range/performance
P062B|CONTROL_MODULE|Internal control module fuel injector control performance
P062C|CONTROL_MODULE|Internal control module vehicle speed performance
P062D|INJECTOR|Fuel injector driver circuit performance (bank 1)
P062E|INJECTOR|Fuel injector driver circuit performance (bank 2)
P062F|CONTROL_MODULE|Internal control module EEPROM error
P0630|CONTROL_MODULE|VIN not programmed or incompatible - ECM/PCM
P0631|GENERAL|VIN not programmed or incompatible - TCM
P0632|CONTROL_MODULE|Odometer not programmed - ECM/PCM
P0633|GENERAL|Immobilizer key not programmed - ECM/PCM
P0634|CONTROL_MODULE|PCM/ECM/TCM internal temperature too high
P0635|POWER_STEERING|Power steering control circuit
P0636|POWER_STEERING|Power steering control circuit low
P0637|POWER_STEERING|Power steering control circuit high
P0638|THROTTLE_ACTUATOR|Throttle actuator control range/performance (bank 1)
P0639|THROTTLE_ACTUATOR|Throttle actuator control range/performance (bank 2)
P063A|CHARGING|Generator voltage sense circuit A
P063B|CHARGING|Generator voltage sense circuit A range/performance
P063C|CHARGING|Generator voltage sense circuit A low
P063D|CHARGING|Generator voltage sense circuit A high
P063E|GENERAL|Auto configuration throttle input not present (bank 1)
P063F|GENERAL|Auto configuration engine coolant temperature input not present
P0640|GLOW_PLUG|Intake air heater control circuit
P0642|SENSOR_SUPPLY|Sensor reference voltage A circuit low
P0643|SENSOR_SUPPLY|Sensor reference voltage A circuit high
P0644|GENERAL|Driver display serial communication circuit
P0646|AIRCON|A/C clutch relay control circuit low
P0647|AIRCON|A/C clutch relay control circuit high
P0648|GENERAL|Immobilizer lamp control circuit
P0649|GENERAL|Speed control lamp control circuit
P0652|SENSOR_SUPPLY|Sensor reference voltage B circuit low
P0653|SENSOR_SUPPLY|Sensor reference voltage B circuit high
P0654|GENERAL|Engine RPM output circuit/open
P0655|GENERAL|Engine hot lamp output control circuit
P0656|FUEL_LEVEL|Fuel level output circuit
P0657|SENSOR_SUPPLY|Actuator supply voltage A circuit/open
P0658|SENSOR_SUPPLY|Actuator supply voltage A circuit low
P0659|SENSOR_SUPPLY|Actuator supply voltage A circuit high
P065A|CHARGING|Generator system performance
P065B|CHARGING|Generator A control circuit range/performance
P0660|INTAKE_RUNNER|Intake manifold tuning valve control circuit/open (bank 1)
P0661|INTAKE_RUNNER|Intake manifold tuning valve control circuit low (bank 1)
P0662|INTAKE_RUNNER|Intake manifold tuning valve control circuit high (bank 1)
P0663|INTAKE_RUNNER|Intake manifold tuning valve control circuit/open (bank 2)
P0664|INTAKE_RUNNER|Intake manifold tuning valve control circuit low (bank 2)
P0665|INTAKE_RUNNER|Intake manifold tuning valve control circuit high (bank 2)
P0666|CONTROL_MODULE|PCM/ECM/TCM internal temperature sensor circuit
P0667|CONTROL_MODULE|PCM/ECM/TCM internal temperature sensor range/performance
P0668|CONTROL_MODULE|PCM/ECM/TCM internal temperature sensor circuit low
P0669|CONTROL_MODULE|PCM/ECM/TCM internal temperature sensor circuit high
P066A|GLOW_PLUG|Cylinder 1 glow plug control circuit low
P066B|GLOW_PLUG|Cylinder 1 glow plug control circuit high
P066C|GLOW_PLUG|Cylinder 2 glow plug control circuit low
P066D|GLOW_PLUG|Cylinder 2 glow plug control circuit high
P066E|GLOW_PLUG|Cylinder 3 glow plug control circuit low
P066F|GLOW_PLUG|Cylinder 3 glow plug control circuit high
P0670|GLOW_PLUG|Glow plug control module control circuit/open
P0671|GLOW_PLUG|Cylinder 1 glow plug circuit/open
P0672|GLOW_PLUG|Cylinder 2 glow plug circuit/open
P0673|GLOW_PLUG|Cylinder 3 glow plug circuit/open
P0674|GLOW_PLUG|Cylinder 4 glow plug circuit/open
P0675|GLOW_PLUG|Cylinder 5 glow plug circuit/open
P0676|GLOW_PLUG|Cylinder 6 glow plug circuit/open
P0677|GLOW_PLUG|Cylinder 7 glow plug circuit/open
P0678|GLOW_PLUG|Cylinder 8 glow plug circuit/open
P0679|GLOW_PLUG|Cylinder 9 glow plug circuit/open
P067A|GLOW_PLUG|Cylinder 4 glow plug control circuit low
P067B|GLOW_PLUG|Cylinder 4 glow plug control circuit high
P067C|GLOW_PLUG|Cylinder 5 glow plug control circuit low
P067D|GLOW_PLUG|Cylinder 5 glow plug control circuit high
P067E|GLOW_PLUG|Cylinder 6 glow plug control circuit low
P067F|GLOW_PLUG|Cylinder 6 glow plug control circuit high
P0680|GLOW_PLUG|Cylinder 10 glow plug circuit/open
P0681|GLOW_PLUG|Cylinder 11 glow plug circuit/open
P0682|GLOW_PLUG|Cylinder 12 glow plug circuit/open
P0683|GLOW_PLUG|Glow plug control module to PCM communication circuit
P0684|GLOW_PLUG|Glow plug control module to PCM communication circuit range/performance
P0685|CONTROL_MODULE|ECM/PCM power relay control circuit/open
P0686|CONTROL_MODULE|ECM/PCM power relay control circuit low
P0687|CONTROL_MODULE|ECM/PCM power relay control circuit high
P0688|CONTROL_MODULE|ECM/PCM power relay sense circuit/open
P0689|CONTROL_MODULE|ECM/PCM power relay sense circuit low
P068A|CONTROL_MODULE|ECM/PCM power relay de-energized performance - too early
P068B|CONTROL_MODULE|ECM/PCM power relay de-energized performance - too late
P068C|GLOW_PLUG|Cylinder 7 glow plug control circuit low
P068D|GLOW_PLUG|Cylinder 7 glow plug control circuit high
P068E|GLOW_PLUG|Cylinder 8 glow plug control circuit low
P068F|GLOW_PLUG|Cylinder 8 glow plug control circuit high
P0690|CONTROL_MODULE|ECM/PCM power relay sense circuit high
P0691|FAN|Fan 1 control circuit low
P0692|FAN|Fan 1 control circuit high
P0693|FAN|Fan 2 control circuit low
P0694|FAN|Fan 2 control circuit high
P0695|FAN|Fan 3 control circuit low
P0696|FAN|Fan 3 control circuit high
P0697|SENSOR_SUPPLY|Sensor reference voltage C circuit/open
P0698|SENSOR_SUPPLY|Sensor reference voltage C circuit low
P0699|SENSOR_SUPPLY|Sensor reference voltage C circuit high
P069A|GLOW_PLUG|Cylinder 9 glow plug control circuit low
P069B|GLOW_PLUG|Cylinder 9 glow plug control circuit high
P069C|GLOW_PLUG|Cylinder 10 glow plug control circuit low
P069D|GLOW_PLUG|Cylinder 10 glow plug control circuit high
P0701|TRANS_SENSOR|Transmission control system range/performance
P0702|TRANS_GENERAL|Transmission control system electrical
P0703|BRAKE_SWITCH|Brake switch B circuit
P0704|TRANS_GENERAL|Clutch switch input circuit
P0709|TRANS_SENSOR|Transmission range sensor A circuit intermittent
P070A|TRANS_SENSOR|Transmission fluid level sensor circuit
P070B|TRANS_SENSOR|Transmission fluid level sensor circuit range/performance
P070C|TRANS_SENSOR|Transmission fluid level sensor circuit low
P070D|TRANS_SENSOR|Transmission fluid level sensor circuit high
P070E|TRANS_SENSOR|Transmission fluid level sensor circuit intermittent/erratic
P070F|TRANS_SENSOR|Transmission fluid level too low
P0714|TRANS_SENSOR|Transmission fluid temperature sensor A circuit intermittent
P0718|TRANS_SENSOR|Input/turbine shaft speed sensor A circuit intermittent
P0719|BRAKE_SWITCH|Brake switch B circuit low
P071A|TRANS_GENERAL|Transmission mode switch A circuit
P071B|TRANS_GENERAL|Transmission mode switch A circuit low
P071C|TRANS_GENERAL|Transmission mode switch A circuit high
P071D|TRANS_GENERAL|Transmission mode switch B circuit
P071E|TRANS_GENERAL|Transmission mode switch B circuit low
P071F|TRANS_GENERAL|Transmission mode switch B circuit high
P0723|TRANS_SENSOR|Output shaft speed sensor circuit intermittent
P0724|BRAKE_SWITCH|Brake switch B circuit high
P0726|POSITION_SENSOR|Engine speed input circuit range/performance
P0727|POSITION_SENSOR|Engine speed input circuit no signal
P0728|POSITION_SENSOR|Engine speed input circuit intermittent
P0729|TRANS_RATIO|Gear 6 incorrect ratio
P0737|TRANS_SENSOR|TCM engine speed output circuit
P0738|TRANS_SENSOR|TCM engine speed output circuit low
P0739|TRANS_SENSOR|TCM engine speed output circuit high
P0744|TRANS_SOLENOID|Torque converter clutch circuit intermittent
P0745|TRANS_SOLENOID|Pressure control solenoid A
P0746|TRANS_SOLENOID|Pressure control solenoid A performance/stuck off
P0747|TRANS_SOLENOID|Pressure control solenoid A stuck on
P0748|TRANS_SOLENOID|Pressure control solenoid A electrical
P0749|TRANS_SOLENOID|Pressure control solenoid A intermittent
P0754|TRANS_SOLENOID|Shift solenoid A intermittent
P0759|TRANS_SOLENOID|Shift solenoid B intermittent
P075A|TRANS_SOLENOID|Shift solenoid G
P075B|TRANS_SOLENOID|Shift solenoid G performance/stuck off
P075C|TRANS_SOLENOID|Shift solenoid G stuck on
P075D|TRANS_SOLENOID|Shift solenoid G electrical
P075E|TRANS_SOLENOID|Shift solenoid G intermittent
P0764|TRANS_SOLENOID|Shift solenoid C intermittent
P0769|TRANS_SOLENOID|Shift solenoid D intermittent
P076A|TRANS_SOLENOID|Shift solenoid H
P076B|TRANS_SOLENOID|Shift solenoid H performance/stuck off
P076C|TRANS_SOLENOID|Shift solenoid H stuck on
P076D|TRANS_SOLENOID|Shift solenoid H electrical
P076E|TRANS_SOLENOID|Shift solenoid H intermittent
P076F|TRANS_RATIO|Gear 7 incorrect ratio
P0774|TRANS_SOLENOID|Shift solenoid E intermittent
P0775|TRANS_SOLENOID|Pressure control solenoid B
P0776|TRANS_SOLENOID|Pressure control solenoid B performance/stuck off
P0777|TRANS_SOLENOID|Pressure control solenoid B stuck on
P0778|TRANS_SOLENOID|Pressure control solenoid B electrical
P0779|TRANS_SOLENOID|Pressure control solenoid B intermittent
P0781|TRANS_GENERAL|1-2 shift
P0782|TRANS_GENERAL|2-3 shift
P0783|TRANS_GENERAL|3-4 shift
P0784|TRANS_GENERAL|4-5 shift
P0785|TRANS_SOLENOID|Shift timing solenoid A
P0786|TRANS_SOLENOID|Shift timing solenoid A range/performance
P0787|TRANS_SOLENOID|Shift timing solenoid A low
P0788|TRANS_SOLENOID|Shift timing solenoid A high
P0789|TRANS_SOLENOID|Shift timing solenoid A intermittent
P0790|TRANS_GENERAL|Normal/performance switch circuit
P0791|TRANS_SENSOR|Intermediate shaft speed sensor A circuit
P0792|TRANS_SENSOR|Intermediate shaft speed sensor A circuit range/performance
P0793|TRANS_SENSOR|Intermediate shaft speed sensor A circuit no signal
P0794|TRANS_SENSOR|Intermediate shaft speed sensor A circuit intermittent
P0795|TRANS_SOLENOID|Pressure control solenoid C
P0796|TRANS_SOLENOID|Pressure control solenoid C performance/stuck off
P0797|TRANS_SOLENOID|Pressure control solenoid C stuck on
P0798|TRANS_SOLENOID|Pressure control solenoid C electrical
P0799|TRANS_SOLENOID|Pressure control solenoid C intermittent
P0800|GENERAL|Transfer case control system (MIL request)
P0801|TRANS_GENERAL|Reverse inhibit control circuit/open
P0802|GENERAL|Transmission control system MIL request circuit/open
P0803|TRANS_SOLENOID|Upshift/skip shift solenoid control circuit
P0804|TRANS_GENERAL|Upshift/skip shift lamp control circuit
P0805|TRANS_GENERAL|Clutch position sensor A circuit
P0806|TRANS_GENERAL|Clutch position sensor A circuit range/performance
P0807|TRANS_GENERAL|Clutch position sensor A circuit low
P0808|TRANS_GENERAL|Clutch position sensor A circuit high
P0809|TRANS_GENERAL|Clutch position sensor A circuit intermittent
P080A|TRANS_GENERAL|Clutch position not learned
P080B|TRANS_SOLENOID|Upshift/skip shift solenoid control circuit range/performance
P080C|TRANS_SOLENOID|Upshift/skip shift solenoid control circuit low
P080D|TRANS_SOLENOID|Upshift/skip shift solenoid control circuit high
P0810|TRANS_GENERAL|Clutch position control error
P0811|TRANS_RATIO|Excessive clutch A slippage
"""

    private const val C1 = """
P0812|TRANS_GENERAL|Reverse input circuit
P0813|TRANS_GENERAL|Reverse output circuit
P0814|TRANS_SENSOR|Transmission range display circuit
P0815|TRANS_GENERAL|Upshift switch circuit
P0816|TRANS_GENERAL|Downshift switch circuit
P0817|STARTER|Starter disable circuit/open
P0818|TRANS_GENERAL|Driveline disconnect switch input circuit
P0819|TRANS_GENERAL|Up and down shift switch to transmission range correlation
P081A|STARTER|Starter disable circuit low
P081B|STARTER|Starter disable circuit high
P081C|TRANS_GENERAL|Park input circuit
P081D|TRANS_GENERAL|Neutral input circuit
P081E|TRANS_RATIO|Excessive clutch B slippage
P0820|TRANS_GENERAL|Gear lever x-y position sensor circuit
P0821|TRANS_GENERAL|Gear lever X position sensor 1 circuit
P0822|TRANS_GENERAL|Gear lever Y position sensor 1 circuit
P0823|TRANS_GENERAL|Gear lever X position sensor 1 circuit intermittent/erratic
P0824|TRANS_GENERAL|Gear lever Y position sensor 1 circuit intermittent/erratic
P0825|TRANS_GENERAL|Gear lever push-pull switch (shift anticipate)
P0826|TRANS_GENERAL|Up and down shift switch circuit
P0827|TRANS_GENERAL|Up and down shift switch circuit low
P0828|TRANS_GENERAL|Up and down shift switch circuit high
P0829|TRANS_GENERAL|5-6 shift
P0830|TRANS_GENERAL|Clutch pedal switch A circuit
P0831|TRANS_GENERAL|Clutch pedal switch A circuit low
P0832|TRANS_GENERAL|Clutch pedal switch A circuit high
P0833|TRANS_GENERAL|Clutch pedal switch B circuit
P0834|TRANS_GENERAL|Clutch pedal switch B circuit low
P0835|TRANS_GENERAL|Clutch pedal switch B circuit high
P0836|TRANS_GENERAL|Four wheel drive (4WD) switch circuit
P0837|TRANS_GENERAL|Four wheel drive (4WD) switch circuit range/performance
P0838|TRANS_GENERAL|Four wheel drive (4WD) switch circuit low
P0839|TRANS_GENERAL|Four wheel drive (4WD) switch circuit high
P083A|TRANS_SENSOR|Transmission fluid pressure sensor/switch G circuit
P083B|TRANS_SENSOR|Transmission fluid pressure sensor/switch G circuit range/performance
P083C|TRANS_SENSOR|Transmission fluid pressure sensor/switch G circuit low
P083D|TRANS_SENSOR|Transmission fluid pressure sensor/switch G circuit high
P083E|TRANS_SENSOR|Transmission fluid pressure sensor/switch G circuit intermittent
P083F|TRANS_GENERAL|Clutch pedal switch A/B correlation
P0840|TRANS_SENSOR|Transmission fluid pressure sensor/switch A circuit
P0841|TRANS_SENSOR|Transmission fluid pressure sensor/switch A circuit range/performance
P0842|TRANS_SENSOR|Transmission fluid pressure sensor/switch A circuit low
P0843|TRANS_SENSOR|Transmission fluid pressure sensor/switch A circuit high
P0844|TRANS_SENSOR|Transmission fluid pressure sensor/switch A circuit intermittent
P0845|TRANS_SENSOR|Transmission fluid pressure sensor/switch B circuit
P0846|TRANS_SENSOR|Transmission fluid pressure sensor/switch B circuit range/performance
P0847|TRANS_SENSOR|Transmission fluid pressure sensor/switch B circuit low
P0848|TRANS_SENSOR|Transmission fluid pressure sensor/switch B circuit high
P0849|TRANS_SENSOR|Transmission fluid pressure sensor/switch B circuit intermittent
P084A|TRANS_SENSOR|Transmission fluid pressure sensor/switch H circuit
P084B|TRANS_SENSOR|Transmission fluid pressure sensor/switch H circuit range/performance
P084C|TRANS_SENSOR|Transmission fluid pressure sensor/switch H circuit low
P084D|TRANS_SENSOR|Transmission fluid pressure sensor/switch H circuit high
P084E|TRANS_SENSOR|Transmission fluid pressure sensor/switch H circuit intermittent
P0850|TRANS_GENERAL|Park/neutral switch input circuit
P0851|TRANS_GENERAL|Park/neutral switch input circuit low
P0852|TRANS_GENERAL|Park/neutral switch input circuit high
P0853|TRANS_GENERAL|Drive switch input circuit
P0854|TRANS_GENERAL|Drive switch input circuit low
P0855|TRANS_GENERAL|Drive switch input circuit high
P0856|GENERAL|Traction control input signal
P0857|GENERAL|Traction control input signal range/performance
P0858|GENERAL|Traction control input signal low
P0859|GENERAL|Traction control input signal high
P085A|TRANS_GENERAL|Gear shift control module B communication circuit
P085B|TRANS_GENERAL|Gear shift control module B communication circuit low
P085C|TRANS_GENERAL|Gear shift control module B communication circuit high
P0860|TRANS_GENERAL|Gear shift control module A communication circuit
P0861|TRANS_GENERAL|Gear shift control module A communication circuit low
P0862|TRANS_GENERAL|Gear shift control module A communication circuit high
P0863|TRANS_GENERAL|TCM communication circuit
P0864|TRANS_SENSOR|TCM communication circuit range/performance
P0865|TRANS_GENERAL|TCM communication circuit low
P0866|TRANS_GENERAL|TCM communication circuit high
P0867|TRANS_SENSOR|Transmission fluid pressure
P0868|TRANS_SENSOR|Transmission fluid pressure low
P0869|TRANS_SENSOR|Transmission fluid pressure high
P0870|TRANS_SENSOR|Transmission fluid pressure sensor/switch C circuit
P0871|TRANS_SENSOR|Transmission fluid pressure sensor/switch C circuit range/performance
P0872|TRANS_SENSOR|Transmission fluid pressure sensor/switch C circuit low
P0873|TRANS_SENSOR|Transmission fluid pressure sensor/switch C circuit high
P0874|TRANS_SENSOR|Transmission fluid pressure sensor/switch C circuit intermittent
P0875|TRANS_SENSOR|Transmission fluid pressure sensor/switch D circuit
P0876|TRANS_SENSOR|Transmission fluid pressure sensor/switch D circuit range/performance
P0877|TRANS_SENSOR|Transmission fluid pressure sensor/switch D circuit low
P0878|TRANS_SENSOR|Transmission fluid pressure sensor/switch D circuit high
P0879|TRANS_SENSOR|Transmission fluid pressure sensor/switch D circuit intermittent
P0880|TRANS_GENERAL|TCM power input signal
P0881|TRANS_GENERAL|TCM power input signal range/performance
P0882|TRANS_GENERAL|TCM power input signal low
P0883|TRANS_GENERAL|TCM power input signal high
P0884|TRANS_GENERAL|TCM power input signal intermittent
P0885|TRANS_GENERAL|TCM power relay control circuit/open
P0886|TRANS_GENERAL|TCM power relay control circuit low
P0887|TRANS_GENERAL|TCM power relay control circuit high
P0888|TRANS_GENERAL|TCM power relay sense circuit
P0889|TRANS_GENERAL|TCM power relay sense circuit range/performance
P0890|TRANS_GENERAL|TCM power relay sense circuit low
P0891|TRANS_GENERAL|TCM power relay sense circuit high
P0892|TRANS_GENERAL|TCM power relay sense circuit intermittent
P0893|TRANS_GENERAL|Multiple gears engaged
P0894|TRANS_RATIO|Transmission component slipping
P0895|TRANS_GENERAL|Shift time too short
P0896|TRANS_GENERAL|Shift time too long
P0897|TRANS_SENSOR|Transmission fluid deteriorated
P0898|GENERAL|Transmission control system MIL request circuit low
P0899|GENERAL|Transmission control system MIL request circuit high
P0900|TRANS_GENERAL|Clutch A actuator control circuit/open
P0901|TRANS_GENERAL|Clutch A actuator control circuit range/performance
P0902|TRANS_GENERAL|Clutch A actuator control circuit low
P0903|TRANS_GENERAL|Clutch A actuator control circuit high
P0904|TRANS_GENERAL|Gate select position circuit A
P0905|TRANS_GENERAL|Gate select position circuit A range/performance
P0906|TRANS_GENERAL|Gate select position circuit A low
P0907|TRANS_GENERAL|Gate select position circuit A high
P0908|TRANS_GENERAL|Gate select position circuit A intermittent
P0909|TRANS_GENERAL|Gate select control error
P0910|TRANS_GENERAL|Gate select actuator circuit/open
P0911|TRANS_GENERAL|Gate select actuator circuit range/performance
P0912|TRANS_GENERAL|Gate select actuator circuit low
P0913|TRANS_GENERAL|Gate select actuator circuit high
P0914|TRANS_GENERAL|Gear shift position circuit A
P0915|TRANS_GENERAL|Gear shift position circuit A range/performance
P0916|TRANS_GENERAL|Gear shift position circuit A low
P0917|TRANS_GENERAL|Gear shift position circuit A high
P0918|TRANS_GENERAL|Gear shift position circuit A intermittent
P0919|TRANS_GENERAL|Gear shift position control error
P0920|TRANS_GENERAL|Gear shift forward actuator circuit/open
P0921|TRANS_GENERAL|Gear shift forward actuator circuit range/performance
P0922|TRANS_GENERAL|Gear shift forward actuator circuit low
P0923|TRANS_GENERAL|Gear shift forward actuator circuit high
P0924|TRANS_GENERAL|Gear shift reverse actuator circuit/open
P0925|TRANS_GENERAL|Gear shift reverse actuator circuit range/performance
P0926|TRANS_GENERAL|Gear shift reverse actuator circuit low
P0927|TRANS_GENERAL|Gear shift reverse actuator circuit high
P0928|TRANS_SOLENOID|Gear shift lock solenoid/actuator control circuit a/open
P0929|TRANS_SOLENOID|Gear shift lock solenoid/actuator control circuit A range/performance
P0930|TRANS_SOLENOID|Gear shift lock solenoid/actuator control circuit A low
P0931|TRANS_SOLENOID|Gear shift lock solenoid/actuator control circuit A high
P0932|TRANS_GENERAL|Hydraulic pressure sensor circuit
P0933|TRANS_GENERAL|Hydraulic pressure sensor range/performance
P0934|TRANS_GENERAL|Hydraulic pressure sensor circuit low
P0935|TRANS_GENERAL|Hydraulic pressure sensor circuit high
P0936|TRANS_GENERAL|Hydraulic pressure sensor circuit intermittent
P0937|TRANS_GENERAL|Hydraulic oil temperature sensor circuit
P0938|TRANS_GENERAL|Hydraulic oil temperature sensor range/performance
P0939|TRANS_GENERAL|Hydraulic oil temperature sensor circuit low
P0940|TRANS_GENERAL|Hydraulic oil temperature sensor circuit high
P0941|TRANS_GENERAL|Hydraulic oil temperature sensor circuit intermittent
P0942|TRANS_GENERAL|Hydraulic pressure unit
P0943|TRANS_GENERAL|Hydraulic pressure unit cycling period too short
P0944|TRANS_GENERAL|Hydraulic pressure unit loss of pressure
P0945|TRANS_GENERAL|Hydraulic pump relay/control circuit/open
P0946|TRANS_GENERAL|Hydraulic pump relay/control circuit range/performance
P0947|TRANS_GENERAL|Hydraulic pump relay/control circuit low
P0948|TRANS_GENERAL|Hydraulic pump relay/control circuit high
P0949|TRANS_GENERAL|Auto shift manual adaptive learning not complete
P0950|TRANS_GENERAL|Auto shift manual control circuit
P0951|TRANS_GENERAL|Auto shift manual control circuit range/performance
P0952|TRANS_GENERAL|Auto shift manual control circuit low
P0953|TRANS_GENERAL|Auto shift manual control circuit high
P0954|TRANS_GENERAL|Auto shift manual control circuit intermittent
P0955|TRANS_GENERAL|Auto shift manual mode circuit
P0956|TRANS_GENERAL|Auto shift manual mode circuit range/performance
P0957|TRANS_GENERAL|Auto shift manual mode circuit low
P0958|TRANS_GENERAL|Auto shift manual mode circuit high
P0959|TRANS_GENERAL|Auto shift manual mode circuit intermittent
P0960|TRANS_SOLENOID|Pressure control solenoid A control circuit/open
P0961|TRANS_SOLENOID|Pressure control solenoid A control circuit range/performance
P0962|TRANS_SOLENOID|Pressure control solenoid A control circuit low
P0963|TRANS_SOLENOID|Pressure control solenoid A control circuit high
P0964|TRANS_SOLENOID|Pressure control solenoid B control circuit/open
P0965|TRANS_SOLENOID|Pressure control solenoid B control circuit range/performance
P0966|TRANS_SOLENOID|Pressure control solenoid B control circuit low
P0967|TRANS_SOLENOID|Pressure control solenoid B control circuit high
P0968|TRANS_SOLENOID|Pressure control solenoid C control circuit/open
P0969|TRANS_SOLENOID|Pressure control solenoid C control circuit range/performance
P0970|TRANS_SOLENOID|Pressure control solenoid C control circuit low
P0971|TRANS_SOLENOID|Pressure control solenoid C control circuit high
P0972|TRANS_SOLENOID|Shift solenoid A control circuit range/performance
P0973|TRANS_SOLENOID|Shift solenoid A control circuit low
P0974|TRANS_SOLENOID|Shift solenoid A control circuit high
P0975|TRANS_SOLENOID|Shift solenoid B control circuit range/performance
P0976|TRANS_SOLENOID|Shift solenoid B control circuit low
P0977|TRANS_SOLENOID|Shift solenoid B control circuit high
P0978|TRANS_SOLENOID|Shift solenoid C control circuit range/performance
P0979|TRANS_SOLENOID|Shift solenoid C control circuit low
P0980|TRANS_SOLENOID|Shift solenoid C control circuit high
P0981|TRANS_SOLENOID|Shift solenoid D control circuit range/performance
P0982|TRANS_SOLENOID|Shift solenoid D control circuit low
P0983|TRANS_SOLENOID|Shift solenoid D control circuit high
P0984|TRANS_SOLENOID|Shift solenoid E control circuit range/performance
P0985|TRANS_SOLENOID|Shift solenoid E control circuit low
P0986|TRANS_SOLENOID|Shift solenoid E control circuit high
P0987|TRANS_SENSOR|Transmission fluid pressure sensor/switch E circuit
P0988|TRANS_SENSOR|Transmission fluid pressure sensor/switch E circuit range/performance
P0989|TRANS_SENSOR|Transmission fluid pressure sensor/switch E circuit low
P0990|TRANS_SENSOR|Transmission fluid pressure sensor/switch E circuit high
P0991|TRANS_SENSOR|Transmission fluid pressure sensor/switch E circuit intermittent
P0992|TRANS_SENSOR|Transmission fluid pressure sensor/switch F circuit
P0993|TRANS_SENSOR|Transmission fluid pressure sensor/switch F circuit range/performance
P0994|TRANS_SENSOR|Transmission fluid pressure sensor/switch F circuit low
P0995|TRANS_SENSOR|Transmission fluid pressure sensor/switch F circuit high
P0996|TRANS_SENSOR|Transmission fluid pressure sensor/switch F circuit intermittent
P0997|TRANS_SOLENOID|Shift solenoid F control circuit range/performance
P0998|TRANS_SOLENOID|Shift solenoid F control circuit low
P0999|TRANS_SOLENOID|Shift solenoid F control circuit high
P099A|TRANS_SOLENOID|Shift solenoid G control circuit range/performance
P099B|TRANS_SOLENOID|Shift solenoid G control circuit low
P099C|TRANS_SOLENOID|Shift solenoid G control circuit high
P099D|TRANS_SOLENOID|Shift solenoid H control circuit range/performance
P099E|TRANS_SOLENOID|Shift solenoid H control circuit low
P099F|TRANS_SOLENOID|Shift solenoid H control circuit high
P0A00|HYBRID|Motor electronics coolant temperature sensor A circuit
P0A01|HYBRID|Motor electronics coolant temperature sensor A circuit range/performance
P0A02|HYBRID|Motor electronics coolant temperature sensor A circuit low
P0A03|HYBRID|Motor electronics coolant temperature sensor A circuit high
P0A04|HYBRID|Motor electronics coolant temperature sensor A circuit intermittent
P0A05|COOLING|Motor electronics coolant pump A control circuit/open
P0A06|COOLING|Motor electronics coolant pump A control circuit low
P0A07|COOLING|Motor electronics coolant pump A control circuit high
P0A08|HYBRID|DC/DC converter status circuit
P0A09|HYBRID|DC/DC converter status circuit low
P0A0A|HYBRID|High voltage system interlock circuit A
P0A0B|HYBRID|High voltage system interlock circuit A performance
P0A0C|HYBRID|High voltage system interlock circuit A low
P0A0D|HYBRID|High voltage system interlock circuit A high
P0A0E|HYBRID|High voltage system interlock circuit A intermittent
P0A0F|GENERAL|Engine failed to start
P0A10|HYBRID|DC/DC converter status circuit high
P0A11|HYBRID|DC/DC converter enable circuit/open
P0A12|HYBRID|DC/DC converter enable circuit low
P0A13|HYBRID|DC/DC converter enable circuit high
P0A14|GENERAL|Engine mount A control circuit/open
P0A15|GENERAL|Engine mount A control circuit low
P0A16|GENERAL|Engine mount A control circuit high
P0A17|GENERAL|Motor torque sensor circuit
P0A18|GENERAL|Motor torque sensor circuit range/performance
P0A19|GENERAL|Motor torque sensor circuit low
P0A1A|HYBRID|Generator control module
P0A1B|HYBRID|Drive motor A control module performance
P0A1C|HYBRID|Drive motor B control module performance
P0A1D|HYBRID|Hybrid/EV powertrain control module A
P0A1E|HYBRID|Starter/generator control module
P0A1F|HYBRID|Battery energy control module A performance
P0A20|GENERAL|Motor torque sensor circuit high
P0A21|GENERAL|Motor torque sensor circuit intermittent
P0A22|HYBRID|Generator torque sensor circuit
P0A23|HYBRID|Generator torque sensor circuit range/performance
P0A24|HYBRID|Generator torque sensor circuit low
P0A25|HYBRID|Generator torque sensor circuit high
P0A26|HYBRID|Generator torque sensor circuit intermittent
P0A27|HYBRID|Hybrid/EV battery power off circuit
P0A28|HYBRID|Hybrid/EV battery power off circuit low
P0A29|HYBRID|Hybrid/EV battery power off circuit high
P0A2A|HYBRID|Drive motor A temperature sensor A circuit
P0A2B|HYBRID|Drive motor A temperature sensor A circuit range/performance
P0A2C|HYBRID|Drive motor A temperature sensor A circuit low
P0A2D|HYBRID|Drive motor A temperature sensor A circuit high
P0A2E|HYBRID|Drive motor A temperature sensor A circuit intermittent
P0A2F|HYBRID|Drive motor A over temperature
P0A30|HYBRID|Drive motor B temperature sensor circuit
P0A31|HYBRID|Drive motor B temperature sensor circuit range/performance
P0A32|HYBRID|Drive motor B temperature sensor circuit low
P0A33|HYBRID|Drive motor B temperature sensor circuit high
P0A34|HYBRID|Drive motor B temperature sensor circuit intermittent
P0A35|HYBRID|Drive motor B over temperature
P0A36|HYBRID|Generator temperature sensor circuit
P0A37|HYBRID|Generator temperature sensor circuit range/performance
P0A38|HYBRID|Generator temperature sensor circuit low
P0A39|HYBRID|Generator temperature sensor circuit high
P0A3A|HYBRID|Generator temperature sensor circuit intermittent
P0A3B|HYBRID|Generator A over temperature
P0A3C|HYBRID|Drive motor A inverter over temperature
P0A3D|HYBRID|Drive motor B inverter over temperature
P0A3E|HYBRID|Generator inverter over temperature
P0A3F|HYBRID|Drive motor A position sensor circuit
P0A40|HYBRID|Drive motor A position sensor circuit range/performance
P0A41|HYBRID|Drive motor A position sensor circuit low
P0A42|HYBRID|Drive motor A position sensor circuit high
P0A43|HYBRID|Drive motor A position sensor circuit intermittent
P0A44|HYBRID|Drive motor A position sensor circuit overspeed
P0A45|HYBRID|Drive motor B position sensor circuit
P0A46|HYBRID|Drive motor B position sensor circuit range/performance
P0A47|HYBRID|Drive motor B position sensor circuit low
P0A48|HYBRID|Drive motor B position sensor circuit high
P0A49|HYBRID|Drive motor B position sensor circuit intermittent
P0A4A|HYBRID|Drive motor B position sensor circuit overspeed
P0A4B|HYBRID|Generator position sensor circuit
P0A4C|HYBRID|Generator position sensor circuit range/performance
P0A4D|HYBRID|Generator position sensor circuit low
P0A4E|HYBRID|Generator position sensor circuit high
P0A4F|HYBRID|Generator position sensor circuit intermittent
P0A50|HYBRID|Generator position sensor circuit overspeed
P0A51|HYBRID|Drive motor A current sensor circuit
P0A52|HYBRID|Drive motor A current sensor circuit range/performance
P0A53|HYBRID|Drive motor A current sensor circuit low
P0A54|HYBRID|Drive motor A current sensor circuit high
P0A55|HYBRID|Drive motor B current sensor circuit
P0A56|HYBRID|Drive motor B current sensor circuit range/performance
P0A57|HYBRID|Drive motor B current sensor circuit low
P0A58|HYBRID|Drive motor B current sensor circuit high
P0A59|HYBRID|Generator current sensor circuit
P0A5A|HYBRID|Generator current sensor circuit range/performance
P0A5B|HYBRID|Generator current sensor circuit low
P0A5C|HYBRID|Generator current sensor circuit high
P0A5D|HYBRID|Drive motor A phase U current
P0A5E|HYBRID|Drive motor A phase U current low
P0A5F|HYBRID|Drive motor A phase U current high
P0A60|HYBRID|Drive motor A phase V current
P0A61|HYBRID|Drive motor A phase V current low
P0A62|HYBRID|Drive motor A phase V current high
P0A63|HYBRID|Drive motor A phase W current
P0A64|HYBRID|Drive motor A phase W current low
P0A65|HYBRID|Drive motor A phase W current high
P0A66|HYBRID|Drive motor B phase U current
P0A67|HYBRID|Drive motor B phase U current low
P0A68|HYBRID|Drive motor B phase U current high
P0A69|HYBRID|Drive motor B phase V current
P0A6A|HYBRID|Drive motor B phase V current low
P0A6B|HYBRID|Drive motor B phase V current high
P0A6C|HYBRID|Drive motor B phase W current
P0A6D|HYBRID|Drive motor B phase W current low
P0A6E|HYBRID|Drive motor B phase W current high
P0A6F|HYBRID|Generator phase U current
P0A70|HYBRID|Generator phase U current low
P0A71|HYBRID|Generator phase U current high
P0A72|HYBRID|Generator phase V current
P0A73|HYBRID|Generator phase V current low
P0A74|HYBRID|Generator phase V current high
P0A75|HYBRID|Generator phase W current
P0A76|HYBRID|Generator phase W current low
P0A77|HYBRID|Generator phase W current high
P0A78|HYBRID|Drive motor A inverter performance
P0A79|HYBRID|Drive motor B inverter performance
P0A7A|HYBRID|Generator inverter performance
P0A7B|GENERAL|Battery energy control module requested MIL illumination
P0A7C|HYBRID|Motor electronics over temperature
P0A7D|HYBRID|Hybrid/EV battery pack state of charge low
P0A7E|HYBRID|Hybrid/EV battery pack A over temperature
P0A7F|HYBRID|Hybrid/EV battery pack A deterioration
P0A80|HYBRID|Replace hybrid/EV battery pack
P0A81|HYBRID|Hybrid/EV battery pack cooling fan 1 control circuit/open
P0A82|HYBRID|Hybrid/EV battery pack cooling fan 1 performance/stuck off
P0A83|HYBRID|Hybrid/EV battery pack cooling fan 1 stuck on
P0A84|HYBRID|Hybrid/EV battery pack cooling fan 1 control circuit low
P0A85|HYBRID|Hybrid/EV battery pack cooling fan 1 control circuit high
P0A86|HYBRID|14 volt power module current sensor A circuit
P0A87|HYBRID|14 volt power module current sensor A circuit range/performance
P0A88|HYBRID|14 volt power module current sensor A circuit low
P0A89|HYBRID|14 volt power module current sensor A circuit high
P0A8A|HYBRID|14 volt power module current sensor A circuit intermittent
P0A8B|HYBRID|14 volt power module system voltage
P0A8C|HYBRID|14 volt power module system voltage unstable
P0A8D|HYBRID|14 volt power module system voltage low
P0A8E|HYBRID|14 volt power module system voltage high
P0A8F|HYBRID|14 volt power module system performance
P0A90|HYBRID|Drive motor A performance
P0A91|HYBRID|Drive motor B performance
P0A92|HYBRID|Hybrid generator performance
P0A93|COOLING|Inverter A cooling system performance
P0A94|HYBRID|DC/DC converter A performance
P0A95|HYBRID|High voltage fuse A
P0A96|HYBRID|Hybrid/EV battery pack cooling fan 2 control circuit/open
P0A97|HYBRID|Hybrid/EV battery pack cooling fan 2 performance/stuck off
P0A98|HYBRID|Hybrid/EV battery pack cooling fan 2 stuck on
P0A99|HYBRID|Hybrid/EV battery pack cooling fan 2 control circuit low
P0A9A|HYBRID|Hybrid/EV battery pack cooling fan 2 control circuit high
P0A9B|HYBRID|Hybrid/EV battery temperature sensor A circuit
P0A9C|HYBRID|Hybrid/EV battery temperature sensor A circuit range/performance
P0A9D|HYBRID|Hybrid/EV battery temperature sensor A circuit low
P0A9E|HYBRID|Hybrid/EV battery temperature sensor A circuit high
P0A9F|HYBRID|Hybrid/EV battery temperature sensor A circuit intermittent/erratic
P0AA0|HYBRID|Hybrid/EV battery positive contactor circuit
P0AA1|HYBRID|Hybrid/EV battery positive contactor circuit stuck closed
P0AA2|HYBRID|Hybrid/EV battery positive contactor circuit stuck open
P0AA3|HYBRID|Hybrid/EV battery negative contactor circuit
P0AA4|HYBRID|Hybrid/EV battery negative contactor circuit stuck closed
P0AA5|HYBRID|Hybrid/EV battery negative contactor circuit stuck open
P0AA6|HYBRID|Hybrid/EV battery voltage system isolation fault
P0AA7|HYBRID|Hybrid/EV battery voltage isolation sensor circuit
P0AA8|HYBRID|Hybrid/EV battery voltage isolation sensor circuit range/performance
P0AA9|HYBRID|Hybrid/EV battery voltage isolation sensor circuit low
P0AAA|HYBRID|Hybrid/EV battery voltage isolation sensor circuit high
P0AAB|HYBRID|Hybrid/EV battery voltage isolation sensor circuit intermittent/erratic
P0AAC|HYBRID|Hybrid/EV battery pack air temperature sensor A circuit
P0AAD|HYBRID|Hybrid/EV battery pack air temperature sensor A circuit range/performance
P0AAE|HYBRID|Hybrid/EV battery pack air temperature sensor A circuit low
P0AAF|HYBRID|Hybrid/EV battery pack air temperature sensor A circuit high
P0AB0|HYBRID|Hybrid/EV battery pack air temperature sensor A circuit intermittent/erratic
P0AB1|HYBRID|Hybrid/EV battery pack air temperature sensor B circuit
P0AB2|HYBRID|Hybrid/EV battery pack air temperature sensor B circuit range/performance
P0AB3|HYBRID|Hybrid/EV battery pack air temperature sensor B circuit low
P0AB4|HYBRID|Hybrid/EV battery pack air temperature sensor B circuit high
P0AB5|HYBRID|Hybrid/EV battery pack air temperature sensor B circuit intermittent/erratic
P0AB6|GENERAL|Engine mount B control circuit/open
P0AB7|GENERAL|Engine mount B control circuit low
P0AB8|GENERAL|Engine mount B control circuit high
P0AB9|HYBRID|Hybrid/EV system performance
P0ABA|HYBRID|Hybrid/EV battery pack voltage sense A circuit
P0ABB|HYBRID|Hybrid/EV battery pack voltage sense A circuit range/performance
P0ABC|HYBRID|Hybrid/EV battery pack voltage sense A circuit low
P0ABD|HYBRID|Hybrid/EV battery pack voltage sense A circuit high
P0ABE|HYBRID|Hybrid/EV battery pack voltage sense A circuit intermittent/erratic
P0ABF|HYBRID|Hybrid/EV battery pack current sensor A circuit
P0AC0|HYBRID|Hybrid/EV battery pack current sensor A circuit range/performance
P0AC1|HYBRID|Hybrid/EV battery pack current sensor A circuit low
P0AC2|HYBRID|Hybrid/EV battery pack current sensor A circuit high
P0AC3|HYBRID|Hybrid/EV battery pack current sensor A circuit intermittent/erratic
P0AC4|GENERAL|Hybrid/EV powertrain control module requested MIL illumination
P0AC5|HYBRID|Hybrid/EV battery temperature sensor B circuit
P0AC6|HYBRID|Hybrid/EV battery temperature sensor B circuit range/performance
P0AC7|HYBRID|Hybrid/EV battery temperature sensor B circuit low
P0AC8|HYBRID|Hybrid/EV battery temperature sensor B circuit high
P0AC9|HYBRID|Hybrid/EV battery temperature sensor B circuit intermittent/erratic
P0ACA|HYBRID|Hybrid/EV battery temperature sensor C circuit
P0ACB|HYBRID|Hybrid/EV battery temperature sensor C circuit range/performance
P0ACC|HYBRID|Hybrid/EV battery temperature sensor C circuit low
P0ACD|HYBRID|Hybrid/EV battery temperature sensor C circuit high
P0ACE|HYBRID|Hybrid/EV battery temperature sensor C circuit intermittent/erratic
P0ACF|HYBRID|Hybrid/EV battery pack cooling fan 3 control circuit/open
P0AD0|HYBRID|Hybrid/EV battery pack cooling fan 3 performance/stuck off
P0AD1|HYBRID|Hybrid/EV battery pack cooling fan 3 stuck on
P0AD2|HYBRID|Hybrid/EV battery pack cooling fan 3 control circuit low
P0AD3|HYBRID|Hybrid/EV battery pack cooling fan 3 control circuit high
P0AD4|HYBRID|Hybrid/EV battery pack air flow system insufficient air flow
P0AD5|HYBRID|Hybrid/EV battery pack air flow valve A control circuit/open
P0AD6|HYBRID|Hybrid/EV battery pack air flow valve A control circuit range/performance
P0AD7|HYBRID|Hybrid/EV battery pack air flow valve A control circuit low
P0AD8|HYBRID|Hybrid/EV battery pack air flow valve A control circuit high
P0AD9|HYBRID|Hybrid/EV battery positive contactor control circuit/open
P0ADA|HYBRID|Hybrid/EV battery positive contactor control circuit range/performance
P0ADB|HYBRID|Hybrid/EV battery positive contactor control circuit low
P0ADC|HYBRID|Hybrid/EV battery positive contactor control circuit high
P0ADD|HYBRID|Hybrid/EV battery negative contactor control circuit/open
P0ADE|HYBRID|Hybrid/EV battery negative contactor control circuit range/performance
P0ADF|HYBRID|Hybrid/EV battery negative contactor control circuit low
P0AE0|HYBRID|Hybrid/EV battery negative contactor control circuit high
P0AE1|HYBRID|Hybrid/EV battery precharge contactor circuit
P0AE2|HYBRID|Hybrid/EV battery precharge contactor circuit stuck closed
P0AE3|HYBRID|Hybrid/EV battery precharge contactor circuit stuck open
P0AE4|HYBRID|Hybrid/EV battery precharge contactor A control circuit/open
P0AE5|HYBRID|Hybrid/EV battery precharge contactor A control circuit range/performance
P0AE6|HYBRID|Hybrid/EV battery precharge contactor A control circuit low
P0AE7|HYBRID|Hybrid/EV battery precharge contactor A control circuit high
P0AE8|HYBRID|Hybrid/EV battery temperature sensor D circuit
P0AE9|HYBRID|Hybrid/EV battery temperature sensor D circuit range/performance
P0AEA|HYBRID|Hybrid/EV battery temperature sensor D circuit low
P0AEB|HYBRID|Hybrid/EV battery temperature sensor D circuit high
P0AEC|HYBRID|Hybrid/EV battery temperature sensor D circuit intermittent/erratic
P0AED|HYBRID|Drive motor inverter temperature sensor A circuit
P0AEE|HYBRID|Drive motor inverter temperature sensor A circuit range/performance
P0AEF|HYBRID|Drive motor inverter temperature sensor A circuit low
P0AF0|HYBRID|Drive motor inverter temperature sensor A circuit high
P0AF1|HYBRID|Drive motor inverter temperature sensor A circuit intermittent/erratic
P0AF2|HYBRID|Drive motor inverter temperature sensor B circuit
P0AF3|HYBRID|Drive motor inverter temperature sensor B circuit range/performance
P0AF4|HYBRID|Drive motor inverter temperature sensor B circuit low
P0AF5|HYBRID|Drive motor inverter temperature sensor B circuit high
P0AF6|HYBRID|Drive motor inverter temperature sensor B circuit intermittent/erratic
P0AF7|CONTROL_MODULE|14 volt power module internal temperature too high
P0AF8|HYBRID|Hybrid/EV battery system voltage
P0AF9|HYBRID|Hybrid/EV battery system voltage unstable
P0AFA|HYBRID|Hybrid/EV battery system voltage low
P0AFB|HYBRID|Hybrid/EV battery system voltage high
P0B00|TRANS_GENERAL|Electric/auxiliary transmission fluid pump motor phase U current
P0B01|TRANS_GENERAL|Electric/auxiliary transmission fluid pump motor phase U current low
P0B02|TRANS_GENERAL|Electric/auxiliary transmission fluid pump motor phase U current high
P0B03|TRANS_GENERAL|Electric/auxiliary transmission fluid pump motor phase V current
P0B04|TRANS_GENERAL|Electric/auxiliary transmission fluid pump motor phase V current low
P0B05|TRANS_GENERAL|Electric/auxiliary transmission fluid pump motor phase V current high
P0B06|TRANS_GENERAL|Electric/auxiliary transmission fluid pump motor phase W current
P0B07|TRANS_GENERAL|Electric/auxiliary transmission fluid pump motor phase W current low
P0B08|TRANS_GENERAL|Electric/auxiliary transmission fluid pump motor phase W current high
P0B09|TRANS_GENERAL|Electric/auxiliary transmission fluid pump A motor supply voltage circuit/open
P0B0A|TRANS_GENERAL|Electric/auxiliary transmission fluid pump A motor supply voltage circuit low
P0B0B|TRANS_GENERAL|Electric/auxiliary transmission fluid pump A motor supply voltage circuit high
P0B0C|TRANS_GENERAL|Electric/auxiliary transmission fluid pump A hydraulic leakage
P0B0D|TRANS_GENERAL|Electric/auxiliary transmission fluid pump motor control module
P2000|DIESEL_AFTERTREATMENT|NOx adsorber efficiency below threshold (bank 1)
P2001|DIESEL_AFTERTREATMENT|NOx adsorber efficiency below threshold (bank 2)
P2003|DIESEL_AFTERTREATMENT|Particulate filter efficiency below threshold (bank 2)
P2005|INTAKE_RUNNER|Intake manifold runner control stuck open (bank 2)
P2006|INTAKE_RUNNER|Intake manifold runner control stuck closed (bank 1)
P2007|INTAKE_RUNNER|Intake manifold runner control stuck closed (bank 2)
P2008|INTAKE_RUNNER|Intake manifold runner control circuit/open (bank 1)
P2009|INTAKE_RUNNER|Intake manifold runner control circuit low (bank 1)
P2010|INTAKE_RUNNER|Intake manifold runner control circuit high (bank 1)
P2011|INTAKE_RUNNER|Intake manifold runner control circuit/open (bank 2)
P2012|INTAKE_RUNNER|Intake manifold runner control circuit low (bank 2)
P2013|INTAKE_RUNNER|Intake manifold runner control circuit high (bank 2)
P2014|INTAKE_RUNNER|Intake manifold runner position sensor/switch circuit (bank 1)
P2015|INTAKE_RUNNER|Intake manifold runner position sensor/switch circuit range/performance (bank 1)
P2016|INTAKE_RUNNER|Intake manifold runner position sensor/switch circuit low (bank 1)
P2017|INTAKE_RUNNER|Intake manifold runner position sensor/switch circuit high (bank 1)
P2018|INTAKE_RUNNER|Intake manifold runner position sensor/switch circuit intermittent (bank 1)
P2019|INTAKE_RUNNER|Intake manifold runner position sensor/switch circuit (bank 2)
P2020|INTAKE_RUNNER|Intake manifold runner position sensor/switch circuit range/performance (bank 2)
P2021|INTAKE_RUNNER|Intake manifold runner position sensor/switch circuit low (bank 2)
P2022|INTAKE_RUNNER|Intake manifold runner position sensor/switch circuit high (bank 2)
P2023|INTAKE_RUNNER|Intake manifold runner position sensor/switch circuit intermittent (bank 2)
P2024|EVAP|EVAP fuel vapor temperature sensor circuit
P2025|EVAP|EVAP fuel vapor temperature sensor performance
P2026|EVAP|EVAP fuel vapor temperature sensor circuit low voltage
P2027|EVAP|EVAP fuel vapor temperature sensor circuit high voltage
P2028|EVAP|EVAP fuel vapor temperature sensor circuit intermittent
P2029|GENERAL|Fuel fired heater disabled
P2030|GENERAL|Fuel fired heater performance
P2031|EXHAUST_TEMP|Exhaust gas temperature sensor circuit (bank 1 sensor 2)
P2032|EXHAUST_TEMP|Exhaust gas temperature sensor circuit low (bank 1 sensor 2)
P2033|EXHAUST_TEMP|Exhaust gas temperature sensor circuit high (bank 1 sensor 2)
P2034|EXHAUST_TEMP|Exhaust gas temperature sensor circuit (bank 2 sensor 2)
P2035|EXHAUST_TEMP|Exhaust gas temperature sensor circuit low (bank 2 sensor 2)
P2036|EXHAUST_TEMP|Exhaust gas temperature sensor circuit high (bank 2 sensor 2)
P2037|DIESEL_AFTERTREATMENT|Reductant injection air pressure sensor A circuit
P2038|DIESEL_AFTERTREATMENT|Reductant injection air pressure sensor A circuit range/performance
P2039|DIESEL_AFTERTREATMENT|Reductant injection air pressure sensor A circuit low
P203A|DIESEL_AFTERTREATMENT|Reductant level sensor A circuit
P203B|DIESEL_AFTERTREATMENT|Reductant level sensor A circuit range/performance
P203C|DIESEL_AFTERTREATMENT|Reductant level sensor A circuit low
P203D|DIESEL_AFTERTREATMENT|Reductant level sensor A circuit high
P203E|DIESEL_AFTERTREATMENT|Reductant level sensor A circuit intermittent/erratic
P203F|DIESEL_AFTERTREATMENT|Reductant level too low
P2040|DIESEL_AFTERTREATMENT|Reductant injection air pressure sensor A circuit high
P2041|DIESEL_AFTERTREATMENT|Reductant injection air pressure sensor A circuit intermittent
P2042|DIESEL_AFTERTREATMENT|Reductant temperature sensor A circuit
P2043|DIESEL_AFTERTREATMENT|Reductant temperature sensor A circuit range/performance
P2044|DIESEL_AFTERTREATMENT|Reductant temperature sensor A circuit low
P2045|DIESEL_AFTERTREATMENT|Reductant temperature sensor A circuit high
P2046|DIESEL_AFTERTREATMENT|Reductant temperature sensor A circuit intermittent
P2047|DIESEL_AFTERTREATMENT|Reductant injection valve circuit/open bank 1 unit 1
P2048|DIESEL_AFTERTREATMENT|Reductant injection valve circuit low bank 1 unit 1
P2049|DIESEL_AFTERTREATMENT|Reductant injection valve circuit high bank 1 unit 1
P204A|DIESEL_AFTERTREATMENT|Reductant pressure sensor circuit
P204B|DIESEL_AFTERTREATMENT|Reductant pressure sensor circuit range/performance
P204C|DIESEL_AFTERTREATMENT|Reductant pressure sensor circuit low
P204D|DIESEL_AFTERTREATMENT|Reductant pressure sensor circuit high
P204E|DIESEL_AFTERTREATMENT|Reductant pressure sensor circuit intermittent/erratic
P2050|DIESEL_AFTERTREATMENT|Reductant injection valve circuit/open bank 2 unit 1
P2051|DIESEL_AFTERTREATMENT|Reductant injection valve circuit low bank 2 unit 1
P2052|DIESEL_AFTERTREATMENT|Reductant injection valve circuit high bank 2 unit 1
P2053|DIESEL_AFTERTREATMENT|Reductant injection valve circuit/open bank 1 unit 2
P2054|DIESEL_AFTERTREATMENT|Reductant injection valve circuit low bank 1 unit 2
P2055|DIESEL_AFTERTREATMENT|Reductant injection valve circuit high bank 1 unit 2
P2056|DIESEL_AFTERTREATMENT|Reductant injection valve circuit/open bank 2 unit 2
P2057|DIESEL_AFTERTREATMENT|Reductant injection valve circuit low bank 2 unit 2
P2058|DIESEL_AFTERTREATMENT|Reductant injection valve circuit high bank 2 unit 2
P2059|DIESEL_AFTERTREATMENT|Reductant injection air pump control circuit/open
P205A|DIESEL_AFTERTREATMENT|Reductant tank temperature sensor A circuit
P205B|DIESEL_AFTERTREATMENT|Reductant tank temperature sensor A circuit range/performance
P205C|DIESEL_AFTERTREATMENT|Reductant tank temperature sensor A circuit low
P205D|DIESEL_AFTERTREATMENT|Reductant tank temperature sensor A circuit high
P205E|DIESEL_AFTERTREATMENT|Reductant tank temperature sensor A circuit intermittent/erratic
P2060|DIESEL_AFTERTREATMENT|Reductant injection air pump control circuit low
P2061|DIESEL_AFTERTREATMENT|Reductant injection air pump control circuit high
P2062|DIESEL_AFTERTREATMENT|Reductant/regeneration supply control circuit/open
P2063|DIESEL_AFTERTREATMENT|Reductant/regeneration supply control circuit low
P2064|DIESEL_AFTERTREATMENT|Reductant/regeneration supply control circuit high
P2065|FUEL_LEVEL|Fuel level sensor B circuit
P2066|FUEL_LEVEL|Fuel level sensor B performance
P2067|FUEL_LEVEL|Fuel level sensor B circuit low
P2068|FUEL_LEVEL|Fuel level sensor B circuit high
P2069|FUEL_LEVEL|Fuel level sensor B circuit intermittent
P206E|INTAKE_RUNNER|Intake manifold tuning (IMT) valve stuck open (bank 2)
P206F|INTAKE_RUNNER|Intake manifold tuning (IMT) valve stuck closed (bank 2)
P2070|INTAKE_RUNNER|Intake manifold tuning (IMT) valve stuck open (bank 1)
P2071|INTAKE_RUNNER|Intake manifold tuning (IMT) valve stuck closed (bank 1)
P2072|THROTTLE_ACTUATOR|Throttle actuator control system - ice blockage (bank 1)
P2073|THROTTLE_SENSOR|Manifold absolute pressure/mass air flow - throttle position correlation at idle
P2074|THROTTLE_SENSOR|Manifold absolute pressure/mass air flow - throttle position correlation at higher load
P2075|INTAKE_RUNNER|Intake manifold tuning (IMT) valve position sensor/switch circuit (bank 1)
P2076|INTAKE_RUNNER|Intake manifold tuning (IMT) valve position sensor/switch circuit range/performance (bank 1)
P2077|INTAKE_RUNNER|Intake manifold tuning (IMT) valve position sensor/switch circuit low (bank 1)
P2078|INTAKE_RUNNER|Intake manifold tuning (IMT) valve position sensor/switch circuit high (bank 1)
P2079|INTAKE_RUNNER|Intake manifold tuning (IMT) valve position sensor/switch circuit intermittent (bank 1)
P207A|INTAKE_RUNNER|Intake manifold tuning (IMT) valve position sensor/switch circuit (bank 2)
P207B|INTAKE_RUNNER|Intake manifold tuning (IMT) valve position sensor/switch circuit range/performance (bank 2)
P207C|INTAKE_RUNNER|Intake manifold tuning (IMT) valve position sensor/switch circuit low (bank 2)
P207D|INTAKE_RUNNER|Intake manifold tuning (IMT) valve position sensor/switch circuit high (bank 2)
P207E|INTAKE_RUNNER|Intake manifold tuning (IMT) valve position sensor/switch circuit intermittent (bank 2)
P2080|EXHAUST_TEMP|Exhaust gas temperature sensor circuit range/performance (bank 1 sensor 1)
P2081|EXHAUST_TEMP|Exhaust gas temperature sensor circuit intermittent (bank 1 sensor 1)
P2082|EXHAUST_TEMP|Exhaust gas temperature sensor circuit range/performance (bank 2 sensor 1)
P2083|EXHAUST_TEMP|Exhaust gas temperature sensor circuit intermittent (bank 2 sensor 1)
P2084|EXHAUST_TEMP|Exhaust gas temperature sensor circuit range/performance (bank 1 sensor 2)
P2085|EXHAUST_TEMP|Exhaust gas temperature sensor circuit intermittent (bank 1 sensor 2)
P2086|EXHAUST_TEMP|Exhaust gas temperature sensor circuit range/performance (bank 2 sensor 2)
P2087|EXHAUST_TEMP|Exhaust gas temperature sensor circuit intermittent (bank 2 sensor 2)
P2088|CAM_TIMING|Camshaft A position actuator control circuit low (bank 1)
P2089|CAM_TIMING|Camshaft A position actuator control circuit high (bank 1)
P2090|CAM_TIMING|Camshaft B position actuator control circuit low (bank 1)
P2091|CAM_TIMING|Camshaft B position actuator control circuit high (bank 1)
P2092|CAM_TIMING|Camshaft A position actuator control circuit low (bank 2)
P2093|CAM_TIMING|Camshaft A position actuator control circuit high (bank 2)
P2094|CAM_TIMING|Camshaft B position actuator control circuit low (bank 2)
P2095|CAM_TIMING|Camshaft B position actuator control circuit high (bank 2)
P2100|THROTTLE_ACTUATOR|Throttle actuator A control motor circuit/open
P2102|THROTTLE_ACTUATOR|Throttle actuator A control motor circuit low
P2103|THROTTLE_ACTUATOR|Throttle actuator A control motor circuit high
P2104|THROTTLE_ACTUATOR|Throttle actuator control system - forced idle
P2105|THROTTLE_ACTUATOR|Throttle actuator control system - forced engine shutdown
P2106|THROTTLE_ACTUATOR|Throttle actuator control system - forced limited power
P2107|CONTROL_MODULE|Throttle actuator A control module processor
P2108|THROTTLE_ACTUATOR|Throttle actuator A control module performance
"""

    private const val C2 = """
P2109|THROTTLE_SENSOR|Throttle/pedal position sensor A minimum stop performance
P2110|THROTTLE_ACTUATOR|Throttle actuator A control system - forced limited RPM
P2113|THROTTLE_SENSOR|Throttle/pedal position sensor B minimum stop performance
P2114|THROTTLE_SENSOR|Throttle/pedal position sensor C minimum stop performance
P2115|THROTTLE_SENSOR|Throttle/pedal position sensor D minimum stop performance
P2116|THROTTLE_SENSOR|Throttle/pedal position sensor E minimum stop performance
P2117|THROTTLE_SENSOR|Throttle/pedal position sensor F minimum stop performance
P2120|THROTTLE_SENSOR|Throttle/pedal position sensor/switch D circuit
P2121|THROTTLE_SENSOR|Throttle/pedal position sensor/switch D circuit range/performance
P2124|THROTTLE_SENSOR|Throttle/pedal position sensor/switch D circuit intermittent
P2125|THROTTLE_SENSOR|Throttle/pedal position sensor/switch E circuit
P2126|THROTTLE_SENSOR|Throttle/pedal position sensor/switch E circuit range/performance
P2129|THROTTLE_SENSOR|Throttle/pedal position sensor/switch E circuit intermittent
P2130|THROTTLE_SENSOR|Throttle/pedal position sensor/switch F circuit
P2131|THROTTLE_SENSOR|Throttle/pedal position sensor/switch F circuit range/performance
P2132|THROTTLE_SENSOR|Throttle/pedal position sensor/switch F circuit low
P2133|THROTTLE_SENSOR|Throttle/pedal position sensor/switch F circuit high
P2134|THROTTLE_SENSOR|Throttle/pedal position sensor/switch F circuit intermittent
P2136|AIRCON|Throttle/pedal position sensor/switch A/C voltage correlation
P2137|THROTTLE_SENSOR|Throttle/pedal position sensor/switch B/C voltage correlation
P2139|THROTTLE_SENSOR|Throttle/pedal position sensor/switch D/F voltage correlation
P213A|EGR|EGR throttle control circuit B /open
P213B|EGR|EGR throttle control circuit B range/performance
P213C|EGR|EGR throttle control circuit B low
P213D|EGR|EGR throttle control circuit B high
P2140|THROTTLE_SENSOR|Throttle/pedal position sensor/switch E/F voltage correlation
P2141|EGR|EGR throttle control circuit A low
P2142|EGR|EGR throttle control circuit A high
P2143|EGR|EGR vent control circuit/open
P2144|EGR|EGR vent control circuit low
P2145|EGR|EGR vent control circuit high
P2146|INJECTOR|Fuel injector group A supply voltage circuit/open
P2147|INJECTOR|Fuel injector group A supply voltage circuit low
P2148|INJECTOR|Fuel injector group A supply voltage circuit high
P2149|INJECTOR|Fuel injector group B supply voltage circuit/open
P2150|INJECTOR|Fuel injector group B supply voltage circuit low
P2151|INJECTOR|Fuel injector group B supply voltage circuit high
P2152|INJECTOR|Fuel injector group C supply voltage circuit/open
P2153|INJECTOR|Fuel injector group C supply voltage circuit low
P2154|INJECTOR|Fuel injector group C supply voltage circuit high
P2155|INJECTOR|Fuel injector group D supply voltage circuit/open
P2156|INJECTOR|Fuel injector group D supply voltage circuit low
P2157|INJECTOR|Fuel injector group D supply voltage circuit high
P2158|SPEED_SENSOR|Vehicle speed sensor B circuit
P2159|SPEED_SENSOR|Vehicle speed sensor B circuit range/performance
P215A|SPEED_SENSOR|Vehicle speed - wheel speed correlation
P215B|SPEED_SENSOR|Vehicle speed - output shaft speed correlation
P2160|SPEED_SENSOR|Vehicle speed sensor B circuit low
P2161|SPEED_SENSOR|Vehicle speed sensor B circuit intermittent/erratic/high
P2162|SPEED_SENSOR|Vehicle speed sensor A/B correlation
P2163|THROTTLE_SENSOR|Throttle/pedal position sensor A maximum stop performance
P2164|THROTTLE_SENSOR|Throttle/pedal position sensor B maximum stop performance
P2165|THROTTLE_SENSOR|Throttle/pedal position sensor C maximum stop performance
P2166|THROTTLE_SENSOR|Throttle/pedal position sensor D maximum stop performance
P2167|THROTTLE_SENSOR|Throttle/pedal position sensor E maximum stop performance
P2168|THROTTLE_SENSOR|Throttle/pedal position sensor F maximum stop performance
P2169|GENERAL|Exhaust pressure regulator vent solenoid control circuit/open
P2170|GENERAL|Exhaust pressure regulator vent solenoid control circuit low
P2171|GENERAL|Exhaust pressure regulator vent solenoid control circuit high
P2172|THROTTLE_ACTUATOR|Throttle actuator control system - sudden high air flow detected
P2173|THROTTLE_ACTUATOR|Throttle actuator control system - high air flow detected
P2174|THROTTLE_ACTUATOR|Throttle actuator control system - sudden low air flow detected
P2175|THROTTLE_ACTUATOR|Throttle actuator control system - low air flow detected
P2180|FUEL_TRIM|System too rich off idle (bank 2)
P2181|COOLING|Cooling system performance
P2182|COOLANT_SENSOR|Engine coolant temperature sensor 2 circuit
P2183|COOLANT_SENSOR|Engine coolant temperature sensor 2 circuit range/performance
P2184|COOLANT_SENSOR|Engine coolant temperature sensor 2 circuit low
P2185|COOLANT_SENSOR|Engine coolant temperature sensor 2 circuit high
P2186|COOLANT_SENSOR|Engine coolant temperature sensor 2 circuit intermittent/erratic
P2191|FUEL_TRIM|System too lean at higher load (bank 1)
P2192|FUEL_TRIM|System too rich at higher load (bank 1)
P2193|FUEL_TRIM|System too lean at higher load (bank 2)
P2194|FUEL_TRIM|System too rich at higher load (bank 2)
P2199|AIR_SENSOR|Intake air temperature sensor 1/2 correlation
P2200|DIESEL_AFTERTREATMENT|NOx sensor circuit (bank 1 sensor 1)
P2201|DIESEL_AFTERTREATMENT|NOx sensor circuit range/performance (bank 1 sensor 1)
P2202|DIESEL_AFTERTREATMENT|NOx sensor circuit low (bank 1 sensor 1)
P2203|DIESEL_AFTERTREATMENT|NOx sensor circuit high (bank 1 sensor 1)
P2204|DIESEL_AFTERTREATMENT|NOx sensor circuit intermittent (bank 1 sensor 1)
P2205|DIESEL_AFTERTREATMENT|NOx sensor heater control circuit/open (bank 1 sensor 1)
P2206|DIESEL_AFTERTREATMENT|NOx sensor heater control circuit low (bank 1 sensor 1)
P2207|DIESEL_AFTERTREATMENT|NOx sensor heater control circuit high (bank 1 sensor 1)
P2208|DIESEL_AFTERTREATMENT|NOx sensor heater sense circuit (bank 1 sensor 1)
P2209|DIESEL_AFTERTREATMENT|NOx sensor heater sense circuit range/performance (bank 1 sensor 1)
P2210|DIESEL_AFTERTREATMENT|NOx sensor heater sense circuit low (bank 1 sensor 1)
P2211|DIESEL_AFTERTREATMENT|NOx sensor heater sense circuit high (bank 1 sensor 1)
P2212|DIESEL_AFTERTREATMENT|NOx sensor heater sense circuit intermittent (bank 1 sensor 1)
P2213|DIESEL_AFTERTREATMENT|NOx sensor circuit (bank 2)
P2214|DIESEL_AFTERTREATMENT|NOx sensor circuit range/performance (bank 2)
P2215|DIESEL_AFTERTREATMENT|NOx sensor circuit low (bank 2)
P2216|DIESEL_AFTERTREATMENT|NOx sensor circuit high (bank 2)
P2217|DIESEL_AFTERTREATMENT|NOx sensor circuit intermittent (bank 2)
P2218|DIESEL_AFTERTREATMENT|NOx sensor heater control circuit/open (bank 2)
P2219|DIESEL_AFTERTREATMENT|NOx sensor heater control circuit low (bank 2)
P2220|DIESEL_AFTERTREATMENT|NOx sensor heater control circuit high (bank 2)
P2221|DIESEL_AFTERTREATMENT|NOx sensor heater sense circuit (bank 2)
P2222|DIESEL_AFTERTREATMENT|NOx sensor heater sense circuit range/performance (bank 2)
P2223|DIESEL_AFTERTREATMENT|NOx sensor heater sense circuit low (bank 2)
P2224|DIESEL_AFTERTREATMENT|NOx sensor heater sense circuit high (bank 2)
P2225|DIESEL_AFTERTREATMENT|NOx sensor heater sense circuit intermittent (bank 2)
P2226|AIR_SENSOR|Barometric pressure sensor A circuit
P2227|AIR_SENSOR|Barometric pressure sensor A circuit range/performance
P2228|AIR_SENSOR|Barometric pressure sensor A circuit low
P2229|AIR_SENSOR|Barometric pressure sensor A circuit high
P2230|AIR_SENSOR|Barometric pressure sensor A circuit intermittent/erratic
P2231|O2_HEATER|O2 sensor signal circuit shorted to heater circuit (bank 1 sensor 1)
P2232|O2_HEATER|O2 sensor signal circuit shorted to heater circuit (bank 1 sensor 2)
P2233|O2_HEATER|O2 sensor signal circuit shorted to heater circuit (bank 1 sensor 3)
P2234|O2_HEATER|O2 sensor signal circuit shorted to heater circuit (bank 2 sensor 1)
P2235|O2_HEATER|O2 sensor signal circuit shorted to heater circuit (bank 2 sensor 2)
P2236|O2_HEATER|O2 sensor signal circuit shorted to heater circuit (bank 2 sensor 3)
P2237|O2|O2 sensor positive current control circuit/open (bank 1 sensor 1)
P2238|O2|O2 sensor positive current control circuit low (bank 1 sensor 1)
P2239|O2|O2 sensor positive current control circuit high (bank 1 sensor 1)
P2240|O2|O2 sensor positive current control circuit/open (bank 2 sensor 1)
P2241|O2|O2 sensor positive current control circuit low (bank 2 sensor 1)
P2242|O2|O2 sensor positive current control circuit high (bank 2 sensor 1)
P2243|O2|O2 sensor reference voltage circuit/open (bank 1 sensor 1)
P2244|O2|O2 sensor reference voltage performance (bank 1 sensor 1)
P2245|O2|O2 sensor reference voltage circuit low (bank 1 sensor 1)
P2246|O2|O2 sensor reference voltage circuit high (bank 1 sensor 1)
P2247|O2|O2 sensor reference voltage circuit/open (bank 2 sensor 1)
P2248|O2|O2 sensor reference voltage performance (bank 2 sensor 1)
P2249|O2|O2 sensor reference voltage circuit low (bank 2 sensor 1)
P2250|O2|O2 sensor reference voltage circuit high (bank 2 sensor 1)
P2251|O2|O2 sensor negative current control circuit/open (bank 1 sensor 1)
P2252|O2|O2 sensor negative current control circuit low (bank 1 sensor 1)
P2253|O2|O2 sensor negative current control circuit high (bank 1 sensor 1)
P2254|O2|O2 sensor negative current control circuit/open (bank 2 sensor 1)
P2255|O2|O2 sensor negative current control circuit low (bank 2 sensor 1)
P2256|O2|O2 sensor negative current control circuit high (bank 2 sensor 1)
P2257|SECONDARY_AIR|Secondary air injection system control A circuit low
P2258|SECONDARY_AIR|Secondary air injection system control A circuit high
P2259|SECONDARY_AIR|Secondary air injection system control B circuit low
P2260|SECONDARY_AIR|Secondary air injection system control B circuit high
P2261|TURBO|Turbocharger/supercharger bypass valve A - mechanical
P2262|TURBO|Turbocharger/supercharger boost pressure A not detected - mechanical
P2263|TURBO|Turbocharger/supercharger A boost system performance
P2264|GENERAL|Water in fuel sensor A circuit
P2265|GENERAL|Water in fuel sensor A circuit range/performance
P2266|GENERAL|Water in fuel sensor A circuit low
P2267|GENERAL|Water in fuel sensor A circuit high
P2268|GENERAL|Water in fuel sensor A circuit intermittent
P2269|GENERAL|Water in fuel condition - sensor A
P2274|O2|O2 sensor signal biased/stuck lean (bank 1 sensor 3)
P2275|O2|O2 sensor signal biased/stuck rich (bank 1 sensor 3)
P2276|O2|O2 sensor signal biased/stuck lean (bank 2 sensor 3)
P2277|O2|O2 sensor signal biased/stuck rich (bank 2 sensor 3)
P2278|O2|O2 sensor signals swapped bank 1 sensor 3/ (bank 2 sensor 3)
P2280|AIR_SENSOR|Air flow restriction/air leak between air filter and MAF (bank 1)
P2281|THROTTLE_ACTUATOR|Air leak between MAF and throttle body
P2282|THROTTLE_ACTUATOR|Air leak between throttle body and intake valves
P2283|INJECTOR|Injector control pressure sensor circuit
P2284|INJECTOR|Injector control pressure sensor circuit range/performance
P2285|INJECTOR|Injector control pressure sensor circuit low
P2286|INJECTOR|Injector control pressure sensor circuit high
P2287|INJECTOR|Injector control pressure sensor circuit intermittent
P2288|INJECTOR|Injector control pressure too high
P2289|INJECTOR|Injector control pressure too high - engine off
P2290|INJECTOR|Injector control pressure too low
P2291|INJECTOR|Injector control pressure too low - engine cranking
P2292|INJECTOR|Injector control pressure erratic
P2293|FUEL_PRESSURE|Fuel pressure regulator 2 performance
P2294|FUEL_PRESSURE|Fuel pressure regulator 2 control circuit
P2295|FUEL_PRESSURE|Fuel pressure regulator 2 control circuit low
P2296|FUEL_PRESSURE|Fuel pressure regulator 2 control circuit high
P2297|O2|O2 sensor out of range during deceleration (bank 1 sensor 1)
P2298|O2|O2 sensor out of range during deceleration (bank 2 sensor 1)
P2299|THROTTLE_SENSOR|Brake pedal position/accelerator pedal position incompatible
P2300|IGNITION_COIL|Ignition coil A primary control circuit low
P2301|IGNITION_COIL|Ignition coil A primary control circuit high
P2302|IGNITION_COIL|Ignition coil A secondary circuit
P2303|IGNITION_COIL|Ignition coil B primary control circuit low
P2304|IGNITION_COIL|Ignition coil B primary control circuit high
P2305|IGNITION_COIL|Ignition coil B secondary circuit
P2306|IGNITION_COIL|Ignition coil C primary control circuit low
P2307|IGNITION_COIL|Ignition coil C primary control circuit high
P2308|IGNITION_COIL|Ignition coil C secondary circuit
P2309|IGNITION_COIL|Ignition coil D primary control circuit low
P2310|IGNITION_COIL|Ignition coil D primary control circuit high
P2311|IGNITION_COIL|Ignition coil D secondary circuit
P2312|IGNITION_COIL|Ignition coil E primary control circuit low
P2313|IGNITION_COIL|Ignition coil E primary control circuit high
P2314|IGNITION_COIL|Ignition coil E secondary circuit
P2315|IGNITION_COIL|Ignition coil F primary control circuit low
P2316|IGNITION_COIL|Ignition coil F primary control circuit high
P2317|IGNITION_COIL|Ignition coil F secondary circuit
P2318|IGNITION_COIL|Ignition coil G primary control circuit low
P2319|IGNITION_COIL|Ignition coil G primary control circuit high
P2320|IGNITION_COIL|Ignition coil G secondary circuit
P2321|IGNITION_COIL|Ignition coil H primary control circuit low
P2322|IGNITION_COIL|Ignition coil H primary control circuit high
P2323|IGNITION_COIL|Ignition coil H secondary circuit
P2324|IGNITION_COIL|Ignition coil i primary control circuit low
P2325|IGNITION_COIL|Ignition coil i primary control circuit high
P2326|IGNITION_COIL|Ignition coil i secondary circuit
P2327|IGNITION_COIL|Ignition coil J primary control circuit low
P2328|IGNITION_COIL|Ignition coil J primary control circuit high
P2329|IGNITION_COIL|Ignition coil J secondary circuit
P2330|IGNITION_COIL|Ignition coil K primary control circuit low
P2331|IGNITION_COIL|Ignition coil K primary control circuit high
P2332|IGNITION_COIL|Ignition coil K secondary circuit
P2333|IGNITION_COIL|Ignition coil L primary control circuit low
P2334|IGNITION_COIL|Ignition coil L primary control circuit high
P2335|IGNITION_COIL|Ignition coil L secondary circuit
P2336|KNOCK|Cylinder 1 above knock/combustion vibration sensor threshold
P2337|KNOCK|Cylinder 2 above knock/combustion vibration sensor threshold
P2338|KNOCK|Cylinder 3 above knock/combustion vibration sensor threshold
P2339|KNOCK|Cylinder 4 above knock/combustion vibration sensor threshold
P2340|KNOCK|Cylinder 5 above knock/combustion vibration sensor threshold
P2341|KNOCK|Cylinder 6 above knock/combustion vibration sensor threshold
P2342|KNOCK|Cylinder 7 above knock/combustion vibration sensor threshold
P2343|KNOCK|Cylinder 8 above knock/combustion vibration sensor threshold
P2344|KNOCK|Cylinder 9 above knock/combustion vibration sensor threshold
P2345|KNOCK|Cylinder 10 above knock/combustion vibration sensor threshold
P2346|KNOCK|Cylinder 11 above knock/combustion vibration sensor threshold
P2347|KNOCK|Cylinder 12 above knock/combustion vibration sensor threshold
P2400|EVAP|EVAP system leak detection pump control circuit/open
P2401|EVAP|EVAP system leak detection pump control circuit low
P2402|EVAP|EVAP system leak detection pump control circuit high
P2403|EVAP|EVAP system leak detection pump sense circuit/open
P2404|EVAP|EVAP system leak detection pump sense circuit range/performance
P2405|EVAP|EVAP system leak detection pump sense circuit low
P2406|EVAP|EVAP system leak detection pump sense circuit high
P2407|EVAP|EVAP system leak detection pump sense circuit intermittent/erratic
P2408|EVAP|Fuel cap sensor/switch circuit
P2409|EVAP|Fuel cap sensor/switch circuit range/performance
P240A|EVAP|EVAP system leak detection pump heater control circuit/open
P240B|EVAP|EVAP system leak detection pump heater control circuit low
P240C|EVAP|EVAP system leak detection pump heater control circuit high
P2410|EVAP|Fuel cap sensor/switch circuit low
P2411|EVAP|Fuel cap sensor/switch circuit high
P2412|EVAP|Fuel cap sensor/switch circuit intermittent/erratic
P2413|EGR|EGR system performance
P2414|O2|O2 sensor exhaust sample error (bank 1 sensor 1)
P2415|O2|O2 sensor exhaust sample error (bank 2 sensor 1)
P2416|O2|O2 sensor signals swapped bank 1 sensor 2/ (bank 1 sensor 3)
P2417|O2|O2 sensor signals swapped bank 2 sensor 2/ (bank 2 sensor 3)
P2418|EVAP|EVAP system switching valve control circuit/open
P2419|EVAP|EVAP system switching valve control circuit low
P2420|EVAP|EVAP system switching valve control circuit high
P2421|EVAP|EVAP system vent valve stuck open
P2422|EVAP|EVAP system vent valve stuck closed
P2423|CATALYST|HC adsorption catalyst efficiency below threshold (bank 1)
P2424|CATALYST|HC adsorption catalyst efficiency below threshold (bank 2)
P2425|EGR|EGR cooling valve control circuit/open
P2426|EGR|EGR cooling valve control circuit low
P2427|EGR|EGR cooling valve control circuit high
P2428|EXHAUST_TEMP|Exhaust gas temperature too high (bank 1)
P2429|EXHAUST_TEMP|Exhaust gas temperature too high (bank 2)
P242A|EXHAUST_TEMP|Exhaust gas temperature sensor circuit (bank 1 sensor 3)
P242B|EXHAUST_TEMP|Exhaust gas temperature sensor circuit range/performance (bank 1 sensor 3)
P242C|EXHAUST_TEMP|Exhaust gas temperature sensor circuit low (bank 1 sensor 3)
P242D|EXHAUST_TEMP|Exhaust gas temperature sensor circuit high (bank 1 sensor 3)
P242E|EXHAUST_TEMP|Exhaust gas temperature sensor circuit intermittent/erratic (bank 1 sensor 3)
P2430|SECONDARY_AIR|Secondary air injection system air flow/pressure sensor circuit (bank 1)
P2431|SECONDARY_AIR|Secondary air injection system air flow/pressure sensor circuit range/performance (bank 1)
P2432|SECONDARY_AIR|Secondary air injection system air flow/pressure sensor circuit low (bank 1)
P2433|SECONDARY_AIR|Secondary air injection system air flow/pressure sensor circuit high (bank 1)
P2434|SECONDARY_AIR|Secondary air injection system air flow/pressure sensor circuit intermittent/erratic (bank 1)
P2435|SECONDARY_AIR|Secondary air injection system air flow/pressure sensor circuit (bank 2)
P2436|SECONDARY_AIR|Secondary air injection system air flow/pressure sensor circuit range/performance (bank 2)
P2437|SECONDARY_AIR|Secondary air injection system air flow/pressure sensor circuit low (bank 2)
P2438|SECONDARY_AIR|Secondary air injection system air flow/pressure sensor circuit high (bank 2)
P2439|SECONDARY_AIR|Secondary air injection system air flow/pressure sensor circuit intermittent/erratic (bank 2)
P2440|SECONDARY_AIR|Secondary air injection system switching valve stuck open (bank 1)
P2441|SECONDARY_AIR|Secondary air injection system switching valve stuck closed (bank 1)
P2442|SECONDARY_AIR|Secondary air injection system switching valve stuck open (bank 2)
P2443|SECONDARY_AIR|Secondary air injection system switching valve stuck closed (bank 2)
P2444|SECONDARY_AIR|Secondary air injection system pump stuck on (bank 1)
P2445|SECONDARY_AIR|Secondary air injection system pump stuck off (bank 1)
P2446|SECONDARY_AIR|Secondary air injection system pump stuck on (bank 2)
P2447|SECONDARY_AIR|Secondary air injection system pump stuck off (bank 2)
P2448|SECONDARY_AIR|Secondary air injection system high air flow (bank 1)
P2449|SECONDARY_AIR|Secondary air injection system high air flow (bank 2)
P244A|DIESEL_AFTERTREATMENT|Particulate filter differential pressure too low (bank 1)
P2450|EVAP|EVAP system switching valve performance/stuck open
P2451|EVAP|EVAP system switching valve stuck closed
P2452|DIESEL_AFTERTREATMENT|Particulate filter pressure sensor A circuit
P2453|DIESEL_AFTERTREATMENT|Particulate filter pressure sensor A circuit range/performance
P2454|DIESEL_AFTERTREATMENT|Particulate filter pressure sensor A circuit low
P2455|DIESEL_AFTERTREATMENT|Particulate filter pressure sensor A circuit high
P2456|DIESEL_AFTERTREATMENT|Particulate filter pressure sensor A circuit intermittent/erratic
P2458|DIESEL_AFTERTREATMENT|Particulate filter regeneration duration (bank 1)
P245A|EGR|EGR cooler bypass control circuit/open (bank 1)
P245B|EGR|EGR cooler bypass control circuit range/performance (bank 1)
P245C|EGR|EGR cooler bypass control circuit low (bank 1)
P245D|EGR|EGR cooler bypass control circuit high (bank 1)
P2500|CHARGING|Generator lamp/l-terminal circuit low
P2501|CHARGING|Generator lamp/l-terminal circuit high
P2502|CHARGING|Charging system voltage
P2503|CHARGING|Charging system voltage low
P2504|CHARGING|Charging system voltage high
P2505|CONTROL_MODULE|ECM/PCM power input signal
P2506|CONTROL_MODULE|ECM/PCM power input signal range/performance
P2507|CONTROL_MODULE|ECM/PCM power input signal low
P2508|CONTROL_MODULE|ECM/PCM power input signal high
P2509|CONTROL_MODULE|ECM/PCM power input signal intermittent
P250A|ENGINE_OIL|Engine oil level sensor circuit
P250B|ENGINE_OIL|Engine oil level sensor circuit range/performance
P250C|ENGINE_OIL|Engine oil level sensor circuit low
P250D|ENGINE_OIL|Engine oil level sensor circuit high
P250E|ENGINE_OIL|Engine oil level sensor circuit intermittent/erratic
P250F|ENGINE_OIL|Engine oil level too low
P2510|CONTROL_MODULE|ECM/PCM power relay sense circuit range/performance
P2511|CONTROL_MODULE|ECM/PCM power relay sense circuit intermittent
P2512|GENERAL|Event data recorder request circuit/open
P2513|GENERAL|Event data recorder request circuit low
P2514|GENERAL|Event data recorder request circuit high
P2515|AIRCON|A/C refrigerant pressure sensor B circuit
P2516|AIRCON|A/C refrigerant pressure sensor B circuit range/performance
P2517|AIRCON|A/C refrigerant pressure sensor B circuit low
P2518|AIRCON|A/C refrigerant pressure sensor B circuit high
P2519|AIRCON|A/C request A circuit
P251A|GENERAL|PTO enable switch circuit/open
P251B|GENERAL|PTO enable switch circuit low
P251C|GENERAL|PTO enable switch circuit high
P251D|GENERAL|PTO engine shutdown circuit/open
P251E|GENERAL|PTO engine shutdown circuit low
P251F|GENERAL|PTO engine shutdown circuit high
P2520|AIRCON|A/C request A circuit low
P2521|AIRCON|A/C request A circuit high
P2522|AIRCON|A/C request B circuit
P2523|AIRCON|A/C request B circuit low
P2524|AIRCON|A/C request B circuit high
P2525|GENERAL|Vacuum reservoir pressure sensor circuit
P2526|GENERAL|Vacuum reservoir pressure sensor circuit range/performance
P2527|GENERAL|Vacuum reservoir pressure sensor circuit low
P2528|GENERAL|Vacuum reservoir pressure sensor circuit high
P2529|GENERAL|Vacuum reservoir pressure sensor circuit intermittent
P252A|ENGINE_OIL|Engine oil quality sensor circuit
P252B|ENGINE_OIL|Engine oil quality sensor circuit range/performance
P252C|ENGINE_OIL|Engine oil quality sensor circuit low
P252D|ENGINE_OIL|Engine oil quality sensor circuit high
P252E|ENGINE_OIL|Engine oil quality sensor circuit intermittent/erratic
P252F|ENGINE_OIL|Engine oil level too high
P2530|GENERAL|Ignition switch run position circuit
P2531|GENERAL|Ignition switch run position circuit low
P2532|GENERAL|Ignition switch run position circuit high
P2533|GENERAL|Ignition switch on/start position circuit
P2534|GENERAL|Ignition switch on/start position circuit low
P2535|GENERAL|Ignition switch on/start position circuit high
P2536|GENERAL|Ignition switch accessory position circuit
P2537|GENERAL|Ignition switch accessory position circuit low
P2538|GENERAL|Ignition switch accessory position circuit high
P2539|FUEL_PRESSURE|Low pressure fuel system sensor circuit
P253A|GENERAL|PTO sense circuit/open
P253B|GENERAL|PTO sense circuit range/performance
P253C|GENERAL|PTO sense circuit low
P253D|GENERAL|PTO sense circuit high
P253E|GENERAL|PTO sense circuit intermittent/erratic
P253F|ENGINE_OIL|Engine oil deteriorated
P2540|FUEL_PRESSURE|Low pressure fuel system sensor circuit range/performance
P2541|FUEL_PRESSURE|Low pressure fuel system sensor circuit low
P2542|FUEL_PRESSURE|Low pressure fuel system sensor circuit high
P2543|FUEL_PRESSURE|Low pressure fuel system sensor circuit intermittent
P2544|GENERAL|Torque management request input signal A
P2545|GENERAL|Torque management request input signal A range/performance
P2546|GENERAL|Torque management request input signal A low
P2547|GENERAL|Torque management request input signal A high
P2548|GENERAL|Torque management request input signal B
P2549|GENERAL|Torque management request input signal B range/performance
P254A|GENERAL|PTO speed selector sensor/switch 1 circuit/open
P254B|GENERAL|PTO speed selector sensor/switch 1 range/performance
P254C|GENERAL|PTO speed selector sensor/switch 1 circuit low
P254D|GENERAL|PTO speed selector sensor/switch 1 circuit high
P254E|GENERAL|PTO speed selector sensor/switch 1 circuit intermittent/erratic
P254F|GENERAL|Engine hood switch circuit
P2550|GENERAL|Torque management request input signal B low
P2551|GENERAL|Torque management request input signal B high
P2552|GENERAL|Throttle/fuel inhibit A circuit
P2553|GENERAL|Throttle/fuel inhibit A circuit range/performance
P2554|GENERAL|Throttle/fuel inhibit A circuit low
P2555|GENERAL|Throttle/fuel inhibit A circuit high
P2556|COOLING|Engine coolant level sensor/switch circuit
P2557|COOLING|Engine coolant level sensor/switch circuit range/performance
P2558|COOLING|Engine coolant level sensor/switch circuit low
P2559|COOLING|Engine coolant level sensor/switch circuit high
P255A|GENERAL|PTO speed selector sensor/switch 2 circuit/open
P255B|GENERAL|PTO speed selector sensor/switch 2 range/performance
P255C|GENERAL|PTO speed selector sensor/switch 2 circuit low
P255D|GENERAL|PTO speed selector sensor/switch 2 circuit high
P255E|GENERAL|PTO speed selector sensor/switch 2 circuit intermittent/erratic
P2560|COOLING|Engine coolant level low
P2561|AIRCON|A/C control module requested MIL illumination
P2562|TURBO|Turbocharger boost control position sensor A circuit
P2563|TURBO|Turbocharger boost control position sensor A circuit range/performance
P2564|TURBO|Turbocharger boost control position sensor A circuit low
P2565|TURBO|Turbocharger boost control position sensor A circuit high
P2566|TURBO|Turbocharger boost control position sensor A circuit intermittent
P2567|GENERAL|Direct ozone reduction catalyst temperature sensor circuit
P2568|GENERAL|Direct ozone reduction catalyst temperature sensor circuit range/performance
P2569|GENERAL|Direct ozone reduction catalyst temperature sensor circuit low
P256A|IDLE|Engine idle speed selector sensor/switch circuit/open
P256B|IDLE|Engine idle speed selector sensor/switch range/performance
P256C|IDLE|Engine idle speed selector sensor/switch circuit low
P256D|IDLE|Engine idle speed selector sensor/switch circuit high
P256E|IDLE|Engine idle speed selector sensor/switch circuit intermittent/erratic
P2570|GENERAL|Direct ozone reduction catalyst temperature sensor circuit high
P2571|GENERAL|Direct ozone reduction catalyst temperature sensor circuit intermittent/erratic
P2572|GENERAL|Direct ozone reduction catalyst deterioration sensor circuit
P2573|GENERAL|Direct ozone reduction catalyst deterioration sensor circuit range/performance
P2574|GENERAL|Direct ozone reduction catalyst deterioration sensor circuit low
P2575|GENERAL|Direct ozone reduction catalyst deterioration sensor circuit high
P2576|GENERAL|Direct ozone reduction catalyst deterioration sensor circuit intermittent/erratic
P2577|GENERAL|Direct ozone reduction catalyst efficiency below threshold
P2578|TURBO|Turbocharger/supercharger speed sensor A circuit
P2579|TURBO|Turbocharger/supercharger speed sensor A circuit range/performance
P257A|GENERAL|Vacuum reservoir control circuit/open
P257B|GENERAL|Vacuum reservoir control circuit low
P257C|GENERAL|Vacuum reservoir control circuit high
P257D|GENERAL|Engine hood switch circuit range/performance
P257E|GENERAL|Engine hood switch circuit low
P257F|GENERAL|Engine hood switch circuit high
P2580|TURBO|Turbocharger/supercharger speed sensor A circuit low
P2581|TURBO|Turbocharger/supercharger speed sensor A circuit high
P2582|TURBO|Turbocharger/supercharger speed sensor A circuit intermittent
P2583|CRUISE|Cruise control front distance range sensor single sensor or center
P2584|GENERAL|Fuel additive control module requested MIL illumination
P2585|GENERAL|Fuel additive control module warning lamp request
P2586|TURBO|Turbocharger boost control position sensor B circuit
P2587|TURBO|Turbocharger boost control position sensor B circuit range/performance
P2588|TURBO|Turbocharger boost control position sensor B circuit low
P2589|TURBO|Turbocharger boost control position sensor B circuit high
P2590|TURBO|Turbocharger boost control position sensor B circuit intermittent/erratic
P2600|COOLING|Coolant pump A control circuit/open
P2601|COOLING|Coolant pump A control circuit performance/stuck off
P2602|COOLING|Coolant pump A control circuit low
P2603|COOLING|Coolant pump A control circuit high
P2604|GLOW_PLUG|Intake air heater A circuit range/performance
P2605|GLOW_PLUG|Intake air heater A circuit/open
P2606|GLOW_PLUG|Intake air heater B circuit range/performance
P2607|GLOW_PLUG|Intake air heater B circuit low
P2608|GLOW_PLUG|Intake air heater B circuit high
P2609|GLOW_PLUG|Intake air heater system performance
P260A|GENERAL|PTO control circuit/open
P260B|GENERAL|PTO control circuit low
P260C|GENERAL|PTO control circuit high
P260D|GENERAL|PTO engaged lamp control circuit
P260F|CONTROL_MODULE|EVAP system monitoring processor performance
P2610|CONTROL_MODULE|ECM/PCM engine off timer performance
P2611|AIRCON|A/C refrigerant distribution valve A control circuit/open
P2612|AIRCON|A/C refrigerant distribution valve A control circuit low
P2613|AIRCON|A/C refrigerant distribution valve A control circuit high
P2614|POSITION_SENSOR|Camshaft A position signal output circuit/open (bank 1)
P2615|POSITION_SENSOR|Camshaft A position signal output circuit low (bank 1)
P2616|POSITION_SENSOR|Camshaft A position signal output circuit high (bank 1)
P2617|POSITION_SENSOR|Crankshaft position signal output circuit/open
P2618|POSITION_SENSOR|Crankshaft position signal output circuit low
P2619|POSITION_SENSOR|Crankshaft position signal output circuit high
P2620|THROTTLE_SENSOR|Throttle position output circuit/open
P2621|THROTTLE_SENSOR|Throttle position output circuit low
P2622|THROTTLE_SENSOR|Throttle position output circuit high
P2623|INJECTOR|Injector control pressure regulator circuit/open
P2624|INJECTOR|Injector control pressure regulator circuit low
P2625|INJECTOR|Injector control pressure regulator circuit high
P2626|O2|O2 sensor pumping current trim circuit/open (bank 1 sensor 1)
P2627|O2|O2 sensor pumping current trim circuit low (bank 1 sensor 1)
P2628|O2|O2 sensor pumping current trim circuit high (bank 1 sensor 1)
P2629|O2|O2 sensor pumping current trim circuit/open (bank 2 sensor 1)
P2630|O2|O2 sensor pumping current trim circuit low (bank 2 sensor 1)
P2631|O2|O2 sensor pumping current trim circuit high (bank 2 sensor 1)
P2632|FUEL_PRESSURE|Fuel pump B control circuit /open
P2633|FUEL_PRESSURE|Fuel pump B control circuit low
P2634|FUEL_PRESSURE|Fuel pump B control circuit high
P2635|FUEL_PRESSURE|Fuel pump A low flow/performance
P2636|FUEL_PRESSURE|Fuel pump B low flow/performance
P2637|GENERAL|Torque management feedback signal A
P2638|GENERAL|Torque management feedback signal A range/performance
P2639|GENERAL|Torque management feedback signal A low
P2640|GENERAL|Torque management feedback signal A high
P2641|GENERAL|Torque management feedback signal B
P2642|GENERAL|Torque management feedback signal B range/performance
P2643|GENERAL|Torque management feedback signal B low
P2644|GENERAL|Torque management feedback signal B high
P2645|VALVE_LIFT|Rocker arm A actuator control circuit/open (bank 1)
P2646|VALVE_LIFT|Rocker arm A actuator system performance/stuck off (bank 1)
P2647|VALVE_LIFT|Rocker arm A actuator system stuck on (bank 1)
P2648|VALVE_LIFT|Rocker arm A actuator control circuit low (bank 1)
P2649|VALVE_LIFT|Rocker arm A actuator control circuit high (bank 1)
P264A|VALVE_LIFT|Rocker arm A actuator position sensor circuit (bank 1)
P264B|VALVE_LIFT|Rocker arm A actuator position sensor circuit range/performance (bank 1)
P264C|VALVE_LIFT|Rocker arm A actuator position sensor circuit low (bank 1)
P264D|VALVE_LIFT|Rocker arm A actuator position sensor circuit high (bank 1)
P264E|VALVE_LIFT|Rocker arm A actuator position sensor circuit intermittent/erratic (bank 1)
P2650|VALVE_LIFT|Rocker arm B actuator control circuit/open (bank 1)
P2651|VALVE_LIFT|Rocker arm B actuator system performance/stuck off (bank 1)
P2652|VALVE_LIFT|Rocker arm B actuator system stuck on (bank 1)
P2653|VALVE_LIFT|Rocker arm B actuator control circuit low (bank 1)
P2654|VALVE_LIFT|Rocker arm B actuator control circuit high (bank 1)
P2655|VALVE_LIFT|Rocker arm A actuator control circuit/open (bank 2)
P2656|VALVE_LIFT|Rocker arm A actuator system performance/stuck off (bank 2)
P2657|VALVE_LIFT|Rocker arm A actuator system stuck on (bank 2)
P2658|VALVE_LIFT|Rocker arm A actuator control circuit low (bank 2)
P2659|VALVE_LIFT|Rocker arm A actuator control circuit high (bank 2)
P265A|VALVE_LIFT|Rocker arm B actuator position sensor circuit (bank 1)
P265B|VALVE_LIFT|Rocker arm B actuator position sensor circuit range/performance (bank 1)
P265C|VALVE_LIFT|Rocker arm B actuator position sensor circuit low (bank 1)
P265D|VALVE_LIFT|Rocker arm B actuator position sensor circuit high (bank 1)
P265E|VALVE_LIFT|Rocker arm B actuator position sensor circuit intermittent/erratic (bank 1)
P2660|VALVE_LIFT|Rocker arm B actuator control circuit/open (bank 2)
P2661|VALVE_LIFT|Rocker arm B actuator system performance/stuck off (bank 2)
P2662|VALVE_LIFT|Rocker arm B actuator system stuck on (bank 2)
P2663|VALVE_LIFT|Rocker arm B actuator control circuit low (bank 2)
P2664|VALVE_LIFT|Rocker arm B actuator control circuit high (bank 2)
P2665|FUEL_PRESSURE|Fuel shutoff valve B control circuit/open
P2666|FUEL_PRESSURE|Fuel shutoff valve B control circuit low
P2667|FUEL_PRESSURE|Fuel shutoff valve B control circuit high
P2668|GENERAL|Fuel mode indicator lamp control circuit
P2669|SENSOR_SUPPLY|Actuator supply voltage B circuit /open
P266A|VALVE_LIFT|Rocker arm A actuator position sensor circuit (bank 2)
P266B|VALVE_LIFT|Rocker arm A actuator position sensor circuit range/performance (bank 2)
P266C|VALVE_LIFT|Rocker arm A actuator position sensor circuit low (bank 2)
P266D|VALVE_LIFT|Rocker arm A actuator position sensor circuit high (bank 2)
P266E|VALVE_LIFT|Rocker arm A actuator position sensor circuit intermittent/erratic (bank 2)
P2670|SENSOR_SUPPLY|Actuator supply voltage B circuit low
P2671|SENSOR_SUPPLY|Actuator supply voltage B circuit high
P2672|INJECTOR|Injection pump timing offset
P2673|INJECTOR|Injection pump timing calibration not learned
P2674|INJECTOR|Injection pump fuel calibration not learned
P2675|GENERAL|Air cleaner inlet control circuit/open
P2676|GENERAL|Air cleaner inlet control circuit low
P2677|GENERAL|Air cleaner inlet control circuit high
P2678|COOLING|Coolant degassing valve control circuit/open
P2679|COOLING|Coolant degassing valve control circuit low
P267A|VALVE_LIFT|Rocker arm B actuator position sensor circuit (bank 2)
P267B|VALVE_LIFT|Rocker arm B actuator position sensor circuit range/performance (bank 2)
P267C|VALVE_LIFT|Rocker arm B actuator position sensor circuit low (bank 2)
P267D|VALVE_LIFT|Rocker arm B actuator position sensor circuit high (bank 2)
P267E|VALVE_LIFT|Rocker arm B actuator position sensor circuit intermittent/erratic (bank 2)
P2680|COOLING|Coolant degassing valve control circuit high
P2681|COOLING|Engine coolant bypass valve A control circuit/open
P2682|COOLING|Engine coolant bypass valve A control circuit low
P2683|COOLING|Engine coolant bypass valve A control circuit high
P2687|GENERAL|Fuel supply heater control circuit/open
P2688|GENERAL|Fuel supply heater control circuit low
P2689|GENERAL|Fuel supply heater control circuit high
P2700|TRANS_SENSOR|Transmission friction element A apply time range/performance
P2701|TRANS_SENSOR|Transmission friction element B apply time range/performance
P2702|TRANS_SENSOR|Transmission friction element C apply time range/performance
P2703|TRANS_SENSOR|Transmission friction element D apply time range/performance
P2704|TRANS_SENSOR|Transmission friction element E apply time range/performance
P2705|TRANS_SENSOR|Transmission friction element F apply time range/performance
P2706|TRANS_SOLENOID|Shift solenoid F
P2707|TRANS_SOLENOID|Shift solenoid F performance/stuck off
P2708|TRANS_SOLENOID|Shift solenoid F stuck on
P2709|TRANS_SOLENOID|Shift solenoid F electrical
P2710|TRANS_SOLENOID|Shift solenoid F intermittent
P2711|TRANS_GENERAL|Unexpected mechanical gear disengagement
P2712|TRANS_GENERAL|Hydraulic power unit leakage
P2713|TRANS_SOLENOID|Pressure control solenoid D
P2714|TRANS_SOLENOID|Pressure control solenoid D performance/stuck off
P2715|TRANS_SOLENOID|Pressure control solenoid D stuck on
P2716|TRANS_SOLENOID|Pressure control solenoid D electrical
P2717|TRANS_SOLENOID|Pressure control solenoid D intermittent
P2718|TRANS_SOLENOID|Pressure control solenoid D control circuit/open
P2719|TRANS_SOLENOID|Pressure control solenoid D control circuit range/performance
P2720|TRANS_SOLENOID|Pressure control solenoid D control circuit low
P2721|TRANS_SOLENOID|Pressure control solenoid D control circuit high
P2722|TRANS_SOLENOID|Pressure control solenoid E
P2723|TRANS_SOLENOID|Pressure control solenoid E performance/stuck off
P2724|TRANS_SOLENOID|Pressure control solenoid E stuck on
P2725|TRANS_SOLENOID|Pressure control solenoid E electrical
P2726|TRANS_SOLENOID|Pressure control solenoid E intermittent
P2727|TRANS_SOLENOID|Pressure control solenoid E control circuit/open
P2728|TRANS_SOLENOID|Pressure control solenoid E control circuit range/performance
P2729|TRANS_SOLENOID|Pressure control solenoid E control circuit low
P2730|TRANS_SOLENOID|Pressure control solenoid E control circuit high
P2731|TRANS_SOLENOID|Pressure control solenoid F
P2732|TRANS_SOLENOID|Pressure control solenoid F performance/stuck off
P2733|TRANS_SOLENOID|Pressure control solenoid F stuck on
P2734|TRANS_SOLENOID|Pressure control solenoid F electrical
P2735|TRANS_SOLENOID|Pressure control solenoid F intermittent
P2736|TRANS_SOLENOID|Pressure control solenoid F control circuit/open
P2737|TRANS_SOLENOID|Pressure control solenoid F control circuit range/performance
P2738|TRANS_SOLENOID|Pressure control solenoid F control circuit low
P2739|TRANS_SOLENOID|Pressure control solenoid F control circuit high
P273A|TRANS_SENSOR|Transmission friction element G apply time range/performance
P273B|TRANS_SENSOR|Transmission friction element H apply time range/performance
P2740|TRANS_SENSOR|Transmission fluid temperature sensor B circuit
P2741|TRANS_SENSOR|Transmission fluid temperature sensor B circuit range/performance
P2742|TRANS_SENSOR|Transmission fluid temperature sensor B circuit low
P2743|TRANS_SENSOR|Transmission fluid temperature sensor B circuit high
P2744|TRANS_SENSOR|Transmission fluid temperature sensor B circuit intermittent
P2745|TRANS_SENSOR|Intermediate shaft speed sensor B circuit
P2746|TRANS_SENSOR|Intermediate shaft speed sensor B circuit range/performance
P2747|TRANS_SENSOR|Intermediate shaft speed sensor B circuit no signal
P2748|TRANS_SENSOR|Intermediate shaft speed sensor B circuit intermittent
P2749|TRANS_SENSOR|Intermediate shaft speed sensor C circuit
P2750|TRANS_SENSOR|Intermediate shaft speed sensor C circuit range/performance
P2751|TRANS_SENSOR|Intermediate shaft speed sensor C circuit no signal
P2752|TRANS_SENSOR|Intermediate shaft speed sensor C circuit intermittent
P2753|TRANS_SENSOR|Transmission fluid cooler control circuit/open
P2754|TRANS_SENSOR|Transmission fluid cooler control circuit low
P2755|TRANS_SENSOR|Transmission fluid cooler control circuit high
P2756|TRANS_SOLENOID|Torque converter clutch pressure control solenoid
P2757|TRANS_SOLENOID|Torque converter clutch pressure control solenoid control circuit performance/stuck off
P2758|TRANS_SOLENOID|Torque converter clutch pressure control solenoid control circuit stuck on
P2759|TRANS_SOLENOID|Torque converter clutch pressure control solenoid control circuit electrical
"""

    private const val C3 = """
P2760|TRANS_SOLENOID|Torque converter clutch pressure control solenoid control circuit intermittent
P2761|TRANS_SOLENOID|Torque converter clutch pressure control solenoid control circuit/open
P2762|TRANS_SOLENOID|Torque converter clutch pressure control solenoid control circuit range/performance
P2763|TRANS_SOLENOID|Torque converter clutch pressure control solenoid control circuit high
P2764|TRANS_SOLENOID|Torque converter clutch pressure control solenoid control circuit low
P2765|TRANS_SENSOR|Input/turbine shaft speed sensor B circuit
P2766|TRANS_SENSOR|Input/turbine shaft speed sensor B circuit range/performance
P2767|TRANS_SENSOR|Input/turbine shaft speed sensor B circuit no signal
P2768|TRANS_SENSOR|Input/turbine shaft speed sensor B circuit intermittent
P2769|TRANS_SOLENOID|Torque converter clutch circuit low
P2770|TRANS_SOLENOID|Torque converter clutch circuit high
P2771|TRANS_GENERAL|Four wheel drive (4WD) low switch circuit
P2772|TRANS_GENERAL|Four wheel drive (4WD) low switch circuit range/performance
P2773|TRANS_GENERAL|Four wheel drive (4WD) low switch circuit low
P2774|TRANS_GENERAL|Four wheel drive (4WD) low switch circuit high
P2775|TRANS_GENERAL|Upshift switch circuit range/performance
P2776|TRANS_GENERAL|Upshift switch circuit low
P2777|TRANS_GENERAL|Upshift switch circuit high
P2778|TRANS_GENERAL|Upshift switch circuit intermittent/erratic
P2779|TRANS_GENERAL|Downshift switch circuit range/performance
P2780|TRANS_GENERAL|Downshift switch circuit low
P2781|TRANS_GENERAL|Downshift switch circuit high
P2782|TRANS_GENERAL|Downshift switch circuit intermittent/erratic
P2783|TRANS_SOLENOID|Torque converter temperature too high
P2784|TRANS_SENSOR|Input/turbine speed sensor A/B correlation
P2785|TRANS_GENERAL|Clutch actuator temperature too high
P2786|TRANS_GENERAL|Gear shift actuator temperature too high
P2787|TRANS_GENERAL|Clutch A temperature too high
P2788|TRANS_GENERAL|Auto shift manual adaptive learning at limit
P2789|TRANS_GENERAL|Clutch A adaptive learning at limit
P278A|TRANS_GENERAL|Kick down switch circuit
P278B|TRANS_GENERAL|Kick down switch circuit range/performance
P278C|TRANS_GENERAL|Kick down switch circuit low
P278D|TRANS_GENERAL|Kick down switch circuit high
P278E|TRANS_GENERAL|Kick down switch circuit intermittent/erratic
P2790|TRANS_GENERAL|Gate select direction circuit
P2791|TRANS_GENERAL|Gate select direction circuit low
P2792|TRANS_GENERAL|Gate select direction circuit high
P2793|TRANS_GENERAL|Gear shift direction circuit
P2794|TRANS_GENERAL|Gear shift direction circuit low
P2795|TRANS_GENERAL|Gear shift direction circuit high
P2796|TRANS_GENERAL|Electric/auxiliary transmission fluid pump A control circuit/open
P2797|TRANS_GENERAL|Electric/auxiliary transmission fluid pump A performance/stuck off
P2798|TRANS_GENERAL|Electric/auxiliary transmission fluid pump A control circuit low
P2799|TRANS_GENERAL|Electric/auxiliary transmission fluid pump A control circuit high
P2800|TRANS_SENSOR|Transmission range sensor B circuit (PRNDL input)
P2801|TRANS_SENSOR|Transmission range sensor B circuit range/performance
P2802|TRANS_SENSOR|Transmission range sensor B circuit low
P2803|TRANS_SENSOR|Transmission range sensor B circuit high
P2804|TRANS_SENSOR|Transmission range sensor B circuit intermittent
P2805|TRANS_SENSOR|Transmission range sensor A/B correlation
P2806|TRANS_SENSOR|Transmission range sensor alignment
P2807|TRANS_SOLENOID|Pressure control solenoid G
P2808|TRANS_SOLENOID|Pressure control solenoid G performance/stuck off
P2809|TRANS_SOLENOID|Pressure control solenoid G stuck on
P2810|TRANS_SOLENOID|Pressure control solenoid G electrical
P2811|TRANS_SOLENOID|Pressure control solenoid G intermittent
P2812|TRANS_SOLENOID|Pressure control solenoid G control circuit/open
P2813|TRANS_SOLENOID|Pressure control solenoid G control circuit range/performance
P2814|TRANS_SOLENOID|Pressure control solenoid G control circuit low
P2815|TRANS_SOLENOID|Pressure control solenoid G control circuit high
P2816|TRANS_SOLENOID|Pressure control solenoid H
P2817|TRANS_SOLENOID|Pressure control solenoid H performance/stuck off
P2818|TRANS_SOLENOID|Pressure control solenoid H stuck on
P2819|TRANS_SOLENOID|Pressure control solenoid H electrical
P281A|TRANS_SOLENOID|Pressure control solenoid H intermittent
P281B|TRANS_SOLENOID|Pressure control solenoid H control circuit/open
P281C|TRANS_SOLENOID|Pressure control solenoid H control circuit range/performance
P281D|TRANS_SOLENOID|Pressure control solenoid H control circuit low
P281E|TRANS_SOLENOID|Pressure control solenoid H control circuit high
P281F|TRANS_SOLENOID|Pressure control solenoid J
P2820|TRANS_SOLENOID|Pressure control solenoid J performance/stuck off
P2821|TRANS_SOLENOID|Pressure control solenoid J stuck on
P2822|TRANS_SOLENOID|Pressure control solenoid J electrical
P2823|TRANS_SOLENOID|Pressure control solenoid J intermittent
P2824|TRANS_SOLENOID|Pressure control solenoid J control circuit/open
P2825|TRANS_SOLENOID|Pressure control solenoid J control circuit range/performance
P2826|TRANS_SOLENOID|Pressure control solenoid J control circuit low
P2827|TRANS_SOLENOID|Pressure control solenoid J control circuit high
P2828|TRANS_SOLENOID|Pressure control solenoid K
P2829|TRANS_SOLENOID|Pressure control solenoid K performance/stuck off
P282A|TRANS_SOLENOID|Pressure control solenoid K stuck on
P282B|TRANS_SOLENOID|Pressure control solenoid K electrical
P282C|TRANS_SOLENOID|Pressure control solenoid K intermittent
P282D|TRANS_SOLENOID|Pressure control solenoid K control circuit/open
P282E|TRANS_SOLENOID|Pressure control solenoid K control circuit range/performance
P282F|TRANS_SOLENOID|Pressure control solenoid K control circuit low
P2830|TRANS_SOLENOID|Pressure control solenoid K control circuit high
P2A01|O2|O2 sensor circuit range/performance (bank 1 sensor 2)
P2A02|O2|O2 sensor circuit range/performance (bank 1 sensor 3)
P2A04|O2|O2 sensor circuit range/performance (bank 2 sensor 2)
P2A05|O2|O2 sensor circuit range/performance (bank 2 sensor 3)
P2A06|O2|O2 sensor negative voltage (bank 1 sensor 1)
P2A07|O2|O2 sensor negative voltage (bank 1 sensor 2)
P2A08|O2|O2 sensor negative voltage (bank 1 sensor 3)
P2A09|O2|O2 sensor negative voltage (bank 2 sensor 1)
P2A10|O2|O2 sensor negative voltage (bank 2 sensor 2)
P2A11|O2|O2 sensor negative voltage (bank 2 sensor 3)
P3400|CYL_DEACT|Cylinder deactivation system (bank 1)
P3401|CYL_DEACT|Cylinder 1 deactivation/intake valve control circuit/open
P3402|CYL_DEACT|Cylinder 1 deactivation/intake valve control circuit performance
P3403|CYL_DEACT|Cylinder 1 deactivation/intake valve control circuit low
P3404|CYL_DEACT|Cylinder 1 deactivation/intake valve control circuit high
P3405|CYL_DEACT|Cylinder 1 exhaust valve control circuit/open
P3406|CYL_DEACT|Cylinder 1 exhaust valve control circuit performance
P3407|CYL_DEACT|Cylinder 1 exhaust valve control circuit low
P3408|CYL_DEACT|Cylinder 1 exhaust valve control circuit high
P3409|CYL_DEACT|Cylinder 2 deactivation/intake valve control circuit/open
P3410|CYL_DEACT|Cylinder 2 deactivation/intake valve control circuit performance
P3411|CYL_DEACT|Cylinder 2 deactivation/intake valve control circuit low
P3412|CYL_DEACT|Cylinder 2 deactivation/intake valve control circuit high
P3413|CYL_DEACT|Cylinder 2 exhaust valve control circuit/open
P3414|CYL_DEACT|Cylinder 2 exhaust valve control circuit performance
P3415|CYL_DEACT|Cylinder 2 exhaust valve control circuit low
P3416|CYL_DEACT|Cylinder 2 exhaust valve control circuit high
P3417|CYL_DEACT|Cylinder 3 deactivation/intake valve control circuit/open
P3418|CYL_DEACT|Cylinder 3 deactivation/intake valve control circuit performance
P3419|CYL_DEACT|Cylinder 3 deactivation/intake valve control circuit low
P3420|CYL_DEACT|Cylinder 3 deactivation/intake valve control circuit high
P3421|CYL_DEACT|Cylinder 3 exhaust valve control circuit/open
P3422|CYL_DEACT|Cylinder 3 exhaust valve control circuit performance
P3423|CYL_DEACT|Cylinder 3 exhaust valve control circuit low
P3424|CYL_DEACT|Cylinder 3 exhaust valve control circuit high
P3425|CYL_DEACT|Cylinder 4 deactivation/intake valve control circuit/open
P3426|CYL_DEACT|Cylinder 4 deactivation/intake valve control circuit performance
P3427|CYL_DEACT|Cylinder 4 deactivation/intake valve control circuit low
P3428|CYL_DEACT|Cylinder 4 deactivation/intake valve control circuit high
P3429|CYL_DEACT|Cylinder 4 exhaust valve control circuit/open
P3430|CYL_DEACT|Cylinder 4 exhaust valve control circuit performance
P3431|CYL_DEACT|Cylinder 4 exhaust valve control circuit low
P3432|CYL_DEACT|Cylinder 4 exhaust valve control circuit high
P3433|CYL_DEACT|Cylinder 5 deactivation/intake valve control circuit/open
P3434|CYL_DEACT|Cylinder 5 deactivation/intake valve control circuit performance
P3435|CYL_DEACT|Cylinder 5 deactivation/intake valve control circuit low
P3436|CYL_DEACT|Cylinder 5 deactivation/intake valve control circuit high
P3437|CYL_DEACT|Cylinder 5 exhaust valve control circuit/open
P3438|CYL_DEACT|Cylinder 5 exhaust valve control circuit performance
P3439|CYL_DEACT|Cylinder 5 exhaust valve control circuit low
P3440|CYL_DEACT|Cylinder 5 exhaust valve control circuit high
P3441|CYL_DEACT|Cylinder 6 deactivation/intake valve control circuit/open
P3442|CYL_DEACT|Cylinder 6 deactivation/intake valve control circuit performance
P3443|CYL_DEACT|Cylinder 6 deactivation/intake valve control circuit low
P3444|CYL_DEACT|Cylinder 6 deactivation/intake valve control circuit high
P3445|CYL_DEACT|Cylinder 6 exhaust valve control circuit/open
P3446|CYL_DEACT|Cylinder 6 exhaust valve control circuit performance
P3447|CYL_DEACT|Cylinder 6 exhaust valve control circuit low
P3448|CYL_DEACT|Cylinder 6 exhaust valve control circuit high
P3449|CYL_DEACT|Cylinder 7 deactivation/intake valve control circuit/open
P3450|CYL_DEACT|Cylinder 7 deactivation/intake valve control circuit performance
P3451|CYL_DEACT|Cylinder 7 deactivation/intake valve control circuit low
P3452|CYL_DEACT|Cylinder 7 deactivation/intake valve control circuit high
P3453|CYL_DEACT|Cylinder 7 exhaust valve control circuit/open
P3454|CYL_DEACT|Cylinder 7 exhaust valve control circuit performance
P3455|CYL_DEACT|Cylinder 7 exhaust valve control circuit low
P3456|CYL_DEACT|Cylinder 7 exhaust valve control circuit high
P3457|CYL_DEACT|Cylinder 8 deactivation/intake valve control circuit/open
P3458|CYL_DEACT|Cylinder 8 deactivation/intake valve control circuit performance
P3459|CYL_DEACT|Cylinder 8 deactivation/intake valve control circuit low
P3460|CYL_DEACT|Cylinder 8 deactivation/intake valve control circuit high
P3461|CYL_DEACT|Cylinder 8 exhaust valve control circuit/open
P3462|CYL_DEACT|Cylinder 8 exhaust valve control circuit performance
P3463|CYL_DEACT|Cylinder 8 exhaust valve control circuit low
P3464|CYL_DEACT|Cylinder 8 exhaust valve control circuit high
P3465|CYL_DEACT|Cylinder 9 deactivation/intake valve control circuit/open
P3466|CYL_DEACT|Cylinder 9 deactivation/intake valve control circuit performance
P3467|CYL_DEACT|Cylinder 9 deactivation/intake valve control circuit low
P3468|CYL_DEACT|Cylinder 9 deactivation/intake valve control circuit high
P3469|CYL_DEACT|Cylinder 9 exhaust valve control circuit/open
P3470|CYL_DEACT|Cylinder 9 exhaust valve control circuit performance
P3471|CYL_DEACT|Cylinder 9 exhaust valve control circuit low
P3472|CYL_DEACT|Cylinder 9 exhaust valve control circuit high
P3473|CYL_DEACT|Cylinder 10 deactivation/intake valve control circuit/open
P3474|CYL_DEACT|Cylinder 10 deactivation/intake valve control circuit performance
P3475|CYL_DEACT|Cylinder 10 deactivation/intake valve control circuit low
P3476|CYL_DEACT|Cylinder 10 deactivation/intake valve control circuit high
P3477|CYL_DEACT|Cylinder 10 exhaust valve control circuit/open
P3478|CYL_DEACT|Cylinder 10 exhaust valve control circuit performance
P3479|CYL_DEACT|Cylinder 10 exhaust valve control circuit low
P3480|CYL_DEACT|Cylinder 10 exhaust valve control circuit high
P3481|CYL_DEACT|Cylinder 11 deactivation/intake valve control circuit/open
P3482|CYL_DEACT|Cylinder 11 deactivation/intake valve control circuit performance
P3483|CYL_DEACT|Cylinder 11 deactivation/intake valve control circuit low
P3484|CYL_DEACT|Cylinder 11 deactivation/intake valve control circuit high
P3485|CYL_DEACT|Cylinder 11 exhaust valve control circuit/open
P3486|CYL_DEACT|Cylinder 11 exhaust valve control circuit performance
P3487|CYL_DEACT|Cylinder 11 exhaust valve control circuit low
P3488|CYL_DEACT|Cylinder 11 exhaust valve control circuit high
P3489|CYL_DEACT|Cylinder 12 deactivation/intake valve control circuit/open
P3490|CYL_DEACT|Cylinder 12 deactivation/intake valve control circuit performance
P3491|CYL_DEACT|Cylinder 12 deactivation/intake valve control circuit low
P3492|CYL_DEACT|Cylinder 12 deactivation/intake valve control circuit high
P3493|CYL_DEACT|Cylinder 12 exhaust valve control circuit/open
P3494|CYL_DEACT|Cylinder 12 exhaust valve control circuit performance
P3495|CYL_DEACT|Cylinder 12 exhaust valve control circuit low
P3496|CYL_DEACT|Cylinder 12 exhaust valve control circuit high
P3497|CYL_DEACT|Cylinder deactivation system (bank 2)
U0002|NETWORK|High speed CAN communication bus performance
U0003|NETWORK|High speed CAN communication bus (+) open
U0004|NETWORK|High speed CAN communication bus (+) low
U0005|NETWORK|High speed CAN communication bus (+) high
U0006|NETWORK|High speed CAN communication bus (-) open
U0007|NETWORK|High speed CAN communication bus (-) low
U0008|NETWORK|High speed CAN communication bus (-) high
U0009|NETWORK|High speed CAN communication bus (-) shorted to bus (+)
U0010|NETWORK|Medium speed CAN communication bus
U0011|NETWORK|Medium speed CAN communication bus performance
U0012|NETWORK|Medium speed CAN communication bus (+) open
U0013|NETWORK|Medium speed CAN communication bus (+) low
U0014|NETWORK|Medium speed CAN communication bus (+) high
U0015|NETWORK|Medium speed CAN communication bus (-) open
U0016|NETWORK|Medium speed CAN communication bus (-) low
U0017|NETWORK|Medium speed CAN communication bus (-) high
U0018|NETWORK|Medium speed CAN communication bus (-) shorted to bus (+)
U0019|NETWORK|Low speed CAN communication bus
U0020|NETWORK|Low speed CAN communication bus performance
U0021|NETWORK|Low speed CAN communication bus (+) open
U0022|NETWORK|Low speed CAN communication bus (+) low
U0023|NETWORK|Low speed CAN communication bus (+) high
U0024|NETWORK|Low speed CAN communication bus (-) open
U0025|NETWORK|Low speed CAN communication bus (-) low
U0026|NETWORK|Low speed CAN communication bus (-) high
U0027|NETWORK|Low speed CAN communication bus (-) shorted to bus (+)
U0028|NETWORK|Vehicle communication bus A
U0029|NETWORK|Vehicle communication bus A performance
U0030|NETWORK|Vehicle communication bus A (+) open
U0031|NETWORK|Vehicle communication bus A (+) low
U0032|NETWORK|Vehicle communication bus A (+) high
U0033|NETWORK|Vehicle communication bus A (-) open
U0034|NETWORK|Vehicle communication bus A (-) low
U0035|NETWORK|Vehicle communication bus A (-) high
U0036|NETWORK|Vehicle communication bus A (-) shorted to bus A (+)
U0037|NETWORK|Vehicle communication bus B
U0038|NETWORK|Vehicle communication bus B performance
U0039|NETWORK|Vehicle communication bus B (+) open
U0040|NETWORK|Vehicle communication bus B (+) low
U0041|NETWORK|Vehicle communication bus B (+) high
U0042|NETWORK|Vehicle communication bus B (-) open
U0043|NETWORK|Vehicle communication bus B (-) low
U0044|NETWORK|Vehicle communication bus B (-) high
U0045|NETWORK|Vehicle communication bus B (-) shorted to bus B (+)
U0046|NETWORK|Vehicle communication bus C
U0047|NETWORK|Vehicle communication bus C performance
U0048|NETWORK|Vehicle communication bus C (+) open
U0049|NETWORK|Vehicle communication bus C (+) low
U0050|NETWORK|Vehicle communication bus C (+) high
U0051|NETWORK|Vehicle communication bus C (-) open
U0052|NETWORK|Vehicle communication bus C (-) low
U0053|NETWORK|Vehicle communication bus C (-) high
U0054|NETWORK|Vehicle communication bus C (-) shorted to bus C (+)
U0055|NETWORK|Vehicle communication bus D
U0056|NETWORK|Vehicle communication bus D performance
U0057|NETWORK|Vehicle communication bus D (+) open
U0058|NETWORK|Vehicle communication bus D (+) low
U0059|NETWORK|Vehicle communication bus D (+) high
U0060|NETWORK|Vehicle communication bus D (-) open
U0061|NETWORK|Vehicle communication bus D (-) low
U0062|NETWORK|Vehicle communication bus D (-) high
U0063|NETWORK|Vehicle communication bus D (-) shorted to bus D (+)
U0064|NETWORK|Vehicle communication bus E
U0065|NETWORK|Vehicle communication bus E performance
U0066|NETWORK|Vehicle communication bus E (+) open
U0067|NETWORK|Vehicle communication bus E (+) low
U0068|NETWORK|Vehicle communication bus E (+) high
U0069|NETWORK|Vehicle communication bus E (-) open
U0070|NETWORK|Vehicle communication bus E (-) low
U0071|NETWORK|Vehicle communication bus E (-) high
U0072|NETWORK|Vehicle communication bus E (-) shorted to bus E (+)
U0074|NETWORK|Control module communication bus B off
U0103|NETWORK|Lost communication with gear shift control module A
U0104|NETWORK|Lost communication with cruise control module
U0105|NETWORK|Lost communication with fuel injector control module
U0106|NETWORK|Lost communication with glow plug control module
U0107|NETWORK|Lost communication with throttle actuator A control module
U0108|NETWORK|Lost communication with alternative fuel control module
U0109|NETWORK|Lost communication with fuel pump control module A
U0110|NETWORK|Lost communication with drive motor control module A
U0111|NETWORK|Lost communication with battery energy control module A
U0112|NETWORK|Lost communication with battery energy control module B
U0113|NETWORK|Lost communication with emissions critical control information
U0114|NETWORK|Lost communication with four-wheel drive clutch control module
U0115|NETWORK|Lost communication with ECM/PCM B
U0116|NETWORK|Lost communication with coolant temperature control module
U0117|NETWORK|Lost communication with PTO control module
U0118|NETWORK|Lost communication with fuel additive control module
U0119|NETWORK|Lost communication with fuel cell control module
U0120|NETWORK|Lost communication with starter/generator control module
U0122|NETWORK|Lost communication with vehicle dynamics control module
U0123|NETWORK|Lost communication with yaw rate sensor module
U0124|NETWORK|Lost communication with lateral acceleration sensor module
U0125|NETWORK|Lost communication with multi-axis acceleration sensor module A
U0127|NETWORK|Lost communication with tire pressure monitor module
U0128|NETWORK|Lost communication with park brake control module
U0129|NETWORK|Lost communication with brake system control module A
U0130|NETWORK|Lost communication with steering effort control module
U0132|NETWORK|Lost communication with ride level control module
U0133|NETWORK|Lost communication with active roll control module
U0134|NETWORK|Lost communication with power steering control module - rear
U0135|NETWORK|Lost communication with differential control module - front
U0136|NETWORK|Lost communication with differential control module - rear
U0137|NETWORK|Lost communication with trailer brake control module
U0138|NETWORK|Lost communication with all terrain control module
U0139|NETWORK|Lost communication with suspension control module B
U0141|NETWORK|Lost communication with body control module A
U0142|NETWORK|Lost communication with body control module B
U0143|NETWORK|Lost communication with body control module C
U0144|NETWORK|Lost communication with body control module D
U0145|NETWORK|Lost communication with body control module E
U0146|NETWORK|Lost communication with gateway A
U0147|NETWORK|Lost communication with gateway B
U0148|NETWORK|Lost communication with gateway C
U0149|NETWORK|Lost communication with gateway D
U0150|NETWORK|Lost communication with gateway E
U0152|NETWORK|Lost communication with side restraints control module - left
U0153|NETWORK|Lost communication with side restraints control module - right
U0154|NETWORK|Lost communication with restraints occupant classification system module
U0156|NETWORK|Lost communication with information center A
U0157|NETWORK|Lost communication with information center B
U0158|NETWORK|Lost communication with head up display
U0159|NETWORK|Lost communication with parking assist control module A
U0160|NETWORK|Lost communication with audible alert control module
U0161|NETWORK|Lost communication with compass module
U0162|NETWORK|Lost communication with navigation display module
U0163|NETWORK|Lost communication with navigation control module
U0165|NETWORK|Lost communication with HVAC control module - rear
U0166|NETWORK|Lost communication with auxiliary heater control module
U0167|NETWORK|Lost communication with vehicle immobilizer control module
U0168|NETWORK|Lost communication with vehicle security control module
U0169|NETWORK|Lost communication with sunroof control module
U0170|NETWORK|Lost communication with restraints system sensor A
U0171|NETWORK|Lost communication with restraints system sensor B
U0172|NETWORK|Lost communication with restraints system sensor C
U0173|NETWORK|Lost communication with restraints system sensor D
U0174|NETWORK|Lost communication with restraints system sensor E
U0175|NETWORK|Lost communication with restraints system sensor F
U0176|NETWORK|Lost communication with restraints system sensor G
U0177|NETWORK|Lost communication with restraints system sensor H
U0178|NETWORK|Lost communication with restraints system sensor i
U0179|NETWORK|Lost communication with restraints system sensor J
U017A|NETWORK|Lost communication with restraints system sensor K
U017B|NETWORK|Lost communication with restraints system sensor L
U017C|NETWORK|Lost communication with restraints system sensor m
U017D|NETWORK|Lost communication with restraints system sensor n
U0180|NETWORK|Lost communication with automatic lighting control module
U0181|NETWORK|Lost communication with headlamp leveling control module A
U0182|NETWORK|Lost communication with lighting control module - front
U0183|NETWORK|Lost communication with lighting control module - rear A
U0185|NETWORK|Lost communication with antenna control module
U0186|NETWORK|Lost communication with audio amplifier A
U0187|NETWORK|Lost communication with digital disc player/changer module A
U0188|NETWORK|Lost communication with digital disc player/changer module B
U0189|NETWORK|Lost communication with digital disc player/changer module C
U0190|NETWORK|Lost communication with digital disc player/changer module D
U0191|NETWORK|Lost communication with television
U0192|NETWORK|Lost communication with personal computer
U0193|NETWORK|Lost communication with digital audio control module A
U0194|NETWORK|Lost communication with digital audio control module B
U0195|NETWORK|Lost communication with subscription entertainment receiver module
U0196|NETWORK|Lost communication with entertainment control module - rear A
U0197|NETWORK|Lost communication with telephone control module
U0198|NETWORK|Lost communication with telematic control module
U0199|NETWORK|Lost communication with door control module A
U0200|NETWORK|Lost communication with door control module B
U0201|NETWORK|Lost communication with door control module C
U0202|NETWORK|Lost communication with door control module D
U0203|NETWORK|Lost communication with door control module E
U0204|NETWORK|Lost communication with door control module F
U0205|NETWORK|Lost communication with door control module G
U0206|NETWORK|Lost communication with folding top control module
U0207|NETWORK|Lost communication with moveable roof control module A
U0208|NETWORK|Lost communication with seat control module A
U0209|NETWORK|Lost communication with seat control module B
U0210|NETWORK|Lost communication with seat control module C
U0211|NETWORK|Lost communication with seat control module D
U0212|NETWORK|Lost communication with steering column control module
U0213|NETWORK|Lost communication with mirror control module A
U0214|NETWORK|Lost communication with remote function actuation module
U0215|NETWORK|Lost communication with door switch A
U0216|NETWORK|Lost communication with door switch B
U0217|NETWORK|Lost communication with door switch C
U0218|NETWORK|Lost communication with door switch D
U0219|NETWORK|Lost communication with door switch E
U0220|NETWORK|Lost communication with door switch F
U0221|NETWORK|Lost communication with door switch G
U0222|NETWORK|Lost communication with door window motor A
U0223|NETWORK|Lost communication with door window motor B
U0224|NETWORK|Lost communication with door window motor C
U0225|NETWORK|Lost communication with door window motor D
U0226|NETWORK|Lost communication with door window motor E
U0227|NETWORK|Lost communication with door window motor F
U0228|NETWORK|Lost communication with door window motor G
U0229|NETWORK|Lost communication with heated steering wheel module
U0230|NETWORK|Lost communication with rear gate module
U0231|NETWORK|Lost communication with rain sensing module
U0232|NETWORK|Lost communication with side obstacle detection control module A
U0233|NETWORK|Lost communication with side obstacle detection control module - right
U0234|NETWORK|Lost communication with convenience recall module
U0235|NETWORK|Lost communication with front distance range sensor - single sensor or center
U0236|NETWORK|Lost communication with column lock module
U0237|NETWORK|Lost communication with digital audio control module C
U0238|NETWORK|Lost communication with digital audio control module D
U0239|NETWORK|Lost communication with entrapment control module A
U0240|NETWORK|Lost communication with entrapment control module B
U0241|NETWORK|Lost communication with headlamp control module A
U0242|NETWORK|Lost communication with headlamp control module B
U0243|NETWORK|Lost communication with parking assist control module B
U0244|NETWORK|Lost communication with running board control module A
U0245|NETWORK|Lost communication with entertainment control module - front
U0246|NETWORK|Lost communication with seat control module E
U0247|NETWORK|Lost communication with seat control module F
U0248|NETWORK|Lost communication with remote accessory module
U0249|NETWORK|Lost communication with entertainment control module - rear B
U0250|NETWORK|Lost communication with impact classification system module
U0251|NETWORK|Lost communication with running board control module B
U0252|NETWORK|Lost communication with lighting control module - rear B
U0291|NETWORK|Lost communication with gear shift control module B
U0292|NETWORK|Lost communication with drive motor control module B
U0293|NETWORK|Lost communication with hybrid/EV powertrain control module
U0294|NETWORK|Lost communication with powertrain control monitor module
U0300|CONTROL_MODULE|Internal control module software incompatibility
U0301|NETWORK|Software incompatibility with ECM/PCM
U0302|NETWORK|Software incompatibility with transmission control module
U0303|NETWORK|Software incompatibility with transfer case control module
U0304|NETWORK|Software incompatibility with gear shift control module A
U0305|NETWORK|Software incompatibility with cruise control module
U0306|NETWORK|Software incompatibility with fuel injector control module
U0307|NETWORK|Software incompatibility with glow plug control module
U0308|NETWORK|Software incompatibility with throttle actuator control module
U0309|NETWORK|Software incompatibility with alternative fuel control module
U0310|NETWORK|Software incompatibility with fuel pump control module
U0311|NETWORK|Software incompatibility with drive motor control module
U0312|NETWORK|Software incompatibility with battery energy control module A
U0313|NETWORK|Software incompatibility with battery energy control module B
U0314|NETWORK|Software incompatibility with four-wheel drive clutch control module
U0315|NETWORK|Software incompatibility with anti-lock brake system (ABS) control module
U0316|NETWORK|Software incompatibility with vehicle dynamics control module
U0317|NETWORK|Software incompatibility with park brake control module
U0318|NETWORK|Software incompatibility with brake system control module A
U0319|NETWORK|Software incompatibility with steering effort control module
U0320|NETWORK|Software incompatibility with power steering control module
U0321|NETWORK|Software incompatibility with ride level control module
U0322|NETWORK|Software incompatibility with body control module
U0323|NETWORK|Software incompatibility with instrument panel control module
U0324|NETWORK|Software incompatibility with HVAC control module
U0325|NETWORK|Software incompatibility with auxiliary heater control module
U0326|NETWORK|Software incompatibility with vehicle immobilizer control module
U0327|NETWORK|Software incompatibility with vehicle security control module
U0328|NETWORK|Software incompatibility with steering angle sensor module
U0329|NETWORK|Software incompatibility with steering column control module
U0330|NETWORK|Software incompatibility with tire pressure monitor module
U0331|NETWORK|Software incompatibility with body control module A
U0400|NETWORK|Invalid data received
U0403|NETWORK|Invalid data received from transfer case control module
U0404|NETWORK|Invalid data received from gear shift control module A
U0405|NETWORK|Invalid data received from cruise control module
U0406|NETWORK|Invalid data received from fuel injector control module
U0407|NETWORK|Invalid data received from glow plug control module
U0408|NETWORK|Invalid data received from throttle actuator A control module
U0409|NETWORK|Invalid data received from alternative fuel control module
U0410|NETWORK|Invalid data received from fuel pump control module A
U0411|NETWORK|Invalid data received from drive motor control module A
U0412|NETWORK|Invalid data received from battery energy control module A
U0413|NETWORK|Invalid data received from battery energy control module B
U0414|NETWORK|Invalid data received from four-wheel drive clutch control module
U0416|NETWORK|Invalid data received from vehicle dynamics control module
U0417|NETWORK|Invalid data received from park brake control module
U0418|NETWORK|Invalid data received from brake system control module A
U0419|NETWORK|Invalid data received from steering effort control module
U0420|NETWORK|Invalid data received from power steering control module A
U0421|NETWORK|Invalid data received from suspension control module A
U0422|NETWORK|Invalid data received from body control module
U0423|NETWORK|Invalid data received from instrument panel cluster control module
U0424|NETWORK|Invalid data received from HVAC control module
U0425|NETWORK|Invalid data received from auxiliary heater control module
U0426|NETWORK|Invalid data received from vehicle immobilizer control module
U0427|NETWORK|Invalid data received from vehicle security control module
U0428|NETWORK|Invalid data received from steering angle sensor module
U0429|NETWORK|Invalid data received from steering column control module
U0430|NETWORK|Invalid data received from tire pressure monitor module
U0431|NETWORK|Invalid data received from body control module A
U0432|NETWORK|Invalid data received from multi-axis acceleration sensor module A
U0433|NETWORK|Invalid data received from front distance range sensor - single sensor or center
U0434|NETWORK|Invalid data received from active roll control module
U0435|NETWORK|Invalid data received from power steering control module - rear
U0436|NETWORK|Invalid data received from differential control module - front
U0437|NETWORK|Invalid data received from differential control module - rear
U0438|NETWORK|Invalid data received from trailer brake control module
U0439|NETWORK|Invalid data received from all terrain control module
U0441|NETWORK|Invalid data received from emissions critical control information
U0442|NETWORK|Invalid data received from ECM/PCM B
U0443|NETWORK|Invalid data received from body control module B
U0444|NETWORK|Invalid data received from body control module C
U0445|NETWORK|Invalid data received from body control module D
U0446|NETWORK|Invalid data received from body control module E
U0447|NETWORK|Invalid data received from serial data gateway module A
U0448|NETWORK|Invalid data received from serial data gateway module B
U0449|NETWORK|Invalid data received from serial data gateway module C
U044A|NETWORK|Invalid data received from serial data gateway module D
U0451|NETWORK|Invalid data received from serial data gateway module E
U0452|NETWORK|Invalid data received from restraints control module
U0453|NETWORK|Invalid data received from side restraints control module - left
U0454|NETWORK|Invalid data received from side restraints control module - right
U0455|NETWORK|Invalid data received from restraints occupant classification system module
U0456|NETWORK|Invalid data received from coolant temperature control module
U0457|NETWORK|Invalid data received from information center A
U0458|NETWORK|Invalid data received from information center B
U0459|NETWORK|Invalid data received from head up display
U045A|NETWORK|Invalid data received from parking assist control module A
U0461|NETWORK|Invalid data received from audible alert control module
U0462|NETWORK|Invalid data received from compass module
U0463|NETWORK|Invalid data received from navigation display module
U0464|NETWORK|Invalid data received from navigation control module
U0465|NETWORK|Invalid data received from PTO control module
U0466|NETWORK|Invalid data received from HVAC control module - rear
U0467|NETWORK|Invalid data received from fuel additive control module
U0468|NETWORK|Invalid data received from fuel cell control module
U0469|NETWORK|Invalid data received from starter/generator control module
U046A|NETWORK|Invalid data received from sunroof control module
U0471|NETWORK|Invalid data received from restraints system sensor A
U0472|NETWORK|Invalid data received from restraints system sensor B
U0473|NETWORK|Invalid data received from restraints system sensor C
U0474|NETWORK|Invalid data received from restraints system sensor D
U0475|NETWORK|Invalid data received from restraints system sensor E
U0476|NETWORK|Invalid data received from restraints system sensor F
U0477|NETWORK|Invalid data received from restraints system sensor G
U0478|NETWORK|Invalid data received from restraints system sensor H
U0479|NETWORK|Invalid data received from restraints system sensor i
U047A|NETWORK|Invalid data received from restraints system sensor J
U047B|NETWORK|Invalid data received from restraints system sensor K
U047C|NETWORK|Invalid data received from restraints system sensor L
U047D|NETWORK|Invalid data received from restraints system sensor m
U047E|NETWORK|Invalid data received from restraints system sensor n
U0481|NETWORK|Invalid data received from automatic lighting control module
U0482|NETWORK|Invalid data received from headlamp leveling control module A
U0483|NETWORK|Invalid data received from lighting control module - front
U0484|NETWORK|Invalid data received from lighting control module - rear A
U0485|NETWORK|Invalid data received from radio
U0486|NETWORK|Invalid data received from antenna control module
U0487|NETWORK|Invalid data received from audio amplifier A
U0488|NETWORK|Invalid data received from digital disc player/changer module A
U0489|NETWORK|Invalid data received from digital disc player/changer module B
U0491|NETWORK|Invalid data received from digital disc player/changer module D
U0492|NETWORK|Invalid data received from television
U0493|NETWORK|Invalid data received from personal computer
U0494|NETWORK|Invalid data received from digital audio control module A
U0495|NETWORK|Invalid data received from digital audio control module B
U0496|NETWORK|Invalid data received from subscription entertainment receiver module
U0497|NETWORK|Invalid data received from entertainment control module - rear A
U0498|NETWORK|Invalid data received from telephone control module
U0499|NETWORK|Invalid data received from telematic control module
U049A|NETWORK|Invalid data received from door control module A
U0501|NETWORK|Invalid data received from door control module B
U0502|NETWORK|Invalid data received from door control module C
U0503|NETWORK|Invalid data received from door control module D
U0504|NETWORK|Invalid data received from door control module E
U0505|NETWORK|Invalid data received from door control module F
U0506|NETWORK|Invalid data received from door control module G
U0507|NETWORK|Invalid data received from folding top control module
U0508|NETWORK|Invalid data received from moveable roof control module A
U0509|NETWORK|Invalid data received from seat control module A
U050A|NETWORK|Invalid data received from seat control module B
U0511|NETWORK|Invalid data received from seat control module C
U0512|NETWORK|Invalid data received from seat control module D
U0513|NETWORK|Invalid data received from yaw rate sensor module
U0514|NETWORK|Invalid data received from mirror control module A
U0515|NETWORK|Invalid data received from remote function actuation module
U0516|NETWORK|Invalid data received from door switch A
U0517|NETWORK|Invalid data received from door switch B
U0518|NETWORK|Invalid data received from door switch C
U0519|NETWORK|Invalid data received from door switch D
U051A|NETWORK|Invalid data received from door switch E
U0521|NETWORK|Invalid data received from door switch F
U0522|NETWORK|Invalid data received from door switch G
U0523|NETWORK|Invalid data received from door window motor A
U0524|NETWORK|Invalid data received from door window motor B
U0525|NETWORK|Invalid data received from door window motor C
U0526|NETWORK|Invalid data received from door window motor D
U0527|NETWORK|Invalid data received from door window motor E
U0528|NETWORK|Invalid data received from door window motor F
U0529|NETWORK|Invalid data received from door window motor G
U052A|NETWORK|Invalid data received from heated steering wheel module
U0531|NETWORK|Invalid data received from rear gate module
U0532|NETWORK|Invalid data received from rain sensing module
U0533|NETWORK|Invalid data received from side obstacle detection control module A
U0535|NETWORK|Invalid data received from convenience recall module
U0536|NETWORK|Invalid data received from lateral acceleration sensor module
U0537|NETWORK|Invalid data received from column lock module
U0538|NETWORK|Invalid data received from digital audio control module C
U0539|NETWORK|Invalid data received from digital audio control module D
U053A|NETWORK|Invalid data received from entrapment control module A
U0541|NETWORK|Invalid data received from entrapment control module B
U0542|NETWORK|Invalid data received from headlamp control module A
U0543|NETWORK|Invalid data received from headlamp control module B
U0544|NETWORK|Invalid data received from parking assist control module B
U0545|NETWORK|Invalid data received from running board control module A
U0546|NETWORK|Invalid data received from entertainment control module - front
U0547|NETWORK|Invalid data received from seat control module E
U0548|NETWORK|Invalid data received from seat control module F
U0549|NETWORK|Invalid data received from remote accessory module
U054A|NETWORK|Invalid data received from entertainment control module - rear B
U0551|NETWORK|Invalid data received from impact classification system module
U0552|NETWORK|Invalid data received from running board control module B
U0553|NETWORK|Invalid data received from lighting control module - rear B
U0592|NETWORK|Invalid data received from gear shift control module B
U0593|NETWORK|Invalid data received from drive motor control module B
U0594|NETWORK|Invalid data received from hybrid/EV powertrain control module
U0595|NETWORK|Invalid data received from powertrain control monitor module
U0596|NETWORK|Invalid data received from AC/AC converter control module
U0599|NETWORK|Invalid data received from DC/DC converter control module A
U059A|NETWORK|Invalid data received from DC/DC converter control module B
U3000|CONTROL_MODULE|Control module
"""

    private const val C4 = """
U3001|CONTROL_MODULE|Control module improper shutdown performance
U3002|GENERAL|Vehicle identification number
U3003|CHARGING|Battery voltage
U3004|CONTROL_MODULE|Accessory power relay
U3005|GENERAL|Retained accessory power
U3006|CONTROL_MODULE|Control module input power A circuit/open
U3007|CONTROL_MODULE|Control module input power B circuit/open
U3008|CONTROL_MODULE|Control module ground A
U3009|CONTROL_MODULE|Control module ground B
U300A|GENERAL|Ignition switch
U300B|GENERAL|Ignition input accessory/on/start
U300C|GENERAL|Ignition input off/on/start
U300D|GENERAL|Ignition input on/start
U300E|GENERAL|Ignition input on
U300F|GENERAL|Ignition input accessory
U3010|GENERAL|Ignition input start
"""

}
