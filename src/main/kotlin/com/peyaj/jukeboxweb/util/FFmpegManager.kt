package com.peyaj.jukeboxweb.util

import com.peyaj.jukeboxweb.PeyajCustomDisc
import java.io.File
import java.io.InputStream
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.tukaani.xz.XZInputStream
import java.util.zip.ZipInputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.BufferedInputStream

object FFmpegManager {
    // Links (Pinned to reliable static builds)
    private const val LINUX_URL = "https://johnvansickle.com/ffmpeg/releases/ffmpeg-release-amd64-static.tar.xz"
    // Using BtbN GitHub Release (Latest)
    private const val WINDOWS_URL = "https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-win64-gpl.zip"

    fun ensureFFmpeg(plugin: PeyajCustomDisc) {
        // 1. Check if AudioConverter already works (System FFmpeg)
        if (AudioConverter.isFFmpegAvailable("ffmpeg")) {
            plugin.logger.info("System FFmpeg detected and working.")
            AudioConverter.executablePath = "ffmpeg"
            return
        }

        // 2. Check LOCAL FFmpeg (plugins/peyajCustomDisc/ffmpeg/ffmpeg)
        val binDir = File(plugin.dataFolder, "bin")
        if (!binDir.exists()) binDir.mkdirs()

        val os = System.getProperty("os.name").lowercase()
        val isWindows = os.contains("win")
        val binaryName = if (isWindows) "ffmpeg.exe" else "ffmpeg"
        
        // Search recursively in bin for the binary
        var localBinary: File? = binDir.walkTopDown().find { it.name == binaryName && it.isFile }

        if (localBinary != null && localBinary!!.exists()) {
             // Validate it
             if (!isWindows) localBinary!!.setExecutable(true) // Ensure exec
             
             if (AudioConverter.isFFmpegAvailable(localBinary!!.absolutePath)) {
                 plugin.logger.info("Local FFmpeg detected at ${localBinary!!.absolutePath}")
                 AudioConverter.executablePath = localBinary!!.absolutePath
                 return
             } else {
                 plugin.logger.warning("Local FFmpeg found but failed validation. Redownloading...")
                 localBinary!!.delete()
             }
        }

        // 3. Download Logic
        plugin.logger.info("FFmpeg not found. Downloading portable version for OS: $os ...")
        
        try {
            val downloadUrl = if (isWindows) WINDOWS_URL else LINUX_URL
            val archiveExt = if (isWindows) ".zip" else ".tar.xz"
            val archiveFile = File(binDir, "ffmpeg_archive$archiveExt")

            // Download
            plugin.logger.info("Downloading from: $downloadUrl")
            URL(downloadUrl).openStream().use { input ->
                Files.copy(input, archiveFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            plugin.logger.info("Download complete. Extracting...")

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
                plugin.logger.info("FFmpeg installed successfully: ${localBinary!!.absolutePath}")
                
            } else {
                plugin.logger.severe("Extraction failed: Could not find $binaryName in extracted files.")
            }
            
            // Cleanup
            archiveFile.delete()

        } catch (e: Exception) {
            plugin.logger.severe("Failed to download FFmpeg: ${e.message}")
            e.printStackTrace()
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
        // Chain: FileInputStream -> Buffered -> XZInputStream -> TarArchiveInputStream
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
