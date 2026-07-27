package com.jussicodes.music.playback.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class AudioEffectMode {
    OFF,
    EIGHT_D,
    BASS_BOOST,
    MUFFLED,
    REVERB
}

@UnstableApi
class EightDAudioProcessor : BaseAudioProcessor() {
    private var enabled = false
    private var time: Double = 0.0
    private var rotationSpeed: Double = 0.00001
    private var depth: Float = 0.8f

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        if (!enabled) time = 0.0
    }

    fun setSpeed(normalizedSpeed: Float) {
        rotationSpeed = (0.000006 + normalizedSpeed * 0.00009).coerceIn(0.000006, 0.000096)
    }

    fun setDepth(normalizedIntensity: Float) {
        depth = (0.35f + normalizedIntensity * 0.65f).coerceIn(0.35f, 1f)
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat =
        if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) inputAudioFormat else AudioProcessor.AudioFormat.NOT_SET

    override fun onFlush() {
        if (enabled) time = 0.0
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val outputBuffer = replaceOutputBuffer(remaining)
        if (!enabled) {
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        while (inputBuffer.remaining() >= 2) {
            if (inputAudioFormat.channelCount == 2 && inputBuffer.remaining() >= 4) {
                val left = inputBuffer.getShort().toFloat()
                val right = inputBuffer.getShort().toFloat()

                time += rotationSpeed
                val pan = sin(time) * depth
                val angle = (pan + 1.0) * PI / 4.0
                val pannedLeft = left * cos(angle)
                val pannedRight = right * sin(angle)
                val dry = 1f - depth

                outputBuffer.putShort((left * dry + pannedLeft * depth).toInt().toShort())
                outputBuffer.putShort((right * dry + pannedRight * depth).toInt().toShort())
            } else {
                outputBuffer.putShort(inputBuffer.getShort())
            }
        }
        outputBuffer.flip()
    }
}

@UnstableApi
class FxAudioProcessor : BaseAudioProcessor() {
    private var isMuffled = false
    private var isBassBoost = false
    private var bassBoostGain = 10f
    private var muffledCutoff = 800f

    private var b0M = 0f
    private var b1M = 0f
    private var b2M = 0f
    private var a1M = 0f
    private var a2M = 0f
    private var x1M = 0f
    private var x2M = 0f
    private var y1M = 0f
    private var y2M = 0f

    private var b0B = 0f
    private var b1B = 0f
    private var b2B = 0f
    private var a1B = 0f
    private var a2B = 0f
    private var x1B = 0f
    private var x2B = 0f
    private var y1B = 0f
    private var y2B = 0f

    fun setEffects(muffled: Boolean, bassBoost: Boolean) {
        if (isMuffled != muffled || isBassBoost != bassBoost) {
            isMuffled = muffled
            isBassBoost = bassBoost
            resetStates()
            calculateCoefficients()
        }
    }

    fun setBassBoostGain(normalizedIntensity: Float) {
        val newGain = (6f + normalizedIntensity * 18f).coerceIn(6f, 24f)
        if (newGain != bassBoostGain) {
            bassBoostGain = newGain
            if (isBassBoost) {
                resetStates()
                calculateCoefficients()
            }
        }
    }

    fun setMuffledCutoff(normalizedIntensity: Float) {
        val newCutoff = (180f + normalizedIntensity * 1820f).coerceIn(180f, 2000f)
        if (newCutoff != muffledCutoff) {
            muffledCutoff = newCutoff
            if (isMuffled) {
                resetStates()
                calculateCoefficients()
            }
        }
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        resetStates()
        calculateCoefficients()
        return inputAudioFormat
    }

