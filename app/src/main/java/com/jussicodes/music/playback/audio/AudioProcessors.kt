package com.jussicodes.music.playback.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

enum class AudioEffectMode {
    OFF,
    EIGHT_D,
    REVERB
}

@UnstableApi
class EightDAudioProcessor : BaseAudioProcessor() {
    @Volatile
    private var enabled = false

    @Volatile
    private var rotationPeriodSeconds = 10.0

    @Volatile
    private var depth = 0.5f

    private var coefficients: ShortArray = SadieHrirData.HRIR_48000
    private var phase = 0.0
    private var phaseIncrement = 0.0
    private var history = FloatArray(SadieHrirData.TAPS)
    private var historyIndex = 0
    private var leftLowPassState = 0f
    private var rightLowPassState = 0f

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        if (!enabled) resetState()
    }

    fun setSpeed(normalizedSpeed: Float) {
        rotationPeriodSeconds = 24.0 - normalizedSpeed.coerceIn(0f, 1f) * 20.0
        updatePhaseIncrement()
    }

    fun setDepth(normalizedIntensity: Float) {
        depth = normalizedIntensity.coerceIn(0f, 1f)
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount != 2) {
            return AudioProcessor.AudioFormat.NOT_SET
        }

        coefficients = SadieHrirData.coefficientsFor(inputAudioFormat.sampleRate)
        history = FloatArray(SadieHrirData.TAPS)
        updatePhaseIncrement(inputAudioFormat.sampleRate)
        resetState()
        return inputAudioFormat
    }

    override fun onFlush() {
        resetState()
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

        while (inputBuffer.remaining() >= 4) {
            val dryLeft = inputBuffer.getShort().toFloat()
            val dryRight = inputBuffer.getShort().toFloat()
            val mono = (dryLeft + dryRight) * 0.5f

            history[historyIndex] = mono
            val spatialPhase = orbitPhase()
            val rawWetLeft = convolveEar(LEFT_EAR)
            val rawWetRight = convolveEar(RIGHT_EAR)
            val frontAmount = maxOf(0.0, cos(spatialPhase)).toFloat()
            val rearAmount = maxOf(0.0, -cos(spatialPhase)).toFloat()
            val wetLeft = applyMedianPlaneCue(rawWetLeft, LEFT_EAR, frontAmount, rearAmount)
            val wetRight = applyMedianPlaneCue(rawWetRight, RIGHT_EAR, frontAmount, rearAmount)

            val mix = if (depth <= 0f) 0f else (0.35f + depth * 0.65f).coerceIn(0f, 1f)
            outputBuffer.putShort(mix(dryLeft, wetLeft, mix))
            outputBuffer.putShort(mix(dryRight, wetRight, mix))

            historyIndex = (historyIndex + 1) % history.size
            phase += phaseIncrement
            if (phase >= TWO_PI) phase -= TWO_PI
        }
        outputBuffer.flip()
    }

    private fun convolveEar(ear: Int): Float {
        val scaledPosition = orbitPhase() / TWO_PI * SadieHrirData.POSITIONS
        val firstPosition = floor(scaledPosition).toInt() % SadieHrirData.POSITIONS
        val secondPosition = (firstPosition + 1) % SadieHrirData.POSITIONS
        val blend = (scaledPosition - floor(scaledPosition)).toFloat()
        val firstOffset = ((firstPosition * EARS) + ear) * SadieHrirData.TAPS
        val secondOffset = ((secondPosition * EARS) + ear) * SadieHrirData.TAPS
        var sum = 0f
        var readIndex = historyIndex

        for (tap in 0 until SadieHrirData.TAPS) {
            val coefficient = coefficients[firstOffset + tap] * (1f - blend) +
                coefficients[secondOffset + tap] * blend
            sum += history[readIndex] * coefficient
            readIndex--
            if (readIndex < 0) readIndex = history.lastIndex
        }
        return sum / SadieHrirData.SCALE
    }

    private fun orbitPhase(): Double {
        val slowedAtFrontAndRear = phase - FRONT_REAR_DWELL * 0.5 * sin(phase * 2.0)
        return if (slowedAtFrontAndRear < 0.0) slowedAtFrontAndRear + TWO_PI else slowedAtFrontAndRear
    }

    private fun applyMedianPlaneCue(input: Float, ear: Int, frontAmount: Float, rearAmount: Float): Float {
        val lowPassed = lowPass(input, ear, 4_800f)
        val highPassed = input - lowPassed
        val frontPresence = input + highPassed * 0.22f * frontAmount
        val rearDarkened = frontPresence * (1f - rearAmount * 0.18f) - highPassed * 0.58f * rearAmount
        return rearDarkened
    }

    private fun lowPass(input: Float, ear: Int, cutoffHz: Float): Float {
        val sampleRate = inputAudioFormat.sampleRate.coerceAtLeast(1)
        val memory = exp(-TWO_PI * cutoffHz / sampleRate).toFloat()
        return if (ear == LEFT_EAR) {
            leftLowPassState = input * (1f - memory) + leftLowPassState * memory
            leftLowPassState
        } else {
            rightLowPassState = input * (1f - memory) + rightLowPassState * memory
            rightLowPassState
        }
    }

    private fun mix(dry: Float, wet: Float, amount: Float): Short =
        (dry * (1f - amount) + wet * amount)
            .roundToInt()
            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            .toShort()

    private fun resetState() {
        phase = 0.0
        history.fill(0f)
        historyIndex = 0
        leftLowPassState = 0f
        rightLowPassState = 0f
    }

    private fun updatePhaseIncrement(sampleRate: Int = inputAudioFormat.sampleRate) {
        if (sampleRate > 0) phaseIncrement = TWO_PI / (rotationPeriodSeconds * sampleRate)
    }

    private companion object {
        const val EARS = 2
        const val LEFT_EAR = 0
        const val RIGHT_EAR = 1
        const val FRONT_REAR_DWELL = 0.58
        const val TWO_PI = 2.0 * PI
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
    private var channels: Array<ReverbChannel> = emptyArray()
    private var channelCount = 0
    private var wet = 0.22f
    private var dry = 0.82f
    private var roomSize = 0.76f
    private var damping = 0.42f

    fun setEnabled(enabled: Boolean) {
        if (this.enabled != enabled) {
            this.enabled = enabled
            if (!enabled) resetStates()
        }
    }

    fun setAmount(normalizedIntensity: Float) {
        val intensity = normalizedIntensity.coerceIn(0f, 1f)
        wet = (0.08f + intensity * 0.24f).coerceIn(0.08f, 0.32f)
        dry = (0.92f - intensity * 0.12f).coerceIn(0.80f, 0.92f)
        roomSize = (0.68f + intensity * 0.16f).coerceIn(0.68f, 0.84f)
        damping = (0.34f + intensity * 0.34f).coerceIn(0.34f, 0.68f)
        channels.forEach { it.setFeedback(roomSize, damping) }
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        channelCount = inputAudioFormat.channelCount.coerceAtLeast(1)
        createFilters(inputAudioFormat.sampleRate, channelCount)
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

        var channel = 0
        while (inputBuffer.remaining() >= 2) {
            val inputSample = inputBuffer.getShort().toFloat() / Short.MAX_VALUE
            val reverbOut = channels.getOrNull(channel)?.process(inputSample * INPUT_GAIN) ?: inputSample
            val outputSample = floatToPcm16(inputSample * dry + reverbOut * wet)

            outputBuffer.putShort(outputSample)
            channel = (channel + 1) % channelCount
        }
        outputBuffer.flip()
    }

    private fun createFilters(sampleRate: Int, channelCount: Int) {
        val scale = sampleRate / 44100f
        channels = Array(channelCount.coerceAtLeast(1)) { channel ->
            ReverbChannel(
                sampleRate = sampleRate,
                combSizes = COMB_DELAYS.map { delay ->
                    ((delay + channel * STEREO_SPREAD) * scale).roundToInt().coerceAtLeast(1)
                },
                allPassSizes = ALL_PASS_DELAYS.map { delay ->
                    ((delay + channel * STEREO_SPREAD) * scale).roundToInt().coerceAtLeast(1)
                }
            )
        }
        setAmount(((wet - 0.08f) / 0.24f).coerceIn(0f, 1f))
    }

    private fun resetStates() {
        channels.forEach(ReverbChannel::reset)
    }

    private fun floatToPcm16(sample: Float): Short {
        val limited = softClip(sample)
        return (limited * Short.MAX_VALUE)
            .roundToInt()
            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            .toShort()
    }

    private fun softClip(sample: Float): Float {
        val x = sample.coerceIn(-SOFT_CLIP_LIMIT, SOFT_CLIP_LIMIT)
        return x * (1.5f - 0.5f * x * x)
    }

    private class ReverbChannel(
        sampleRate: Int,
        combSizes: List<Int>,
        allPassSizes: List<Int>
    ) {
        private val combFilters = combSizes.map(::CombFilter).toTypedArray()
        private val allPassFilters = allPassSizes.map(::AllPassFilter).toTypedArray()
        private val inputLowCut = OnePoleHighPass(sampleRate, WET_LOW_CUT_HZ)
        private val outputLowCut = OnePoleHighPass(sampleRate, WET_LOW_CUT_HZ)

        fun setFeedback(feedback: Float, damping: Float) {
            combFilters.forEach { it.setFeedback(feedback, damping) }
        }

        fun process(input: Float): Float {
            val filteredInput = inputLowCut.process(input)
            val combSum = combFilters.sumOf { it.process(filteredInput).toDouble() }.toFloat()
            val combOut = combSum * COMB_GAIN
            val diffused = allPassFilters.fold(combOut) { sample, filter -> filter.process(sample) }
            return outputLowCut.process(diffused)
        }

        fun reset() {
            combFilters.forEach(CombFilter::reset)
            allPassFilters.forEach(AllPassFilter::reset)
            inputLowCut.reset()
            outputLowCut.reset()
        }
    }

    private class OnePoleHighPass(sampleRate: Int, cutoffHz: Float) {
        private val coefficient = 1f - exp(-TWO_PI_FLOAT * cutoffHz / sampleRate.coerceAtLeast(1)).toFloat()
        private var low = 0f

        fun process(input: Float): Float {
            low += coefficient * (input - low)
            return input - low
        }

        fun reset() {
            low = 0f
        }
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
            buffer[index] = (input + dampedSample * feedback).sanitize()
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
            buffer[index] = (input + buffered * feedback).sanitize()
            index = (index + 1) % buffer.size
            return output
        }

        fun reset() {
            buffer.fill(0f)
            index = 0
        }
    }

    private companion object {
        val COMB_DELAYS = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617)
        val ALL_PASS_DELAYS = intArrayOf(556, 441, 341, 225)
        const val STEREO_SPREAD = 23
        const val INPUT_GAIN = 0.62f
        const val COMB_GAIN = 0.125f
        const val SOFT_CLIP_LIMIT = 1f
        const val WET_LOW_CUT_HZ = 140f
        const val TWO_PI_FLOAT = (2f * PI).toFloat()

        fun Float.sanitize(): Float = if (this.isFinite()) this.coerceIn(-4f, 4f) else 0f
    }
}

@UnstableApi
object PlaybackAudioProcessors {
    val eightD = EightDAudioProcessor()
    val fx = FxAudioProcessor()
    val reverb = ReverbAudioProcessor()

    fun asArray(): Array<AudioProcessor> = arrayOf(eightD, fx, reverb)

    fun setMode(mode: AudioEffectMode) {
        setEnabled(
            eightDEnabled = mode == AudioEffectMode.EIGHT_D,
            reverbEnabled = mode == AudioEffectMode.REVERB
        )
    }

    fun setEnabled(eightDEnabled: Boolean, reverbEnabled: Boolean) {
        eightD.setEnabled(eightDEnabled)
        fx.setEffects(muffled = false, bassBoost = false)
        reverb.setEnabled(reverbEnabled)
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
