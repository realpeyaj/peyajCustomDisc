package com.peyaj.jukeboxweb.util

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.tukaani.xz.XZInputStream

object FFmpegManager {
    // Links (BtbN GitHub Release builds for high speed & architecture support)
    private const val LINUX_AMD64_URL = "https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-linux64-gpl.tar.xz"
    private const val LINUX_ARM64_URL = "https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-linuxarm64-gpl.tar.xz"
    private const val WINDOWS_URL = "https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-win64-gpl.zip"
    private const val LINUX_AMD64_FALLBACK_URL = "https://johnvansickle.com/ffmpeg/releases/ffmpeg-release-amd64-static.tar.xz"

    fun ensureFFmpeg(
        dataFolder: File,
        logInfo: (String) -> Unit = {},
        logWarning: (String) -> Unit = {},
        logSevere: (String, Throwable?) -> Unit = { _, _ -> }
    ) {
        // 1. Check if AudioConverter already works (System FFmpeg)
        if (AudioConverter.isFFmpegAvailable("ffmpeg")) {
            logInfo("System FFmpeg detected and working.")
            AudioConverter.executablePath = "ffmpeg"
            return
        }

        // 2. Check LOCAL FFmpeg (plugins/peyajCustomDisc/bin/ffmpeg)
        val binDir = File(dataFolder, "bin")
        if (!binDir.exists()) binDir.mkdirs()

        val os = System.getProperty("os.name").lowercase()
        val arch = System.getProperty("os.arch").lowercase()
        val isWindows = os.contains("win")
        val isArm = arch.contains("aarch64") || arch.contains("arm")
        val binaryName = if (isWindows) "ffmpeg.exe" else "ffmpeg"
        
        // Search recursively in bin for the binary
        var localBinary: File? = binDir.walkTopDown().find { it.name == binaryName && it.isFile }

        if (localBinary != null && localBinary!!.exists()) {
             // Validate it
             if (!isWindows) localBinary!!.setExecutable(true) // Ensure exec
             
             if (AudioConverter.isFFmpegAvailable(localBinary!!.absolutePath)) {
                 logInfo("Local FFmpeg detected at ${localBinary!!.absolutePath}")
                 AudioConverter.executablePath = localBinary!!.absolutePath
                 return
             } else {
                 logWarning("Local FFmpeg found but failed validation. Redownloading...")
                 localBinary!!.delete()
             }
        }

        // 3. Download Logic
        logInfo("FFmpeg not found. Downloading portable version for OS: $os ($arch) ...")
        
        try {
            val downloadUrl = if (isWindows) {
                WINDOWS_URL
            } else if (isArm) {
                LINUX_ARM64_URL
            } else {
                LINUX_AMD64_URL
            }
            val archiveExt = if (isWindows) ".zip" else ".tar.xz"
            val archiveFile = File(binDir, "ffmpeg_archive$archiveExt")

            // Download
            logInfo("Downloading from: $downloadUrl")
            try {
                URL(downloadUrl).openStream().use { input ->
                    Files.copy(input, archiveFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            } catch (dlEx: Exception) {
                if (!isWindows && !isArm) {
                    logWarning("Primary download failed (${dlEx.message}). Trying fallback...")
                    URL(LINUX_AMD64_FALLBACK_URL).openStream().use { input ->
                        Files.copy(input, archiveFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    }
                } else {
                    throw dlEx
                }
            }
            logInfo("Download complete. Extracting...")

            // Extract
            if (isWindows) {
                unzip(archiveFile, binDir)
            } else {
                untarxz(archiveFile, binDir)
            }
            
            // Re-find binary
            localBinary = binDir.walkTopDown().find { it.name == binaryName }
            
            if (localBinary != null) {
                if (!isWindows) localBinary!!.setExecutable(true)
                
                AudioConverter.executablePath = localBinary!!.absolutePath
                logInfo("FFmpeg installed successfully: ${localBinary!!.absolutePath}")
                
            } else {
                logSevere("Extraction failed: Could not find $binaryName in extracted files.", null)
            }
            
            // Cleanup
            archiveFile.delete()

        } catch (e: Exception) {
            logSevere("Failed to download FFmpeg: ${e.message}", e)
        }
    }

    private fun unzip(zipFile: File, destDir: File) {
        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val newFile = File(destDir, entry.name)
                if (entry.isDirectory) {
                    newFile.mkdirs()
                } else {
                    newFile.parentFile.mkdirs()
                    FileOutputStream(newFile).use { fos ->
                        zis.copyTo(fos)
                    }
                }
                entry = zis.nextEntry
            }
        }
    }

    private fun untarxz(tarXzFile: File, destDir: File) {
        FileInputStream(tarXzFile).use { fis ->
            BufferedInputStream(fis).use { bis ->
                XZInputStream(bis).use { xzis ->
                    TarArchiveInputStream(xzis).use { tarIn ->
                        var entry = tarIn.nextTarEntry
                        while (entry != null) {
                            val newFile = File(destDir, entry.name)
                            if (entry.isDirectory) {
                                newFile.mkdirs()
                            } else {
                                newFile.parentFile.mkdirs()
                                FileOutputStream(newFile).use { fos ->
                                    tarIn.copyTo(fos)
                                }
                            }
                            entry = tarIn.nextTarEntry
                        }
                    }
                }
            }
        }
    }
}
