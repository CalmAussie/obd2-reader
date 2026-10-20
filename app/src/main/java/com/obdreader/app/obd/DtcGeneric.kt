package com.obdreader.app.obd

import com.obdreader.app.obd.DtcLibrary.DtcInfo
import com.obdreader.app.obd.DtcLibrary.Severity

/**
 * Standard definitions for generic trouble codes (SAE J2012 / ISO 15031-6) that aren't in the
 * hand-written catalogue in [DtcLibrary]. Generic codes mean the same thing on every car, so
 * each gets its exact name, and the explanation, causes and checks for its kind of fault:
 * what the part does, and what an electrical "circuit low", "range/performance" and similar
 * fault usually points to.
 *
 * Only codes whose standard definition is well established are listed. Anything else still
 * falls back to the explanation worked out from the code's structure.
 */
internal object DtcGeneric {

    /** A family of parts, with what it does and what usually goes wrong. */
    private enum class Kind(
        val part: String,
        val severity: Severity,
        val causes: List<String>,
        val symptoms: List<String>,
        val checks: List<String>,
    ) {
        O2(
            "Oxygen sensors measure the oxygen left in the exhaust, so the engine computer can keep the fuel mixture right. Sensor 1 is before the catalytic converter, sensor 2 after it. Bank 1 is the side of the engine with cylinder 1.",
            Severity.MEDIUM,
            listOf("A worn or contaminated oxygen sensor", "Damaged or melted wiring near the exhaust", "An exhaust leak near the sensor", "A fuel mixture problem making the sensor read correctly but out of range"),
            listOf("Higher fuel use", "Rough running in some cases", "Failed emissions test"),
            listOf("Check the sensor's wiring and connector, away from hot exhaust parts", "Look for exhaust leaks before the sensor", "Fix any lean or rich codes first, then replace the sensor if the code returns"),
        ),
        O2_HEATER(
            "Each oxygen sensor has a heater so it starts working soon after a cold start. Sensor 1 is before the catalytic converter, sensor 2 after it.",
            Severity.LOW,
            listOf("A failed heater inside the oxygen sensor (the most common cause)", "A blown fuse for the heater circuit", "Damaged wiring or a corroded connector"),
            listOf("Usually nothing you'd notice", "Slightly higher fuel use after a cold start"),
            listOf("Check the oxygen sensor fuse", "Check the connector and wiring", "Measure the heater's resistance; replace the sensor if it's open"),
        ),
        CAM_TIMING(
            "The variable valve timing system uses oil pressure and a control solenoid to move the camshaft, changing valve timing with engine speed and load.",
            Severity.MEDIUM,
            listOf("Low, old or wrong-grade engine oil", "A faulty or sticking oil control (VVT) solenoid", "Damaged solenoid wiring", "A worn timing chain or cam phaser"),
            listOf("Rough idle", "Loss of power or poor fuel economy", "Rattle at start-up"),
            listOf("Check the oil level and condition first", "Check the VVT solenoid's connector and test the solenoid", "Have the timing chain checked if the code returns"),
        ),
        POSITION_SENSOR(
            "The crankshaft and camshaft position sensors tell the engine computer exactly where the engine is in its cycle, for spark and fuel timing.",
            Severity.HIGH,
            listOf("A failed sensor", "Damaged wiring or a loose connector", "A damaged reluctor (tone) ring", "Timing chain or belt problems, for correlation faults"),
            listOf("Hard starting or no start", "Stalling, sometimes when hot", "Misfires or rough running"),
            listOf("Check the sensor's connector and wiring", "Check the sensor's gap and mounting", "Replace the sensor; for correlation codes, have the timing checked"),
        ),
        AIR_SENSOR(
            "This sensor measures the air going into the engine (its amount, pressure or temperature), which the engine computer uses to work out how much fuel to add.",
            Severity.MEDIUM,
            listOf("A dirty or failed sensor", "A loose connector or damaged wiring", "An intake air leak or a cracked vacuum hose", "A blocked air filter"),
            listOf("Rough idle or stalling", "Poor acceleration", "Higher fuel use"),
            listOf("Check the connector and wiring", "Check the intake and vacuum hoses for leaks", "Clean or replace the sensor"),
        ),
        COOLANT_SENSOR(
            "The coolant temperature sensor tells the engine computer how warm the engine is, for fuel, idle and the cooling fans. The dashboard temperature reading often uses it too.",
            Severity.MEDIUM,
            listOf("A failed sensor", "A loose connector or damaged wiring", "Low coolant leaving the sensor in air", "A thermostat stuck open, for range codes"),
            listOf("Hard starting", "Poor fuel economy or black smoke", "Cooling fans running all the time", "Wrong temperature on the dashboard"),
            listOf("Check the coolant level (only when the engine is cold)", "Check the sensor's connector and wiring", "Compare the reading to the real engine temperature, then replace the sensor"),
        ),
        THROTTLE_SENSOR(
            "The throttle and accelerator pedal position sensors tell the engine computer how far the throttle is open and how far you're pressing the pedal. Most cars have two of each, checked against one another.",
            Severity.HIGH,
            listOf("A worn or failed sensor", "A loose connector or damaged wiring", "A dirty throttle body", "A sensor-reference voltage fault shared with other sensors"),
            listOf("Reduced power (limp mode)", "Poor or delayed throttle response", "Rough or high idle"),
            listOf("Check the throttle body and pedal connectors", "Clean the throttle body", "Replace the sensor, pedal assembly or throttle body as indicated"),
        ),
        THROTTLE_ACTUATOR(
            "The electronic throttle uses a motor to open and close the throttle. The engine computer watches it closely, and limits power if it doesn't move as commanded.",
            Severity.HIGH,
            listOf("A dirty or sticking throttle body", "A failing throttle motor", "Damaged wiring or a poor connection", "Low battery voltage"),
            listOf("Reduced power (limp mode)", "Stalling or a high idle", "The throttle not responding to the pedal"),
            listOf("Check the battery and its terminals", "Clean the throttle body", "Check the throttle body connector, then replace the throttle body if needed"),
        ),
        FUEL_PRESSURE(
            "The fuel system keeps pressure in the fuel rail so the injectors can spray the right amount of fuel. A sensor reports that pressure to the engine computer.",
            Severity.HIGH,
            listOf("A failing fuel pump", "A blocked fuel filter", "A faulty fuel pressure sensor or regulator", "Damaged wiring"),
            listOf("Loss of power under load", "Hesitation, surging or stalling", "Hard starting"),
            listOf("Make sure there's plenty of fuel in the tank", "Check the sensor's connector and wiring", "Have the fuel pressure measured with a gauge"),
        ),
        FUEL_TRIM(
            "Fuel trim is how much the engine computer is adjusting the fuel to keep the mixture right. This code means it has reached its limit.",
            Severity.MEDIUM,
            listOf("A vacuum or intake leak (lean)", "A dirty MAF sensor", "Low fuel pressure (lean) or a leaking injector (rich)", "An exhaust leak before an oxygen sensor", "A faulty oxygen sensor"),
            listOf("Rough idle", "Hesitation", "Higher fuel use"),
            listOf("Check for vacuum leaks around hoses and the intake", "Clean the MAF sensor", "Have fuel pressure and the oxygen sensors checked"),
        ),
        INJECTOR(
            "Each fuel injector is opened by the engine computer to spray fuel into a cylinder. The computer watches each injector's electrical circuit.",
            Severity.HIGH,
            listOf("A failed injector coil", "A loose or corroded injector connector", "Damaged wiring", "A fault in the engine computer's injector driver (rare)"),
            listOf("Misfire on that cylinder", "Rough running and loss of power", "Higher fuel use"),
            listOf("Check that cylinder's injector connector", "Measure the injector's resistance and compare with the others", "Check the wiring back to the engine computer"),
        ),
        MISFIRE(
            "A misfire means fuel isn't burning properly in a cylinder. Unburnt fuel can overheat and damage the catalytic converter.",
            Severity.HIGH,
            listOf("Worn spark plugs or a failing ignition coil", "A clogged or leaking fuel injector", "A vacuum leak", "Low compression from worn valves or rings"),
            listOf("Rough or shaky idle", "Hesitation or loss of power", "A flashing check-engine light (a severe misfire)", "Higher fuel use"),
            listOf("If the check-engine light is flashing, ease off and get it checked soon", "Check the spark plugs and ignition coils", "Check for vacuum leaks, then have a compression test done"),
        ),
        IGNITION_COIL(
            "Each ignition coil makes the spark for its cylinder. Coil A is usually cylinder 1, coil B cylinder 2, and so on. The engine computer watches each coil's circuit.",
            Severity.HIGH,
            listOf("A failed ignition coil", "A loose or corroded coil connector", "Damaged wiring", "A worn spark plug overloading the coil"),
            listOf("Misfire on that cylinder", "Rough running and loss of power", "A flashing check-engine light"),
            listOf("Check that coil's connector", "Swap the coil with another cylinder and see if the fault follows it", "Replace the coil and check its spark plug"),
        ),
        KNOCK(
            "Knock sensors listen for engine knock (pinging), so the engine computer can retard the spark before it causes damage.",
            Severity.MEDIUM,
            listOf("A failed knock sensor", "Damaged wiring or a corroded connector", "A sensor not tightened properly", "Real engine knock from low-octane fuel or carbon build-up"),
            listOf("Reduced power and fuel economy (the computer plays safe)", "Pinging under load"),
            listOf("Use the fuel grade your car needs", "Check the sensor's wiring and connector", "Check the sensor is tightened correctly, then replace it"),
        ),
        EGR(
            "Exhaust gas recirculation (EGR) feeds a little exhaust back into the intake to lower combustion temperatures and emissions.",
            Severity.MEDIUM,
            listOf("Carbon build-up in the EGR valve or its passages", "A failed EGR valve or position sensor", "A split vacuum hose or failed control solenoid", "Damaged wiring"),
            listOf("Rough idle or stalling (too much flow)", "Pinging under load (too little flow)", "Failed emissions test"),
            listOf("Check the EGR valve's connector and vacuum hoses", "Clean carbon from the valve and passages", "Replace the EGR valve if it's stuck or its sensor fails"),
        ),
        SECONDARY_AIR(
            "The secondary air system pumps fresh air into the exhaust after a cold start, to warm the catalytic converter quickly.",
            Severity.LOW,
            listOf("A failed air pump or blown fuse", "A stuck check valve or switching valve", "Split hoses", "Water damage to the pump"),
            listOf("Usually nothing you'd notice", "A whine or no pump noise after a cold start"),
            listOf("Check the pump's fuse and relay", "Check the hoses and check valve", "Test the pump after a cold start"),
        ),
        CATALYST(
            "The catalytic converter cleans the exhaust. The engine computer compares the oxygen sensors before and after it to see how well it's working.",
            Severity.MEDIUM,
            listOf("A worn-out catalytic converter", "A faulty rear oxygen sensor", "An exhaust leak", "Misfires or oil burning that have damaged the converter"),
            listOf("Usually nothing you'd notice", "A rotten-egg smell in some cases", "Failed emissions test"),
            listOf("Fix any misfire or fuel mixture codes first", "Check for exhaust leaks", "Have the oxygen sensors checked before replacing the converter"),
        ),
        EVAP(
            "The evaporative emissions (EVAP) system stores fuel vapour from the tank and burns it in the engine, instead of letting it into the air. The computer tests it for leaks.",
            Severity.LOW,
            listOf("A loose, damaged or wrong fuel cap", "A cracked EVAP hose", "A faulty purge or vent valve", "A faulty EVAP pressure sensor"),
            listOf("Usually nothing you'd notice", "A fuel smell in some cases", "Trouble filling the tank, for vent faults"),
            listOf("Check the fuel cap is on tight and its seal isn't damaged", "Check the EVAP hoses and the charcoal canister", "Test the purge and vent valves"),
        ),
        FUEL_LEVEL(
            "The fuel level sensor in the tank reports how much fuel is left.",
            Severity.LOW,
            listOf("A worn fuel level sender", "A loose connector or damaged wiring", "A faulty instrument cluster (rare)"),
            listOf("A wrong or jumping fuel gauge", "The low-fuel light at the wrong time"),
            listOf("Check the sender's connector on top of the tank", "Check the wiring", "Replace the fuel level sender"),
        ),
        FAN(
            "The cooling fans pull air through the radiator when the car is slow or stopped.",
            Severity.HIGH,
            listOf("A blown fuse or failed fan relay", "A failed fan motor", "Damaged wiring", "A faulty fan control module"),
            listOf("Overheating in traffic", "The air conditioning not cooling when stopped"),
            listOf("Watch the temperature gauge and stop if it climbs", "Check the fan fuses and relays", "Check the fan motor and its connector"),
        ),
        SPEED_SENSOR(
            "The vehicle speed sensor tells the engine and transmission computers how fast the car is going. ABS wheel speed sensors often feed it.",
            Severity.MEDIUM,
            listOf("A failed speed sensor", "Damaged wiring or a loose connector", "A faulty ABS wheel speed sensor feeding the signal"),
            listOf("A speedometer that's wrong or doesn't work", "Harsh or wrong gear changes", "Cruise control not working"),
            listOf("Check the sensor's connector and wiring", "Check for ABS codes too", "Replace the speed sensor"),
        ),
        IDLE(
            "The engine computer controls idle speed with an idle air valve or the electronic throttle.",
            Severity.MEDIUM,
            listOf("A dirty throttle body or idle air valve", "A vacuum leak (high idle)", "A failed idle air control valve", "Idle needing to be relearned after a battery change"),
            listOf("Idle too high, too low or hunting", "Stalling when stopping"),
            listOf("Check for vacuum leaks", "Clean the throttle body and idle air valve", "Do an idle relearn if your car needs one"),
        ),
        OIL_PRESSURE(
            "The oil pressure sensor or switch tells the engine computer whether the engine has enough oil pressure.",
            Severity.HIGH,
            listOf("Low oil level", "A failed oil pressure sensor or switch", "Damaged wiring", "A worn oil pump or engine bearings (real low pressure)"),
            listOf("The oil warning light", "Engine noise or ticking"),
            listOf("Check the oil level now, and don't drive if it's low or the light is on", "Check the sensor's connector", "Have the real oil pressure measured with a gauge"),
        ),
        CHARGING(
            "The charging system (alternator and battery) powers the car. The engine computer watches the system voltage and the alternator's control.",
            Severity.HIGH,
            listOf("A failing alternator or its regulator", "A loose or corroded battery terminal", "A worn battery", "A slipping drive belt"),
            listOf("A battery warning light", "Dim or flickering lights", "The car not starting"),
            listOf("Check the battery terminals are clean and tight", "Check the alternator belt", "Have the battery and alternator tested"),
        ),
        CONTROL_MODULE(
            "The engine computer checks itself and its own circuits. This code points to the computer, its power supply, or a circuit it drives.",
            Severity.HIGH,
            listOf("Low battery voltage or a poor earth (ground)", "A blown fuse", "Damaged wiring or a corroded connector", "Water in the computer or a failed computer (less common)", "Software that needs updating"),
            listOf("Varies: from nothing at all to stalling or no start"),
            listOf("Check the battery, its terminals and the engine earth straps", "Check the computer's fuses and connectors", "Ask a dealer whether there's a software update before replacing the computer"),
        ),
        SENSOR_SUPPLY(
            "The engine computer supplies a steady reference voltage (usually 5 volts) to several sensors at once. A fault here affects every sensor on that supply.",
            Severity.HIGH,
            listOf("One of the sensors on that supply shorted inside", "Damaged wiring shorted to ground", "A faulty engine computer (less common)"),
            listOf("Several sensor codes at once", "Reduced power (limp mode)", "Stalling or no start"),
            listOf("Unplug sensors on that supply one at a time to see if the voltage comes back", "Check the wiring for chafing", "Have the computer tested if no sensor is to blame"),
        ),
        TRANS_SENSOR(
            "The transmission uses sensors (range selector, fluid temperature, input and output speed) so its computer can choose gears and shift smoothly.",
            Severity.MEDIUM,
            listOf("A failed sensor", "Damaged wiring or a loose connector", "Low or old transmission fluid", "A selector (range) switch out of adjustment"),
            listOf("Harsh or wrong gear changes", "The transmission stuck in one gear (limp mode)", "A wrong speedometer, for speed sensors"),
            listOf("Check the transmission fluid level and condition", "Check the sensor's connector and wiring", "Replace the sensor if the fault stays"),
        ),
        TRANS_RATIO(
            "The transmission computer compares input and output speeds to check each gear. This code means a gear isn't giving the ratio it should, so something is slipping.",
            Severity.HIGH,
            listOf("Low, burnt or wrong transmission fluid", "A faulty shift solenoid or valve body", "Worn clutches or bands inside the transmission", "A faulty speed sensor"),
            listOf("Slipping, flaring or engine revs rising without the car speeding up", "Harsh or delayed shifts", "The transmission stuck in one gear"),
            listOf("Check the fluid level and condition (burnt smell, dark colour)", "Check for other transmission codes, such as speed sensors and solenoids", "Have a transmission specialist test it before more damage is done"),
        ),
        TRANS_SOLENOID(
            "Solenoids inside the transmission are switched by its computer to change gears and lock the torque converter. The computer watches each solenoid's circuit and effect.",
            Severity.HIGH,
            listOf("A failed solenoid", "Low or dirty transmission fluid", "A damaged internal wiring harness or connector", "A sticking valve in the valve body"),
            listOf("Harsh, late or missing gear changes", "The transmission stuck in one gear (limp mode)", "Shuddering at steady speeds, for torque converter faults"),
            listOf("Check the fluid level and condition", "Check the transmission's main connector", "Have the solenoid tested; it's often replaced with the valve body"),
        ),
        TURBO(
            "The turbocharger or supercharger boosts the air going into the engine. The engine computer compares the boost it asked for with what it measures.",
            Severity.HIGH,
            listOf("A split or loose boost hose or intercooler pipe (underboost)", "A sticking wastegate or boost control valve", "A faulty boost pressure sensor", "A worn turbocharger"),
            listOf("Loss of power (limp mode)", "A whistling or hissing noise", "Black smoke on diesels"),
            listOf("Check every boost hose and clamp for splits and leaks", "Check the boost control valve and its vacuum lines", "Have the turbo and boost sensor checked"),
        ),
        DIESEL_AFTERTREATMENT(
            "Diesel exhaust treatment traps soot in a particulate filter (DPF), which burns it off during regeneration, and may use AdBlue (SCR) to cut NOx.",
            Severity.MEDIUM,
            listOf("Lots of short trips, so the filter never regenerates", "A faulty DPF pressure sensor or its hoses", "Low-quality or low AdBlue, or a faulty dosing system", "An engine fault causing extra soot"),
            listOf("Reduced power", "A DPF or AdBlue warning light", "The cooling fan running after switching off (during regeneration)"),
            listOf("Do a long drive at steady motorway speed to let the filter regenerate", "Check the DPF pressure sensor hoses", "Have a forced regeneration or DPF clean done if it won't clear"),
        ),
        TEMPERATURE_LIMIT(
            "The engine computer has seen a temperature or speed beyond a safe limit, and may cut power to protect the engine or transmission.",
            Severity.HIGH,
            listOf("Low coolant or a coolant leak", "A stuck thermostat or failed water pump", "Cooling fans not working", "Low transmission fluid or heavy towing, for transmission overheating"),
            listOf("A high temperature gauge or warning light", "Reduced power", "Steam or a coolant smell"),
            listOf("Pull over and switch off if the engine is overheating", "Check the coolant only once the engine is cold", "Check for leaks and that the fans run"),
        ),
        THERMOSTAT(
            "The engine computer expects the engine to reach its normal temperature within a set time. If it warms up too slowly, the thermostat is usually stuck open.",
            Severity.LOW,
            listOf("A thermostat stuck open (the most common cause)", "Low coolant", "A faulty coolant temperature sensor"),
            listOf("Temperature gauge reading low", "Weak heater output", "Higher fuel use"),
            listOf("Check the coolant level when cold", "Watch the temperature gauge during a drive", "Replace the thermostat"),
        ),
        AIRCON(
            "The air conditioning system's sensors and clutch are monitored by the engine computer, which also adjusts idle when the compressor runs.",
            Severity.LOW,
            listOf("Low refrigerant", "A faulty refrigerant pressure sensor", "A failed compressor clutch relay", "Damaged wiring"),
            listOf("Air conditioning not cooling", "The compressor not switching on"),
            listOf("Check the A/C fuses and relays", "Check the pressure sensor's connector", "Have the A/C system's refrigerant checked"),
        ),
        BRAKE_SWITCH(
            "The brake light switch tells the engine computer when you press the brake, for cruise control, the shift lock and the throttle.",
            Severity.MEDIUM,
            listOf("A faulty or badly adjusted brake light switch", "A blown brake light fuse", "Damaged wiring"),
            listOf("Brake lights not working or staying on", "Cruise control not working", "Can't shift out of Park"),
            listOf("Check the brake lights work", "Check the brake light fuse", "Adjust or replace the brake light switch"),
        ),
        STARTER(
            "The engine computer controls the starter relay in many cars.",
            Severity.MEDIUM,
            listOf("A faulty starter relay", "Damaged wiring", "A weak battery"),
            listOf("The engine not cranking, or cranking only sometimes"),
            listOf("Check the battery", "Check the starter relay and its fuse", "Check the wiring to the starter"),
        ),
        WARNING_LAMP(
            "The engine computer drives the check-engine light. This code means it can't control that light properly.",
            Severity.MEDIUM,
            listOf("A blown bulb or LED in the cluster", "Damaged wiring", "A faulty instrument cluster"),
            listOf("The check-engine light not coming on at start-up, or staying on"),
            listOf("Check the light comes on for a moment with the ignition on", "Check the cluster's connector", "Have the cluster tested"),
        ),
        INTAKE_RUNNER(
            "Intake manifold runner or tuning valves change the intake's shape for better power at different engine speeds.",
            Severity.LOW,
            listOf("Carbon build-up sticking the runner flaps", "A failed actuator or vacuum leak", "Damaged wiring"),
            listOf("Slight loss of power at some engine speeds", "Rough idle in some cases"),
            listOf("Check the actuator's connector and vacuum hose", "Check the flaps move freely", "Replace the actuator or manifold if they're stuck"),
        ),
        NETWORK(
            "The car's computers talk to each other over a network (usually CAN). This code means one computer stopped hearing another, or got data that made no sense.",
            Severity.MEDIUM,
            listOf("A low or failing battery (the most common cause)", "A blown fuse for the module that went quiet", "Damaged network wiring or a corroded connector", "A failed module"),
            listOf("Warning lights for the system concerned", "Features controlled by that module not working"),
            listOf("Check the battery and charging first", "Check the fuses for the module named", "Check its connectors and the network wiring, then have the module tested"),
        ),
    }