    override fun onFlush() {
        resetStates()
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val outputBuffer = replaceOutputBuffer(remaining)
        if (!isMuffled && !isBassBoost) {
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        while (inputBuffer.remaining() >= 2) {
            var sample = inputBuffer.getShort().toFloat()

            if (isMuffled) {
                val filtered = b0M * sample + b1M * x1M + b2M * x2M - a1M * y1M - a2M * y2M
                x2M = x1M
                x1M = sample
                y2M = y1M
                y1M = filtered
                sample = filtered
            }

            if (isBassBoost) {
                val filtered = b0B * sample + b1B * x1B + b2B * x2B - a1B * y1B - a2B * y2B
                x2B = x1B
                x1B = sample
                y2B = y1B
                y1B = filtered
                sample = filtered
            }

            val out = sample.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()
            outputBuffer.putShort(out)
        }
        outputBuffer.flip()
    }

    private fun resetStates() {
        x1M = 0f
        x2M = 0f
        y1M = 0f
        y2M = 0f
        x1B = 0f
        x2B = 0f
        y1B = 0f
        y2B = 0f
    }

    private fun calculateCoefficients() {
        val sampleRate = inputAudioFormat.sampleRate.toFloat().coerceAtLeast(44100f)

        if (isMuffled) {
            val w0 = (2.0 * PI * muffledCutoff / sampleRate).toFloat()
            val alpha = (sin(w0) / (2.0 * 0.707f)).toFloat()
            val cosW0 = cos(w0).toFloat()
            val a0 = 1f + alpha

            b0M = ((1f - cosW0) / 2f) / a0
            b1M = (1f - cosW0) / a0
            b2M = ((1f - cosW0) / 2f) / a0
            a1M = (-2f * cosW0) / a0
            a2M = (1f - alpha) / a0
        }

        if (isBassBoost) {
            val gain = bassBoostGain
            val shelfSlope = 1f
            val amp = Math.pow(10.0, gain / 40.0).toFloat()
            val w0 = (2.0 * PI * 100f / sampleRate).toFloat()
            val sinW0 = sin(w0).toFloat()
            val cosW0 = cos(w0).toFloat()
            val alpha = sinW0 / 2f *
                Math.sqrt(((amp + 1f / amp) * (1f / shelfSlope - 1f) + 2f).toDouble()).toFloat()
            val beta = 2f * Math.sqrt(amp.toDouble()).toFloat() * alpha
            val a0 = (amp + 1f) + (amp - 1f) * cosW0 + beta

            b0B = (amp * ((amp + 1f) - (amp - 1f) * cosW0 + beta)) / a0
            b1B = (2f * amp * ((amp - 1f) - (amp + 1f) * cosW0)) / a0
            b2B = (amp * ((amp + 1f) - (amp - 1f) * cosW0 - beta)) / a0
            a1B = (-2f * ((amp - 1f) + (amp + 1f) * cosW0)) / a0
            a2B = ((amp + 1f) + (amp - 1f) * cosW0 - beta) / a0
        }
    }
}

@UnstableApi
class ReverbAudioProcessor : BaseAudioProcessor() {
    private var enabled = false
    private var combFilters: Array<CombFilter> = emptyArray()
    private var allPassFilters: Array<AllPassFilter> = emptyArray()
    private var wet = 0.25f
    private var dry = 0.75f
    private var roomSize = 0.78f
    private var damping = 0.35f

    fun setEnabled(enabled: Boolean) {
        if (this.enabled != enabled) {
            this.enabled = enabled
            if (!enabled) resetStates()
        }
    }

    fun setAmount(normalizedIntensity: Float) {
        val intensity = normalizedIntensity.coerceIn(0f, 1f)
        wet = (0.12f + intensity * 0.42f).coerceIn(0.12f, 0.54f)
        dry = (1f - wet * 0.65f).coerceIn(0.65f, 0.95f)
        roomSize = (0.68f + intensity * 0.22f).coerceIn(0.68f, 0.9f)
        damping = (0.22f + intensity * 0.48f).coerceIn(0.22f, 0.7f)
        combFilters.forEach { it.setFeedback(roomSize, damping) }
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        createFilters(inputAudioFormat.sampleRate, inputAudioFormat.channelCount)
        return inputAudioFormat
    }

