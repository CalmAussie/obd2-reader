package com.obdreader.app.obd

import java.util.Locale

/**
 * Plain-English explanations for OBD2 trouble codes: what the code means, how urgent it is,
 * common causes, what you might notice and what to check first.
 *
 * Generic SAE codes (P0xxx, P2xxx, U0xxx) have the same meaning on every car. The common ones
 * are written out in full here; many more have their standard definition in [DtcGeneric].
 * Codes in neither are explained from their structure (system and subsystem), and
 * manufacturer-specific codes (P1xxx, P3xxx and so on) say so plainly.
 */
object DtcLibrary {

    enum class Severity(val label: String, val advice: String) {
        HIGH("Stop soon", "Driving on can damage the engine or catalytic converter, or leave you stranded. Get it checked as soon as you can."),
        MEDIUM("Check soon", "The car is usually safe to drive for now, but fuel use, emissions or performance may suffer. Get it checked in the next few weeks."),
        LOW("Check when convenient", "Usually safe to drive. It mainly affects emissions or a minor system, but it will keep the check-engine light on."),
        UNKNOWN("Varies", "How serious this is depends on the car. Look it up for your make or ask a mechanic."),
    }

    data class DtcInfo(
        val code: String,
        /** Short name, e.g. "Cylinder 3 misfire detected". */
        val title: String,
        /** What the code means, in one or two sentences. */
        val meaning: String,
        val severity: Severity,
        val causes: List<String> = emptyList(),
        val symptoms: List<String> = emptyList(),
        /** What to check first, in a sensible order. */
        val checks: List<String> = emptyList(),
        /** "Engine and transmission (powertrain), generic code" style description of the code's family. */
        val system: String = "",
        /** True when the code is from the catalogue rather than worked out from its structure. */
        val known: Boolean = true,
    )

    private val S_HIGH = Severity.HIGH
    private val S_MED = Severity.MEDIUM
    private val S_LOW = Severity.LOW

    // ---------- Shared wording ----------

    private val LEAN_CAUSES = listOf(
        "A vacuum leak, such as a split intake hose, a loose intake boot or a leaking intake gasket",
        "A dirty or faulty mass air flow (MAF) sensor reading less air than is really entering",
        "Low fuel pressure from a weak fuel pump or a blocked fuel filter",
        "Clogged or leaking fuel injectors",
        "An exhaust leak before the front oxygen sensor",
    )
    private val RICH_CAUSES = listOf(
        "A leaking fuel injector or a fuel pressure regulator stuck closed",
        "A faulty MAF or coolant temperature sensor making the computer add too much fuel",
        "A very dirty air filter",
        "A leaking purge valve letting fuel vapour into the intake",
        "A faulty front oxygen sensor",
    )
    private val MISFIRE_SYMPTOMS = listOf(
        "Rough or shaky idle",
        "Hesitation or loss of power when accelerating",
        "A flashing check-engine light while driving (a severe misfire)",
        "Higher fuel use",
    )

    private fun bank(n: Int) = "bank $n"

    // ---------- The catalogue ----------

