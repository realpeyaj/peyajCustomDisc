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
            config.jetty.modifyServer { server ->
                for (connector in server.connectors) {
                    if (connector is org.eclipse.jetty.server.AbstractConnector) {
                        connector.idleTimeout = 3600000
                    }
                }

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
                httpConfig.addCustomizer(org.eclipse.jetty.server.ForwardedRequestCustomizer())
            }
        }.start(port)
        
        app?.before { ctx ->
            ctx.header("Cross-Origin-Opener-Policy", "same-origin")
            ctx.header("Cross-Origin-Embedder-Policy", "require-corp")
            
            // Intercept unauthenticated web requests and protect REST API endpoints
            val path = ctx.path()
            if (path.startsWith("/download") || path.startsWith("/login") || path.startsWith("/unauthorized") || path.startsWith("/listen") || path.endsWith(".css") || path.endsWith(".js")) {
                return@before
            }
            
            val sessionCookie = ctx.cookie("session_token")
            if (!com.peyaj.jukeboxweb.auth.AuthManager.isValidSession(sessionCookie)) {
                if (path.startsWith("/api")) {
                    ctx.status(401).result("Unauthorized")
                } else {
                    ctx.redirect("/unauthorized")
                }
                ctx.skipRemainingHandlers()
            }
        }

        app?.get("/login") { ctx ->
            val token = ctx.queryParam("token")
            if (token != null) {
                val session = com.peyaj.jukeboxweb.auth.AuthManager.redeemToken(token)
                if (session != null) {
                    ctx.cookie("session_token", session, 86400)
                    ctx.redirect("/")
                } else {
                    ctx.redirect("/unauthorized?error=invalid_token")
                }
            } else {
                ctx.redirect("/unauthorized")
            }
        }

        app?.post("/login") { ctx ->
            val pass = ctx.formParam("password") ?: ""
            val configPass = plugin.config.getString("admin-password", "changeme") ?: ""
            val session = com.peyaj.jukeboxweb.auth.AuthManager.authenticatePassword(pass, configPass)
            if (session != null) {
                ctx.cookie("session_token", session, 86400)
                ctx.redirect("/")
            } else {
                ctx.redirect("/unauthorized?error=invalid_password")
            }
        }
        
        app?.get("/unauthorized") { ctx ->
            val error = ctx.queryParam("error")
            val errorMsg = when (error) {
                "invalid_token" -> "<div style='color:#ef4444; margin-bottom:12px;'>⚠️ Invalid or Expired Token Link.</div>"
                "invalid_password" -> "<div style='color:#ef4444; margin-bottom:12px;'>❌ Incorrect Admin Password.</div>"
                else -> ""
            }
            ctx.html("""
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <title>Disc Creator - Security Login</title>
                    <style>
                        body { background: #0f172a; color: #f8fafc; font-family: 'Segoe UI', Tahoma, sans-serif; display: flex; align-items: center; justify-content: center; height: 100vh; margin: 0; }
                        .card { background: #1e293b; padding: 32px; border-radius: 16px; border: 1px solid #334155; width: 340px; box-shadow: 0 10px 25px rgba(0,0,0,0.5); text-align: center; }
                        h2 { margin-top: 0; color: #38bdf8; }
                        input[type="password"] { width: 100%; padding: 12px; margin: 12px 0; border-radius: 8px; border: 1px solid #475569; background: #0f172a; color: #fff; box-sizing: border-box; font-size: 16px; text-align: center; }
                        button { width: 100%; padding: 12px; background: #0284c7; color: white; border: none; border-radius: 8px; font-weight: bold; cursor: pointer; font-size: 16px; }
                        button:hover { background: #0369a1; }
                        .help { margin-top: 20px; font-size: 13px; color: #94a3b8; line-height: 1.4; }
                        code { background: #0f172a; padding: 2px 6px; border-radius: 4px; color: #facc15; }
                    </style>
                </head>
                <body>
                    <div class="card">
                        <h2>🔒 Disc Creator</h2>
                        <p style="font-size:14px; color:#cbd5e1;">Security Authentication Required</p>
                        $errorMsg
                        <form action="/login" method="POST">
                            <input type="password" name="password" placeholder="Enter Admin Password" required autofocus />
                            <button type="submit">Unlock Disc Studio</button>
                        </form>
                        <div class="help">
                            Or run <code>/disc web</code> in-game to generate a 1-click login link.
                        </div>
                    </div>
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

        app?.post("/api/disc") { ctx ->
            val name = ctx.formParam("name") ?: "Unknown Disc"
            val author = ctx.formParam("author") ?: "Unknown Artist"
            val loreRaw = ctx.formParam("lore") ?: ""
            val style = ctx.formParam("style") ?: "cat"
            val uploadedFile = ctx.uploadedFile("file")

            if (uploadedFile == null) {
                ctx.status(400).result("No file uploaded")
                return@post
            }

            val id = name.lowercase().replace(Regex("[^a-z0-9]"), "_") + "_" + UUID.randomUUID().toString().substring(0, 4)

            val filename = uploadedFile.filename().lowercase()
            val isOgg = filename.endsWith(".ogg")
            
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
                if (!com.peyaj.jukeboxweb.util.AudioConverter.isFFmpegAvailable()) {
                     ctx.status(501).result("Server-side conversion unreachable. FFmpeg not installed on server/host OS.")
                     return@post
                }
                
                val ext = filename.substringAfterLast('.', "tmp")
                val tempFile = File(plugin.dataFolder, "temp/$id.$ext")
                tempFile.parentFile.mkdirs()
                uploadedFile.content().copyTo(tempFile.outputStream())
                
                val result = com.peyaj.jukeboxweb.util.AudioConverter.convertToOgg(tempFile, targetOgg)
                tempFile.delete()
                
                if (!result.first) {
                    ctx.status(500).result("Conversion Failed. Check server console.")
                    return@post
                }
                durationSeconds = result.second
                
            } else {
                uploadedFile.content().copyTo(targetOgg.outputStream())
            }

            val uploadedCover = ctx.uploadedFile("cover")
            if (uploadedCover != null) {
                val targetPng = File(plugin.dataFolder, "discs/$id.png")
                targetPng.parentFile.mkdirs()
                uploadedCover.content().copyTo(targetPng.outputStream())
            }

            val disc = CustomDisc(
                id = id,
                name = name,
                author = author,
                lore = loreRaw.split("\n"),
                durationSeconds = durationSeconds,
                style = style
            )

            plugin.server.scheduler.runTask(plugin, Runnable {
                plugin.discManager.addDisc(disc)
                
                plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
                    plugin.packGenerator.buildResourcePack(plugin.discManager.getAllDiscs())
                    
                    plugin.server.scheduler.runTask(plugin, Runnable {
                        com.peyaj.jukeboxweb.pack.PackUpdater.updateAllPlayers(plugin)
                        if (plugin.config.getBoolean("auto-reload-geyser", true)) {
                            plugin.server.dispatchCommand(plugin.server.consoleSender, "geyser reload")
                        }
                    })
                })
            })

            ctx.status(201).result("Disc created: $id")
        }
        
        app?.get("/api/discs") { ctx ->
            ctx.json(plugin.discManager.getAllDiscs())
        }
        
        app?.delete("/api/disc/{id}") { ctx ->
             val id = ctx.pathParam("id")
             if (plugin.discManager.deleteDisc(id)) {
                 plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
                     plugin.packGenerator.buildResourcePack(plugin.discManager.getAllDiscs())
                     plugin.server.scheduler.runTask(plugin, Runnable {
                         com.peyaj.jukeboxweb.pack.PackUpdater.updateAllPlayers(plugin)
                         if (plugin.config.getBoolean("auto-reload-geyser", true)) {
                             plugin.server.dispatchCommand(plugin.server.consoleSender, "geyser reload")
                         }
                     })
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
