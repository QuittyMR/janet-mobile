package tech.quitty.janet

import android.Manifest
import android.animation.ObjectAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import android.content.Context

class MainActivity : AppCompatActivity() {

    private lateinit var recordButton: Button
    private lateinit var abortButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var messageTextView: TextView
    private lateinit var privateSwitch: Switch

    companion object {
        private const val LOG_TAG = "AudioRecordTest"
        private const val REQUEST_RECORD_AUDIO_PERMISSION = 200
        private const val PREFS_NAME = "JanetPrefs"
        private const val KEY_OWNER = "owner"
        private const val KEY_PRIVATE = "private"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        recordButton = findViewById(R.id.recordButton)
        abortButton = findViewById(R.id.abortButton)
        progressBar = findViewById(R.id.progressBar)
        messageTextView = findViewById(R.id.messageTextView)
        privateSwitch = findViewById(R.id.privateSwitch)

        val sharedPref = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        privateSwitch.isChecked = sharedPref.getBoolean(KEY_PRIVATE, false)

        privateSwitch.setOnCheckedChangeListener { _, isChecked ->
            with(sharedPref.edit()) {
                putBoolean(KEY_PRIVATE, isChecked)
                apply()
            }
        }

        recordButton.setOnClickListener {
            val serviceIntent = Intent(this, RecordingService::class.java)
            startService(serviceIntent)
        }

        abortButton.setOnClickListener {
            val serviceIntent = Intent(this, RecordingService::class.java).apply {
                action = RecordingService.ACTION_ABORT
            }
            startService(serviceIntent)
        }

        RecordingState.isRecording.observe(this) { isRecording ->
            updateButtonState(isRecording)
            rotateRecordButton()
            if (isRecording) {
                abortButton.visibility = View.VISIBLE
            }
        }

        RecordingState.isUploading.observe(this) { isUploading ->
            if (isUploading) {
                progressBar.visibility = View.VISIBLE
                recordButton.visibility = View.GONE
                abortButton.visibility = View.GONE
            } else {
                progressBar.visibility = View.GONE
                recordButton.visibility = View.VISIBLE
                abortButton.visibility = View.GONE
            }
        }

        RecordingState.uploadResponse.observe(this) { message ->
            message?.let {
                showMessage(it)
                RecordingState.uploadResponse.postValue(null)
            }
        }

        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO_PERMISSION)
    }

    private fun updateButtonState(isRecording: Boolean) {
        recordButton.text = if (isRecording) "Click to Stop" else "Click to Janet"
    }

    private fun rotateRecordButton() {
        val rotation = recordButton.rotation + 180f
        val animator = ObjectAnimator.ofFloat(recordButton, "rotation", rotation)
        animator.duration = 500
        animator.start()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                showSettingsDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showSettingsDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Set Owner Name")

        val input = EditText(this)
        input.setText(getOwner())
        builder.setView(input)

        builder.setPositiveButton("Save") { _, _ ->
            saveOwner(input.text.toString())
        }
        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }

        builder.show()
    }

    private fun saveOwner(name: String) {
        val sharedPref = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) ?: return
        with(sharedPref.edit()) {
            putString(KEY_OWNER, name)
            apply()
        }
    }

    private fun getOwner(): String? {
        val sharedPref = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPref.getString(KEY_OWNER, null)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION && grantResults.isNotEmpty() && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            Log.e(LOG_TAG, "Permission to record not granted")
            finish()
        }
    }

    private fun showMessage(message: String) {
        runOnUiThread {
            messageTextView.text = message
            messageTextView.visibility = View.VISIBLE
            messageTextView.alpha = 1f
            messageTextView.animate()
                .setStartDelay(3000)
                .alpha(0f)
                .setDuration(3000)
                .withEndAction {
                    messageTextView.visibility = View.GONE
                }
                .start()
        }
    }
}