    private val catalogue: Map<String, DtcInfo> = buildMap {
        fun add(code: String, title: String, meaning: String, severity: Severity, causes: List<String>, symptoms: List<String>, checks: List<String>) {
            put(code, DtcInfo(code, title, meaning, severity, causes, symptoms, checks))
        }

        // Camshaft timing and position
        for ((code, b, kind, dir) in listOf(
            Quad("P0011", 1, "Intake", "over-advanced"), Quad("P0012", 1, "Intake", "over-retarded"),
            Quad("P0014", 1, "Exhaust", "over-advanced"), Quad("P0021", 2, "Intake", "over-advanced"),
            Quad("P0022", 2, "Intake", "over-retarded"), Quad("P0024", 2, "Exhaust", "over-advanced"),
        )) {
            add(code, "$kind camshaft timing $dir (${bank(b)})",
                "The engine computer asked the variable valve timing system to move the ${kind.lowercase(Locale.US)} camshaft, but it's sitting further $dir than requested.",
                S_MED,
                listOf("Low or old engine oil (the timing system is moved by oil pressure)", "A sticking or faulty oil control (VVT) solenoid", "A worn or stretched timing chain, or a faulty cam phaser", "Sludge blocking the solenoid's oil screen"),
                listOf("Rough idle", "Rattling from the front of the engine at start-up", "Poor fuel economy or loss of power"),
                listOf("Check the oil level and condition first, and change it if it's old", "Test the VVT solenoid and clean or replace its screen", "Check timing chain stretch if the code returns"))
        }
        add("P0016", "Crankshaft and camshaft positions don't match (bank 1)",
            "The crankshaft and camshaft sensors disagree about where the engine is in its cycle, which usually means the timing is out.",
            S_HIGH,
            listOf("A stretched timing chain or worn guides", "A timing belt that has jumped a tooth", "A faulty crank or cam sensor, or a damaged reluctor ring", "Low oil affecting the cam phaser"),
            listOf("Rattle at start-up", "Rough running or stalling", "Hard starting"),
            listOf("Check oil level and condition", "Check the cam and crank sensors and their wiring", "Have the timing chain or belt inspected; a jumped belt can damage the engine"))

        // Fuel and air metering
        add("P0087", "Fuel rail or system pressure too low",
            "Fuel pressure in the rail is below what the engine computer expects, so the engine may not get enough fuel.",
            S_HIGH,
            listOf("A weak or failing fuel pump", "A blocked fuel filter", "Running very low on fuel", "A faulty fuel pressure sensor or regulator", "On diesels, air in the fuel or a failing high-pressure pump"),
            listOf("Loss of power, especially under load", "Hesitation or surging", "Stalling or hard starting"),
            listOf("Make sure there's plenty of fuel in the tank", "Replace the fuel filter if it's due", "Have the fuel pressure measured with a gauge"))
        add("P0100", "Mass air flow (MAF) sensor circuit fault",
            "The engine computer has a problem with the signal from the mass air flow sensor, which measures how much air enters the engine.",
            S_MED,
            listOf("A disconnected or damaged MAF connector", "Damaged wiring", "A failed MAF sensor"),
            listOf("Rough idle or stalling", "Poor acceleration", "Higher fuel use"),
            listOf("Check the MAF connector is clipped in", "Inspect the wiring", "Clean the sensor with MAF cleaner, then replace it if needed"))
        add("P0101", "Mass air flow (MAF) sensor reading out of range",
            "The airflow the MAF sensor reports doesn't match what the engine computer expects from the engine speed and throttle.",
            S_MED,
            listOf("A dirty MAF sensor element", "An air leak between the sensor and the engine", "A very dirty or badly fitted air filter", "A failing MAF sensor"),
            listOf("Hesitation or poor acceleration", "Rough idle", "Higher fuel use"),
            listOf("Check the air filter and intake ducting for leaks", "Clean the MAF sensor with MAF cleaner (not brake cleaner)", "Compare MAF readings to specification"))
        add("P0102", "Mass air flow (MAF) sensor signal too low",
            "The MAF sensor's signal is lower than possible, usually an electrical fault rather than real low airflow.",
            S_MED,
            listOf("A disconnected connector or broken wire", "A failed MAF sensor", "A very dirty sensor element"),
            listOf("Stalling", "Poor power", "Rough idle"),
            listOf("Check the connector and wiring", "Clean the sensor", "Replace the MAF if the signal stays low"))
        add("P0103", "Mass air flow (MAF) sensor signal too high",
            "The MAF sensor's signal is higher than possible, usually a wiring short or a faulty sensor.",
            S_MED,
            listOf("Wiring shorted to power", "A failed MAF sensor"),
            listOf("Running rich, black smoke or a fuel smell", "Rough idle"),
            listOf("Check the wiring for chafing", "Test or replace the MAF sensor"))
        add("P0106", "Manifold pressure (MAP) sensor reading out of range",
            "The intake manifold pressure reading doesn't make sense for the engine speed and throttle position.",
            S_MED,
            listOf("A vacuum leak or a split MAP sensor hose", "A blocked or faulty MAP sensor", "Wiring problems"),
            listOf("Rough idle", "Poor acceleration", "Higher fuel use"),
            listOf("Check the MAP sensor's vacuum hose", "Check for intake leaks", "Test or replace the MAP sensor"))
        add("P0107", "Manifold pressure (MAP) sensor signal too low",
            "The MAP sensor's signal is lower than possible, usually an open circuit or a failed sensor.",
            S_MED, listOf("An unplugged connector or broken wire", "A failed MAP sensor"), listOf("Rough running", "Stalling"),
            listOf("Check the connector and wiring", "Replace the MAP sensor if the wiring is good"))
        add("P0108", "Manifold pressure (MAP) sensor signal too high",
            "The MAP sensor's signal is higher than possible, often from a wiring short or a leaking vacuum hose.",
            S_MED, listOf("A disconnected or leaking vacuum hose", "Wiring shorted to power", "A failed MAP sensor"), listOf("Running rich", "Poor fuel economy"),
            listOf("Check the vacuum hose", "Check the wiring", "Replace the MAP sensor if needed"))
        add("P0112", "Intake air temperature sensor signal too low",
            "The intake air temperature sensor reads impossibly hot, usually a wiring short or a failed sensor.",
            S_LOW, listOf("A wiring short to ground", "A failed sensor (often built into the MAF)"), listOf("Usually none, or slightly harder cold starts"),
            listOf("Check the wiring and connector", "Replace the sensor"))
        add("P0113", "Intake air temperature sensor signal too high",
            "The intake air temperature sensor reads impossibly cold, usually because it's unplugged or the circuit is broken.",
            S_LOW, listOf("An unplugged connector, often after air filter work", "A broken wire", "A failed sensor"), listOf("Usually none, or slightly rough cold running"),
            listOf("Check the connector, especially if the air box was recently opened", "Check the wiring", "Replace the sensor"))
        add("P0116", "Coolant temperature sensor reading out of range",
            "The coolant temperature reading doesn't change the way it should as the engine warms up.",
            S_MED, listOf("A faulty coolant temperature sensor", "A thermostat stuck open", "Low coolant"), listOf("Temperature gauge acting oddly", "Poor fuel economy", "Cooling fan running at odd times"),
            listOf("Check the coolant level when cold", "Compare the sensor's reading with the actual temperature", "Check the thermostat"))
        add("P0117", "Coolant temperature sensor signal too low",
            "The coolant temperature sensor reads impossibly hot, usually a short in the wiring or sensor.",
            S_MED, listOf("A wiring short to ground", "A failed sensor"), listOf("Cooling fans running constantly", "Hard starting", "Poor fuel economy"),
            listOf("Check the wiring and connector", "Replace the sensor"))
        add("P0118", "Coolant temperature sensor signal too high",
            "The coolant temperature sensor reads impossibly cold, usually because it's unplugged or the wiring is broken.",
            S_MED, listOf("An unplugged connector or broken wire", "A failed sensor", "Corroded terminals"), listOf("Hard starting when warm", "Black smoke or high fuel use", "Temperature gauge reading cold"),
            listOf("Check the connector and wiring", "Replace the sensor"))
        add("P0121", "Throttle position sensor reading out of range",
            "The throttle position sensor's reading doesn't match the airflow and other readings the computer expects.",
            S_MED, listOf("A worn throttle position sensor", "A dirty throttle body", "Wiring problems"), listOf("Hesitation or surging", "Erratic idle", "Limp mode with reduced power on some cars"),
            listOf("Clean the throttle body", "Check the sensor wiring", "Replace the sensor or throttle body"))
        add("P0122", "Throttle position sensor signal too low",
            "The throttle position sensor's signal is lower than possible, usually an open circuit.",
            S_MED, listOf("A disconnected connector or broken wire", "A failed sensor"), listOf("Poor acceleration", "Limp mode"),
            listOf("Check the connector and wiring", "Replace the sensor or throttle body"))
        add("P0123", "Throttle position sensor signal too high",
            "The throttle position sensor's signal is higher than possible, usually a short to power.",
            S_MED, listOf("Wiring shorted to power", "A failed sensor"), listOf("High or surging idle", "Limp mode"),
            listOf("Check the wiring", "Replace the sensor or throttle body"))
        add("P0128", "Engine not reaching operating temperature (thermostat)",
            "The coolant takes too long to warm up, which almost always means the thermostat is stuck open.",
            S_LOW,
            listOf("A thermostat stuck open", "A faulty coolant temperature sensor", "A cooling fan running all the time", "Low coolant"),
            listOf("Temperature gauge stays low", "Weak cabin heater", "Higher fuel use"),
            listOf("Check the coolant level when cold", "Watch the coolant temperature on the dashboard: it should reach about 85 to 105 °C", "Replace the thermostat, a common and cheap fix"))

        // Oxygen sensors: circuit, slow response and heater, for each bank and sensor position
        val o2 = listOf(
            "P0130" to Pair(1, 1), "P0136" to Pair(1, 2), "P0150" to Pair(2, 1), "P0156" to Pair(2, 2),
        )
        for ((base, pos) in o2) {
            val (b, s) = pos
            val where = "${bank(b)}, sensor $s"
            val role = if (s == 1) "the front oxygen sensor (before the catalytic converter), which the computer uses to set the fuel mixture," else "the rear oxygen sensor (after the catalytic converter), which checks the converter is working,"
            val n = base.substring(1).toInt()
            fun c(off: Int) = "P" + String.format(Locale.US, "%04d", n + off)
            add(c(0), "Oxygen sensor circuit fault ($where)",
                "There's a problem with the signal from ${role.removeSuffix(",")}.",
                S_MED, listOf("A failed oxygen sensor", "Damaged or melted wiring near the exhaust", "An exhaust leak near the sensor"), listOf("Higher fuel use", "Rough running if it's the front sensor"),
                listOf("Check the sensor wiring near the exhaust", "Check for exhaust leaks", "Replace the sensor"))
            add(c(1), "Oxygen sensor circuit low voltage ($where)",
                "${role.replaceFirstChar { it.uppercase() }} is reading low (lean) all the time.",
                S_MED, listOf("An exhaust or vacuum leak letting extra air in", "A failed sensor", "Wiring shorted to ground"), listOf("Higher fuel use"),
                listOf("Check for exhaust and vacuum leaks", "Check the wiring", "Replace the sensor"))
            add(c(2), "Oxygen sensor circuit high voltage ($where)",
                "${role.replaceFirstChar { it.uppercase() }} is reading high (rich) all the time.",
                S_MED, listOf("The engine really running rich", "Wiring shorted to power", "A sensor contaminated by fuel, oil or coolant"), listOf("Higher fuel use", "Fuel smell"),
                listOf("Check for a rich condition and related codes", "Check the wiring", "Replace the sensor"))
            add(c(3), "Oxygen sensor slow response ($where)",
                "${role.replaceFirstChar { it.uppercase() }} is reacting too slowly to changes in the exhaust. It's usually an ageing sensor.",
                S_MED,
                listOf("An old, worn-out oxygen sensor (common after 150,000 km)", "A sensor contaminated by oil burning or a coolant leak", "An exhaust leak near the sensor"),
                listOf("Higher fuel use", "Can lead to a catalytic converter code if ignored"),
                listOf("Check for exhaust leaks near the sensor", "Check the engine isn't burning oil or coolant", "Replace the sensor"))
            add(c(4), "Oxygen sensor shows no activity ($where)",
                "${role.replaceFirstChar { it.uppercase() }} isn't switching at all, as if it's dead or disconnected.",
                S_MED, listOf("A failed sensor", "An unplugged connector or broken wire", "A failed sensor heater keeping it too cold to work"), listOf("Higher fuel use"),
                listOf("Check the connector and wiring", "Check the heater circuit", "Replace the sensor"))
            add(c(5), "Oxygen sensor heater circuit fault ($where)",
                "The heater inside the oxygen sensor isn't working. The heater gets the sensor up to temperature quickly after a cold start.",
                S_LOW, listOf("A failed sensor heater (the sensor needs replacing)", "A blown fuse for the heater circuit", "Damaged wiring"), listOf("Usually none, or slightly higher fuel use after cold starts"),
                listOf("Check the fuse", "Check the wiring", "Replace the sensor"))
        }

        // Fuel trim
        for ((code, b) in listOf("P0171" to 1, "P0174" to 2)) {
            add(code, "Fuel mixture too lean (${bank(b)})",
                "The engine is getting too much air or too little fuel, and the computer has reached the limit of how much fuel it can add to compensate.",
                S_MED, LEAN_CAUSES, listOf("Rough or hunting idle", "Hesitation when accelerating", "Misfires if severe"),
                listOf("Listen for hissing vacuum leaks and check intake hoses", "Clean the MAF sensor", "Check fuel pressure", "Look at long-term fuel trim on the dashboard: above +10% confirms lean"))
        }
        for ((code, b) in listOf("P0172" to 1, "P0175" to 2)) {
            add(code, "Fuel mixture too rich (${bank(b)})",
                "The engine is getting too much fuel or too little air, and the computer has reached the limit of how much fuel it can take away.",
                S_MED, RICH_CAUSES, listOf("Strong fuel smell", "Black smoke", "Poor fuel economy", "Fouled spark plugs"),
                listOf("Check the air filter", "Check for leaking injectors", "Check the coolant temperature reading is sensible", "Test the purge valve"))
        }
        add("P0191", "Fuel rail pressure sensor reading out of range",
            "The fuel rail pressure reading doesn't match what the computer expects.",
            S_MED, listOf("A faulty fuel rail pressure sensor", "Low fuel pressure", "Wiring problems"), listOf("Hard starting", "Loss of power", "Stalling"),
            listOf("Check the sensor wiring", "Measure fuel pressure", "Replace the sensor"))

        // Injectors 1 to 12
        for (cyl in 1..12) {
            add("P02" + String.format(Locale.US, "%02d", cyl), "Fuel injector circuit fault (cylinder $cyl)",
                "The engine computer detected a fault in the electrical circuit of cylinder $cyl's fuel injector, so that cylinder may not get fuel.",
                S_HIGH, listOf("A failed injector", "A damaged connector or wire to the injector", "A faulty injector driver in the engine computer (rare)"),
                listOf("Misfire on cylinder $cyl", "Rough running", "Loss of power"),
                listOf("Check the injector connector", "Measure the injector's resistance", "Swap injectors between cylinders to see if the fault follows"))
        }
        add("P0217", "Engine overheating",
            "The engine coolant temperature has gone above the safe limit.",
            S_HIGH, listOf("Low coolant from a leak", "A failed water pump or thermostat stuck closed", "A cooling fan not working", "A blocked radiator", "A failing head gasket"),
            listOf("Temperature gauge in the red", "Steam from under the bonnet", "Reduced power as the engine protects itself"),
            listOf("Stop and turn the engine off. Don't open the radiator cap while hot", "Check the coolant level once cool", "Check the cooling fans run when hot", "Look for leaks"))
        add("P0234", "Turbo or supercharger overboost",
            "Boost pressure went higher than the engine computer allows.",
            S_HIGH, listOf("A sticking wastegate or wastegate actuator", "A faulty boost control solenoid", "A split or disconnected wastegate hose", "Aftermarket tuning"),
            listOf("Surging power", "Limp mode with sudden power loss"),
            listOf("Check the wastegate hoses", "Check the wastegate moves freely", "Test the boost control solenoid"))
        add("P0299", "Turbo or supercharger underboost",
            "Boost pressure is lower than the engine computer asked for.",
            S_MED, listOf("A boost leak from a split intercooler hose or loose clamp", "A sticking wastegate", "A worn turbo", "A blocked air filter or, on diesels, a blocked particulate filter"),
            listOf("Loss of power", "Whistling or whooshing under boost", "Limp mode"),
            listOf("Check intercooler hoses and clamps for leaks", "Check the air filter", "Check the wastegate and turbo for play"))

        // Misfires
        add("P0300", "Random or multiple cylinder misfire",
            "The engine computer detected misfires on more than one cylinder, or on cylinders that change.",
            S_HIGH,
            listOf("Worn spark plugs or failing ignition coils", "A vacuum leak", "Low fuel pressure", "A lean mixture", "Low compression"),
            MISFIRE_SYMPTOMS,
            listOf("If the check-engine light is flashing, ease off and avoid hard driving: unburned fuel can overheat the catalytic converter", "Check spark plugs and coils", "Check for vacuum leaks and look for lean codes", "Check fuel pressure"))
        for (cyl in 1..12) {
            add("P03" + String.format(Locale.US, "%02d", cyl), "Cylinder $cyl misfire detected",
                "Cylinder $cyl isn't firing properly, so it's not adding its share of power and unburned fuel is going into the exhaust.",
                S_HIGH,
                listOf("A worn spark plug in cylinder $cyl", "A failing ignition coil or plug lead for cylinder $cyl", "A clogged or failing fuel injector", "Low compression from a valve or ring problem"),
                MISFIRE_SYMPTOMS,
                listOf("If the check-engine light is flashing, ease off: a severe misfire can overheat the catalytic converter", "Swap cylinder $cyl's coil with a neighbour's: if the misfire moves, replace the coil", "Check the spark plug", "If the misfire stays, check the injector and do a compression test"))
        }

        // Knock, crank and cam sensors
        for ((code, b) in listOf("P0325" to 1, "P0330" to 2)) {
            add(code, "Knock sensor circuit fault (${bank(b)})",
                "There's an electrical fault in the knock sensor circuit. The knock sensor listens for engine knock so the computer can retard ignition timing to protect the engine.",
                S_MED, listOf("A failed knock sensor", "Damaged wiring (often under the intake manifold)", "A loose sensor"),
                listOf("Reduced power and fuel economy, because the computer plays it safe with timing", "Sometimes no symptoms"),
                listOf("Check the wiring and connector", "Check the sensor is tightened correctly", "Replace the sensor"))
        }
        add("P0335", "Crankshaft position sensor circuit fault",
            "The engine computer isn't getting a usable signal from the crankshaft position sensor, which tells it the engine's speed and position.",
            S_HIGH, listOf("A failed crankshaft sensor", "Damaged wiring or connector", "A damaged reluctor (tone) ring"),
            listOf("Engine cranks but won't start", "Stalling while driving, often when hot", "Tachometer dropping out"),
            listOf("Check the sensor connector and wiring", "Check the sensor's resistance or signal", "Replace the sensor"))
        add("P0336", "Crankshaft position sensor reading out of range",
            "The crankshaft sensor's signal is erratic or doesn't match what's expected.",
            S_HIGH, listOf("A failing sensor", "Debris on the sensor tip", "A damaged reluctor ring", "Electrical interference from poorly routed wiring"),
            listOf("Intermittent stalling", "Misfires", "Hard starting"),
            listOf("Check the sensor and its tip", "Check the wiring routing", "Replace the sensor"))
        for ((code, b) in listOf("P0340" to 1, "P0345" to 2)) {
            add(code, "Camshaft position sensor circuit fault (${bank(b)})",
                "The engine computer isn't getting a usable signal from the camshaft position sensor, which it uses to time fuel injection and spark.",
                S_MED, listOf("A failed camshaft sensor", "Damaged wiring or connector", "Timing chain or belt problems"),
                listOf("Hard or slow starting", "Rough running", "Stalling on some cars"),
                listOf("Check the sensor connector and wiring", "Replace the sensor", "Check the timing if the code returns"))
        }
        add("P0341", "Camshaft position sensor reading out of range (bank 1)",
            "The camshaft sensor's signal doesn't line up with the crankshaft sensor's.",
            S_MED, listOf("A failing cam sensor", "A stretched timing chain or jumped belt", "Wiring problems"), listOf("Hard starting", "Rough running"),
            listOf("Check the sensor and wiring", "Check timing if the sensor is good"))

        // Ignition coils A to L (cylinders 1 to 12)
        for (cyl in 1..12) {
            val letter = 'A' + (cyl - 1)
            add("P03" + (50 + cyl), "Ignition coil $letter circuit fault (usually cylinder $cyl)",
                "The engine computer detected a fault in ignition coil $letter's circuit, which on most engines fires cylinder $cyl. That cylinder may not get a spark.",
                S_HIGH, listOf("A failed ignition coil", "A damaged connector or wire to the coil", "A faulty coil driver in the engine computer (rare)"),
                listOf("Misfire on cylinder $cyl", "Rough running", "Flashing check-engine light"),
                listOf("Check the coil connector", "Swap the coil with a neighbour's and see if the fault moves", "Replace the coil"))
        }
        add("P0380", "Glow plug heater circuit fault",
            "There's a fault in the diesel glow plug circuit, which heats the cylinders for cold starts.",
            S_MED, listOf("One or more failed glow plugs", "A failed glow plug relay or control module", "Damaged wiring"),
            listOf("Hard starting in cold weather", "White smoke after starting", "Rough running when cold"),
            listOf("Test each glow plug's resistance", "Check the glow plug relay or module", "Check the wiring"))

        // Emissions: EGR, secondary air, catalytic converter, EVAP
        add("P0400", "Exhaust gas recirculation (EGR) flow fault",
            "The EGR system, which sends some exhaust back into the intake to cut emissions, isn't flowing as expected.",
            S_MED, listOf("A carbon-clogged EGR valve or passages", "A failed EGR valve", "A faulty EGR solenoid or vacuum hose"), listOf("Rough idle", "Pinging when accelerating"),
            listOf("Check the EGR valve and passages for carbon", "Check vacuum hoses to the valve", "Test the EGR valve"))
        add("P0401", "Exhaust gas recirculation (EGR) flow too low",
            "Not enough exhaust gas is flowing back through the EGR system, usually because of carbon build-up.",
            S_MED, listOf("Carbon blocking the EGR valve or passages (very common)", "A stuck EGR valve", "A faulty EGR position sensor or vacuum solenoid"),
            listOf("Pinging or knocking under load", "Sometimes no symptoms"),
            listOf("Clean the EGR valve and its passages", "Check the vacuum supply or electrical control", "Replace the valve if it doesn't move"))
        add("P0402", "Exhaust gas recirculation (EGR) flow too high",
            "Too much exhaust is flowing back into the intake, often because the EGR valve is stuck open.",
            S_MED, listOf("An EGR valve stuck open with carbon", "A faulty EGR solenoid"), listOf("Rough idle or stalling", "Hesitation"),
            listOf("Check the EGR valve closes fully", "Clean or replace the valve"))
        add("P0403", "Exhaust gas recirculation (EGR) control circuit fault",
            "There's an electrical fault in the circuit that controls the EGR valve.",
            S_MED, listOf("A failed EGR solenoid or valve motor", "Damaged wiring or connector"), listOf("Rough idle", "Pinging"),
            listOf("Check the connector and wiring", "Test the solenoid or valve"))
        add("P0404", "Exhaust gas recirculation (EGR) reading out of range",
            "The EGR valve isn't in the position the computer asked for.",
            S_MED, listOf("Carbon making the valve stick", "A failing EGR valve or position sensor"), listOf("Rough idle", "Hesitation"),
            listOf("Clean the EGR valve", "Replace it if it still sticks"))
        add("P0410", "Secondary air injection system fault",
            "The secondary air system, which pumps air into the exhaust after a cold start to heat up the catalytic converter, isn't working.",
            S_LOW, listOf("A failed air pump", "A stuck check valve", "A blown fuse or failed relay", "Moisture damage in the pump"),
            listOf("Usually none, or a noisy air pump after cold starts"),
            listOf("Check the fuse and relay", "Check the pump runs after a cold start", "Check the hoses and check valve"))
        for ((code, b) in listOf("P0420" to 1, "P0430" to 2)) {
            add(code, "Catalytic converter efficiency below threshold (${bank(b)})",
                "The rear oxygen sensor shows the catalytic converter isn't cleaning the exhaust as well as it should.",
                S_LOW,
                listOf("An old or failing catalytic converter", "A faulty rear oxygen sensor", "An exhaust leak near the sensors", "A misfire or oil burning that has damaged the converter"),
                listOf("Usually none", "Sometimes a rotten-egg smell or reduced power if the converter is blocked", "May fail an emissions inspection"),
                listOf("Fix any misfire or fuel mixture codes first: they can cause this one", "Check for exhaust leaks", "Compare front and rear oxygen sensor readings", "Replace the converter only after ruling out sensors and leaks"))
        }
        add("P0440", "Evaporative emissions (EVAP) system fault",
            "The EVAP system, which traps fuel vapour from the tank, has a problem.",
            S_LOW, listOf("A loose or faulty fuel cap", "A leaking EVAP hose", "A faulty purge or vent valve"), listOf("Usually none", "Sometimes a fuel smell"),
            listOf("Check the fuel cap is tight and its seal is intact", "Inspect EVAP hoses", "Test the purge and vent valves"))
        add("P0441", "Evaporative emissions (EVAP) incorrect purge flow",
            "Fuel vapour isn't being drawn from the charcoal canister into the engine as expected.",
            S_LOW, listOf("A faulty purge valve", "A blocked or cracked purge hose"), listOf("Usually none", "Sometimes rough idle after refuelling"),
            listOf("Check the purge valve and hoses"))
        add("P0442", "Evaporative emissions (EVAP) small leak detected",
            "The EVAP system found a small leak, about the size of a pinhole.",
            S_LOW, listOf("A loose or worn fuel cap (the most common cause)", "A cracked EVAP hose", "A faulty purge or vent valve"), listOf("Usually none", "Sometimes a faint fuel smell"),
            listOf("Tighten the fuel cap until it clicks, or replace it if the seal is worn", "Have a smoke test done to find the leak"))
        add("P0443", "Evaporative emissions (EVAP) purge valve circuit fault",
            "There's an electrical fault in the purge valve circuit.",
            S_LOW, listOf("A failed purge valve", "Damaged wiring or connector"), listOf("Usually none", "Sometimes rough idle"),
            listOf("Check the connector and wiring", "Replace the purge valve"))
        add("P0446", "Evaporative emissions (EVAP) vent control fault",
            "The EVAP vent valve, which lets the system breathe, isn't working properly.",
            S_LOW, listOf("A stuck or failed vent valve", "A blocked vent filter", "Wiring problems"), listOf("Usually none", "Sometimes the pump clicks off early when refuelling"),
            listOf("Check the vent valve and filter, usually near the fuel tank"))
        add("P0455", "Evaporative emissions (EVAP) large leak detected",
            "The EVAP system found a large leak, or couldn't hold vacuum at all.",
            S_LOW, listOf("A missing or loose fuel cap", "A disconnected or split EVAP hose", "A purge or vent valve stuck open"), listOf("Fuel smell", "Usually no driving symptoms"),
            listOf("Check the fuel cap first", "Check EVAP hoses near the engine and the tank", "Have a smoke test done"))
        add("P0456", "Evaporative emissions (EVAP) very small leak detected",
            "The EVAP system found a very small leak.",
            S_LOW, listOf("A worn fuel cap seal", "A tiny crack in an EVAP hose or the canister"), listOf("None"),
            listOf("Replace the fuel cap if its seal is worn", "Have a smoke test done if the code returns"))
        add("P0461", "Fuel level sensor reading out of range",
            "The fuel level reading doesn't change as fuel is used, or jumps around.",
            S_LOW, listOf("A worn fuel level sender in the tank", "Wiring problems"), listOf("Fuel gauge stuck or erratic"),
            listOf("Check the wiring at the tank", "Replace the fuel level sender"))
        add("P0480", "Cooling fan relay circuit fault",
            "There's a problem in the circuit that switches on the radiator cooling fan.",
            S_MED, listOf("A failed fan relay or fuse", "A failed fan motor", "Damaged wiring"), listOf("Overheating in traffic", "Air conditioning not cooling at idle"),
            listOf("Check the fan fuse and relay", "Check the fan runs when the engine is hot or the air conditioning is on"))

        // Speed, idle and electrical
        add("P0500", "Vehicle speed sensor fault",
            "The engine computer isn't getting a usable road speed signal.",
            S_MED, listOf("A failed speed sensor", "Damaged wiring", "A faulty ABS wheel sensor on cars that share it"), listOf("Speedometer not working", "Harsh or erratic gear changes", "Cruise control not working"),
            listOf("Check the speed sensor and wiring", "Check for ABS codes as well"))
        add("P0505", "Idle air control system fault",
            "The engine can't control its idle speed properly.",
            S_MED, listOf("A dirty throttle body or idle control valve", "A vacuum leak", "A failed idle control valve"), listOf("Idle too high, too low or hunting", "Stalling"),
            listOf("Clean the throttle body and idle control valve", "Check for vacuum leaks"))
        add("P0506", "Idle speed lower than expected",
            "The engine idles slower than the computer is aiming for.",
            S_LOW, listOf("A dirty throttle body", "A sticking idle control valve", "A restricted air intake"), listOf("Low or rough idle", "Stalling when stopping"),
            listOf("Clean the throttle body", "Check the air filter"))
        add("P0507", "Idle speed higher than expected",
            "The engine idles faster than the computer is aiming for, often because of a vacuum leak.",
            S_LOW, listOf("A vacuum leak", "A sticking idle control valve", "A dirty or sticking throttle"), listOf("High idle", "Car creeps forward in automatic"),
            listOf("Check for vacuum leaks", "Clean the throttle body"))
        add("P0520", "Oil pressure sensor circuit fault",
            "There's a problem with the oil pressure sensor or its circuit.",
            S_MED, listOf("A failed oil pressure sensor", "Damaged wiring", "Low oil pressure"), listOf("Oil pressure warning light or gauge acting oddly"),
            listOf("Check the oil level straight away", "Have the actual oil pressure measured with a gauge", "Replace the sensor"))
        add("P0524", "Engine oil pressure too low",
            "Oil pressure has dropped below the safe level.",
            S_HIGH, listOf("Low oil level", "The wrong oil grade or very old oil", "A worn oil pump", "Worn engine bearings", "A failed oil pressure sensor"),
            listOf("Oil pressure warning light", "Engine noise, ticking or knocking"),
            listOf("Stop and check the oil level now: low oil pressure can destroy an engine", "Top up with the correct oil", "If the level is fine, have the pressure measured before driving far"))
        add("P0562", "System voltage low",
            "The engine computer sees battery voltage below what it needs.",
            S_MED, listOf("A failing alternator", "A weak or old battery", "Loose or corroded battery terminals", "A slipping alternator belt"),
            listOf("Dim lights", "Slow cranking", "Battery warning light", "Other warning lights at random"),
            listOf("Check the battery voltage on the dashboard: about 13.8 to 14.6 V with the engine running is normal", "Clean and tighten the battery terminals", "Have the battery and alternator tested"))
        add("P0563", "System voltage high",
            "The engine computer sees battery voltage above the safe limit, usually an overcharging alternator.",
            S_MED, listOf("A faulty alternator voltage regulator", "A poor ground connection"), listOf("Very bright or flickering lights", "Bulbs blowing", "A swollen battery"),
            listOf("Check charging voltage: above about 15 V means overcharging", "Have the alternator tested"))
        add("P0571", "Brake switch circuit fault",
            "The engine computer has a problem with the brake pedal switch signal.",
            S_MED, listOf("A failed or badly adjusted brake light switch", "A blown brake light fuse"), listOf("Brake lights not working", "Cruise control not working", "Can't shift out of park on some cars"),
            listOf("Check the brake lights work", "Check the fuse", "Adjust or replace the brake switch"))
        add("P0600", "Engine computer communication fault",
            "The engine computer has a problem talking to another module inside it or on the network.",
            S_MED, listOf("Low battery voltage or a bad ground", "Damaged network wiring", "An internal computer fault"), listOf("Various warning lights", "Rough running or no start"),
            listOf("Check battery and ground connections", "Check for other communication (U) codes"))
        add("P0606", "Engine computer internal fault",
            "The engine computer has detected an internal processor fault.",
            S_HIGH, listOf("A failing engine computer", "Low voltage or a bad ground making it look faulty", "Water damage"), listOf("Stalling", "No start", "Limp mode"),
            listOf("Check the battery, fuses and grounds first", "Check for software updates at a dealer", "The engine computer may need repair or replacement"))

        // Transmission
        add("P0700", "Transmission control system fault",
            "The transmission computer has found a fault and stored its own code. This code tells the engine computer to turn the check-engine light on.",
            S_MED, listOf("Any transmission fault: the real code is stored in the transmission computer"), listOf("Harsh or delayed shifts", "Stuck in one gear (limp mode)"),
            listOf("Read the transmission computer's codes with a tool that supports it", "Check the transmission fluid level and condition"))
        add("P0705", "Transmission range sensor fault",
            "The sensor that reports the gear selector position (park, reverse, neutral, drive) has a problem.",
            S_MED, listOf("A failed or misadjusted range sensor", "Damaged wiring"), listOf("Won't start in park or neutral", "Reversing lights not working", "Erratic shifting"),
            listOf("Check the sensor adjustment and wiring", "Replace the sensor"))
        add("P0715", "Transmission input speed sensor fault",
            "The transmission computer isn't getting a usable signal from the input (turbine) speed sensor.",
            S_MED, listOf("A failed speed sensor", "Damaged wiring", "Low fluid"), listOf("Harsh or erratic shifting", "Limp mode", "Speedometer problems on some cars"),
            listOf("Check the fluid level", "Check the sensor and wiring"))
        add("P0720", "Transmission output speed sensor fault",
            "The transmission computer isn't getting a usable signal from the output speed sensor.",
            S_MED, listOf("A failed speed sensor", "Damaged wiring"), listOf("Erratic shifting", "Speedometer not working"),
            listOf("Check the sensor and wiring", "Replace the sensor"))
        add("P0730", "Incorrect gear ratio",
            "The transmission's input and output speeds don't match the gear it should be in, which means something is slipping.",
            S_HIGH, listOf("Low or burnt transmission fluid", "Worn clutches or bands inside the transmission", "A faulty shift solenoid"),
            listOf("Slipping, with revs flaring between gears", "Delayed engagement", "Limp mode"),
            listOf("Check the fluid level and condition: dark or burnt-smelling fluid is a bad sign", "Read the transmission computer's codes", "Avoid hard driving until it's checked"))
        add("P0740", "Torque converter clutch circuit fault",
            "There's a problem with the torque converter clutch, which locks up the converter at cruising speed to save fuel.",
            S_MED, listOf("A faulty torque converter clutch solenoid", "Low or contaminated fluid", "Damaged wiring"), listOf("Higher fuel use on the highway", "Shudder at cruising speed", "Engine stalling when stopping on some cars"),
            listOf("Check the fluid", "Test the solenoid and wiring"))
        for ((code, letter) in listOf("P0750" to "A", "P0755" to "B", "P0760" to "C")) {
            add(code, "Shift solenoid $letter fault",
                "There's a problem with shift solenoid $letter, which the transmission uses to change gear.",
                S_MED, listOf("A failed shift solenoid", "Damaged wiring", "Low or dirty fluid"), listOf("Harsh or missed shifts", "Stuck in one gear"),
                listOf("Check the fluid level and condition", "Test the solenoid and wiring"))
        }

        // Newer generic codes
        for ((code, lean, b) in listOf(Triple("P2096", true, 1), Triple("P2097", false, 1), Triple("P2098", true, 2), Triple("P2099", false, 2))) {
            add(code, "Post-converter fuel trim too ${if (lean) "lean" else "rich"} (${bank(b)})",
                "The rear oxygen sensor shows the exhaust after the catalytic converter is running too ${if (lean) "lean" else "rich"}.",
                S_MED, if (lean) LEAN_CAUSES.take(3) + "An exhaust leak near the rear oxygen sensor" else RICH_CAUSES.take(3), listOf("Higher fuel use", "Often appears with catalytic converter codes"),
                listOf("Check for exhaust leaks", "Check front and rear oxygen sensors", "Look for related fuel mixture codes"))
        }
        add("P2135", "Throttle position sensors A and B disagree",
            "Electronic throttles have two position sensors that must agree. Their readings don't match, so the car may go into limp mode for safety.",
            S_HIGH, listOf("A failing throttle body (the sensors are built in)", "A dirty throttle body", "Wiring or connector problems"), listOf("Limp mode with very little power", "Erratic idle", "Throttle not responding"),
            listOf("Check the throttle body connector", "Clean the throttle body", "Replace the throttle body if the code returns"))
        for ((code, lean, b) in listOf(Triple("P2187", true, 1), Triple("P2188", false, 1), Triple("P2189", true, 2), Triple("P2190", false, 2))) {
            add(code, "Fuel mixture too ${if (lean) "lean" else "rich"} at idle (${bank(b)})",
                "The fuel mixture is too ${if (lean) "lean" else "rich"} at idle, though it may be fine at higher revs.",
                S_MED, if (lean) LEAN_CAUSES.take(2) + "A leaking PCV valve or hose" else RICH_CAUSES.take(3), listOf("Rough idle"),
                listOf(if (lean) "Check for vacuum leaks, which show up most at idle" else "Check for leaking injectors and the purge valve", "Clean the MAF sensor"))
        }
        add("P2002", "Diesel particulate filter efficiency below threshold (bank 1)",
            "The diesel particulate filter isn't trapping soot as well as it should, often because it's damaged or the pressure sensor is faulty.",
            S_MED, listOf("A cracked or damaged particulate filter", "A faulty differential pressure sensor or split sensor hose"), listOf("Usually none", "Sometimes black smoke"),
            listOf("Check the pressure sensor hoses", "Test the differential pressure sensor", "Have the filter inspected"))
        add("P2463", "Diesel particulate filter soot build-up too high",
            "The diesel particulate filter is clogged with soot and needs to burn it off (regenerate).",
            S_MED, listOf("Lots of short trips that never let the filter regenerate", "A faulty pressure sensor", "Injector or EGR problems making extra soot"), listOf("Loss of power", "Limp mode", "A DPF warning light"),
            listOf("A 20 to 30 minute highway drive above about 60 km/h may clear it", "If not, a workshop can run a forced regeneration", "Fix any EGR or injector faults"))

        // Network
        add("U0073", "Control module communication bus off",
            "One of the car's data networks has stopped working, so modules can't talk to each other.",
            S_HIGH, listOf("A short in the network wiring", "A faulty module pulling the network down", "Low battery voltage"), listOf("Several warning lights at once", "Systems not working", "No start on some cars"),
            listOf("Check the battery voltage and connections", "Check for water damage and wiring damage", "Unplug modules one at a time to find the culprit (a workshop job)"))
        add("U0100", "Lost communication with the engine computer",
            "Another module stopped hearing from the engine computer on the car's network.",
            S_HIGH, listOf("Low battery voltage or a bad ground", "A blown engine computer fuse", "Damaged network wiring", "A failing engine computer"), listOf("Stalling or no start", "Several warning lights"),
            listOf("Check the battery and ground connections", "Check the engine computer's fuses", "Check its connectors for corrosion"))
        add("U0101", "Lost communication with the transmission computer",
            "Another module stopped hearing from the transmission computer.",
            S_HIGH, listOf("Low battery voltage", "A blown fuse", "Damaged wiring", "A failing transmission computer"), listOf("Stuck in one gear", "Harsh shifts"),
            listOf("Check the battery, fuses and grounds", "Check the transmission computer's connector"))
        add("U0121", "Lost communication with the ABS module",
            "Another module stopped hearing from the anti-lock brake (ABS) module.",
            S_MED, listOf("A blown ABS fuse", "Damaged wiring", "A failing ABS module"), listOf("ABS and traction control warning lights", "Speedometer problems on some cars"),
            listOf("Check the ABS fuses", "Check the ABS module connector, which is exposed to water under the bonnet"))
        add("U0140", "Lost communication with the body control module",
            "Another module stopped hearing from the body control module, which runs lights, locks and similar.",
            S_MED, listOf("Low battery voltage", "A blown fuse", "Damaged wiring"), listOf("Lights, locks or wipers not working properly"),
            listOf("Check the battery and fuses", "Check the body control module's connectors"))
    }

