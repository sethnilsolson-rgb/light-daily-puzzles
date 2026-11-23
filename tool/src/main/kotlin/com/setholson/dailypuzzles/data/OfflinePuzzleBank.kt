package com.setholson.dailypuzzles.data

import com.setholson.dailypuzzles.model.DailyChessPuzzle
import com.setholson.dailypuzzles.model.DailyCrosswordPuzzle
import com.setholson.dailypuzzles.model.DailyFermiPuzzle
import com.setholson.dailypuzzles.model.DailyWordlePuzzle
import com.setholson.dailypuzzles.model.FermiQuestion
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * 100% offline, deterministic daily puzzle bank.
 *
 * Every Light Phone user receives the exact same Daily Word, Daily Fermi trio,
 * Daily Chess tactic, and Daily Crossword on any given calendar date — with zero
 * network requests and zero Android permissions.
 */
object OfflinePuzzleBank {

    internal data class ChessSeed(
        val title: String,
        val fen: String,
        val moves: List<String>,
    )

    // 28 classic, public-domain & original forced-line chess tactics (White and Black to move)
    internal val chessSeeds: List<ChessSeed> = listOf(
        ChessSeed(
            title = "Lock In!",
            fen = "1R6/4r1pk/2p3Rp/1b1p3P/3BP3/r4PP1/p1P4K/8 w - - 0 1",
            moves = listOf("f4", "a1=Q", "Rxh6+", "Kxh6", "Rh8#"),
        ),
        ChessSeed(
            title = "Back-Rank Strike",
            fen = "6k1/5ppp/8/8/8/8/5PPP/3R2K1 w - - 0 1",
            moves = listOf("Rd8#"),
        ),
        ChessSeed(
            title = "Back-Rank Counter",
            fen = "3r2k1/5ppp/8/8/8/8/5PPP/6K1 b - - 0 1",
            moves = listOf("Rd1#"),
        ),
        ChessSeed(
            title = "Queen Sacrifice Deflection",
            fen = "2r3k1/5ppp/8/3Q4/8/8/5PPP/3R2K1 w - - 0 1",
            moves = listOf("Qd8+", "Rxd8", "Rxd8#"),
        ),
        ChessSeed(
            title = "Black Queen Deflection",
            fen = "3r2k1/5ppp/8/8/3q4/8/5PPP/2R3K1 b - - 0 1",
            moves = listOf("Qd1+", "Rxd1", "Rxd1#"),
        ),
        ChessSeed(
            title = "Opera Mate Finale",
            fen = "4kb1r/p2n1ppp/4q3/4p1B1/4P3/1Q6/PPP2PPP/2KR4 w k - 1 16",
            moves = listOf("Qb8+", "Nxb8", "Rd8#"),
        ),
        ChessSeed(
            title = "Inverted Opera Mate",
            fen = "2kr4/ppp2ppp/1q6/4p3/4P1b1/4Q3/P2N1PPP/4KB1R b K - 0 1",
            moves = listOf("Qb1+", "Nxb1", "Rd1#"),
        ),
        ChessSeed(
            title = "Philidor's Legacy",
            fen = "5r1k/6pp/7N/8/8/1Q6/6PP/6K1 w - - 0 1",
            moves = listOf("Qg8+", "Rxg8", "Nf7#"),
        ),
        ChessSeed(
            title = "Black Smothered Legacy",
            fen = "6k1/6pp/1q6/8/8/7n/6PP/5R1K b - - 0 1",
            moves = listOf("Qg1+", "Rxg1", "Nf2#"),
        ),
        ChessSeed(
            title = "Anastasia's Mate",
            fen = "5r1k/4N1pp/8/7Q/8/3R4/5PPP/6K1 w - - 0 1",
            moves = listOf("Qxh7+", "Kxh7", "Rh3#"),
        ),
        ChessSeed(
            title = "Black Anastasia's Mate",
            fen = "6k1/5ppp/3r4/8/7q/8/4n1PP/5R1K b - - 0 1",
            moves = listOf("Qxh2+", "Kxh2", "Rh6#"),
        ),
        ChessSeed(
            title = "Boden's Crossed Bishops",
            fen = "2kr4/pp1n4/2n5/8/5B2/5Q2/PP2BPPP/6K1 w - - 0 1",
            moves = listOf("Qxc6+", "bxc6", "Ba6#"),
        ),
        ChessSeed(
            title = "Black Boden's Mate",
            fen = "6k1/pp2bppp/5q2/5b2/8/2N5/PP1N4/2KR4 b - - 0 1",
            moves = listOf("Qxc3+", "bxc3", "Ba3#"),
        ),
        ChessSeed(
            title = "Legal's Classic Trap",
            fen = "r2qkb1r/ppp2ppp/2np4/4N3/2B1P3/2N5/PPPP1PPP/R1BbK2R w KQkq - 0 6",
            moves = listOf("Bxf7+", "Ke7", "Nd5#"),
        ),
        ChessSeed(
            title = "Double Rook Breakthrough",
            fen = "2r3k1/5ppp/8/8/8/8/3R1PPP/3R2K1 w - - 0 1",
            moves = listOf("Rd8+", "Rxd8", "Rxd8#"),
        ),
        ChessSeed(
            title = "Black Rook Battery",
            fen = "3r2k1/3r1ppp/8/8/8/8/5PPP/2R3K1 b - - 0 1",
            moves = listOf("Rd1+", "Rxd1", "Rxd1#"),
        ),
        ChessSeed(
            title = "Promotion Decoy",
            fen = "r6k/4P1pp/8/8/8/8/8/4R1K1 w - - 0 1",
            moves = listOf("e8=Q+", "Rxe8", "Rxe8#"),
        ),
        ChessSeed(
            title = "File Sacrifice",
            fen = "r5k1/5ppp/8/8/8/8/4QPPP/4R1K1 w - - 0 1",
            moves = listOf("Qe8+", "Rxe8", "Rxe8#"),
        ),
        ChessSeed(
            title = "Black File Sacrifice",
            fen = "4r1k1/5ppp/8/8/8/8/4qPPP/R5K1 b - - 0 1",
            moves = listOf("Qe1+", "Rxe1", "Rxe1#"),
        ),
        ChessSeed(
            title = "Corner Arabian",
            fen = "7k/6p1/5N2/8/8/8/8/4R1K1 w - - 0 1",
            moves = listOf("Re8#"),
        ),
        ChessSeed(
            title = "Black Arabian",
            fen = "4r1k1/8/8/8/8/5n2/6P1/7K b - - 0 1",
            moves = listOf("Re1#"),
        ),
        ChessSeed(
            title = "Smothered Corner",
            fen = "6rk/6pp/8/6N1/8/8/5PPP/6K1 w - - 0 1",
            moves = listOf("Nf7#"),
        ),
        ChessSeed(
            title = "Black Smothered Corner",
            fen = "6k1/5ppp/8/8/6n1/8/6PP/6RK b - - 0 1",
            moves = listOf("Nf2#"),
        ),
        ChessSeed(
            title = "Epaulette Finish",
            fen = "3rkr2/8/8/8/8/8/4Q3/6K1 w - - 0 1",
            moves = listOf("Qe6#"),
        ),
        ChessSeed(
            title = "Black Epaulette Finish",
            fen = "6k1/4q3/8/8/8/8/8/3RKR2 b - - 0 1",
            moves = listOf("Qe3#"),
        ),
        ChessSeed(
            title = "Swallow's Tail",
            fen = "3r1r2/4k3/8/8/2Q5/8/4R3/6K1 w - - 0 1",
            moves = listOf("Qe6#"),
        ),
        ChessSeed(
            title = "Scholar's Finish",
            fen = "r1bqkb1r/pppp1ppp/2n5/4p2Q/2B1P3/8/PPPP1PPP/RNB1K1NR w KQkq - 4 4",
            moves = listOf("Qxf7#"),
        ),
        ChessSeed(
            title = "Ladder Checkmate",
            fen = "4k3/R7/1R6/8/8/8/8/6K1 w - - 0 1",
            moves = listOf("Rb8#"),
        ),
    )

