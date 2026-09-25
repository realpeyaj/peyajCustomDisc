package com.peyaj.jukeboxweb.auth

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object AuthManager {
    private val loginTokens = ConcurrentHashMap<String, UUID>()
    private val sessions = ConcurrentHashMap<String, UUID>()

    fun createLoginToken(playerUuid: UUID): String {
        val token = UUID.randomUUID().toString()
        loginTokens[token] = playerUuid
        return token
    }

    fun redeemToken(token: String): String? {
        if (loginTokens.containsKey(token)) {
            val uuid = loginTokens.remove(token)!!
            val sessionCookie = UUID.randomUUID().toString()
            sessions[sessionCookie] = uuid
            return sessionCookie
        }
        return null
    }

    fun authenticatePassword(password: String, expectedPassword: String): String? {
        if (expectedPassword.isNotBlank() && expectedPassword != "changeme" && password == expectedPassword) {
            val sessionCookie = UUID.randomUUID().toString()
            sessions[sessionCookie] = UUID.randomUUID()
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
