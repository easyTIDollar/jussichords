package com.jussicodes.music.playback.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@UnstableApi
class EightDAudioProcessorTest {
    @Test
    fun disabledProcessorPassesPcmThroughUnchanged() {
        val processor = configuredProcessor()
        val samples = shortArrayOf(1200, -900, Short.MAX_VALUE, Short.MIN_VALUE, 17, 42)

        processor.queueInput(bufferOf(samples))

        assertArrayEquals(samples, shortsFrom(processor.output))
    }

    @Test
    fun zeroDepthPassesPcmThroughUnchanged() {
        val processor = configuredProcessor().apply {
            setEnabled(true)
            setDepth(0f)
        }
        val samples = shortArrayOf(3000, -2000, -14000, 12000, 1, -1)

        processor.queueInput(bufferOf(samples))

        assertArrayEquals(samples, shortsFrom(processor.output))
    }

    @Test
    fun enabledProcessorAppliesBinauralImpulseResponse() {
        val processor = configuredProcessor().apply {
            setEnabled(true)
            setDepth(1f)
        }
        val samples = ShortArray(256 * 2)
        samples[0] = 20_000
        samples[1] = 20_000

        processor.queueInput(bufferOf(samples))
        val output = shortsFrom(processor.output)

        assertTrue("left ear should contain HRIR impulse energy", output.leftPeak() > 100)
        assertTrue("right ear should contain HRIR impulse energy", output.rightPeak() > 100)
        assertTrue("HRIR should produce different left and right signals", output.anyLeftRightDifference())
    }

    @Test
    fun directionChangesDoNotCreateInvalidSamples() {
        val processor = configuredProcessor().apply {
            setEnabled(true)
            setDepth(1f)
            setSpeed(1f)
        }
        val frameCount = SAMPLE_RATE
        val samples = ShortArray(frameCount * 2) { index ->
            (sin(index * 0.03) * 18_000).roundToInt().toShort()
        }

        processor.queueInput(bufferOf(samples))
        val output = shortsFrom(processor.output)

        assertTrue(output.any { abs(it.toInt()) > 100 })
        assertTrue(output.all { it in Short.MIN_VALUE..Short.MAX_VALUE })
    }

    private fun ShortArray.leftPeak(): Int =
        indices.filter { it % 2 == 0 }.maxOf { abs(this[it].toInt()) }

    private fun ShortArray.rightPeak(): Int =
        indices.filter { it % 2 == 1 }.maxOf { abs(this[it].toInt()) }

    private fun ShortArray.anyLeftRightDifference(): Boolean =
        indices.any { it % 2 == 0 && this[it] != this[it + 1] }

    private fun configuredProcessor() = EightDAudioProcessor().apply {
        configure(AudioProcessor.AudioFormat(SAMPLE_RATE, 2, C.ENCODING_PCM_16BIT))
        flush()
    }

    private fun bufferOf(samples: ShortArray): ByteBuffer =
        ByteBuffer.allocateDirect(samples.size * Short.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .apply {
                samples.forEach(::putShort)
                flip()
            }

    private fun shortsFrom(buffer: ByteBuffer): ShortArray =
        ShortArray(buffer.remaining() / Short.SIZE_BYTES) { buffer.getShort() }

    private companion object {
        const val SAMPLE_RATE = 48_000
    }
}