    private class Def(val title: String, val kind: Kind)

    // Bank and sensor wording.
    private fun bs(b: Int, s: Int) = "bank $b sensor $s"

    private val defs: Map<String, Def> by lazy {
        val m = LinkedHashMap<String, Def>()
        fun d(code: String, title: String, kind: Kind) { m[code] = Def(title, kind) }

        // Oxygen sensor heaters
        for ((base, b, s) in listOf(Triple(30, 1, 1), Triple(36, 1, 2), Triple(50, 2, 1), Triple(56, 2, 2))) {
            d(String.format("P%04d", base), "Oxygen sensor heater control circuit (${bs(b, s)})", Kind.O2_HEATER)
            d(String.format("P%04d", base + 1), "Oxygen sensor heater control circuit low (${bs(b, s)})", Kind.O2_HEATER)
            d(String.format("P%04d", base + 2), "Oxygen sensor heater control circuit high (${bs(b, s)})", Kind.O2_HEATER)
        }
        // Camshaft timing
        d("P0010", "Intake camshaft position actuator circuit (bank 1)", Kind.CAM_TIMING)
        d("P0013", "Exhaust camshaft position actuator circuit (bank 1)", Kind.CAM_TIMING)
        d("P0015", "Exhaust camshaft timing over-retarded (bank 1)", Kind.CAM_TIMING)
        d("P0020", "Intake camshaft position actuator circuit (bank 2)", Kind.CAM_TIMING)
        d("P0023", "Exhaust camshaft position actuator circuit (bank 2)", Kind.CAM_TIMING)
        d("P0025", "Exhaust camshaft timing over-retarded (bank 2)", Kind.CAM_TIMING)
        d("P0017", "Crankshaft and camshaft position correlation (bank 1 sensor B)", Kind.POSITION_SENSOR)
        d("P0018", "Crankshaft and camshaft position correlation (bank 2 sensor A)", Kind.POSITION_SENSOR)
        d("P0019", "Crankshaft and camshaft position correlation (bank 2 sensor B)", Kind.POSITION_SENSOR)

        // Air metering
        d("P0068", "Manifold pressure or mass air flow doesn't match throttle position", Kind.AIR_SENSOR)
        d("P0069", "Manifold pressure and barometric pressure don't match", Kind.AIR_SENSOR)
        d("P0104", "Mass air flow (MAF) sensor circuit intermittent", Kind.AIR_SENSOR)
        d("P0105", "Manifold pressure (MAP) or barometric pressure sensor circuit", Kind.AIR_SENSOR)
        d("P0106", "Manifold pressure (MAP) or barometric pressure sensor range/performance", Kind.AIR_SENSOR)
        d("P0107", "Manifold pressure (MAP) or barometric pressure sensor circuit low", Kind.AIR_SENSOR)
        d("P0108", "Manifold pressure (MAP) or barometric pressure sensor circuit high", Kind.AIR_SENSOR)
        d("P0109", "Manifold pressure (MAP) or barometric pressure sensor circuit intermittent", Kind.AIR_SENSOR)
        d("P0110", "Intake air temperature sensor circuit", Kind.AIR_SENSOR)
        d("P0111", "Intake air temperature sensor range/performance", Kind.AIR_SENSOR)
        d("P0112", "Intake air temperature sensor circuit low", Kind.AIR_SENSOR)
        d("P0113", "Intake air temperature sensor circuit high", Kind.AIR_SENSOR)
        d("P0114", "Intake air temperature sensor circuit intermittent", Kind.AIR_SENSOR)
        d("P2279", "Intake air system leak", Kind.FUEL_TRIM)

        // Coolant temperature
        d("P0115", "Engine coolant temperature sensor circuit", Kind.COOLANT_SENSOR)
        d("P0116", "Engine coolant temperature sensor range/performance", Kind.COOLANT_SENSOR)
        d("P0117", "Engine coolant temperature sensor circuit low", Kind.COOLANT_SENSOR)
        d("P0118", "Engine coolant temperature sensor circuit high", Kind.COOLANT_SENSOR)
        d("P0119", "Engine coolant temperature sensor circuit intermittent", Kind.COOLANT_SENSOR)
        d("P0125", "Coolant temperature too low for closed-loop fuel control", Kind.THERMOSTAT)
        d("P0126", "Coolant temperature too low for stable operation", Kind.THERMOSTAT)
        d("P0128", "Coolant thermostat: temperature below regulating temperature", Kind.THERMOSTAT)

        // Throttle and pedal position
        for ((letter, base) in listOf("A" to 120, "B" to 220)) {
            d(String.format("P%04d", base), "Throttle or pedal position sensor $letter circuit", Kind.THROTTLE_SENSOR)
            d(String.format("P%04d", base + 1), "Throttle or pedal position sensor $letter range/performance", Kind.THROTTLE_SENSOR)
            d(String.format("P%04d", base + 2), "Throttle or pedal position sensor $letter circuit low", Kind.THROTTLE_SENSOR)
            d(String.format("P%04d", base + 3), "Throttle or pedal position sensor $letter circuit high", Kind.THROTTLE_SENSOR)
        }
        d("P0124", "Throttle or pedal position sensor A circuit intermittent", Kind.THROTTLE_SENSOR)
        d("P2122", "Accelerator pedal position sensor D circuit low", Kind.THROTTLE_SENSOR)
        d("P2123", "Accelerator pedal position sensor D circuit high", Kind.THROTTLE_SENSOR)
        d("P2127", "Accelerator pedal position sensor E circuit low", Kind.THROTTLE_SENSOR)
        d("P2128", "Accelerator pedal position sensor E circuit high", Kind.THROTTLE_SENSOR)
        d("P2135", "Throttle position sensors A and B disagree", Kind.THROTTLE_SENSOR)
        d("P2138", "Accelerator pedal position sensors D and E disagree", Kind.THROTTLE_SENSOR)
        d("P2101", "Throttle actuator control motor circuit range/performance", Kind.THROTTLE_ACTUATOR)
        d("P2111", "Throttle actuator control system stuck open", Kind.THROTTLE_ACTUATOR)
        d("P2112", "Throttle actuator control system stuck closed", Kind.THROTTLE_ACTUATOR)
        d("P2118", "Throttle actuator control motor current range/performance", Kind.THROTTLE_ACTUATOR)
        d("P2119", "Throttle actuator control throttle body range/performance", Kind.THROTTLE_ACTUATOR)
        d("P2176", "Throttle actuator control system idle position not learned", Kind.THROTTLE_ACTUATOR)

        // Oxygen sensors: circuit, low, high, slow response, no activity, heater
        for ((base, b, s) in listOf(Triple(130, 1, 1), Triple(136, 1, 2), Triple(142, 1, 3), Triple(150, 2, 1), Triple(156, 2, 2))) {
            d(String.format("P%04d", base), "Oxygen sensor circuit (${bs(b, s)})", Kind.O2)
            d(String.format("P%04d", base + 1), "Oxygen sensor circuit low voltage (${bs(b, s)})", Kind.O2)
            d(String.format("P%04d", base + 2), "Oxygen sensor circuit high voltage (${bs(b, s)})", Kind.O2)
            d(String.format("P%04d", base + 3), "Oxygen sensor circuit slow response (${bs(b, s)})", Kind.O2)
            d(String.format("P%04d", base + 4), "Oxygen sensor circuit no activity detected (${bs(b, s)})", Kind.O2)
            d(String.format("P%04d", base + 5), "Oxygen sensor heater circuit (${bs(b, s)})", Kind.O2_HEATER)
        }
        d("P2195", "Oxygen sensor signal stuck lean (bank 1 sensor 1)", Kind.O2)
        d("P2196", "Oxygen sensor signal stuck rich (bank 1 sensor 1)", Kind.O2)
        d("P2197", "Oxygen sensor signal stuck lean (bank 2 sensor 1)", Kind.O2)
        d("P2198", "Oxygen sensor signal stuck rich (bank 2 sensor 1)", Kind.O2)
        d("P2270", "Oxygen sensor signal stuck lean (bank 1 sensor 2)", Kind.O2)
        d("P2271", "Oxygen sensor signal stuck rich (bank 1 sensor 2)", Kind.O2)
        d("P2272", "Oxygen sensor signal stuck lean (bank 2 sensor 2)", Kind.O2)
        d("P2273", "Oxygen sensor signal stuck rich (bank 2 sensor 2)", Kind.O2)
        d("P2A00", "Oxygen (air-fuel ratio) sensor circuit range/performance (bank 1 sensor 1)", Kind.O2)
        d("P2A03", "Oxygen (air-fuel ratio) sensor circuit range/performance (bank 2 sensor 1)", Kind.O2)

        // Fuel trim and fuel pressure
        d("P0170", "Fuel trim malfunction (bank 1)", Kind.FUEL_TRIM)
        d("P0171", "System too lean (bank 1)", Kind.FUEL_TRIM)
        d("P0172", "System too rich (bank 1)", Kind.FUEL_TRIM)
        d("P0173", "Fuel trim malfunction (bank 2)", Kind.FUEL_TRIM)
        d("P0174", "System too lean (bank 2)", Kind.FUEL_TRIM)
        d("P0175", "System too rich (bank 2)", Kind.FUEL_TRIM)
        d("P2096", "Fuel trim after the catalytic converter too lean (bank 1)", Kind.FUEL_TRIM)
        d("P2097", "Fuel trim after the catalytic converter too rich (bank 1)", Kind.FUEL_TRIM)
        d("P2098", "Fuel trim after the catalytic converter too lean (bank 2)", Kind.FUEL_TRIM)
        d("P2099", "Fuel trim after the catalytic converter too rich (bank 2)", Kind.FUEL_TRIM)
        d("P2177", "System too lean away from idle (bank 1)", Kind.FUEL_TRIM)
        d("P2178", "System too rich away from idle (bank 1)", Kind.FUEL_TRIM)
        d("P2179", "System too lean away from idle (bank 2)", Kind.FUEL_TRIM)
        d("P2187", "System too lean at idle (bank 1)", Kind.FUEL_TRIM)
        d("P2188", "System too rich at idle (bank 1)", Kind.FUEL_TRIM)
        d("P2189", "System too lean at idle (bank 2)", Kind.FUEL_TRIM)
        d("P2190", "System too rich at idle (bank 2)", Kind.FUEL_TRIM)
        d("P0088", "Fuel rail or system pressure too high", Kind.FUEL_PRESSURE)
        d("P0089", "Fuel pressure regulator performance", Kind.FUEL_PRESSURE)
        d("P0090", "Fuel pressure regulator control circuit", Kind.FUEL_PRESSURE)
        d("P0190", "Fuel rail pressure sensor circuit", Kind.FUEL_PRESSURE)
        d("P0191", "Fuel rail pressure sensor range/performance", Kind.FUEL_PRESSURE)
        d("P0192", "Fuel rail pressure sensor circuit low", Kind.FUEL_PRESSURE)
        d("P0193", "Fuel rail pressure sensor circuit high", Kind.FUEL_PRESSURE)
        d("P0194", "Fuel rail pressure sensor circuit intermittent", Kind.FUEL_PRESSURE)
        d("P0230", "Fuel pump primary circuit", Kind.FUEL_PRESSURE)
        d("P0627", "Fuel pump control circuit open", Kind.FUEL_PRESSURE)

        // Injectors
        d("P0200", "Fuel injector circuit", Kind.INJECTOR)
        for (n in 1..12) d(String.format("P%04d", 200 + n), "Fuel injector circuit (cylinder $n)", Kind.INJECTOR)
        for (n in 1..8) {
            val base = 261 + (n - 1) * 3
            d(String.format("P%04d", base), "Fuel injector circuit low (cylinder $n)", Kind.INJECTOR)
            d(String.format("P%04d", base + 1), "Fuel injector circuit high (cylinder $n)", Kind.INJECTOR)
        }

        // Temperature and speed limits, boost
        d("P0217", "Engine overheating", Kind.TEMPERATURE_LIMIT)
        d("P0218", "Transmission fluid overheating", Kind.TEMPERATURE_LIMIT)
        d("P0219", "Engine over-speed", Kind.TEMPERATURE_LIMIT)
        d("P0234", "Turbocharger or supercharger overboost", Kind.TURBO)
        d("P0299", "Turbocharger or supercharger underboost", Kind.TURBO)

        // Misfires
        d("P0300", "Random or multiple cylinder misfire detected", Kind.MISFIRE)
        for (n in 1..12) d(String.format("P%04d", 300 + n), "Cylinder $n misfire detected", Kind.MISFIRE)
        d("P0313", "Misfire detected with low fuel", Kind.MISFIRE)
        d("P0314", "Single cylinder misfire (cylinder not specified)", Kind.MISFIRE)
        d("P0316", "Misfire detected on start-up (first 1,000 revolutions)", Kind.MISFIRE)

        // Knock sensors
        d("P0325", "Knock sensor 1 circuit (bank 1)", Kind.KNOCK)
        d("P0326", "Knock sensor 1 range/performance (bank 1)", Kind.KNOCK)
        d("P0327", "Knock sensor 1 circuit low (bank 1)", Kind.KNOCK)
        d("P0328", "Knock sensor 1 circuit high (bank 1)", Kind.KNOCK)
        d("P0330", "Knock sensor 2 circuit (bank 2)", Kind.KNOCK)
        d("P0332", "Knock sensor 2 circuit low (bank 2)", Kind.KNOCK)
        d("P0333", "Knock sensor 2 circuit high (bank 2)", Kind.KNOCK)

        // Crank and cam position sensors
        d("P0335", "Crankshaft position sensor A circuit", Kind.POSITION_SENSOR)
        d("P0336", "Crankshaft position sensor A range/performance", Kind.POSITION_SENSOR)
        d("P0337", "Crankshaft position sensor A circuit low", Kind.POSITION_SENSOR)
        d("P0338", "Crankshaft position sensor A circuit high", Kind.POSITION_SENSOR)
        d("P0339", "Crankshaft position sensor A circuit intermittent", Kind.POSITION_SENSOR)
        d("P0340", "Camshaft position sensor A circuit (bank 1)", Kind.POSITION_SENSOR)
        d("P0341", "Camshaft position sensor A range/performance (bank 1)", Kind.POSITION_SENSOR)
        d("P0342", "Camshaft position sensor A circuit low (bank 1)", Kind.POSITION_SENSOR)
        d("P0343", "Camshaft position sensor A circuit high (bank 1)", Kind.POSITION_SENSOR)
        d("P0344", "Camshaft position sensor A circuit intermittent (bank 1)", Kind.POSITION_SENSOR)
        d("P0345", "Camshaft position sensor A circuit (bank 2)", Kind.POSITION_SENSOR)
        d("P0365", "Camshaft position sensor B circuit (bank 1)", Kind.POSITION_SENSOR)
        d("P0366", "Camshaft position sensor B range/performance (bank 1)", Kind.POSITION_SENSOR)
        d("P0390", "Camshaft position sensor B circuit (bank 2)", Kind.POSITION_SENSOR)

        // Ignition coils
        d("P0350", "Ignition coil primary or secondary circuit", Kind.IGNITION_COIL)
        for (n in 1..12) d(String.format("P%04d", 350 + n), "Ignition coil ${'A' + (n - 1)} primary or secondary circuit", Kind.IGNITION_COIL)

        // EGR and secondary air
        d("P0400", "Exhaust gas recirculation (EGR) flow", Kind.EGR)
        d("P0401", "Exhaust gas recirculation (EGR) flow insufficient", Kind.EGR)
        d("P0402", "Exhaust gas recirculation (EGR) flow excessive", Kind.EGR)
        d("P0403", "Exhaust gas recirculation (EGR) control circuit", Kind.EGR)
        d("P0404", "Exhaust gas recirculation (EGR) control circuit range/performance", Kind.EGR)
        d("P0405", "Exhaust gas recirculation (EGR) sensor A circuit low", Kind.EGR)
        d("P0406", "Exhaust gas recirculation (EGR) sensor A circuit high", Kind.EGR)
        d("P0410", "Secondary air injection system", Kind.SECONDARY_AIR)
        d("P0411", "Secondary air injection system incorrect flow", Kind.SECONDARY_AIR)
        d("P0412", "Secondary air injection switching valve A circuit", Kind.SECONDARY_AIR)
        d("P0491", "Secondary air injection system insufficient flow (bank 1)", Kind.SECONDARY_AIR)
        d("P0492", "Secondary air injection system insufficient flow (bank 2)", Kind.SECONDARY_AIR)

        // Catalytic converters
        d("P0420", "Catalytic converter efficiency below threshold (bank 1)", Kind.CATALYST)
        d("P0421", "Warm-up catalytic converter efficiency below threshold (bank 1)", Kind.CATALYST)
        d("P0430", "Catalytic converter efficiency below threshold (bank 2)", Kind.CATALYST)
        d("P0431", "Warm-up catalytic converter efficiency below threshold (bank 2)", Kind.CATALYST)

        // EVAP
        d("P0440", "Evaporative emission (EVAP) system", Kind.EVAP)
        d("P0441", "Evaporative emission (EVAP) system incorrect purge flow", Kind.EVAP)
        d("P0442", "Evaporative emission (EVAP) system small leak detected", Kind.EVAP)
        d("P0443", "Evaporative emission (EVAP) purge control valve circuit", Kind.EVAP)
        d("P0446", "Evaporative emission (EVAP) vent control circuit", Kind.EVAP)
        d("P0449", "Evaporative emission (EVAP) vent valve circuit", Kind.EVAP)
        d("P0451", "Evaporative emission (EVAP) pressure sensor range/performance", Kind.EVAP)
        d("P0452", "Evaporative emission (EVAP) pressure sensor circuit low", Kind.EVAP)
        d("P0453", "Evaporative emission (EVAP) pressure sensor circuit high", Kind.EVAP)
        d("P0455", "Evaporative emission (EVAP) system large leak detected", Kind.EVAP)
        d("P0456", "Evaporative emission (EVAP) system very small leak detected", Kind.EVAP)
        d("P0457", "Evaporative emission (EVAP) system leak detected (fuel cap loose or off)", Kind.EVAP)
        d("P0496", "Evaporative emission (EVAP) system high purge flow", Kind.EVAP)
        d("P0497", "Evaporative emission (EVAP) system low purge flow", Kind.EVAP)

        // Fuel level, fans
        d("P0460", "Fuel level sensor A circuit", Kind.FUEL_LEVEL)
        d("P0461", "Fuel level sensor A range/performance", Kind.FUEL_LEVEL)
        d("P0462", "Fuel level sensor A circuit low", Kind.FUEL_LEVEL)
        d("P0463", "Fuel level sensor A circuit high", Kind.FUEL_LEVEL)
        d("P0464", "Fuel level sensor A circuit intermittent", Kind.FUEL_LEVEL)
        d("P0480", "Cooling fan 1 control circuit", Kind.FAN)
        d("P0481", "Cooling fan 2 control circuit", Kind.FAN)

        // Speed, idle, brake switch
        d("P0500", "Vehicle speed sensor A", Kind.SPEED_SENSOR)
        d("P0501", "Vehicle speed sensor A range/performance", Kind.SPEED_SENSOR)
        d("P0502", "Vehicle speed sensor A circuit low", Kind.SPEED_SENSOR)
        d("P0503", "Vehicle speed sensor A intermittent, erratic or high", Kind.SPEED_SENSOR)
        d("P0505", "Idle air control system", Kind.IDLE)
        d("P0506", "Idle speed lower than expected", Kind.IDLE)
        d("P0507", "Idle speed higher than expected", Kind.IDLE)
        d("P0510", "Closed throttle position switch", Kind.THROTTLE_SENSOR)
        d("P0571", "Brake switch A circuit", Kind.BRAKE_SWITCH)

        // Oil pressure, A/C, charging
        d("P0520", "Engine oil pressure sensor or switch circuit", Kind.OIL_PRESSURE)
        d("P0521", "Engine oil pressure sensor or switch range/performance", Kind.OIL_PRESSURE)
        d("P0522", "Engine oil pressure sensor or switch circuit low", Kind.OIL_PRESSURE)
        d("P0523", "Engine oil pressure sensor or switch circuit high", Kind.OIL_PRESSURE)
        d("P0524", "Engine oil pressure too low", Kind.OIL_PRESSURE)
        d("P0530", "A/C refrigerant pressure sensor A circuit", Kind.AIRCON)
        d("P0532", "A/C refrigerant pressure sensor A circuit low", Kind.AIRCON)
        d("P0533", "A/C refrigerant pressure sensor A circuit high", Kind.AIRCON)
        d("P0645", "A/C clutch relay control circuit", Kind.AIRCON)
        d("P0560", "System voltage", Kind.CHARGING)
        d("P0562", "System voltage low", Kind.CHARGING)
        d("P0563", "System voltage high", Kind.CHARGING)
        d("P0620", "Generator (alternator) control circuit", Kind.CHARGING)
        d("P0622", "Generator (alternator) field terminal circuit", Kind.CHARGING)

        // Engine computer
        d("P0600", "Serial communication link", Kind.CONTROL_MODULE)
        d("P0601", "Engine computer memory checksum error", Kind.CONTROL_MODULE)
        d("P0602", "Engine computer programming error", Kind.CONTROL_MODULE)
        d("P0603", "Engine computer keep-alive memory (KAM) error", Kind.CONTROL_MODULE)
        d("P0604", "Engine computer random access memory (RAM) error", Kind.CONTROL_MODULE)
        d("P0605", "Engine computer read-only memory (ROM) error", Kind.CONTROL_MODULE)
        d("P0606", "Engine computer processor fault", Kind.CONTROL_MODULE)
        d("P0607", "Engine computer performance", Kind.CONTROL_MODULE)
        d("P0615", "Starter relay circuit", Kind.STARTER)
        d("P0641", "Sensor reference voltage A circuit open", Kind.SENSOR_SUPPLY)
        d("P0651", "Sensor reference voltage B circuit open", Kind.SENSOR_SUPPLY)
        d("P0650", "Check-engine light (MIL) control circuit", Kind.WARNING_LAMP)

        // Intake runners
        d("P2004", "Intake manifold runner control stuck open (bank 1)", Kind.INTAKE_RUNNER)

        // Transmission
        d("P0700", "Transmission control system fault (the transmission computer has a code of its own)", Kind.TRANS_SENSOR)
        d("P0705", "Transmission range sensor A circuit (gear selector input)", Kind.TRANS_SENSOR)
        d("P0706", "Transmission range sensor A range/performance", Kind.TRANS_SENSOR)
        d("P0707", "Transmission range sensor A circuit low", Kind.TRANS_SENSOR)
        d("P0708", "Transmission range sensor A circuit high", Kind.TRANS_SENSOR)
        d("P0710", "Transmission fluid temperature sensor A circuit", Kind.TRANS_SENSOR)
        d("P0711", "Transmission fluid temperature sensor A range/performance", Kind.TRANS_SENSOR)
        d("P0712", "Transmission fluid temperature sensor A circuit low", Kind.TRANS_SENSOR)
        d("P0713", "Transmission fluid temperature sensor A circuit high", Kind.TRANS_SENSOR)
        d("P0715", "Transmission input or turbine speed sensor A circuit", Kind.TRANS_SENSOR)
        d("P0716", "Transmission input or turbine speed sensor A range/performance", Kind.TRANS_SENSOR)
        d("P0717", "Transmission input or turbine speed sensor A no signal", Kind.TRANS_SENSOR)
        d("P0720", "Transmission output speed sensor circuit", Kind.TRANS_SENSOR)
        d("P0721", "Transmission output speed sensor range/performance", Kind.TRANS_SENSOR)
        d("P0722", "Transmission output speed sensor no signal", Kind.TRANS_SENSOR)
        d("P0725", "Engine speed input circuit", Kind.TRANS_SENSOR)
        d("P0730", "Incorrect gear ratio", Kind.TRANS_RATIO)
        for ((n, gear) in listOf(1 to "1st gear", 2 to "2nd gear", 3 to "3rd gear", 4 to "4th gear", 5 to "5th gear", 6 to "Reverse")) {
            d(String.format("P%04d", 730 + n), "$gear incorrect ratio", Kind.TRANS_RATIO)
        }
        d("P0740", "Torque converter clutch solenoid circuit", Kind.TRANS_SOLENOID)
        d("P0741", "Torque converter clutch solenoid performance or stuck off", Kind.TRANS_SOLENOID)
        d("P0742", "Torque converter clutch solenoid stuck on", Kind.TRANS_SOLENOID)
        d("P0743", "Torque converter clutch solenoid electrical", Kind.TRANS_SOLENOID)
        for ((letter, base) in listOf("A" to 750, "B" to 755, "C" to 760, "D" to 765, "E" to 770)) {
            d(String.format("P%04d", base), "Shift solenoid $letter", Kind.TRANS_SOLENOID)
            d(String.format("P%04d", base + 1), "Shift solenoid $letter performance or stuck off", Kind.TRANS_SOLENOID)
            d(String.format("P%04d", base + 2), "Shift solenoid $letter stuck on", Kind.TRANS_SOLENOID)
            d(String.format("P%04d", base + 3), "Shift solenoid $letter electrical", Kind.TRANS_SOLENOID)
        }
        d("P0780", "Shift error", Kind.TRANS_RATIO)

        // Diesel exhaust treatment
        d("P2002", "Diesel particulate filter efficiency below threshold (bank 1)", Kind.DIESEL_AFTERTREATMENT)
        d("P242F", "Diesel particulate filter restriction: ash build-up", Kind.DIESEL_AFTERTREATMENT)
        d("P2459", "Diesel particulate filter regenerating too often", Kind.DIESEL_AFTERTREATMENT)
        d("P2463", "Diesel particulate filter restriction: soot build-up", Kind.DIESEL_AFTERTREATMENT)
        d("P20EE", "SCR NOx catalyst efficiency below threshold (bank 1)", Kind.DIESEL_AFTERTREATMENT)

        // Network
        d("U0001", "High-speed CAN communication bus", Kind.NETWORK)
        d("U0073", "Control module communication bus A off", Kind.NETWORK)
        d("U0100", "Lost communication with the engine computer (ECM/PCM)", Kind.NETWORK)
        d("U0101", "Lost communication with the transmission computer (TCM)", Kind.NETWORK)
        d("U0102", "Lost communication with the transfer case control module", Kind.NETWORK)
        d("U0121", "Lost communication with the ABS control module", Kind.NETWORK)
        d("U0126", "Lost communication with the steering angle sensor module", Kind.NETWORK)
        d("U0131", "Lost communication with the power steering control module", Kind.NETWORK)
        d("U0140", "Lost communication with the body control module", Kind.NETWORK)
        d("U0151", "Lost communication with the airbag (restraints) control module", Kind.NETWORK)
        d("U0155", "Lost communication with the instrument cluster", Kind.NETWORK)
        d("U0164", "Lost communication with the climate control (HVAC) module", Kind.NETWORK)
        d("U0184", "Lost communication with the radio", Kind.NETWORK)
        d("U0401", "Invalid data received from the engine computer (ECM/PCM)", Kind.NETWORK)
        d("U0402", "Invalid data received from the transmission computer (TCM)", Kind.NETWORK)
        d("U0415", "Invalid data received from the ABS control module", Kind.NETWORK)
        m
    }

