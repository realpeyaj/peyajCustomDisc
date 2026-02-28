package com.peyaj.jukeboxweb.web

import com.peyaj.jukeboxweb.PeyajCustomDisc
import com.peyaj.jukeboxweb.disc.CustomDisc
import io.javalin.Javalin
import io.javalin.http.ContentType
import io.javalin.http.UploadedFile
import java.io.File
import java.util.UUID

class WebServer(private val plugin: PeyajCustomDisc) {

    private var app: Javalin? = null

    fun start(port: Int) {
        val classLoader = this.javaClass.classLoader
        
        app = Javalin.create { config ->
            // config.staticFiles.add("/web", io.javalin.http.staticfiles.Location.CLASSPATH)
            
            config.jetty.modifyServer { server ->
                // Fix for Proxy/HAProxy connection issues
                for (connector in server.connectors) {
                    if (connector is org.eclipse.jetty.server.AbstractConnector) {
                        connector.idleTimeout = 3600000 // 1 hour timeout
                    }
                }

                // Check for HAProxy Support (PROXY Protocol)
                val haProxySupport = plugin.config.getBoolean("haproxy-support", false)
                if (haProxySupport) {
                     for (connector in server.connectors) {
                        if (connector is org.eclipse.jetty.server.ServerConnector) {
                            val proxyConnectionFactory = org.eclipse.jetty.server.ProxyConnectionFactory()
                            connector.addConnectionFactory(proxyConnectionFactory)
                         }
                     }
                }
            }
            
            config.jetty.modifyHttpConfiguration { httpConfig ->
                // Enable X-Forwarded-For / Proto support
                httpConfig.addCustomizer(org.eclipse.jetty.server.ForwardedRequestCustomizer())
            }
        }.start(port)
        
        app?.before { ctx ->
            ctx.header("Cross-Origin-Opener-Policy", "same-origin")
            ctx.header("Cross-Origin-Embedder-Policy", "require-corp")
            
            // Authentication Middleware
            val path = ctx.path()
            // Public Paths
            if (path.startsWith("/download") || path.startsWith("/login") || path.startsWith("/unauthorized") || path.startsWith("/listen") || path.endsWith(".css") || path.endsWith(".js")) {
                return@before
            }
            
            // Check Session
            val sessionCookie = ctx.cookie("session_token")
            if (!com.peyaj.jukeboxweb.auth.AuthManager.isValidSession(sessionCookie)) {
                // If accessing API via fetch, return 401
                if (path.startsWith("/api")) {
                    ctx.status(401).result("Unauthorized")
                } else {
                    // Redirect browser to unauthorized page
                    ctx.redirect("/unauthorized")
                }
                // Skip remaining handlers
                ctx.skipRemainingHandlers()
            }
        }

        // Login Route (Redeem Token)
        app?.get("/login") { ctx ->
            val token = ctx.queryParam("token")
            if (token != null) {
                val session = com.peyaj.jukeboxweb.auth.AuthManager.redeemToken(token)
                if (session != null) {
                    // Valid! Set cookie and redirect home
                    ctx.cookie("session_token", session, 86400) // 24h
                    ctx.redirect("/")
                } else {
                    ctx.result("Invalid or Expired Link.")
                }
            } else {
                ctx.result("Missing Token.")
            }
        }
        
        // Unauthorized Page
        app?.get("/unauthorized") { ctx ->
            ctx.html("""
                <html>
                <body style='background-color:#1a1a1a; color: white; user-select:none; font-family: monospace; text-align: center; padding-top: 50px;'>
                   <h1>401 Unauthorized</h1>
                   <p>Access Denied.</p>
                   <p>Please run <span style='color: yellow;'>/disc web</span> in-game to generate a login link.</p>
                </body>
                </html>
            """.trimIndent())
        }

        app?.get("/") { ctx ->
            val stream = plugin.getResource("secure/index.html")
            if (stream != null) {
                ctx.contentType("text/html").result(stream)
            } else {
                ctx.result("Frontend not found")
            }
        }
        
        // (Web Radio removed in v1.5)

        app?.post("/api/disc") { ctx ->
            val name = ctx.formParam("name") ?: "Unknown Disc"
            val author = ctx.formParam("author") ?: "Unknown Artist"
            val loreRaw = ctx.formParam("lore") ?: ""
            val style = ctx.formParam("style") ?: "cat" // NEW: Disc style selection
            val uploadedFile = ctx.uploadedFile("file")

            if (uploadedFile == null) {
                ctx.status(400).result("No file uploaded")
                return@post
            }

            // ID generation
            val id = name.lowercase().replace(Regex("[^a-z0-9]"), "_") + "_" + UUID.randomUUID().toString().substring(0, 4)

            // Determine if input is OGG or needs conversion
            val filename = uploadedFile.filename().lowercase()
            val isOgg = filename.endsWith(".ogg")
            
            // Accepted formats for conversion (Audio extraction)
            val validExtensions = listOf(".mp3", ".wav", ".flac", ".m4a", ".mp4", ".wma", ".aac", ".webm")
            val isValidInput = isOgg || validExtensions.any { filename.endsWith(it) }
            
            if (!isValidInput) {
                 ctx.status(400).result("Invalid format. Supported: .ogg (Direct), .mp3, .wav, .flac, .m4a, .mp4, .wma, .aac")
                 return@post
            }
            
            val targetOgg = File(plugin.dataFolder, "discs/$id.ogg")
            targetOgg.parentFile.mkdirs()
            
            var durationSeconds = 0
            
            if (!isOgg) {
                // Server-Side Conversion (Extract Audio)
                if (!com.peyaj.jukeboxweb.util.AudioConverter.isFFmpegAvailable()) {
                     ctx.status(501).result("Server-side conversion unreachable. FFmpeg not installed on server/host OS.")
                     return@post
                }
                
                // Save temp file (preserve extension for ffmpeg detection)
                val ext = filename.substringAfterLast('.', "tmp")
                val tempFile = File(plugin.dataFolder, "temp/$id.$ext")
                tempFile.parentFile.mkdirs()
                uploadedFile.content().copyTo(tempFile.outputStream())
                
                // Convert
                val result = com.peyaj.jukeboxweb.util.AudioConverter.convertToOgg(tempFile, targetOgg)
                tempFile.delete() // Cleanup temp
                
                if (!result.first) {
                    ctx.status(500).result("Conversion Failed. Check server console.")
                    return@post
                }
                durationSeconds = result.second
                
            } else {
                // Direct Save (OGG)
                uploadedFile.content().copyTo(targetOgg.outputStream())
                // TODO: Parse OGG duration? For now 0.
            }

            // Create Disc Object with style
            val disc = CustomDisc(
                id = id,
                name = name,
                author = author,
                lore = loreRaw.split("\n"),
                durationSeconds = durationSeconds,
                style = style // NEW: Pass style to disc
            )

            // Sync with main thread for Bukkit logic
            plugin.server.scheduler.runTask(plugin, Runnable {
                plugin.discManager.addDisc(disc)
                
                // Rebuild resource pack
                plugin.packGenerator.buildResourcePack(plugin.discManager.getAllDiscs())
                
                // Trigger Auto-Update
                com.peyaj.jukeboxweb.pack.PackUpdater.updateAllPlayers(plugin)
            })

            ctx.status(201).result("Disc created: $id")
        }
        
        app?.get("/api/discs") { ctx ->
            ctx.json(plugin.discManager.getAllDiscs())
        }
        
        app?.delete("/api/disc/{id}") { ctx ->
             val id = ctx.pathParam("id")
             if (plugin.discManager.deleteDisc(id)) {
                 // Rebuild pack
                 plugin.server.scheduler.runTask(plugin, Runnable {
                     plugin.packGenerator.buildResourcePack(plugin.discManager.getAllDiscs())
                     com.peyaj.jukeboxweb.pack.PackUpdater.updateAllPlayers(plugin)
                 })
                 ctx.status(200).result("Deleted")
             } else {
                 ctx.status(404).result("Not Found")
             }
        }
        
        app?.get("/download/pack") { ctx ->
            val packFile = plugin.packGenerator.getPackFile()
            if (packFile.exists()) {
                ctx.contentType("application/zip")
                ctx.header("Content-Disposition", "attachment; filename=\"peyajCD-Java.zip\"")
                ctx.result(packFile.inputStream())
            } else {
                ctx.status(404).result("Pack not generated yet")
            }
        }
        
        app?.get("/download/geyser") { ctx ->
            val packFile = plugin.packGenerator.getBedrockPackFile()
            if (packFile.exists()) {
                ctx.contentType("application/zip") 
                ctx.header("Content-Disposition", "attachment; filename=\"peyajCD-Bedrock.mcpack\"")
                ctx.result(packFile.inputStream())
            } else {
                ctx.status(404).result("Bedrock Pack not generated yet")
            }
        }
    }

    fun stop() {
        app?.stop()
    }
}
