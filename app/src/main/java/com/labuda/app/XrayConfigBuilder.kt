package com.labuda.app

import android.net.Uri

object XrayConfigBuilder {
    fun build(profile: VlessProfile): String {
        val uri = Uri.parse(profile.raw)
        val q = buildMap {
            uri.queryParameterNames.forEach { put(it, uri.getQueryParameter(it).orEmpty()) }
        }
        val security = q["security"].orEmpty().ifBlank { "none" }
        val network = q["type"].orEmpty().ifBlank { "tcp" }
        val flow = q["flow"].orEmpty()
        val sni = q["sni"].orEmpty().ifBlank { q["host"].orEmpty() }
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
            if (security == "reality") {
                append(",\"realitySettings\":{\"serverName\":\"").append(escape(sni)).append("\",\"fingerprint\":\"").append(escape(fingerprint)).append("\",\"publicKey\":\"").append(escape(publicKey)).append("\",\"shortId\":\"").append(escape(shortId)).append("\",\"spiderX\":\"").append(escape(spiderX)).append("\"}")
            }
            if (network == "ws") {
                append(",\"wsSettings\":{\"path\":\"").append(escape(q["path"].orEmpty().ifBlank { "/" })).append("\",\"headers\":{\"Host\":\"").append(escape(q["host"].orEmpty().ifBlank { sni })).append("\"}}")
            }
            if (network == "grpc") {
                append(",\"grpcSettings\":{\"serviceName\":\"").append(escape(q["serviceName"].orEmpty())).append("\"}")
            }
            if (network == "tcp" && q["headerType"].orEmpty().isNotBlank()) {
                append(",\"tcpSettings\":{\"header\":{\"type\":\"").append(escape(q["headerType"].orEmpty())).append("\"}}")
            }
            if (alpn.isNotEmpty()) append(",\"tlsSettings\":{\"serverName\":\"").append(escape(sni)).append("\",\"alpn\":[").append(alpn.joinToString(",") { "\"${escape(it)}\"" }).append("]}")
            append("}")
        }

        return """
        {
          "log":{"loglevel":"warning"},
          "inbounds":[{
            "tag":"tun",
            "port":0,
            "protocol":"tun",
            "settings":{"name":"labuda0","MTU":1500,"userLevel":8},
            "sniffing":{"enabled":true,"destOverride":["http","tls"]}
          }],
          "outbounds":[{
            "tag":"proxy",
            "protocol":"vless",
            "settings":{"vnext":[{"address":"${escape(profile.host)}","port":${profile.port},"users":[$user}]},
            "streamSettings":$stream
          },{"tag":"direct","protocol":"freedom"},{"tag":"block","protocol":"blackhole"}],
          "routing":{"domainStrategy":"AsIs","rules":[{"type":"field","inboundTag":["tun"],"outboundTag":"proxy"}]}
        }
        """.trimIndent()
    }

    private fun escape(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
}