    override fun onFlush() {
        resetStates()
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val outputBuffer = replaceOutputBuffer(remaining)
        if (!enabled) {
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        while (inputBuffer.remaining() >= 2) {
            val inputSample = inputBuffer.getShort().toFloat() / Short.MAX_VALUE
            val combSum = combFilters.sumOf { it.process(inputSample).toDouble() }.toFloat()
            val combOut = if (combFilters.isNotEmpty()) combSum / combFilters.size else inputSample
            val reverbOut = allPassFilters.fold(combOut) { sample, filter -> filter.process(sample) }
            val outputSample = ((inputSample * dry + reverbOut * wet) * Short.MAX_VALUE)
                .toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()

            outputBuffer.putShort(outputSample)
        }
        outputBuffer.flip()
    }

    private fun createFilters(sampleRate: Int, channelCount: Int) {
        val scale = sampleRate / 44100f
        val channels = channelCount.coerceAtLeast(1)
        combFilters = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491)
            .map { delay ->
                CombFilter((delay * scale).toInt().coerceAtLeast(1) * channels)
            }
            .toTypedArray()
        allPassFilters = intArrayOf(556, 441, 341)
            .map { delay ->
                AllPassFilter((delay * scale).toInt().coerceAtLeast(1) * channels)
            }
            .toTypedArray()
        setAmount((wet - 0.12f) / 0.42f)
    }

    private fun resetStates() {
        combFilters.forEach(CombFilter::reset)
        allPassFilters.forEach(AllPassFilter::reset)
    }

    private class CombFilter(size: Int) {
        private val buffer = FloatArray(size)
        private var index = 0
        private var feedback = 0.78f
        private var damping = 0.35f
        private var dampedSample = 0f

        fun setFeedback(feedback: Float, damping: Float) {
            this.feedback = feedback
            this.damping = damping
        }

        fun process(input: Float): Float {
            val output = buffer[index]
            dampedSample = output * (1f - damping) + dampedSample * damping
            buffer[index] = input + dampedSample * feedback
            index = (index + 1) % buffer.size
            return output
        }

        fun reset() {
            buffer.fill(0f)
            index = 0
            dampedSample = 0f
        }
    }

    private class AllPassFilter(size: Int) {
        private val buffer = FloatArray(size)
        private var index = 0
        private val feedback = 0.5f

        fun process(input: Float): Float {
            val buffered = buffer[index]
            val output = -input + buffered
            buffer[index] = input + buffered * feedback
            index = (index + 1) % buffer.size
            return output
        }

        fun reset() {
            buffer.fill(0f)
            index = 0
        }
    }
}

@UnstableApi
object PlaybackAudioProcessors {
    val eightD = EightDAudioProcessor()
    val fx = FxAudioProcessor()
    val reverb = ReverbAudioProcessor()

    fun asArray(): Array<AudioProcessor> = arrayOf(eightD, fx, reverb)

    fun setMode(mode: AudioEffectMode) {
        eightD.setEnabled(mode == AudioEffectMode.EIGHT_D)
        fx.setEffects(
            muffled = mode == AudioEffectMode.MUFFLED,
            bassBoost = mode == AudioEffectMode.BASS_BOOST
        )
        reverb.setEnabled(mode == AudioEffectMode.REVERB)
    }

    fun setIntensity(normalizedIntensity: Float) {
        val intensity = normalizedIntensity.coerceIn(0f, 1f)
        eightD.setDepth(intensity)
        fx.setBassBoostGain(intensity)
        fx.setMuffledCutoff(1f - intensity)
        reverb.setAmount(intensity)
    }

    fun setSpeed(normalizedSpeed: Float) {
        eightD.setSpeed(normalizedSpeed.coerceIn(0f, 1f))
    }
}
