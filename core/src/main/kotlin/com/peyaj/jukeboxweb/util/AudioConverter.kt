package com.peyaj.jukeboxweb.util

import java.io.File
import java.util.concurrent.TimeUnit

object AudioConverter {

    var executablePath: String = "ffmpeg" // Default to PATH

    // Check if FFmpeg is installed and accessible
    fun isFFmpegAvailable(path: String = executablePath): Boolean {
        return try {
            val process = ProcessBuilder(path, "-version").start()
            val exited = process.waitFor(5, TimeUnit.SECONDS)
            exited && process.exitValue() == 0
        } catch (e: Exception) {
            false
        }
    }

    // Returns Pair(Success, DurationSeconds)
    fun convertToOgg(input: File, output: File, channels: Int = 1): Pair<Boolean, Int> {
        try {
            val commands = listOf(
                executablePath,
                "-y",
                "-i", input.absolutePath,
                "-map", "0:a:0", // Select first audio stream 
                "-vn", // Disable video
                "-map_metadata", "-1", // Strip metadata
                "-acodec", "libvorbis",
                "-ac", channels.toString(), // 1 = Mono (Jukeboxes), 2 = Stereo (Regions)
                "-ar", "44100", // Force 44.1kHz
                "-q:a", "4",
                output.absolutePath
            )

            val process = ProcessBuilder(commands)
                .redirectErrorStream(true) 
                .start()

            // Read process output for logging & duration parsing
            val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
            var line: String?
            var duration = 0
            
            // Regex: Duration: 00:03:45.32
            val durationRegex = Regex("Duration: (\\d{2}):(\\d{2}):(\\d{2})")
            
            while (reader.readLine().also { line = it } != null) {
                 println("[FFmpeg] $line")
                 if (line != null) {
                     val match = durationRegex.find(line!!)
                     if (match != null) {
                         val (h, m, s) = match.destructured
                         duration = h.toInt() * 3600 + m.toInt() * 60 + s.toInt()
                     }
                 }
            }
            
            val completed = process.waitFor(60, TimeUnit.SECONDS) // 1 minute timeout max
            
            // Check if successful (Exit 0) AND file created
            if (completed && process.exitValue() == 0 && output.exists() && output.length() > 0) {
                return Pair(true, duration)
            } else {
                return Pair(false, 0)
            }

        } catch (e: Exception) {
            e.printStackTrace()
            return Pair(false, 0)
        }
    }

    fun convertToOggStereo(input: File, output: File): Pair<Boolean, Int> {
        return convertToOgg(input, output, channels = 2)
    }

    // Probes audio file duration in seconds using FFmpeg without transcoding
    fun getAudioDuration(input: File): Int {
        if (!isFFmpegAvailable() || !input.exists()) return 0
        try {
            val commands = listOf(
                executablePath,
                "-i", input.absolutePath
            )
            val process = ProcessBuilder(commands)
                .redirectErrorStream(true)
                .start()

            val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
            var line: String?
            var duration = 0
            val durationRegex = Regex("Duration: (\\d{2}):(\\d{2}):(\\d{2})")

            while (reader.readLine().also { line = it } != null) {
                if (line != null) {
                    val match = durationRegex.find(line!!)
                    if (match != null) {
                        val (h, m, s) = match.destructured
                        duration = h.toInt() * 3600 + m.toInt() * 60 + s.toInt()
                    }
                }
            }
            process.waitFor(5, TimeUnit.SECONDS)
            return duration
        } catch (e: Exception) {
            return 0
        }
    }
}