    /** What this kind of electrical or performance fault usually means, from the code's name. */
    private fun faultType(title: String): String? {
        val t = title.lowercase()
        return when {
            "circuit low" in t || "low voltage" in t ->
                "The signal is lower than the part can really give, which usually means a wire shorted to ground, a broken wire or a failed part rather than a real reading."
            "circuit high" in t || "high voltage" in t ->
                "The signal is higher than the part can really give, which usually means a broken or disconnected wire, a short to power or a failed part rather than a real reading."
            "range/performance" in t ->
                "The signal is within its limits but doesn't make sense alongside the car's other readings, or doesn't change the way it should."
            "intermittent" in t ->
                "The signal drops out or jumps now and then, which often points to a loose connector or a chafed wire."
            "no activity" in t || "no signal" in t ->
                "The engine computer isn't seeing any signal from this part."
            "slow response" in t ->
                "The sensor is working but reacting too slowly, which is typical of an ageing sensor."
            t.endsWith("circuit") || "circuit (" in t || "circuit open" in t || "electrical" in t ->
                "The engine computer has found an electrical fault in this circuit: an open or shorted wire, a poor connection or a failed part."
            else -> null
        }
    }

    fun lookup(code: String, system: String): DtcInfo? {
        val def = defs[code] ?: return null
        // An oxygen sensor's low or high voltage can be a real lean or rich mixture, not just wiring.
        val t = def.title.lowercase()
        val fault = when {
            def.kind == Kind.O2 && "low voltage" in t -> "The sensor has read lean (low voltage) for longer than expected. That can be a real lean mixture or an exhaust leak, as well as a sensor or wiring fault."
            def.kind == Kind.O2 && "high voltage" in t -> "The sensor has read rich (high voltage) for longer than expected. That can be a real rich mixture, as well as a sensor or wiring fault."
            def.kind == Kind.O2 && "stuck lean" in t -> "The sensor's signal stays lean when the engine computer expects it to switch. That can be a real lean mixture, an exhaust leak or an ageing sensor."
            def.kind == Kind.O2 && "stuck rich" in t -> "The sensor's signal stays rich when the engine computer expects it to switch. That can be a real rich mixture or an ageing sensor."
            else -> faultType(def.title)
        }
        val meaning = listOfNotNull(fault, def.kind.part).joinToString(" ")
        return DtcInfo(
            code, def.title, meaning, def.kind.severity, def.kind.causes, def.kind.symptoms, def.kind.checks,
            system = system,
        )
    }

    /** Every code with a standard definition here (for tests). */
    val codes: Set<String> get() = defs.keys
}