    private data class Quad(val a: String, val b: Int, val c: String, val d: String)

    // ---------- Codes not in the catalogue: explained from their structure ----------

    private fun systemOf(code: String): String {
        val c = code.uppercase(Locale.US)
        val system = when (c.firstOrNull()) {
            'P' -> "Engine and transmission (powertrain)"
            'C' -> "Chassis: brakes, steering and suspension"
            'B' -> "Body: airbags, seats, climate, lights and similar"
            'U' -> "Network: communication between the car's computers"
            else -> "Unknown system"
        }
        // P0 and P2 codes are generic, as are B0, C0, U0 and the B3, C3 and U3 ranges.
        // So is P3400-P34FF (cylinder deactivation). P1, B1, B2, C1, C2, U1, U2 and the rest of the
        // P3 range are defined by each manufacturer.
        val second = c.getOrNull(1)
        val generic = second == '0' || (c.firstOrNull() == 'P' && second == '2') || (c.firstOrNull() != 'P' && second == '3') || c.startsWith("P34")
        return "$system, ${if (generic) "generic code (same meaning on every car)" else "manufacturer-specific code"}"
    }

    private fun subsystem(code: String): String? {
        val c = code.uppercase(Locale.US)
        if (c.firstOrNull() != 'P' || c.getOrNull(1) !in listOf('0', '2')) return null
        return when (c.getOrNull(2)) {
            '0' -> "fuel and air metering, or emission controls"
            '1', '2' -> "fuel and air metering"
            '3' -> "ignition or misfire detection"
            '4' -> "auxiliary emission controls (EGR, EVAP, catalytic converter)"
            '5' -> "vehicle speed, idle control or auxiliary inputs"
            '6' -> "the engine computer or its outputs"
            '7', '8', '9' -> "the transmission"
            'A', 'B', 'C' -> "hybrid or electric drive systems"
            else -> null
        }
    }

