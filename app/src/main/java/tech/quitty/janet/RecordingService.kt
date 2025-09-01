package tech.quitty.janet

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.cert.X509Certificate
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class RecordingService : Service() {

    private var mediaRecorder: MediaRecorder? = null
    private val uploadUrl = "https://domicile.home.mr/janet"
    
    // Create a trust manager that does not validate certificate chains
    private val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        }

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> {
            return arrayOf()
        }
    })

    // Install the all-trusting trust manager
    private val sslContext = SSLContext.getInstance("SSL").apply {
        init(null, trustAllCerts, java.security.SecureRandom())
    }

    // Create an ssl socket factory with our all-trusting manager
    private val sslSocketFactory = sslContext.socketFactory

    companion object {
        private const val LOG_TAG = "RecordingService"
        private const val PREFS_NAME = "JanetPrefs"
        private const val KEY_OWNER = "owner"
        private const val KEY_PRIVATE = "private"
        private const val CHANNEL_ID = "RecordingChannel"
        const val ACTION_ABORT = "tech.quitty.janet.ACTION_ABORT"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_ABORT) {
            abort()
            return START_NOT_STICKY
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Janet")
            .setContentText("Recording in progress...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()

        startForeground(1, notification)

        if (RecordingState.isRecording.value == true) {
            stopRecordingAndUpload()
        } else {
            startRecording()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        updateWidgetIcon(false)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Recording Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun startRecording() {
        val audioFile = File(cacheDir, "audio.mp3")
        mediaRecorder = MediaRecorder(this).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(audioFile.absolutePath)
            try {
                prepare()
                start()
                RecordingState.isRecording.postValue(true)
                updateWidgetIcon(true)
                Log.d(LOG_TAG, "Recording started")
            } catch (e: IOException) {
                Log.e(LOG_TAG, "prepare() failed: ${e.message}")
            }
        }
    }

    private fun stopRecordingAndUpload() {
        mediaRecorder?.apply {
            stop()
            release()
        }
        mediaRecorder = null
        RecordingState.isRecording.postValue(false)
        updateWidgetIcon(false)
        Log.d(LOG_TAG, "Recording stopped")
        uploadFile(File(cacheDir, "audio.mp3"))
    }

    private fun abort() {
        mediaRecorder?.apply {
            stop()
            release()
        }
        mediaRecorder = null
        RecordingState.isRecording.postValue(false)

        RecordingState.activeCall?.cancel()
        RecordingState.isUploading.postValue(false)

        stopForeground(true)
        stopSelf()
    }

    private fun updateWidgetIcon(isRecording: Boolean) {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val thisAppWidget = ComponentName(this, JanetWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(thisAppWidget)
        val iconResId = if (isRecording) R.mipmap.ic_launcher_rotated else R.mipmap.ic_launcher

        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(packageName, R.layout.janet_widget)
            views.setImageViewResource(R.id.widget_button, iconResId)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    private fun getOwner(): String? {
        val sharedPref = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPref.getString(KEY_OWNER, null)
    }

    private fun uploadFile(audioFile: File) {
        if (!audioFile.exists()) {
            Log.e(LOG_TAG, "Audio file is null, cannot upload.")
            return
        }
        val client = OkHttpClient.Builder()
            .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()

        val httpUrlBuilder = uploadUrl.toHttpUrlOrNull()!!.newBuilder()
                .addQueryParameter("transactionID", UUID.randomUUID().toString())

        val owner = getOwner()
        if (!owner.isNullOrEmpty()) {
            httpUrlBuilder.addQueryParameter("owner", owner)
        }

        val sharedPref = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (sharedPref.getBoolean(KEY_PRIVATE, false)) {
            httpUrlBuilder.addQueryParameter("private", "1")
        }

        val finalUrl = httpUrlBuilder.build()

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", audioFile.name, audioFile.asRequestBody("audio/mpeg".toMediaTypeOrNull()))
            .build()

        val request = Request.Builder().url(finalUrl).post(requestBody).build()

        val call = client.newCall(request)
        RecordingState.activeCall = call
        RecordingState.isUploading.postValue(true)

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (call.isCanceled()) {
                    Log.d(LOG_TAG, "Upload cancelled")
                } else {
                    val errorMessage = "Upload failed: ${e.message}"
                    Log.e(LOG_TAG, errorMessage)
                    RecordingState.uploadResponse.postValue(errorMessage)
                }
                RecordingState.isUploading.postValue(false)
                stopForeground(true)
                stopSelf()
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                if (!response.isSuccessful) {
                    val errorMessage = "Upload failed with code: ${response.code}, body: $responseBody"
                    Log.e(LOG_TAG, errorMessage)
                    RecordingState.uploadResponse.postValue(errorMessage)
                } else {
                    try {
                        val jsonObject = JSONObject(responseBody)
                        val message = jsonObject.getJSONObject("output").getString("message")
                        Log.d(LOG_TAG, "Upload successful: $message")
                        RecordingState.uploadResponse.postValue(message)
                    } catch (e: Exception) {
                        val errorMessage = "Failed to parse server response: $responseBody"
                        Log.e(LOG_TAG, errorMessage)
                        RecordingState.uploadResponse.postValue(errorMessage)
                    }
                }
                response.body?.close()
                RecordingState.isUploading.postValue(false)
                stopForeground(true)
                stopSelf()
            }
        })
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
