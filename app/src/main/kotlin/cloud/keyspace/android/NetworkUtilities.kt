package com.keyspace.keyspacemobile


import android.content.Context
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import cloud.keyspace.android.CryptoUtilities
import cloud.keyspace.android.IOUtilities
import com.android.volley.*
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.*
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeoutException


/**
 * Networking utilities such as API calls, web requests and so on can be found here. We're enslaved by the backend team for this one.
 * @param context The context of the activity that a `NetworkUtilities` object is initialized in, example: `applicationContext`, `this` etc.
 */


class NetworkUtilities (
    private var applicationContext: Context,  // The context to derive information from.
    private var appCompatActivity: AppCompatActivity, // The activity to display the permissions prompt inside of.
    private var keyring: CryptoUtilities.Keyring,
) {

    fun ByteArray.toHexString(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }

    var api_endpoint: String = "https://api.keyspace.cloud"
    var keyauth_endpoint: String = "$api_endpoint/keyauth"
    var login_endpoint: String = "$api_endpoint/login"
    var signup_endpoint: String = "$api_endpoint/signup"
    var keyroute_endpoint: String = "wss://${api_endpoint.replace("https://", "")}/ws/keyroute"

    var vault_items_endpoint: String = "$api_endpoint/vaults/items"

    private var context: Context = applicationContext

    val mapper = jacksonObjectMapper()
    val crypto = CryptoUtilities(context, appCompatActivity)

    data class KeyspaceStatus (
        val status: String,
        val apiVersion: String,
    )

    private val queueFileExtension = "queue"
    private val saveFilename = "save"
    private val editFilename = "edit"
    private val deleteFilename = "delete"

    private var saveQueueFilename: String? = "$saveFilename.$queueFileExtension"
    private var editQueueFilename: String? = "$editFilename.$queueFileExtension"
    private var deleteQueueFilename: String? = "$deleteFilename.$queueFileExtension"

    fun writeQueueTask (dataObject: Any, mode: String): Boolean {

        val saveQueue = File(applicationContext.cacheDir, saveQueueFilename!!)
        val editQueue = File(applicationContext.cacheDir, editQueueFilename!!)
        val deleteQueue = File(applicationContext.cacheDir, deleteQueueFilename!!)

        val saveQueueStream = FileOutputStream (saveQueue, true)
        val editQueueStream = FileOutputStream (editQueue, true)
        val deleteQueueStream = FileOutputStream (deleteQueue, true)

        try {
            if (mode == MODE_DELETE) {
                val deleteId = dataObject.toString()
                val dataAsString = mapper.writer().writeValueAsString(
                    BackendDataObjectId (
                        id = deleteId
                    )
                ).toString()
                val jsonifiedMap = mapper.writer().writeValueAsString(mapper.readValue(dataAsString, java.util.HashMap::class.java) as Map<*, *>).toString()

                val writer = OutputStreamWriter(deleteQueueStream)
                writer.appendLine(jsonifiedMap)
                writer.close()
                deleteQueueStream.close()

            } else if (mode == MODE_PUT) {
                val dataAsString = mapper.writer().writeValueAsString(
                    BackendDataObject (
                        data = dataObject
                    )
                ).toString()
                val jsonifiedMap = mapper.writer().writeValueAsString(mapper.readValue(dataAsString, java.util.HashMap::class.java) as Map<*, *>).toString()

                val writer = OutputStreamWriter(editQueueStream)
                writer.appendLine(jsonifiedMap)
                writer.close()
                editQueueStream.close()

            } else if (mode == MODE_POST) {
                val dataAsString = mapper.writer().writeValueAsString(
                    BackendDataObject (
                        data = dataObject
                    )
                ).toString()
                val jsonifiedMap = mapper.writer().writeValueAsString(mapper.readValue(dataAsString, java.util.HashMap::class.java) as Map<*, *>).toString()

                val writer = OutputStreamWriter(saveQueueStream)
                writer.appendLine(jsonifiedMap)
                writer.close()
                saveQueueStream.close()

            }

        } catch (e: FileNotFoundException) {
            e.printStackTrace()
            return false

        } catch (e: IOException) {
            e.printStackTrace()
            return false

        }

        return true
    }

    fun getDeleteTaskCount (): Int {
        val deleteQueueFile = File(applicationContext.cacheDir, deleteQueueFilename!!)
        var count = 0
        try {
            deleteQueueFile.forEachLine { count += 1 }
        } catch (_: FileNotFoundException) { }
        return count
    }

    fun getSaveTaskCount (): Int {
        val saveQueueFile = File(applicationContext.cacheDir, saveQueueFilename!!)
        var count = 0
        try {
            saveQueueFile.forEachLine { count += 1 }
        } catch (_: FileNotFoundException) { }
        return count
    }

    fun getEditTaskCount (): Int {
        val editQueueFile = File(applicationContext.cacheDir, editQueueFilename!!)
        var count = 0
        try {
            editQueueFile.forEachLine { count += 1 }
        } catch (_: FileNotFoundException) { }
        return count
    }

    fun completeQueueTasks (signedToken: String): Boolean {
        var deleteQueueFile = File(applicationContext.cacheDir, deleteQueueFilename!!)
        val deleteQueueStream = FileOutputStream (deleteQueueFile, true)
        val deleteQueue: MutableList<String> = mutableListOf()
        deleteQueueFile.forEachLine { deleteQueue.add(it) }
        for (item in deleteQueue) {
            Log.d("Keyspace", "Delete queue: Clearing item ${deleteQueue.indexOf(item)+1} of ${deleteQueue.size}")
            CoroutineScope(Dispatchers.IO).launch {
                val response = sendDataToBackend (signedToken, item, mode = MODE_DELETE)
                withContext (Dispatchers.IO) {
                    if (response?.get("status").toString() == RESPONSE_SUCCESS) {
                        deleteQueue.remove(item)
                        if (deleteQueue.isEmpty()) {
                            deleteQueueFile.writer().write(deleteQueue.joinToString("\n")).apply { deleteQueueStream.close() }
                            deleteQueueFile = File(applicationContext.cacheDir, deleteQueueFilename!!)
                            deleteQueueFile.delete()
                            Log.d("Keyspace", "Delete queue: all tasks completed!")
                        }
                    }
                }
            }
        }

        var editQueueFile = File(applicationContext.cacheDir, editQueueFilename!!)
        val editQueueStream = FileOutputStream (editQueueFile, true)
        val editQueue: MutableList<String> = mutableListOf()
        editQueueFile.forEachLine { editQueue.add(it) }
        for (item in editQueue) {
            Log.d("Keyspace", "Edit queue: Clearing item ${editQueue.indexOf(item)+1} of ${editQueue.size}")
            CoroutineScope(Dispatchers.IO).launch {
                val response = sendDataToBackend (signedToken, item, mode = MODE_PUT)
                withContext (Dispatchers.IO) {
                    if (response?.get("status").toString() == RESPONSE_SUCCESS) {
                        editQueue.remove(item)
                        if (editQueue.isEmpty()) {
                            editQueueFile.writer().write(editQueue.joinToString("\n")).apply { editQueueStream.close() }
                            editQueueFile = File(applicationContext.cacheDir, editQueueFilename!!)
                            editQueueFile.delete()
                            Log.d("Keyspace", "Edit queue: all tasks completed!")
                        }
                    }
                }
            }
        }

        var saveQueueFile = File(applicationContext.cacheDir, saveQueueFilename!!)
        val saveQueueStream = FileOutputStream (saveQueueFile, true)
        val saveQueue: MutableList<String> = mutableListOf()
        saveQueueFile.forEachLine { saveQueue.add(it) }
        for (item in saveQueue) {
            Log.d("Keyspace", "Save queue: Clearing item ${saveQueue.indexOf(item)+1} of ${saveQueue.size}")
            CoroutineScope(Dispatchers.IO).launch {
                val response = sendDataToBackend (signedToken, item, mode = MODE_POST)
                withContext (Dispatchers.IO) {
                    if (response?.get("status").toString() == RESPONSE_SUCCESS) {
                    saveQueue.remove(item)
                        if (saveQueue.isEmpty()) {
                            saveQueueFile.writer().write(saveQueue.joinToString("\n")).apply { saveQueueStream.close() }
                            saveQueueFile = File(applicationContext.cacheDir, saveQueueFilename!!)
                            saveQueueFile.delete()
                            Log.d("Keyspace", "Save queue: all tasks completed!")
                        }
                    }
                }
            }
        }

        return true

    }

    suspend fun generateSignedToken(): String {
        val mapper = jacksonObjectMapper()
        val message = mapper.writer().withDefaultPrettyPrinter().writeValueAsString(sendKeyauthRequest())
        return crypto.sign(message, keyring.ED25519_PRIVATE_KEY!!)
    }

    suspend fun keyspaceStatus(): NetworkUtilities.KeyspaceStatus = suspendCancellableCoroutine { continuation ->
        lateinit var status: String
        lateinit var apiVersion: String
        val mainScope = CoroutineScope(Dispatchers.Main)
        val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
            Log.e("Keyspace", "Keyspace: Couldn't access this resource. Is it online and is your device connected to the internet?" + "\nError: ${throwable.stackTrace}")
        }

        mainScope.launch(exceptionHandler) {  // used to run synchronous Kotlin functions like `suspend fun foo()`
            try {
                val response = synchronousGetRequest(api_endpoint)
                status = response?.get("status").toString()
                apiVersion = response?.get("api-version").toString()

                continuation.resumeWith (
                    Result.success (
                        NetworkUtilities.KeyspaceStatus(
                            status, apiVersion
                        )
                    )
                )

            } catch (ex: NetworkError) {
                ex.printStackTrace()
                continuation.resumeWith (
                    Result.success (
                        KeyspaceStatus(
                            "no_network", "0.0"
                        )
                    )
                )
            }
        }

    }

    /**
     * Make a synchronous GET request using Volley and Kotlin coroutines. The response from this function can be used on a UI thread.
     *
     * @param url A URL as a String.
     * @return A JSONObject response.
     * @see <a href="https://stackoverflow.com/questions/16904741/can-i-do-a-synchronous-request-with-volley">StackOverflow</a>
     */
    suspend fun synchronousGetRequest (url: String): JSONObject? {
        return suspendCancellableCoroutine { continuation ->
            val queue = Volley.newRequestQueue(context)
            val jsonObjectRequest = JsonObjectRequest (
                Request.Method.GET, url, null,
                { response ->
                    try {
                        continuation.resumeWith(Result.success(response))
                    } catch (e: JSONException) {
                        Log.e("Keyspace", "Invalid JSON Object received from backend.")
                        e.printStackTrace()
                    }
                }) { error ->
                error.printStackTrace()
                continuation.cancel (NetworkError())
                Log.e("Keyspace", "Keyspace: Couldn't access this resource. Is it online and is your device connected to the internet?")
            }

            queue.add(jsonObjectRequest)
        }
    }

    class IncorrectCredentialsException : Exception()

    /**
     * Make a synchronous GET request with an authorization header using Volley and Kotlin coroutines. The response from this function can be used on a UI thread.
     *
     * @param url A URL as a String.
     * @return A JSONObject response.
     * @see <a href="https://stackoverflow.com/questions/16904741/can-i-do-a-synchronous-request-with-volley">StackOverflow</a>
     */
    suspend fun synchronousGetRequestWithAuthorizationHeader (url: String, signedToken: String): JSONObject? {
        return suspendCancellableCoroutine { continuation ->
            val queue = Volley.newRequestQueue(context)

            val jsonObjectRequest: JsonObjectRequest = object : JsonObjectRequest (
                Method.GET, url, JSONObject(),
                { response ->
                    try {
                        continuation.resumeWith(Result.success(response))
                    } catch (e: JSONException) {
                        Log.e("Keyspace", "Invalid JSON Object received from backend.")
                        e.printStackTrace()
                    }
                },  { error ->
                    error.printStackTrace()
                    try {
                        when (error.networkResponse.statusCode) {
                            500 -> continuation.cancel (IncorrectCredentialsException())
                            else -> continuation.cancel (NetworkError())
                        }
                    } catch (_: NullPointerException) {
                        continuation.cancel (NetworkError())
                    }
                    Log.e("Keyspace", "Keyspace: Couldn't access this resource. Is it online and is your device connected to the internet?")
                }) {
                @Throws(AuthFailureError::class)
                override fun getHeaders(): Map<String, String> {
                    val params: MutableMap<String, String> = HashMap()
                    // Headers are case insensitive
                    params["public-key"] = keyring.ED25519_PUBLIC_KEY!!.toHexString()
                    params["signed-token"] = signedToken
                    return params
                }
            }

            queue.add(jsonObjectRequest)

        }
    }

    /**
     * Make a synchronous JSON POST request using Volley and Kotlin coroutines. The response from this function can be used on a UI thread.
     *
     * @param url A URL as a String.
     * @param parameters A Map of strings containing parameters.
     * @return A JSONObject response.
     * @see <a href="https://stackoverflow.com/questions/16904741/can-i-do-a-synchronous-request-with-volley">StackOverflow</a>
     */
    suspend fun synchronousJsonPostRequest (url: String, parameters: Map<String, String?>): JSONObject? {
        return suspendCancellableCoroutine { continuation ->
            val queue = Volley.newRequestQueue(context)
            val jsonObjectRequest = JsonObjectRequest (
                Request.Method.POST, url, JSONObject(parameters),
                { response ->
                    try {
                        continuation.resumeWith(Result.success(response))
                    } catch (e: JSONException) {
                        Log.e("Keyspace", "Invalid JSON Object received from backend.")
                        e.printStackTrace()
                    }
                }) { error -> error.printStackTrace()
                    continuation.cancel (NetworkError())
                    Log.e("Keyspace", "Keyspace: Couldn't access this resource. Is it online and is your device connected to the internet?")
            }

            queue.add(jsonObjectRequest)
        }
    }

    /**
     * Prototype method for copy-pasting
     * TODO: Not use this method via invocation
     */
    private fun asynchronousJsonPostRequest(username: String, password: String) {
        val myRequestQueue = Volley.newRequestQueue(context)
        val url = signup_endpoint // <----enter your post url here
        val myStringRequest: StringRequest = object : StringRequest(
            Method.POST, url, Response.Listener {
                //This code is executed if the server responds, whether or not the response contains data.
                //The String 'response' contains the server's response.
            }, Response.ErrorListener
            //Create an error listener to handle errors appropriately.
            {
                //This code is executed if there is an error.
            }) {
            override fun getParams(): Map<String, String> {
                val data: MutableMap<String, String> = HashMap()
                data["username"] = username
                data["password"] = password
                return data
            }
        }
        myRequestQueue.add(myStringRequest)
    }


    /**
     * Prototype method for copy-pasting
     * TODO: Not use this method via invocation
     */
    private fun asynchronousGetRequest (url: String): JSONObject? {
        var serverResponse: JSONObject? = null
        try {
            val requestQueue = Volley.newRequestQueue(context)
            val jsonObjectRequest = JsonObjectRequest(
                Request.Method.GET, url, null,
                { response ->
                    try {
                        Log.d("Keyspace", "Received data from backend: $response")
                        serverResponse = response
                    } catch (e: JSONException) {
                        e.printStackTrace()
                    }
                }) { error -> error.printStackTrace() }

            requestQueue.add(jsonObjectRequest)

        } catch (noConnection: NoConnectionError) {
            Log.e("Keyspace", "Couldn't connect to $url. Is your device connected to the internet?")
        } catch (e: InterruptedException) {
            Log.e("Keyspace", "The synchronous GET request thread was interrupted.")
        } catch (e: ExecutionException) {
            Log.e("Keyspace", "The synchronous GET request thread threw an exception.")
        } catch (e: TimeoutException) {
            Log.e("Keyspace", "The synchronous GET request timed out.")
        }
        return serverResponse
    }

    val RESPONSE_VAULT_CORRUPT = "RESPONSE_VAULT_CORRUPT"
    val NETWORK_ERROR = "NETWORK_ERROR"
    val RESPONSE_VAULT_DOES_NOT_EXIST = "RESPONSE_VAULT_DOES_NOT_EXIST"
    val RESPONSE_SUCCESS = "success"

    suspend fun grabLatestVaultFromBackend (signedToken: String): IOUtilities.Vault {

        val io = IOUtilities(applicationContext, appCompatActivity, keyring)
        val freshVault = io.getVault()

        var response: String? = null
        var vault: IOUtilities.Vault? = null

        val vaultData = synchronousGetRequestWithAuthorizationHeader (
            vault_items_endpoint,
            signedToken = signedToken
        )

        val TYPE_LOGIN = "login"
        val TYPE_NOTE = "note"
        val TYPE_CARD = "card"
        val TYPE_TAG = "tag"

        var vaultItemList = mutableListOf<String>()
        if (vaultData.toString().contains("corrupt")) throw InvalidObjectException(RESPONSE_VAULT_CORRUPT)
        val data = JSONObject(vaultData?.get("data").toString())

        var loginsJSONArray = JSONArray()
        var notesJSONArray = JSONArray()
        var cardsJSONArray = JSONArray()
        var tagsJSONArray = JSONArray()

        try { loginsJSONArray = data.getJSONArray(TYPE_LOGIN) } catch (_: JSONException) {}
        try { notesJSONArray = data.getJSONArray(TYPE_NOTE) } catch (_: JSONException) {}
        try { cardsJSONArray = data.getJSONArray(TYPE_CARD) } catch (_: JSONException) {}
        try { tagsJSONArray = data.getJSONArray(TYPE_TAG) } catch (_: JSONException) {}

        val logins: MutableList<IOUtilities.Login> = mutableListOf()
        val notes: MutableList<IOUtilities.Note> = mutableListOf()
        val cards: MutableList<IOUtilities.Card> = mutableListOf()
        val tags: MutableList<IOUtilities.Tag> = mutableListOf()

        for (index in 0 until loginsJSONArray.length()) {
            val loginAsJSONObject = JSONObject(loginsJSONArray[index].toString())["data"].toString()
            val login = mapper.readValue (loginAsJSONObject, IOUtilities.Login::class.java)
            logins.add(login)
        }

        for (index in 0 until notesJSONArray.length()) {
            val noteAsJSONObject = JSONObject(notesJSONArray[index].toString())["data"].toString()
            val note = mapper.readValue (noteAsJSONObject, IOUtilities.Note::class.java)
            notes.add(note)
        }

        for (index in 0 until cardsJSONArray.length()) {
            val cardAsJSONObject = JSONObject(cardsJSONArray[index].toString())["data"].toString()
            val card = mapper.readValue (cardAsJSONObject, IOUtilities.Card::class.java)
            cards.add(card)
        }

        for (index in 0 until tagsJSONArray.length()) {
            val tagAsJSONObject = JSONObject(tagsJSONArray[index].toString())["data"].toString()
            val tag = mapper.readValue (tagAsJSONObject, IOUtilities.Tag::class.java)
            tags.add(tag)
        }

        vault = IOUtilities.Vault (
            version = keyspaceStatus().apiVersion,
            tag = tags,
            login = logins,
            note = notes,
            card = cards,
        )

        return vault

    }

    val MODE_PUT: String = "PUT"
    val MODE_POST: String = "POST"
    val MODE_DELETE: String = "DELETE"

    data class BackendDataObject (
        val data: Any,
    )

    data class BackendDataObjectId (
        val id: String,
    )

    suspend fun sendDataToBackend (signedToken: String, dataObject: Any, mode: String): JSONObject? {
        val method = when (mode) {
            MODE_PUT -> Request.Method.PUT
            MODE_POST -> Request.Method.POST
            MODE_DELETE -> Request.Method.PATCH
            else -> Request.Method.GET
        }

        return suspendCancellableCoroutine { continuation ->
            val queue = Volley.newRequestQueue(context)

            val jsonObjectRequest: JsonObjectRequest = object : JsonObjectRequest (
                method, vault_items_endpoint, JSONObject(),
                { response ->
                    try {
                        continuation.resumeWith(Result.success(response))
                    } catch (e: JSONException) {
                        Log.e("KeyspaceNetUtils", "Invalid JSON Object received from backend.")
                        e.printStackTrace()
                    }
                },  { error ->
                    error.printStackTrace()
                    Log.e("KeyspaceNetUtils", "Keyspace: Couldn't access this resource. Is it online and is your device connected to the internet?")
                }) {

                override fun getBody(): ByteArray {
                    return dataObject.toString().toByteArray(StandardCharsets.UTF_8)
                }

                @Throws(AuthFailureError::class)
                override fun getHeaders(): Map<String, String> {
                    val params: MutableMap<String, String> = HashMap()

                    // Headers are case insensitive
                    params["public-key"] = keyring.ED25519_PUBLIC_KEY!!.toHexString()
                    params["signed-token"] = signedToken
                    return params
                }

            }

            queue.add(jsonObjectRequest)

        }
    }

    data class KeyauthResponse (
        val apiVersion: Float,
        val expiry: Long,
        val token: String,
        val signedToken: String,
    )

    suspend fun sendKeyauthRequest (): KeyauthResponse? {
        var tokenToSign: KeyauthResponse? = null
        try {
            val keyAuthResponse = synchronousGetRequest(keyauth_endpoint)

            // Log.d("Keyspace", "Message before signing: ${keyAuthResponse.toString()}")
            tokenToSign = KeyauthResponse (
                apiVersion =  (keyAuthResponse?.get("apiVersion")).toString().toFloat(),
                expiry =  (keyAuthResponse?.get("expiry")).toString().toLong(),
                token =  (keyAuthResponse?.get("token")).toString(),
                signedToken = (keyAuthResponse?.get("signedToken")).toString(),
            )
        } catch (exception: Exception) {
            exception.printStackTrace()
        }

        return tokenToSign

    }

    data class SignupParameters (
        val email: String,
        val public_key: String,
        val signed_token: String,
    )

    data class SignupResponse(
        val status: String,
        val email: String,
    )

    class AccountExistsException : Exception()
    suspend fun sendSignupRequest (signupParameters: SignupParameters): SignupResponse? {
        var signupResponse: SignupResponse? = null

        val signupParametersAsMap = mapper.readValue (
            mapper
                .writer()
                .withDefaultPrettyPrinter()
                .writeValueAsBytes (
                    signupParameters
                ),
            Map::class.java
        ) as Map<String, String>

        try {
            signupResponse = mapper.readValue (
                synchronousJsonPostRequest(signup_endpoint, signupParametersAsMap)!!.toString(),
                SignupResponse::class.java
            )
        } catch (_: MissingKotlinParameterException) {
            throw AccountExistsException()
        }

        return signupResponse
    }

    data class LoginParameters(
        val email: String,
        val signed_token: String,
    )

    data class LoginResponse (
        val status: String,
        val email: String,
        val token: String,
    )

}