    // 366 curated, recognizable everyday 5-letter English solution words (a full year without repeats)
    internal val dailyWordSolutions: List<String> = listOf(
        "CRANE", "SLATE", "TRACE", "AUDIO", "STARE", "ROAST", "PIANO", "CLOUD", "BREAD", "LIGHT",
        "STONE", "RIVER", "PLANT", "FRAME", "SHINE", "GRACE", "FLAME", "BRAVE", "CROWN", "TRAIL",
        "SPARK", "WHEAT", "STORM", "BLOOM", "CHARM", "DRIFT", "FROST", "GLIDE", "HEART", "IVORY",
        "JEWEL", "KNELT", "LEMON", "MAPLE", "NOBLE", "OLIVE", "PEARL", "QUILT", "ROBIN", "SOLAR",
        "THYME", "UPEND", "VIVID", "WHALE", "YEARN", "ZEBRA", "ACORN", "BIRCH", "CEDAR", "DELTA",
        "EMBER", "FLINT", "GROVE", "HAVEN", "INDEX", "JOKER", "KAYAK", "LUNAR", "MIRTH", "NORTH",
        "OCEAN", "PRISM", "QUART", "RIDGE", "SCOUT", "TIDAL", "UMBRA", "VAPOR", "WRIST", "YOUTH",
        "AMBER", "BLAZE", "CORAL", "DWARF", "EAGLE", "FABLE", "GLEAM", "HOUND", "INLET", "JOLLY",
        "KNACK", "LATCH", "MANOR", "NEXUS", "ORBIT", "PLUME", "QUEST", "RAVEN", "SHORE", "TORCH",
        "USHER", "VAULT", "WALTZ", "YIELD", "ZESTY", "ADOPT", "BENCH", "CABIN", "DAISY", "ELDER",
        "FEAST", "GIANT", "HONEY", "IGLOO", "JAUNT", "KOALA", "LILAC", "MARSH", "NIGHT", "OPERA",
        "PEACH", "QUIET", "REALM", "SALON", "TIGER", "UNITY", "VALET", "WAGON", "XENON", "YACHT",
        "ABIDE", "BADGE", "CAMEL", "DANCE", "EARTH", "FERRY", "GLOVE", "HARSH", "IDEAL", "JOUST",
        "KARMA", "LEAPT", "MANGO", "NOVEL", "OTTER", "PANDA", "QUAIL", "RANCH", "SCARF", "TABLE",
        "ULTRA", "VERSE", "WATER", "YODEL", "ZONAL", "AGILE", "BERRY", "CANDY", "DEBUT", "EPOCH",
        "FIBER", "GRAIN", "HASTE", "IMAGE", "JAZZY", "KEBAB", "LEVEL", "MEDAL", "NURSE", "ONION",
        "PATCH", "QUOTA", "RADIO", "SCALE", "TEMPO", "UNCLE", "VIGOR", "WOVEN", "YOUNG", "ALGAE",
        "BLINK", "CARGO", "DELVE", "EQUAL", "FIELD", "GRAPE", "HEDGE", "IRONY", "JUDGE", "KNIFE",
        "LINEN", "MELON", "NYLON", "OZONE", "PAUSE", "RADAR", "SCENT", "THANK", "UNDER", "VILLA",
        "WATCH", "ANGEL", "BLISS", "CARVE", "DEPOT", "EQUIP", "FIFTH", "GRASS", "HELLO", "ISSUE",
        "JUICE", "KNOCK", "LIVER", "MERCY", "OASIS", "PEDAL", "RAISE", "SCOPE", "THEFT", "UNDUE",
        "VINYL", "WEAVE", "ANGLE", "BLOCK", "CATCH", "DEPTH", "ERECT", "FINAL", "GRAVY", "HERON",
        "IVORY", "JUMBO", "LABEL", "LODGE", "MERGE", "OCCUR", "PENNY", "RALLY", "SCORE", "THEME",
        "UNIFY", "VIOLA", "WEDGE", "ANKLE", "BLOOD", "CAUSE", "DERBY", "ERUPT", "FINCH", "GREEN",
        "HIKER", "JEANS", "LABOR", "LOFTY", "MERIT", "OFFER", "PERCH", "RAPID", "SCORN", "THICK",
        "UNION", "VIPER", "WEIGH", "ANVIL", "BOARD", "CAVED", "DETER", "ESSAY", "FIRST", "GREET",
        "HINGE", "JELLY", "LADEN", "LOGIC", "METAL", "OFTEN", "PERIL", "RATIO", "SCOUR", "THING",
        "UNITE", "VIRAL", "WEIRD", "APPLE", "BOAST", "CEASE", "DEVIL", "ETHER", "FJORD", "GRIEF",
        "HIPPO", "JERKY", "LADLE", "LOOSE", "METER", "OLIVE", "PETAL", "REACH", "SCRAP", "THINK",
        "UNTIL", "VISIT", "WHARF", "APRON", "BONUS", "CHAIN", "DIARY", "ETHIC", "FLAIR", "GRILL",
        "HITCH", "JETTY", "LAGER", "LORRY", "MICRO", "OMEGA", "PHASE", "REACT", "SCREW", "THIRD",
        "UPPER", "VISOR", "WHEEL", "ARENA", "BOOTH", "CHAIR", "DIGIT", "EVADE", "FLAKE", "GRIND",
        "HOARD", "JOINT", "LANCE", "LOTUS", "MIDST", "ONSET", "PHONE", "READY", "SCRUB", "THORN",
        "UPSET", "VISTA", "WHERE", "ARGUE", "BOUND", "CHALK", "DINER", "EVENT", "FLANK", "GRIPE",
        "HOBBY", "JOIST", "LAPEL", "LOUSE", "MIGHT", "OPTIC", "PHOTO", "REALM", "SEIZE", "THOSE",
        "URBAN", "VITAL", "WHICH", "ARISE", "BOWEL", "CHAMP", "DINGO", "EVERY", "FLARE", "GROAN",
        "HOIST", "JUMPY", "LAPSE", "LOVER", "MIMIC", "ORATE", "PIECE", "REBEL", "SENSE", "THREE",
        "USAGE", "VIXEN", "WHILE", "ARMOR", "BOXER", "CHANT", "DIODE", "EVICT", "FLASH", "GROOM",
        "HOLLY", "JUNTA", "LARGE", "LOWER", "MINCE", "ORDER", "PILOT", "RECAP", "SERUM", "THREW",
        "USURP", "VOCAL", "WHINE", "AROMA", "BRACE", "CHAOS", "DIRGE", "EVOKE", "FLASK", "GROPE",
        "HOMER", "JUROR", "LARVA", "LOYAL", "MINER", "ORGAN", "PINCH", "RECUR", "SERVE", "THROB",
        "UTTER", "VODKA", "WHIRL", "ARROW", "BRAID", "CHART", "DIRTY", "EXACT", "FLEET", "GROSS",
        "HONOR", "KAPPA", "LASER", "LUCID", "MINOR", "OTHER", "PIPER", "REFER", "SETUP", "THROW",
        "VAGUE", "VOGUE", "WHISK", "ARSON", "BRAIN", "CHASE", "DOUGH", "EXALT", "FLESH", "GROUP",
        "HORDE", "KAYAK", "LASSO", "LUCKY", "MINTY", "OUGHT", "PIQUE", "REGAL", "SEVEN", "THUMB",
        "VALID", "VOICE", "WHITE", "ASCOT", "BRAKE", "CHASM", "DITCH", "EXCEL", "FLICK", "GROUT",
        "DIZZY", "BLUSH", "CRISP", "DRAPE", "ELFIN", "FUDGE", "GLOAT", "HUMID", "IMPEL", "JAUNT",
    ).distinct()

