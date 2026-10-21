package com.obdreader.app.obd

/**
 * A short, cheeky line about a drive's score, for the share card and the post's text. It teases
 * the driving, never the driver: nothing about looks, age, gender or anything personal. The line
 * is picked from the score band and style, and stays the same for the same drive.
 */
object Roast {

    private val EVERYDAY = listOf(
        listOf("Smooth as a hotel lift. Your passengers could have threaded a needle.", "Butter-smooth. Not a single coffee was spilled.", "Driving instructors would like your autograph."),
        listOf("Pretty tidy. A couple of spirited moments, but we won't tell.", "Mostly smooth, with the odd surprise for the passengers.", "Good drive. The cup holder only filed one complaint."),
        listOf("A solid C+. The brakes are drafting a strongly worded letter.", "Not bad. Your passengers only grabbed the handle twice.", "Respectable, if you squint a bit."),
        listOf("Your passengers are now holding the handle above the door.", "The car would like a quiet word about the braking.", "Somewhere, a set of brake pads is crying."),
        listOf("That wasn't a drive, that was a stunt audition.", "Bold. Very bold. The car needs a lie-down.", "Your seatbelt earned its keep today."),
    )
    private val EFFICIENCY = listOf(
        listOf("Sipping fuel like it's a fine wine.", "Hypermiler energy. The fuel gauge forgot you exist.", "Your fuel bill just sent you a thank-you card."),
        listOf("Efficient-ish. The fuel gauge barely noticed.", "Pretty frugal. Your wallet is mildly impressed.", "Good on fuel, with the odd heavy-footed lapse."),
        listOf("Middle of the road. The fuel company says thanks.", "Not wasteful, not thrifty. Just vibes.", "Your right foot has opinions, and they cost money."),
        listOf("Fuel economy has left the chat.", "Every red light was a drag race, apparently.", "The servo knows you by name."),
        listOf("Somewhere, an oil executive just bought a yacht.", "That drive burned fuel like a bonfire on cracker night.", "Your fuel gauge needs therapy."),
    )
    private val RACING = listOf(
        listOf("The pit wall says: good lap. Mic drop.", "Fast and tidy. Someone call the scouts.", "Clean, quick, committed. Chef's kiss."),
        listOf("Quick, but the stopwatch has notes.", "Solid pace. A few apexes went missing.", "Fast enough to brag, slow enough to stay humble."),
        listOf("Track-day ready... for the parade lap.", "Spirited, in a Sunday-drive kind of way.", "The tyres barely broke a sweat."),
        listOf("Your lap time has grown a beard.", "Lots of revs, not much progress.", "The racing line called. It wants a word."),
        listOf("Even the safety car overtook you.", "That lap was sponsored by the brake pedal.", "Bold strategy: be the slowest car on track."),
    )
    /** The drive score: Everyday and Efficiency together, so smoothness and fuel both count. */
    private val DRIVE = listOf(
        listOf("Smooth and frugal. Your car wants to adopt you.", "Gentle on the brakes, gentle on the fuel. Show-off.", "Silky smooth and barely sipped fuel. Teach us your ways."),
        listOf("Tidy and thrifty, with the odd heavy-footed moment.", "Pretty smooth, pretty frugal. The car approves, mostly.", "Good drive. The fuel gauge and the passengers both nodded."),
        listOf("A solid C+. Smooth-ish, thirsty-ish, very average-ish.", "Not bad. The brakes and the fuel gauge have notes.", "Respectable. Your right foot just needs a little therapy."),
        listOf("The passengers held on, and the fuel gauge held its breath.", "Every red light was a drag race, and the servo noticed.", "Somewhere, a set of brake pads and a fuel tank are crying."),
        listOf("That wasn't a drive, it was a fuel-burning stunt audition.", "Bold. The car needs a lie-down and a refuel.", "Your seatbelt and the fuel company both had a big day."),
    )
    private val TOO_SHORT = listOf("Barely left the driveway. Bold.", "A drive so short the engine didn't warm up.", "Blink and you'd miss it. We did.")

    private fun band(score: Int): Int = when {
        score >= 90 -> 0
        score >= 75 -> 1
        score >= 55 -> 2
        score >= 35 -> 3
        else -> 4
    }

    /** The line for the score a shared card shows: Racing on Race telemetry, else the drive score. */
    fun line(card: ShareScore, seed: Long): String =
        if (card.kind == ShareScore.Kind.RACING) line(DrivingScore.Style.RACING, card.score, seed) else pickFrom(DRIVE, card.score, seed)

    private fun pickFrom(table: List<List<String>>, score: Int?, seed: Long): String {
        val pick = { list: List<String> -> list[Math.floorMod(seed, list.size.toLong()).toInt()] }
        return if (score == null) pick(TOO_SHORT) else pick(table[band(score)])
    }

    /** The line for [score] in [style] (null score: too short to rate). [seed] picks among the lines, e.g. the drive's start time. */
    fun line(style: DrivingScore.Style, score: Int?, seed: Long): String {
        val pick = { list: List<String> -> list[Math.floorMod(seed, list.size.toLong()).toInt()] }
        if (score == null) return pick(TOO_SHORT)
        val table = when (style) {
            DrivingScore.Style.EFFICIENCY -> EFFICIENCY
            DrivingScore.Style.RACING -> RACING
            else -> EVERYDAY
        }
        return pick(table[band(score)])
    }

    /** Every line (for the tests). */
    internal val all: List<String> get() = (EVERYDAY + EFFICIENCY + RACING + DRIVE).flatten() + TOO_SHORT
}
