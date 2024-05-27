package tech.fastsense.scanner

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.io.IOException

class SensorRecorder(private val context: Context) : SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private lateinit var accelerometer: Sensor
    private lateinit var gyroscope: Sensor
    private lateinit var magnetometer: Sensor
    private var fileWriter: FileWriter? = null
    private var recording = false
    private var startTime: Long = 0

    init {
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    }

    fun startRecording(fileName: String) {
        try {
            val file = File(getOutputDirectory(), fileName)
            fileWriter = FileWriter(file)
            recording = true
            startTime = System.nanoTime() // Используем системное время в наносекундах
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_FASTEST)
            sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_FASTEST)
            sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_FASTEST)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun stopRecording() {
        try {
            recording = false
            sensorManager.unregisterListener(this)
            fileWriter?.flush()
            fileWriter?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun isRecording() = recording

    private fun getOutputDirectory(): File {
        val mediaDir = (context as MainActivity).externalMediaDirs.firstOrNull()?.let {
            File(it, context.resources.getString(R.string.app_name)).apply { mkdirs() }
        }
        Log.v("SensorRecorder", "Output directory: $mediaDir")
        return if (mediaDir != null && mediaDir.exists()) mediaDir else context.filesDir
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!recording) return

        val currentTime = (System.nanoTime() - startTime) / 1_000_000_000.0 // Перевод в секунды

        try {
            val jsonObject = JSONObject().apply {
                put("t", currentTime)
                put("s", when (event.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> "acc"
                    Sensor.TYPE_GYROSCOPE -> "gyro"
                    Sensor.TYPE_MAGNETIC_FIELD -> "mag"
                    else -> "unknown"
                })
                put("x", event.values[0])
                put("y", event.values[1])
                put("z", event.values[2])
            }
            fileWriter?.append(jsonObject.toString() + "\n")
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        // Не требуется для этой задачи
    }
}