    // 48 original, well-sourced Fermi estimation questions with clear back-of-the-envelope math
    internal val fermiQuestions: List<FermiQuestion> = listOf(
        FermiQuestion(
            id = "f_01",
            prompt = "How many times does the average human heart beat in a 75-year lifetime?",
            unit = "beats",
            answer = 2_760_000_000.0,
            mathSteps = listOf(
                listOf("Average resting heart rate", "70 beats/min"),
                listOf("Minutes per year", "\u00D7 525,600"),
                listOf("Years in a lifetime", "\u00D7 75"),
                listOf("Estimate", "\u2248 2.76B"),
            ),
        ),
        FermiQuestion(
            id = "f_02",
            prompt = "How many piano tuners work in the United States?",
            unit = "piano tuners",
            answer = 6_000.0,
            mathSteps = listOf(
                listOf("US households", "130,000,000"),
                listOf("Share owning a tuned piano", "\u00D7 0.04"),
                listOf("Tunings per tuner per year", "\u00F7 850"),
                listOf("Estimate", "\u2248 6,000"),
            ),
        ),
        FermiQuestion(
            id = "f_03",
            prompt = "How many golf balls would fit inside a standard US school bus?",
            unit = "golf balls",
            answer = 500_000.0,
            mathSteps = listOf(
                listOf("Interior bus volume", "25 m\u00B3"),
                listOf("Volume per golf ball (with packing)", "\u00F7 5\u00D710\u207B\u2075 m\u00B3"),
                listOf("Estimate", "\u2248 500,000"),
            ),
        ),
        FermiQuestion(
            id = "f_04",
            prompt = "How many commercial flights take off worldwide in a single day?",
            unit = "flights",
            answer = 105_000.0,
            mathSteps = listOf(
                listOf("Active commercial airliners", "28,000"),
                listOf("Average flights per plane per day", "\u00D7 3.8"),
                listOf("Estimate", "\u2248 106,000"),
            ),
        ),
        FermiQuestion(
            id = "f_05",
            prompt = "How many slices of pizza are eaten in the United States every day?",
            unit = "slices",
            answer = 25_000_000.0,
            mathSteps = listOf(
                listOf("US population", "335,000,000"),
                listOf("Share eating pizza on a given day", "\u00D7 0.03"),
                listOf("Average slices per person", "\u00D7 2.5"),
                listOf("Estimate", "\u2248 25,000,000"),
            ),
        ),
        FermiQuestion(
            id = "f_06",
            prompt = "How many bricks were used to build the Empire State Building?",
            unit = "bricks",
            answer = 10_000_000.0,
            mathSteps = listOf(
                listOf("Number of floors", "102"),
                listOf("Bricks per floor (interior + facade)", "\u00D7 100,000"),
                listOf("Estimate", "\u2248 10,000,000"),
            ),
        ),
        FermiQuestion(
            id = "f_07",
            prompt = "How many cups of coffee are consumed globally every single day?",
            unit = "cups",
            answer = 2_250_000_000.0,
            mathSteps = listOf(
                listOf("World adult population", "5.5B"),
                listOf("Share who drink coffee daily", "\u00D7 0.25"),
                listOf("Average cups per drinker", "\u00D7 1.6"),
                listOf("Estimate", "\u2248 2.2B"),
            ),
        ),
        FermiQuestion(
            id = "f_08",
            prompt = "How many keys are on all standard pianos currently in New York City?",
            unit = "keys",
            answer = 26_400_000.0,
            mathSteps = listOf(
                listOf("NYC households", "3.3M"),
                listOf("Share with a piano (plus schools/venues)", "\u00D7 0.09"),
                listOf("Keys per standard piano", "\u00D7 88"),
                listOf("Estimate", "\u2248 26.1M"),
            ),
        ),
        FermiQuestion(
            id = "f_09",
            prompt = "How many miles of paved roads exist in the United States?",
            unit = "miles",
            answer = 4_170_000.0,
            mathSteps = listOf(
                listOf("US land area", "3.5M sq mi"),
                listOf("Average road density", "\u00D7 1.2 mi/sq mi"),
                listOf("Estimate", "\u2248 4.2M miles"),
            ),
        ),
        FermiQuestion(
            id = "f_10",
            prompt = "How many bananas are eaten worldwide in a single year?",
            unit = "bananas",
            answer = 100_000_000_000.0,
            mathSteps = listOf(
                listOf("World population", "8B"),
                listOf("Average bananas per person per year", "\u00D7 12.5"),
                listOf("Estimate", "\u2248 100B"),
            ),
        ),
        FermiQuestion(
            id = "f_11",
            prompt = "How many liters of water flow over Niagara Falls every minute?",
            unit = "liters",
            answer = 168_000_000.0,
            mathSteps = listOf(
                listOf("Average flow rate", "2,800 m\u00B3/sec"),
                listOf("Liters per cubic meter", "\u00D7 1,000"),
                listOf("Seconds per minute", "\u00D7 60"),
                listOf("Estimate", "\u2248 168M liters"),
            ),
        ),
        FermiQuestion(
            id = "f_12",
            prompt = "How many interlocking plastic bricks are manufactured globally per year?",
            unit = "bricks",
            answer = 60_000_000_000.0,
            mathSteps = listOf(
                listOf("Sets sold per year", "150M"),
                listOf("Average bricks per set", "\u00D7 400"),
                listOf("Estimate", "\u2248 60B"),
            ),
        ),
        FermiQuestion(
            id = "f_13",
            prompt = "How many public libraries (central + branches) are in the United States?",
            unit = "libraries",
            answer = 17_200.0,
            mathSteps = listOf(
                listOf("US population", "335M"),
                listOf("People served per branch", "\u00F7 20,000"),
                listOf("Estimate", "\u2248 16,750"),
            ),
        ),
        FermiQuestion(
            id = "f_14",
            prompt = "How many minutes of sunlight does Earth's equator receive in a full year?",
            unit = "minutes",
            answer = 262_800.0,
            mathSteps = listOf(
                listOf("Days per year", "365"),
                listOf("Daylight hours per day at equator", "\u00D7 12"),
                listOf("Minutes per hour", "\u00D7 60"),
                listOf("Estimate", "= 262,800"),
            ),
        ),
        FermiQuestion(
            id = "f_15",
            prompt = "How many grains of rice are in a standard 1-kilogram (2.2 lb) bag?",
            unit = "grains",
            answer = 50_000.0,
            mathSteps = listOf(
                listOf("Weight of bag", "1,000 g"),
                listOf("Average mass per dry grain", "\u00F7 0.02 g"),
                listOf("Estimate", "\u2248 50,000"),
            ),
        ),
        FermiQuestion(
            id = "f_16",
            prompt = "How many trees are currently growing on Earth?",
            unit = "trees",
            answer = 3_040_000_000_000.0,
            mathSteps = listOf(
                listOf("Earth forested area", "40M km\u00B2"),
                listOf("Trees per km\u00B2", "\u00D7 75,000"),
                listOf("Estimate", "\u2248 3T"),
            ),
        ),
        FermiQuestion(
            id = "f_17",
            prompt = "How many hairs are on an average human head?",
            unit = "hairs",
            answer = 100_000.0,
            mathSteps = listOf(
                listOf("Scalp area", "500 cm\u00B2"),
                listOf("Follicles per cm\u00B2", "\u00D7 200"),
                listOf("Estimate", "\u2248 100,000"),
            ),
        ),
        FermiQuestion(
            id = "f_18",
            prompt = "How many Earths could fit by volume inside the planet Jupiter?",
            unit = "Earths",
            answer = 1_321.0,
            mathSteps = listOf(
                listOf("Jupiter radius / Earth radius", "\u2248 11"),
                listOf("Volume ratio (11\u00B3)", "= 1,331"),
                listOf("Exact ratio", "\u2248 1,321"),
            ),
        ),
        FermiQuestion(
            id = "f_19",
            prompt = "How many words are in the complete plays and sonnets of Shakespeare?",
            unit = "words",
            answer = 884_647.0,
            mathSteps = listOf(
                listOf("Number of plays", "38"),
                listOf("Average words per play", "\u00D7 23,000"),
                listOf("Plus sonnets & poems", "+ 20,000"),
                listOf("Estimate", "\u2248 894,000"),
            ),
        ),
        FermiQuestion(
            id = "f_20",
            prompt = "How many seconds does it take light to travel from the Sun to Earth?",
            unit = "seconds",
            answer = 499.0,
            mathSteps = listOf(
                listOf("Sun-Earth distance", "150,000,000 km"),
                listOf("Speed of light", "\u00F7 300,000 km/s"),
                listOf("Estimate", "= 500 seconds"),
            ),
        ),
        FermiQuestion(
            id = "f_21",
            prompt = "How many breaths does a resting adult take in one year?",
            unit = "breaths",
            answer = 7_884_000.0,
            mathSteps = listOf(
                listOf("Average breaths per minute", "15"),
                listOf("Minutes per year", "\u00D7 525,600"),
                listOf("Estimate", "\u2248 7.9M"),
            ),
        ),
        FermiQuestion(
            id = "f_22",
            prompt = "How many gallons of water fill an Olympic-size swimming pool?",
            unit = "gallons",
            answer = 660_000.0,
            mathSteps = listOf(
                listOf("Pool dimensions", "50m \u00D7 25m \u00D7 2m = 2,500 m\u00B3"),
                listOf("Gallons per cubic meter", "\u00D7 264"),
                listOf("Estimate", "= 660,000 gallons"),
            ),
        ),
        FermiQuestion(
            id = "f_23",
            prompt = "How many total steps would it take to walk around Earth's equator?",
            unit = "steps",
            answer = 52_600_000.0,
            mathSteps = listOf(
                listOf("Earth equatorial circumference", "40,075 km"),
                listOf("Meters per step", "\u00F7 0.76 m"),
                listOf("Estimate", "\u2248 52.7M steps"),
            ),
        ),
        FermiQuestion(
            id = "f_24",
            prompt = "How many active bicycles are estimated to exist worldwide?",
            unit = "bicycles",
            answer = 1_000_000_000.0,
            mathSteps = listOf(
                listOf("World population", "8B"),
                listOf("Share owning a bicycle", "\u00D7 0.125"),
                listOf("Estimate", "\u2248 1B bicycles"),
            ),
        ),
        FermiQuestion(
            id = "f_25",
            prompt = "How many stone blocks make up the Great Pyramid of Giza?",
            unit = "blocks",
            answer = 2_300_000.0,
            mathSteps = listOf(
                listOf("Pyramid volume (1/3 \u00D7 230\u00B2 \u00D7 146)", "2.58M m\u00B3"),
                listOf("Average volume per stone block", "\u00F7 1.1 m\u00B3"),
                listOf("Estimate", "\u2248 2.3M blocks"),
            ),
        ),
        FermiQuestion(
            id = "f_26",
            prompt = "How many lightning strikes hit Earth every single day?",
            unit = "strikes",
            answer = 8_600_000.0,
            mathSteps = listOf(
                listOf("Global strikes per second", "\u2248 100"),
                listOf("Seconds per day", "\u00D7 86,400"),
                listOf("Estimate", "\u2248 8.6M strikes"),
            ),
        ),
        FermiQuestion(
            id = "f_27",
            prompt = "How many sheet-music notes are in Beethoven's Symphony No. 9?",
            unit = "notes",
            answer = 250_000.0,
            mathSteps = listOf(
                listOf("Length of symphony", "65 minutes"),
                listOf("Musicians playing simultaneously (avg)", "\u2248 40"),
                listOf("Notes per musician per minute", "\u00D7 100"),
                listOf("Estimate", "\u2248 260,000 notes"),
            ),
        ),
        FermiQuestion(
            id = "f_28",
            prompt = "How many standard basketballs would fit inside a Boeing 747 passenger cabin?",
            unit = "basketballs",
            answer = 65_000.0,
            mathSteps = listOf(
                listOf("Cabin interior volume", "875 m\u00B3"),
                listOf("Packing space per basketball", "\u00F7 0.013 m\u00B3"),
                listOf("Estimate", "\u2248 67,000"),
            ),
        ),
        FermiQuestion(
            id = "f_29",
            prompt = "How many kilometers of blood vessels are inside an adult human body?",
            unit = "kilometers",
            answer = 100_000.0,
            mathSteps = listOf(
                listOf("Estimated capillaries", "\u2248 10B"),
                listOf("Average capillary length", "\u00D7 0.01 mm"),
                listOf("Estimate", "\u2248 100,000 km"),
            ),
        ),
        FermiQuestion(
            id = "f_30",
            prompt = "How many individual leaves grow on a mature full-sized oak tree?",
            unit = "leaves",
            answer = 250_000.0,
            mathSteps = listOf(
                listOf("Canopy surface area", "250 m\u00B2"),
                listOf("Leaf layers (leaf area index)", "\u00D7 5"),
                listOf("Leaves per square meter", "\u00D7 200"),
                listOf("Estimate", "\u2248 250,000 leaves"),
            ),
        ),
        FermiQuestion(
            id = "f_31",
            prompt = "How many blades of grass cover an American football field (including end zones)?",
            unit = "blades",
            answer = 350_000_000.0,
            mathSteps = listOf(
                listOf("Field area", "5,350 m\u00B2"),
                listOf("Blades per square meter", "\u00D7 65,000"),
                listOf("Estimate", "\u2248 348M blades"),
            ),
        ),
        FermiQuestion(
            id = "f_32",
            prompt = "How many US quarters stacked flat would reach the top of Mount Everest?",
            unit = "quarters",
            answer = 5_056_000.0,
            mathSteps = listOf(
                listOf("Height of Mount Everest", "8,849 m"),
                listOf("Thickness of one quarter", "\u00F7 0.00175 m"),
                listOf("Estimate", "\u2248 5.06M quarters"),
            ),
        ),
        FermiQuestion(
            id = "f_33",
            prompt = "How many fire hydrants are installed across New York City?",
            unit = "hydrants",
            answer = 109_000.0,
            mathSteps = listOf(
                listOf("Miles of NYC streets", "6,000 miles"),
                listOf("Feet per mile", "\u00D7 5,280"),
                listOf("Average spacing between hydrants", "\u00F7 290 ft"),
                listOf("Estimate", "\u2248 109,000"),
            ),
        ),
        FermiQuestion(
            id = "f_34",
            prompt = "How many books are held in the Library of Congress catalog?",
            unit = "books",
            answer = 40_000_000.0,
            mathSteps = listOf(
                listOf("Miles of bookshelves", "838 miles"),
                listOf("Books per foot of shelf", "\u00D7 9"),
                listOf("Feet per mile", "\u00D7 5,280"),
                listOf("Estimate", "\u2248 40M books"),
            ),
        ),
        FermiQuestion(
            id = "f_35",
            prompt = "How many metric tons does the Eiffel Tower's iron structure weigh?",
            unit = "metric tons",
            answer = 7_300.0,
            mathSteps = listOf(
                listOf("Total height", "330 m"),
                listOf("Average iron cross-section", "\u2248 3 m\u00B2"),
                listOf("Density of wrought iron", "\u00D7 7.8 t/m\u00B3"),
                listOf("Estimate", "\u2248 7,700 tons"),
            ),
        ),
        FermiQuestion(
            id = "f_36",
            prompt = "How many individual popcorn kernels are in a 1-pound (454 g) bag?",
            unit = "kernels",
            answer = 3_000.0,
            mathSteps = listOf(
                listOf("Weight of bag", "454 g"),
                listOf("Average weight per kernel", "\u00F7 0.15 g"),
                listOf("Estimate", "\u2248 3,000 kernels"),
            ),
        ),
        FermiQuestion(
            id = "f_37",
            prompt = "How many gas stations operate in the United States?",
            unit = "stations",
            answer = 145_000.0,
            mathSteps = listOf(
                listOf("US registered vehicles", "285M"),
                listOf("Vehicles served per station", "\u00F7 2,000"),
                listOf("Estimate", "\u2248 142,500"),
            ),
        ),
        FermiQuestion(
            id = "f_38",
            prompt = "How many rivets hold the Golden Gate Bridge together?",
            unit = "rivets",
            answer = 1_200_000.0,
            mathSteps = listOf(
                listOf("Two main steel towers", "2"),
                listOf("Rivets per tower", "\u00D7 600,000"),
                listOf("Estimate", "= 1,200,000 rivets"),
            ),
        ),
        FermiQuestion(
            id = "f_39",
            prompt = "How many hours of music would it take to listen to 10,000 average pop songs?",
            unit = "hours",
            answer = 583.0,
            mathSteps = listOf(
                listOf("Number of songs", "10,000"),
                listOf("Average song length", "\u00D7 3.5 minutes"),
                listOf("Minutes per hour", "\u00F7 60"),
                listOf("Estimate", "\u2248 583 hours"),
            ),
        ),
        FermiQuestion(
            id = "f_40",
            prompt = "How many baseballs are used in an entire Major League Baseball season?",
            unit = "baseballs",
            answer = 240_000.0,
            mathSteps = listOf(
                listOf("Regular season games", "2,430"),
                listOf("Baseballs used per game", "\u00D7 100"),
                listOf("Estimate", "\u2248 243,000"),
            ),
        ),
        FermiQuestion(
            id = "f_41",
            prompt = "How many sheets of standard letter paper stacked up equal the height of the Empire State Building?",
            unit = "sheets",
            answer = 3_810_000.0,
            mathSteps = listOf(
                listOf("Roof height", "381 m"),
                listOf("Thickness per sheet", "\u00F7 0.0001 m"),
                listOf("Estimate", "= 3.81M sheets"),
            ),
        ),
        FermiQuestion(
            id = "f_42",
            prompt = "How many blinks does an average person make in a single waking day?",
            unit = "blinks",
            answer = 15_000.0,
            mathSteps = listOf(
                listOf("Blinks per minute", "15"),
                listOf("Waking hours per day", "\u00D7 16"),
                listOf("Minutes per hour", "\u00D7 60"),
                listOf("Estimate", "= 14,400 blinks"),
            ),
        ),
        FermiQuestion(
            id = "f_43",
            prompt = "How many raindrops fall on a 1 square kilometer city during a 10 mm rainstorm?",
            unit = "raindrops",
            answer = 200_000_000_000.0,
            mathSteps = listOf(
                listOf("Water volume (1 km\u00B2 \u00D7 0.01 m)", "10,000 m\u00B3"),
                listOf("Milliliters of rain", "10,000,000,000 mL"),
                listOf("Volume per raindrop", "\u00F7 0.05 mL"),
                listOf("Estimate", "= 200B raindrops"),
            ),
        ),
        FermiQuestion(
            id = "f_44",
            prompt = "How many elevators are in operation across the United States?",
            unit = "elevators",
            answer = 1_000_000.0,
            mathSteps = listOf(
                listOf("US population", "335M"),
                listOf("People per elevator", "\u00F7 335"),
                listOf("Estimate", "\u2248 1M elevators"),
            ),
        ),
        FermiQuestion(
            id = "f_45",
            prompt = "How many bees live in a strong summer honeybee hive?",
            unit = "bees",
            answer = 60_000.0,
            mathSteps = listOf(
                listOf("Queen egg-laying rate", "1,500 eggs/day"),
                listOf("Worker bee lifespan in summer", "\u00D7 40 days"),
                listOf("Estimate", "= 60,000 bees"),
            ),
        ),
        FermiQuestion(
            id = "f_46",
            prompt = "How many stars are visible to the naked human eye across the entire night sky?",
            unit = "stars",
            answer = 9_000.0,
            mathSteps = listOf(
                listOf("Stars visible in one hemisphere", "\u2248 4,500"),
                listOf("Both hemispheres combined", "\u00D7 2"),
                listOf("Estimate", "\u2248 9,000 stars"),
            ),
        ),
        FermiQuestion(
            id = "f_47",
            prompt = "How many calories of food energy does an adult eat over an 80-year lifetime?",
            unit = "kilocalories",
            answer = 64_000_000.0,
            mathSteps = listOf(
                listOf("Average daily intake", "2,200 kcal"),
                listOf("Days per year", "\u00D7 365"),
                listOf("Years in a lifetime", "\u00D7 80"),
                listOf("Estimate", "\u2248 64.2M kcal"),
            ),
        ),
        FermiQuestion(
            id = "f_48",
            prompt = "How many teeth does a human adult grow across both baby and permanent sets combined?",
            unit = "teeth",
            answer = 52.0,
            mathSteps = listOf(
                listOf("Primary (baby) teeth", "20"),
                listOf("Permanent adult teeth (with wisdom teeth)", "+ 32"),
                listOf("Total", "= 52 teeth"),
            ),
        ),
    )

