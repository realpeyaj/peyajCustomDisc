package com.peyaj.jukeboxweb.web

import com.peyaj.jukeboxweb.disc.CustomDisc
import com.peyaj.jukeboxweb.disc.DiscManager
import com.peyaj.jukeboxweb.pack.PackGenerator
import com.peyaj.jukeboxweb.platform.JukeboxPlatform
import io.javalin.Javalin
import java.io.File
import java.util.UUID

class WebServer(
    val platform: JukeboxPlatform,
    val discManager: DiscManager,
    val packGenerator: PackGenerator
) {

    private var app: Javalin? = null

    fun start(port: Int) {
        app = Javalin.create { config ->
            config.jetty.modifyServer { server ->
                for (connector in server.connectors) {
                    if (connector is org.eclipse.jetty.server.AbstractConnector) {
                        connector.idleTimeout = 3600000
                    }
                }

                if (platform.haProxySupport) {
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
            val configPass = platform.adminPassword
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
                "invalid_token" -> "<div class='error-msg'>Invalid or Expired Token Link.</div>"
                "invalid_password" -> "<div class='error-msg'>Incorrect Admin Password.</div>"
                else -> ""
            }
            ctx.html("""
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>Disc Creator - Security Login</title>
                    <link href="https://fonts.googleapis.com/css2?family=VT323&display=swap" rel="stylesheet">
                    <style>
                        * {
                            box-sizing: border-box;
                            margin: 0;
                            padding: 0;
                            font-family: 'VT323', monospace;
                            -webkit-font-smoothing: antialiased;
                        }
                        body {
                            background-color: #1a1a1a;
                            background-image:
                                linear-gradient(45deg, #2b2b2b 25%, transparent 25%),
                                linear-gradient(-45deg, #2b2b2b 25%, transparent 25%),
                                linear-gradient(45deg, transparent 75%, #2b2b2b 75%),
                                linear-gradient(-45deg, transparent 75%, #2b2b2b 75%);
                            background-size: 20px 20px;
                            background-position: 0 0, 0 10px, 10px -10px, -10px 0px;
                            min-height: 100vh;
                            display: flex;
                            justify-content: center;
                            align-items: center;
                            color: #fff;
                            padding: 20px;
                        }
                        .container {
                            background-color: #c6c6c6;
                            padding: 4px;
                            width: 100%;
                            max-width: 420px;
                            box-shadow: 4px 4px 0 rgba(0, 0, 0, 0.35);
                        }
                        .window-inner {
                            background-color: #c6c6c6;
                            border: 4px solid #fff;
                            border-bottom-color: #555;
                            border-right-color: #555;
                            padding: 24px;
                            display: flex;
                            flex-direction: column;
                            gap: 16px;
                            text-align: center;
                        }
                        h1 {
                            font-size: 2.8rem;
                            color: #202020;
                            text-shadow: 1px 1px 0px #ffffff;
                            text-transform: uppercase;
                            letter-spacing: 2px;
                            font-weight: bold;
                        }
                        .subtitle {
                            font-size: 1.3rem;
                            color: #333333;
                            font-weight: bold;
                        }
                        .error-msg {
                            background: #2b0000;
                            border: 2px solid #ff5555;
                            color: #ff5555;
                            padding: 8px 12px;
                            font-size: 1.2rem;
                            font-weight: bold;
                            text-shadow: none;
                        }
                        input[type="password"] {
                            width: 100%;
                            background: #000000;
                            border: 2px solid #a0a0a0;
                            border-top-color: #555;
                            border-left-color: #555;
                            color: #ffffff;
                            padding: 10px 14px;
                            font-size: 1.4rem;
                            outline: none;
                            text-align: center;
                        }
                        input[type="password"]:focus {
                            border-color: #ffffff;
                        }
                        .btn {
                            background-color: #5c5c5c;
                            border: 3px solid #ffffff;
                            border-bottom-color: #2a2a2a;
                            border-right-color: #2a2a2a;
                            color: #ffffff !important;
                            font-size: 1.6rem;
                            font-weight: bold;
                            padding: 10px 16px;
                            cursor: pointer;
                            text-transform: uppercase;
                            text-shadow: 1px 1px 0px rgba(0, 0, 0, 0.8);
                            letter-spacing: 1px;
                            width: 100%;
                            margin-top: 10px;
                        }
                        .btn:hover {
                            background-color: #707070;
                        }
                        .btn:active {
                            border: 3px solid #2a2a2a;
                            border-bottom-color: #ffffff;
                            border-right-color: #ffffff;
                        }
                        .help {
                            font-size: 1.15rem;
                            color: #333333;
                            font-weight: bold;
                            line-height: 1.4;
                            margin-top: 4px;
                        }
                        code {
                            background: #000000;
                            padding: 2px 6px;
                            border: 1px solid #555;
                            color: #ffff55;
                            font-size: 1.15rem;
                        }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="window-inner">
                            <h1>Disc Creator</h1>
                            <p class="subtitle">Security Authentication Required</p>
                            $errorMsg
                            <form action="/login" method="POST">
                                <input type="password" name="password" placeholder="Enter Admin Password" required autofocus />
                                <button type="submit" class="btn">Login</button>
                            </form>
                            <div class="help">
                                Or run <code>/disc web</code> in-game to generate a 1-click login link.
                            </div>
                        </div>
                    </div>
                </body>
                </html>
            """.trimIndent())
        }

        app?.get("/") { ctx ->
            val stream = WebServer::class.java.classLoader.getResourceAsStream("secure/index.html")
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
            
            val targetOgg = File(platform.dataFolder, "discs/$id.ogg")
            val targetStereoOgg = File(platform.dataFolder, "discs/${id}_stereo.ogg")
            targetOgg.parentFile.mkdirs()
            
            var durationSeconds = 0
            
            if (!isOgg) {
                if (!com.peyaj.jukeboxweb.util.AudioConverter.isFFmpegAvailable()) {
                     ctx.status(501).result("Server-side conversion unreachable. FFmpeg not installed on server/host OS.")
                     return@post
                }
                
                val ext = filename.substringAfterLast('.', "tmp")
                val tempFile = File(platform.dataFolder, "temp/$id.$ext")
                tempFile.parentFile.mkdirs()
                uploadedFile.content().copyTo(tempFile.outputStream())
                
                // Convert mono for jukeboxes (3D spatial block audio)
                val result = com.peyaj.jukeboxweb.util.AudioConverter.convertToOgg(tempFile, targetOgg, channels = 1)
                // Convert stereo for regions (2-channel background audio)
                com.peyaj.jukeboxweb.util.AudioConverter.convertToOggStereo(tempFile, targetStereoOgg)
                tempFile.delete()
                
                if (!result.first) {
                    ctx.status(500).result("Conversion Failed. Check server console.")
                    return@post
                }
                durationSeconds = result.second
            } else {
                // Direct OGG upload: copy as stereo track, then downmix to mono for jukeboxes
                uploadedFile.content().copyTo(targetStereoOgg.outputStream())
                if (com.peyaj.jukeboxweb.util.AudioConverter.isFFmpegAvailable()) {
                    com.peyaj.jukeboxweb.util.AudioConverter.convertToOgg(targetStereoOgg, targetOgg, channels = 1)
                } else {
                    targetStereoOgg.copyTo(targetOgg, overwrite = true)
                }
                durationSeconds = com.peyaj.jukeboxweb.util.AudioConverter.getAudioDuration(targetOgg)
            }

            val uploadedCover = ctx.uploadedFile("cover")
            if (uploadedCover != null) {
                val targetPng = File(platform.dataFolder, "discs/$id.png")
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

            platform.runSync {
                discManager.addDisc(disc)
                
                platform.runAsync {
                    packGenerator.buildResourcePack(discManager.getAllDiscs())
                    
                    platform.runSync {
                        platform.onPackUpdated()
                        if (platform.autoReloadGeyser) {
                            platform.dispatchCommand("geyser reload")
                        }
                    }
                }
            }

            ctx.status(201).result("Disc created: $id")
        }
        
        app?.get("/api/discs") { ctx ->
            ctx.json(discManager.getAllDiscs())
        }
        
        app?.delete("/api/disc/{id}") { ctx ->
             val id = ctx.pathParam("id")
             if (discManager.deleteDisc(id)) {
                 platform.runAsync {
                     packGenerator.buildResourcePack(discManager.getAllDiscs())
                     platform.runSync {
                         platform.onPackUpdated()
                         if (platform.autoReloadGeyser) {
                             platform.dispatchCommand("geyser reload")
                         }
                     }
                 }
                 ctx.status(200).result("Deleted")
             } else {
                 ctx.status(404).result("Not Found")
             }
        }
        
        app?.get("/download/pack") { ctx ->
            var packFile = packGenerator.getPackFile()
            if (!packFile.exists()) {
                try {
                    packGenerator.buildResourcePack(discManager.getAllDiscs())
                    packFile = packGenerator.getPackFile()
                } catch (e: Exception) {
                    platform.logWarning("Failed to auto-generate missing pack: ${e.message}")
                }
            }
            if (packFile.exists()) {
                ctx.contentType("application/zip")
                ctx.header("Content-Length", packFile.length().toString())
                ctx.header("Content-Disposition", "attachment; filename=\"peyajCD-Java.zip\"")
                ctx.header("Accept-Ranges", "bytes")
                ctx.header("Cache-Control", "no-cache, no-store, must-revalidate")
                ctx.result(packFile.inputStream())
            } else {
                ctx.status(404).result("Pack not generated yet")
            }
        }
        
        app?.get("/download/geyser") { ctx ->
            val packFile = packGenerator.getBedrockPackFile()
            if (packFile.exists()) {
                ctx.contentType("application/zip") 
                ctx.header("Content-Length", packFile.length().toString())
                ctx.header("Content-Disposition", "attachment; filename=\"peyajCD-Bedrock.mcpack\"")
                ctx.header("Accept-Ranges", "bytes")
                ctx.header("Cache-Control", "no-cache, no-store, must-revalidate")
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
