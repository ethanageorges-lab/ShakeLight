package com.example.shakelight

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider

class MainActivity : AppCompatActivity() {

    private lateinit var sharedPrefs: SharedPreferences
    private lateinit var cameraManager: CameraManager
    private var isTorchOn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sharedPrefs = getSharedPreferences("ShakeLightPrefs", Context.MODE_PRIVATE)
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

        // 1. Vérification de la Politique de Confidentialité
        if (!sharedPrefs.getBoolean("privacy_accepted", false)) {
            showPrivacyPolicyDialog()
        }

        // 2. Gestion du Curseur (Slider)
        val slider = findViewById<Slider>(R.id.sensitivitySlider)
        val tvValue = findViewById<TextView>(R.id.tvSensitivityValue)

        val currentSensitivity = sharedPrefs.getFloat("sensitivity", 6.0f)
        slider.value = currentSensitivity
        tvValue.text = "Valeur actuelle : $currentSensitivity"

        slider.addOnChangeListener { _, value, _ ->
            tvValue.text = "Valeur actuelle : $value"
            sharedPrefs.edit().putFloat("sensitivity", value).apply()
        }

        // 3. Gestion du Bouton Manuel
        val btnToggle = findViewById<MaterialButton>(R.id.btnToggleTorch)
        btnToggle.setOnClickListener {
            toggleTorchManually()
        }

        // 4. Lancement du service (avec permission de notification)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                startShakeService()
            }
        } else {
            startShakeService()
        }
    }

    private fun showPrivacyPolicyDialog() {
        AlertDialog.Builder(this)
            .setTitle("Politique de Confidentialité")
            .setMessage("ShakeLight utilise l'accéléromètre et le capteur de proximité en arrière-plan pour détecter vos mouvements. La permission de la caméra est requise uniquement pour contrôler la lampe torche.\n\nFonctionnement 100% hors-ligne : aucune donnée personnelle n'est collectée, stockée ou envoyée sur internet.")
            .setPositiveButton("J'accepte") { _, _ ->
                sharedPrefs.edit().putBoolean("privacy_accepted", true).apply()
            }
            .setCancelable(false)
            .show()
    }

    private fun toggleTorchManually() {
        try {
            val cameraId = cameraManager.cameraIdList.firstOrNull()
            if (cameraId != null) {
                isTorchOn = !isTorchOn
                cameraManager.setTorchMode(cameraId, isTorchOn)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) startShakeService()
    }

    private fun startShakeService() {
        val serviceIntent = Intent(this, ShakeService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }
}