    fun todayLocalIso(now: Calendar = Calendar.getInstance()): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return fmt.format(now.time)
    }

    fun todayPrettyLabel(now: Calendar = Calendar.getInstance()): String {
        val fmt = SimpleDateFormat("EEE, MMM d", Locale.US)
        return fmt.format(now.time)
    }

    /**
     * Deterministic sequential puzzle number starting at #1 on 2026-01-01.
     * Guarantees every Light Phone user gets the exact same puzzle # on the same calendar date.
     */
    fun dailyPuzzleNumber(dateIso: String): Int {
        val parts = dateIso.split("-")
        if (parts.size != 3) return 1
        val y = parts[0].toIntOrNull() ?: return 1
        val m = parts[1].toIntOrNull() ?: return 1
        val d = parts[2].toIntOrNull() ?: return 1

        val utc = TimeZone.getTimeZone("UTC")
        val epoch = Calendar.getInstance(utc).apply {
            clear()
            set(2026, Calendar.JANUARY, 1)
        }
        val target = Calendar.getInstance(utc).apply {
            clear()
            set(y, m - 1, d)
        }
        val diffDays = ((target.timeInMillis - epoch.timeInMillis) / 86_400_000L).toInt()
        return (diffDays + 1).coerceAtLeast(1)
    }

    fun dailyChess(dateIso: String): DailyChessPuzzle {
        val num = dailyPuzzleNumber(dateIso)
        val idx = Math.floorMod(num - 1, chessSeeds.size)
        val seed = chessSeeds[idx]
        return DailyChessPuzzle(
            dateIso = dateIso,
            puzzleNumber = num,
            title = seed.title,
            fen = seed.fen,
            solutionSan = seed.moves,
        )
    }

    fun dailyWord(dateIso: String): DailyWordlePuzzle {
        val num = dailyPuzzleNumber(dateIso)
        // Coprime stride spreads alphabetically sorted regions so consecutive days feel fresh
        val stride = 73
        val idx = Math.floorMod((num - 1) * stride, dailyWordSolutions.size)
        return DailyWordlePuzzle(
            dateIso = dateIso,
            puzzleNumber = num,
            solution = dailyWordSolutions[idx],
        )
    }

    fun dailyFermi(dateIso: String): DailyFermiPuzzle {
        val num = dailyPuzzleNumber(dateIso)
        val total = fermiQuestions.size
        val start = Math.floorMod((num - 1) * 3, total)
        val picked = List(3) { i -> fermiQuestions[(start + i) % total] }
        return DailyFermiPuzzle(
            dateIso = dateIso,
            puzzleNumber = num,
            dateLabel = dateIso,
            questions = picked,
        )
    }

    fun selectDailyCrossword(dateIso: String, bank: List<DailyCrosswordPuzzle>): DailyCrosswordPuzzle {
        val num = dailyPuzzleNumber(dateIso)
        val idx = Math.floorMod(num - 1, bank.size)
        return bank[idx]
    }
}
