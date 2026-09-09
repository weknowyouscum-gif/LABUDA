package com.labuda.app

import android.net.Uri

object XrayConfigBuilder {
    fun build(profile: VlessProfile, bypassPrivate: Boolean = true): String {
        val uri = Uri.parse(profile.raw)
        val q = buildMap {
            uri.queryParameterNames.forEach { put(it, uri.getQueryParameter(it).orEmpty()) }
        }
        val security = q["security"].orEmpty().ifBlank { "none" }.lowercase()
        val network = q["type"].orEmpty().ifBlank { q["network"].orEmpty().ifBlank { "tcp" } }.lowercase()
        val flow = q["flow"].orEmpty()
        val sni = q["sni"].orEmpty().ifBlank { q["host"].orEmpty() }.ifBlank { profile.host }
        val fingerprint = q["fp"].orEmpty().ifBlank { "chrome" }
        val alpn = q["alpn"].orEmpty().split(',').map { it.trim() }.filter { it.isNotBlank() }
        val publicKey = q["pbk"].orEmpty()
        val shortId = q["sid"].orEmpty()
        val spiderX = q["spx"].orEmpty().ifBlank { "/" }

        val user = buildString {
            append("{\"id\":\"").append(escape(profile.uuid)).append("\",\"encryption\":\"none\"")
            if (flow.isNotBlank()) append(",\"flow\":\"").append(escape(flow)).append("\"")
            append("}")
        }

        val stream = buildString {
            append("{\"network\":\"").append(escape(network)).append("\",\"security\":\"").append(escape(security)).append("\"")

            when (security) {
                "reality" -> {
                    append(",\"realitySettings\":{\"serverName\":\"").append(escape(sni))
                        .append("\",\"fingerprint\":\"").append(escape(fingerprint))
                        .append("\",\"publicKey\":\"").append(escape(publicKey))
                        .append("\",\"shortId\":\"").append(escape(shortId))
                        .append("\",\"spiderX\":\"").append(escape(spiderX)).append("\"}")
                }
                "tls" -> {
                    append(",\"tlsSettings\":{\"serverName\":\"").append(escape(sni)).append("\",\"fingerprint\":\"").append(escape(fingerprint)).append("\"")
                    if (alpn.isNotEmpty()) append(",\"alpn\":[").append(alpn.joinToString(",") { "\"${escape(it)}\"" }).append("]")
                    append("}")
                }
            }

            if (network == "ws") {
                append(",\"wsSettings\":{\"path\":\"").append(escape(q["path"].orEmpty().ifBlank { "/" }))
                    .append("\",\"headers\":{\"Host\":\"").append(escape(q["host"].orEmpty().ifBlank { sni })).append("\"}}")
            }
            if (network == "grpc") {
                append(",\"grpcSettings\":{\"serviceName\":\"").append(escape(q["serviceName"].orEmpty())).append("\"}")
            }
            if (network == "tcp" && q["headerType"].orEmpty().isNotBlank()) {
                append(",\"tcpSettings\":{\"header\":{\"type\":\"").append(escape(q["headerType"].orEmpty())).append("\"}}")
            }
            append("}")
        }

        val routingRules = if (bypassPrivate) {
            """
            [
              {"type":"field","ip":["geoip:private"],"outboundTag":"direct"},
              {"type":"field","inboundTag":["tun"],"outboundTag":"proxy"}
            ]
            """.trimIndent()
        } else {
            "[{\"type\":\"field\",\"inboundTag\":[\"tun\"],\"outboundTag\":\"proxy\"}]"
        }

        // Android supplies the TUN file descriptor through XRAY_TUN_FD. Keep the
        // Xray-side TUN definition deliberately minimal for compatibility.
        return """
        {
          "log":{"loglevel":"warning"},
          "inbounds":[{
            "tag":"tun",
            "port":0,
            "protocol":"tun",
            "settings":{"name":"labuda0","mtu":1500}
          }],
          "outbounds":[{
            "tag":"proxy",
            "protocol":"vless",
            "settings":{"vnext":[{"address":"${escape(profile.host)}","port":${profile.port},"users":[$user]}]},
            "streamSettings":$stream
          },{"tag":"direct","protocol":"freedom"},{"tag":"block","protocol":"blackhole"}],
          "routing":{"domainStrategy":"IPIfNonMatch","rules":$routingRules}
        }
        """.trimIndent()
    }

    private fun escape(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
}
