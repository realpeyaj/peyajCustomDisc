package com.peyaj.jukeboxweb.auth

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object AuthManager {
    
    // Map of specific One-Time-Tokens to Player UUIDs
    // Token -> PlayerUUID
    private val loginTokens = ConcurrentHashMap<String, UUID>()
    
    // Map of Session Cookies to Player UUIDs (LoggedIn users)
    // Cookie -> PlayerUUID
    private val sessions = ConcurrentHashMap<String, UUID>()

    fun createLoginToken(playerUuid: UUID): String {
        val token = UUID.randomUUID().toString()
        loginTokens[token] = playerUuid
        
        // Expire token after 5 minutes? (For now, keep it simple manual cleanup or no cleanup until restart/usage)
        // Ideally we'd have a cleanup task.
        return token
    }

    fun redeemToken(token: String): String? {
        // Returns a NEW Session Cookie if valid, null otherwise
        if (loginTokens.containsKey(token)) {
            val uuid = loginTokens.remove(token)!! // Consume token (One-time use)
            val sessionCookie = UUID.randomUUID().toString()
            sessions[sessionCookie] = uuid
            return sessionCookie
        }
        return null
    }

    fun isValidSession(sessionCookie: String?): Boolean {
        if (sessionCookie == null) return false
        return sessions.containsKey(sessionCookie)
    }
    
    fun invalidateSession(sessionCookie: String) {
        sessions.remove(sessionCookie)
    }
}
