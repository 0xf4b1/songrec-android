package io.github.tiefensuche

import java.io.BufferedReader
import java.io.IOException
import java.net.URL
import java.util.UUID
import javax.net.ssl.HttpsURLConnection

class SongRec {
    external fun makeSignatureFromFile(input: String): String

    init {
        System.loadLibrary("songrec")
    }

    fun recognizeSongFromSignature(data: String): String {
        val timestamp = System.currentTimeMillis() / 1000
        val uuid_1 = UUID.randomUUID().toString().uppercase()
        val uuid_2 = UUID.randomUUID().toString()

        val samplems = data.substringBefore(',')
        val signature = data.substringAfter(',')

        val post_data = "{\"geolocation\":{\"altitude\":300,\"latitude\":45,\"longitude\":2},\"signature\":{\"samplems\":$samplems,\"timestamp\":$timestamp,\"uri\":\"$signature\"},\"timestamp\":$timestamp,\"timezone\":\"Europe/Paris\"}"

        val url = "https://amp.shazam.com/discovery/v5/en/US/android/-/tag/${uuid_1}/${uuid_2}"
        val response = WebRequests.post(url, post_data, mapOf("Content-Type" to "application/json"))
        if (response.status != 200)
            throw IllegalArgumentException("Received unexpected status ${response.status}")
        return response.value
    }

    object WebRequests {

        class Response(val status: Int, val value: String, val headers: Map<String, List<String>>?)

        fun get(url: String, headers: Map<String, String>? = null): Response {
            return request(createConnection(url, "GET", headers))
        }

        fun post(url: String, data: String, headers: Map<String, String>? = null): Response {
            val con = createConnection(url, "POST", headers)
            con.doOutput = true
            con.outputStream.write(data.toByteArray())
            return request(con)
        }

        fun createConnection(
            url: String,
            method: String = "GET",
            headers: Map<String, String>? = null
        ): HttpsURLConnection {
            val con = URL(url).openConnection() as? HttpsURLConnection ?: throw IOException()
            con.requestMethod = method
            headers?.forEach { (k, v) -> con.setRequestProperty(k, v) }
            return con
        }

        @Throws(HttpException::class)
        fun request(con: HttpsURLConnection): Response {
            if (con.responseCode < 400) {
                return Response(con.responseCode, con.inputStream.bufferedReader().use(BufferedReader::readText), con.headerFields)
            } else {
                throw HttpException(con.responseCode, con.errorStream.bufferedReader().use(BufferedReader::readText))
            }
        }

        class HttpException(val code: Int, message: String?) : IOException(message)
    }
}