    private val CODE = Regex("^[PCBU][0-9A-F]{4}$")

    /**
     * Full explanation of [raw], from the catalogue or worked out from the code's structure.
     * [make] is the user's car (or the make read from the VIN): manufacturer-specific codes mean
     * different things on different brands, so they're explained for that make. With the model
     * [year] too, a maker's own published definition is used where there is one for that car.
     */
    fun lookup(raw: String, make: String? = null, year: Int? = null): DtcInfo {
        val code = raw.trim().uppercase(Locale.US)
        MakerCodes.lookup(code, make, year)?.let { return it }
        catalogue[code]?.let { return it.copy(system = systemOf(code)) }
        DtcGeneric.lookup(code, systemOf(code))?.let { return it }
        if (!CODE.matches(code)) {
            return DtcInfo(code, "Unrecognised code", "This doesn't look like a standard OBD2 trouble code.", Severity.UNKNOWN, known = false)
        }
        val system = systemOf(code)
        val manufacturer = system.endsWith("manufacturer-specific code")
        val area = subsystem(code)
        val brand = make?.substringBefore(" (")?.substringBefore(" / ")?.trim()?.takeIf { it.isNotEmpty() }
        val title = when {
            area != null -> "Fault in ${area.substringBefore(",").substringBefore(" or ")}"
            manufacturer && brand != null -> "$brand-specific code"
            manufacturer -> "Manufacturer-specific code"
            else -> system.substringBefore(",")
        }
        if (manufacturer) {
            val meaning = if (brand != null) {
                "$brand sets the meaning of this code, and the same code means something different on other brands. " +
                    "Look it up for your $brand model to find exactly which part or circuit it points to."
            } else {
                "This code's meaning is set by the car's maker, so it varies between brands. " +
                    "Choose your car under Diagnostics, Your car, and the app will say which maker's list to look it up in."
            }
            val checks = if (brand != null) {
                listOf(
                    "Search for \"$code $brand\" with your model and year, or check a $brand workshop manual",
                    "A $brand dealer or specialist can read the exact fault with $brand's own diagnostic tool",
                    "Read codes again after a few drives to see if it returns",
                )
            } else {
                listOf("Choose your car so the app can name the maker", "Search for \"$code\" with your make and model", "Read codes again after a few drives to see if it returns")
            }
            return DtcInfo(code, title, meaning, Severity.UNKNOWN, system = system, known = false, checks = checks)
        }
        val meaning = buildString {
            run {
                append("This is a generic code")
                if (area != null) append(" in the group for $area")
                append(". The app doesn't have its exact definition yet; search for \"$code\" to find it.")
            }
        }
        return DtcInfo(code, title, meaning, Severity.UNKNOWN, system = system, known = false,
            checks = listOf("Note when the code appeared and what the car was doing", "Read codes again after a few drives to see if it returns"))
    }

    /** Short name for [code], for lists and the car screen. */
    fun title(code: String, make: String? = null, year: Int? = null): String = lookup(code, make, year).title

    /** One line for the adapter log: the code, what it is and how urgent it is. */
    fun logLine(code: String, make: String? = null, year: Int? = null): String {
        val d = lookup(code, make, year)
        val urgency = if (d.severity == Severity.UNKNOWN) "Urgency varies by car" else d.severity.label
        return "${d.code}: ${d.title}. $urgency."
    }
}
