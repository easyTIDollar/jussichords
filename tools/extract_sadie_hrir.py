import math
from pathlib import Path

import h5py


SOURCE_DIR = Path(
    r"C:\Users\Administrator\AppData\Local\Temp\sadie-h3-24e624f0746c477a8f470510f136b78d\extract\H3_HRIR_SOFA"
)
OUTPUT = Path("app/src/main/java/com/jussicodes/music/playback/audio/SadieHrirData.kt")
SOFA_FILES = {
    44100: "H3_44K_16bit_256tap_FIR_SOFA.sofa",
    48000: "H3_48K_24bit_256tap_FIR_SOFA.sofa",
}
TAPS = 128
POSITIONS = 16


def angular_distance(a, b):
    return abs((a - b + 180.0) % 360.0 - 180.0)


def target_position(index):
    phase = 2.0 * math.pi * index / POSITIONS
    azimuth = math.degrees(phase) % 360.0
    elevation = 35.0 * math.sin(phase * 2.0 + math.pi / 2.0)
    return azimuth, elevation


def nearest_index(positions, azimuth, elevation):
    return min(
        range(len(positions)),
        key=lambda i: angular_distance(float(positions[i][0]), azimuth) ** 2
        + (float(positions[i][1]) - elevation) ** 2,
    )


def q15(value):
    return max(-32768, min(32767, round(float(value) * 32768.0)))


def extract_file(path):
    with h5py.File(path, "r") as sofa:
        positions = sofa["SourcePosition"][:]
        ir = sofa["Data.IR"]
        rows = []
        actual_positions = []
        for i in range(POSITIONS):
            azimuth, elevation = target_position(i)
            source_index = nearest_index(positions, azimuth, elevation)
            actual_positions.append((float(positions[source_index][0]), float(positions[source_index][1])))
            for ear in range(2):
                rows.extend(q15(sample) for sample in ir[source_index, ear, :TAPS])
        return rows, actual_positions


def short_array(values, indent="        ", suffix=""):
    lines = []
    for start in range(0, len(values), 16):
        chunk = ", ".join(f"{value}{suffix}" for value in values[start : start + 16])
        lines.append(f"{indent}{chunk},")
    return "\n".join(lines)


def main():
    generated = {}
    positions = None
    for sample_rate, file_name in SOFA_FILES.items():
        values, actual_positions = extract_file(SOURCE_DIR / file_name)
        generated[sample_rate] = values
        if positions is None:
            positions = actual_positions

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(
        f"""package com.jussicodes.music.playback.audio

internal object SadieHrirData {{
    const val POSITIONS = {POSITIONS}
    const val TAPS = {TAPS}
    const val SCALE = 32768f

    // SADIE II H3 dummy head HRIR, reduced to a 16-position moving path.
    // Source: https://www.york.ac.uk/sadie-project/database.html
    val POSITIONS_DEGREES: FloatArray = floatArrayOf(
{short_array([round(v, 1) for pair in positions for v in pair], "        ", "f")}
    )

    val HRIR_44100: ShortArray = shortArrayOf(
{short_array(generated[44100])}
    )

    val HRIR_48000: ShortArray = shortArrayOf(
{short_array(generated[48000])}
    )

    fun coefficientsFor(sampleRate: Int): ShortArray =
        if (sampleRate <= 44_100) HRIR_44100 else HRIR_48000
}}
""",
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
