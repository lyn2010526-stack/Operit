package com.cynosure.operit.util

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode

/**
 * Utility class for FFmpeg operations
 */
object FFmpegUtil {
    private const val TAG = "FFmpegUtil"

    data class MediaInfo(
        val format: String,
        val duration: String,
        val bitrate: String,
        val streams: List<StreamInfo>
    )

    data class StreamInfo(
        val index: Int,
        val type: String,
        val codec: String,
        val width: Int?,
        val height: Int?,
        val frameRate: String?,
        val sampleRate: String?,
        val channels: Int?
    )

    /**
     * Build a scale filter string that survives FFmpegKit argument parsing.
     * FFmpeg expressions need an escaped comma when passed without a shell.
     */
    fun scaleFilterMaxWidth(maxWidth: Int): String = "scale=min(${maxWidth}\\,iw):-2"

    /**
     * Execute an FFmpeg command and return if it was successful
     */
    fun executeCommand(command: String): Boolean {
        try {
            AppLogger.d(TAG, "Executing FFmpeg command: $command")
            val session = FFmpegKit.execute(command)
            val returnCode = session.returnCode

            if (ReturnCode.isSuccess(returnCode)) {
                AppLogger.d(TAG, "FFmpeg command executed successfully")
                return true
            } else {
                AppLogger.e(
                    TAG,
                    "FFmpeg failed with return code: ${returnCode.value}, output: ${session.output}"
                )
                return false
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error executing FFmpeg command", e)
            return false
        }
    }

    /**
     * Get media information for a file
     */
    fun getMediaInfo(filePath: String): MediaInfo? {
        return try {
            val mediaInformation = FFprobeKit.getMediaInformation(filePath).mediaInformation
                ?: return null
            MediaInfo(
                format = mediaInformation.format ?: "unknown",
                duration = mediaInformation.duration ?: "0",
                bitrate = mediaInformation.bitrate ?: "0",
                streams = mediaInformation.streams.orEmpty().map { stream ->
                    val properties = stream.allProperties
                    StreamInfo(
                        index = stream.index?.toInt() ?: 0,
                        type = stream.type ?: "unknown",
                        codec = stream.codec ?: "unknown",
                        width = stream.width?.toInt(),
                        height = stream.height?.toInt(),
                        frameRate = properties?.optString("r_frame_rate")?.takeIf { it.isNotBlank() },
                        sampleRate = properties?.optString("sample_rate")?.takeIf { it.isNotBlank() },
                        channels = properties?.optInt("channels")?.takeIf { it > 0 }
                    )
                }
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error getting media info", e)
            null
        }
    }
}
