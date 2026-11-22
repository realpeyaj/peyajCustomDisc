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
            if (path.startsWith("/download") || path.startsWith("/login") || path.startsWith("/unauthorized") || path.startsWith("/listen") || path.startsWith("/fonts") || path.endsWith(".css") || path.endsWith(".js")) {
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

        app?.get("/fonts/{file}") { ctx ->
            val allowed = mapOf(
                "Minecraft-Tenv2.woff2" to "font/woff2",
                "Minecraft-Seven_v2.woff" to "font/woff",
                "NotoSans-Regular.woff2" to "font/woff2",
                "NotoSans-Bold.woff2" to "font/woff2"
            )
            val file = ctx.pathParam("file")
            val mime = allowed[file]
            if (mime != null) {
                val stream = WebServer::class.java.classLoader.getResourceAsStream("secure/fonts/$file")
                if (stream != null) {
                    ctx.contentType(mime)
                    ctx.header("Cross-Origin-Resource-Policy", "cross-origin")
                    ctx.header("Access-Control-Allow-Origin", "*")
                    ctx.header("Cache-Control", "public, max-age=86400")
                    ctx.result(stream)
                    return@get
                }
            }
            ctx.status(404).result("Not Found")
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
                    <style>
                        @font-face {
                            font-family: 'MinecraftTenv2';
                            src: url('/fonts/Minecraft-Tenv2.woff2') format('woff2');
                            font-weight: 400;
                            font-style: normal;
                            font-display: swap;
                        }
                        @font-face {
                            font-family: 'MinecraftSeven-v2';
                            src: url('/fonts/Minecraft-Seven_v2.woff') format('woff');
                            font-weight: 400;
                            font-style: normal;
                            font-display: swap;
                        }
                        @font-face {
                            font-family: 'Noto Sans';
                            src: url('/fonts/NotoSans-Regular.woff2') format('woff2');
                            font-weight: 400;
                            font-style: normal;
                            font-display: swap;
                        }
                        @font-face {
                            font-family: 'Noto Sans';
                            src: url('/fonts/NotoSans-Bold.woff2') format('woff2');
                            font-weight: 700;
                            font-style: normal;
                            font-display: swap;
                        }
                        * {
                            box-sizing: border-box;
                            margin: 0;
                            padding: 0;
                            font-family: 'Noto Sans', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
                            -webkit-font-smoothing: antialiased;
                        }
                        h1 {
                            font-family: 'MinecraftTenv2', 'MinecraftSeven-v2', 'Noto Sans', monospace;
                        }
                        .btn {
                            font-family: 'MinecraftSeven-v2', 'Noto Sans', monospace;
                        }
                        body {
                            min-height: 100vh;
                            background-color: #211811;
                            background-image:
                                linear-gradient(rgba(16, 12, 9, 0.58), rgba(16, 12, 9, 0.68)),
                                url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='16' height='16' shape-rendering='crispEdges'%3E%3Crect width='16' height='16' fill='%233d291b'/%3E%3Crect x='0' y='0' width='2' height='2' fill='%23322115'/%3E%3Crect x='4' y='1' width='2' height='1' fill='%23483121'/%3E%3Crect x='9' y='0' width='3' height='2' fill='%23362417'/%3E%3Crect x='14' y='1' width='2' height='2' fill='%234d3423'/%3E%3Crect x='2' y='3' width='2' height='2' fill='%23493221'/%3E%3Crect x='6' y='4' width='1' height='2' fill='%232e1e13'/%3E%3Crect x='11' y='3' width='2' height='2' fill='%23452f1f'/%3E%3Crect x='0' y='6' width='3' height='2' fill='%23342216'/%3E%3Crect x='5' y='7' width='2' height='1' fill='%23543a27'/%3E%3Crect x='8' y='6' width='2' height='2' fill='%232d1d12'/%3E%3Crect x='13' y='7' width='3' height='2' fill='%23382518'/%3E%3Crect x='3' y='9' width='2' height='2' fill='%232e1e13'/%3E%3Crect x='7' y='10' width='3' height='2' fill='%23483121'/%3E%3Crect x='12' y='10' width='1' height='1' fill='%23525252'/%3E%3Crect x='1' y='12' width='2' height='2' fill='%23473020'/%3E%3Crect x='5' y='13' width='2' height='2' fill='%23312014'/%3E%3Crect x='9' y='13' width='2' height='2' fill='%23503725'/%3E%3Crect x='14' y='12' width='2' height='3' fill='%23322115'/%3E%3Crect x='4' y='5' width='1' height='1' fill='%23505050'/%3E%3C/svg%3E");
                            background-size: 100% 100%, 64px 64px;
                            image-rendering: pixelated;
                            display: flex;
                            justify-content: center;
                            align-items: center;
                            color: #fff;
                            padding: 20px;
                        }
                        .container {
                            background-color: #c6c6c6;
                            border: 2px solid #2b2b2b;
                            width: 100%;
                            max-width: 440px;
                            box-shadow: 0 8px 22px rgba(0, 0, 0, 0.48);
                        }
                        .window-inner {
                            background-color: #c6c6c6;
                            border-top: 3px solid #e2e2e2;
                            border-left: 3px solid #e2e2e2;
                            border-bottom: 3px solid #787878;
                            border-right: 3px solid #787878;
                            padding: 24px;
                            display: flex;
                            flex-direction: column;
                            gap: 14px;
                            text-align: center;
                        }
                        .disc-emblem {
                            margin: 0 auto;
                            width: 44px;
                            height: 44px;
                            background: #8b8b8b;
                            border-top: 2px solid #545454;
                            border-left: 2px solid #545454;
                            border-right: 2px solid #e2e2e2;
                            border-bottom: 2px solid #e2e2e2;
                            display: flex;
                            align-items: center;
                            justify-content: center;
                        }
                        h1 {
                            font-size: 1.7rem;
                            color: #363636;
                            text-shadow: 1px 1px 0px #e6e6e6;
                            text-transform: uppercase;
                            letter-spacing: 0.5px;
                            font-weight: 400;
                        }
                        .subtitle {
                            font-size: 0.92rem;
                            color: #4a4a4a;
                            font-weight: 400;
                        }
                        .error-msg {
                            background: #421b1b;
                            border: 2px solid #d46868;
                            color: #ffb3b3;
                            padding: 8px 12px;
                            font-size: 0.88rem;
                            font-weight: 600;
                        }
                        input[type="password"] {
                            width: 100%;
                            background: #2b2b2b;
                            border-top: 2px solid #1e1e1e;
                            border-left: 2px solid #1e1e1e;
                            border-right: 2px solid #8c8c8c;
                            border-bottom: 2px solid #8c8c8c;
                            color: #f3f3f3;
                            padding: 10px 12px;
                            font-size: 0.95rem;
                            text-align: center;
                        }
                        input[type="password"]:focus {
                            outline: 2px solid #d8c95d;
                            background: #242424;
                        }
                        .btn {
                            background-color: #4b8c38;
                            border: 2px solid #2d2d2d;
                            box-shadow:
                                inset 2px 2px 0 rgba(255, 255, 255, 0.24),
                                inset -2px -3px 0 rgba(0, 0, 0, 0.28);
                            color: #ffffff;
                            font-size: 0.98rem;
                            font-weight: 400;
                            padding: 11px 16px 13px 16px;
                            cursor: pointer;
                            text-transform: uppercase;
                            text-shadow: 1px 1px 0px rgba(0, 0, 0, 0.35);
                            letter-spacing: 0.5px;
                            width: 100%;
                            margin-top: 12px;
                        }
                        .btn:hover {
                            background-color: #559c41;
                        }
                        .btn:active {
                            box-shadow:
                                inset 2px 2px 0 rgba(0, 0, 0, 0.3),
                                inset -2px -2px 0 rgba(255, 255, 255, 0.16);
                            padding-top: 12px;
                            padding-bottom: 12px;
                        }
                        .help {
                            font-size: 0.84rem;
                            color: #444444;
                            font-weight: 400;
                            line-height: 1.45;
                            margin-top: 4px;
                        }
                        code {
                            background: #2b2b2b;
                            padding: 2px 6px;
                            border: 1px solid #555555;
                            color: #efe070;
                            font-size: 0.84rem;
                        }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="window-inner">
                            <div class="disc-emblem">
                                <svg width="26" height="26" viewBox="0 0 16 16" shape-rendering="crispEdges">
                                    <rect x="5" y="2" width="6" height="1" fill="#181818" />
                                    <rect x="3" y="3" width="10" height="1" fill="#252525" />
                                    <rect x="2" y="4" width="12" height="8" fill="#1f1f1f" />
                                    <rect x="3" y="5" width="10" height="6" fill="#323232" />
                                    <rect x="5" y="6" width="6" height="4" fill="#f0c83c" />
                                    <rect x="7" y="7" width="2" height="2" fill="#000000" />
                                    <rect x="3" y="12" width="10" height="1" fill="#181818" />
                                    <rect x="5" y="13" width="6" height="1" fill="#181818" />
                                </svg>
                            </div>
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

        app?.get("/api/disc/{id}/cover") { ctx ->
            val safeId = ctx.pathParam("id").replace(Regex("[^a-zA-Z0-9_-]"), "")
            val coverFile = File(platform.dataFolder, "discs/$safeId.png")
            if (safeId.isNotEmpty() && coverFile.exists() && coverFile.isFile) {
                ctx.contentType("image/png")
                ctx.header("Cache-Control", "no-cache")
                ctx.result(coverFile.inputStream())
            } else {
                ctx.status(404).result("Not Found")
            }
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
