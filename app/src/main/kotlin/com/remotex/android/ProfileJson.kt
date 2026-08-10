package com.remotex.android

import com.remotex.core.database.ProfileRepository
import com.remotex.core.model.AuthenticationMode
import com.remotex.core.model.ConnectionProfile
import com.remotex.core.model.CredentialPolicy
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

object ProfileJson {
    suspend fun export(repository: ProfileRepository): String {
        val array = JSONArray()
        repository.observeAll().first().forEach { profile ->
            array.put(JSONObject().apply {
                put("name", profile.name)
                put("host", profile.host)
                put("username", profile.username)
                put("notes", profile.notes)
                put("favorite", profile.favorite)
                put("vncEnabled", profile.vncEnabled)
                put("vncPort", profile.vncPort)
                put("sshEnabled", profile.sshEnabled)
                put("sshPort", profile.sshPort)
                put("authenticationMode", profile.authenticationMode.name)
                put("credentialPolicy", profile.credentialPolicy.name)
            })
        }
        return JSONObject().apply {
            put("format", "RemoteXProfiles")
            put("version", 1)
            put("profiles", array)
        }.toString(2)
    }

    suspend fun import(repository: ProfileRepository, json: String): Int {
        val root = JSONObject(json)
        require(root.optString("format") == "RemoteXProfiles") { "Format profil tidak dikenal" }
        require(root.optInt("version") == 1) { "Versi backup belum didukung" }
        val profiles = root.getJSONArray("profiles")
        var imported = 0
        for (i in 0 until profiles.length()) {
            val row = profiles.getJSONObject(i)
            val profile = ConnectionProfile.new(
                name = row.getString("name"),
                host = row.getString("host"),
                username = row.optString("username"),
                notes = row.optString("notes"),
                favorite = row.optBoolean("favorite", false),
                vncEnabled = row.optBoolean("vncEnabled", true),
                vncPort = row.optInt("vncPort", 5900),
                sshEnabled = row.optBoolean("sshEnabled", true),
                sshPort = row.optInt("sshPort", 22),
                authenticationMode = runCatching { AuthenticationMode.valueOf(row.optString("authenticationMode")) }
                    .getOrDefault(AuthenticationMode.PASSWORD),
                credentialPolicy = runCatching { CredentialPolicy.valueOf(row.optString("credentialPolicy")) }
                    .getOrDefault(CredentialPolicy.ALWAYS_ASK),
            )
            repository.save(profile)
            imported++
        }
        return imported
    }
}
