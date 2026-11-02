package com.example.shakelight

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlin.math.sqrt

class ShakeService : Service(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var proximitySensor: Sensor? = null
    private lateinit var cameraManager: CameraManager
    private var cameraId: String? = null
    private lateinit var sharedPrefs: SharedPreferences

    private var isTorchOn = false
    private var lastShakeTime: Long = 0
    private var isCovered = false // Variable pour le mode poche

    companion object {
        private const val CHANNEL_ID = "ShakeServiceChannel"
        private const val NOTIFICATION_ID = 1001
        private const val SHAKE_THRESHOLD_G = 6.0f // Ta nouvelle sensibilité
        private const val DEBOUNCE_TIME_MS = 1000L
    }

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        sharedPrefs = getSharedPreferences("ShakeLightPrefs", Context.MODE_PRIVATE)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        // Initialisation de l'accéléromètre
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }

        // Initialisation du capteur de proximité (Mode Poche)
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        proximitySensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            cameraId = cameraManager.cameraIdList.firstOrNull()
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        // 1. Gérer le capteur de proximité
        if (event.sensor.type == Sensor.TYPE_PROXIMITY) {
            // Si la distance est inférieure à 2cm, le téléphone est dans la poche ou face contre table
            isCovered = event.values[0] < 2.0f
            return
        }

        // 2. Gérer l'accéléromètre
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            val gForce = sqrt((x * x + y * y + z * z).toDouble()).toFloat() / SensorManager.GRAVITY_EARTH
            val currentTime = System.currentTimeMillis()

            // On récupère la sensibilité choisie par l'utilisateur (6.0 par défaut)
            val dynamicThreshold = sharedPrefs.getFloat("sensitivity", 6.0f)
            val isPaused = sharedPrefs.getBoolean("is_paused", false)

            // On n'allume QUE si la secousse est forte, que le capteur n'est pas couvert, ET que ce n'est pas en pause
            if (gForce > dynamicThreshold && !isCovered && !isPaused) {
                if (currentTime - lastShakeTime >= DEBOUNCE_TIME_MS) {
                    lastShakeTime = currentTime
                    toggleTorch()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun toggleTorch() {
        val id = cameraId ?: return
        try {
            isTorchOn = !isTorchOn
            cameraManager.setTorchMode(id, isTorchOn)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Service Lampe Torche",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Lampe Torche par Secousse")
            .setContentText("Mode poche et contrôle actif.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        if (isTorchOn) {
            cameraId?.let { id ->
                try {
                    cameraManager.setTorchMode(id, false)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}