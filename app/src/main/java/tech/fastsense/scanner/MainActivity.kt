package tech.fastsense.scanner

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.abs

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private lateinit var linearAccelerationSensor: Sensor
    private var velocityX = 0.0
    private var lastTimestamp = 0L
    private val accelerationHistory = mutableListOf<Pair<Double, Long>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        linearAccelerationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    }

    override fun onResume() {
        super.onResume()
        sensorManager.registerListener(this, linearAccelerationSensor, SensorManager.SENSOR_DELAY_FASTEST)
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            if (it.sensor.type == Sensor.TYPE_LINEAR_ACCELERATION) {
                val accelerationX = it.values[0].toDouble()
                val timestamp = it.timestamp

                accelerationHistory.add(Pair(accelerationX, timestamp))

                if (accelerationHistory.size > 3) {
                    // Используем последние четыре точки для интегрирования методом Рунге-Кутты 4-го порядка
                    val n = accelerationHistory.size
                    val (a0, t0) = accelerationHistory[n - 4]
                    val (a1, t1) = accelerationHistory[n - 3]
                    val (a2, t2) = accelerationHistory[n - 2]
                    val (a3, t3) = accelerationHistory[n - 1]

                    val deltaTime1 = (t1 - t0) / 1_000_000_000.0
                    val deltaTime2 = (t2 - t1) / 1_000_000_000.0
                    val deltaTime3 = (t3 - t2) / 1_000_000_000.0

                    // Средний временной интервал
                    val deltaTime = (deltaTime1 + deltaTime2 + deltaTime3) / 3

                    val k1 = a0
                    val k2 = a1 + 0.5 * deltaTime * k1
                    val k3 = a2 + 0.5 * deltaTime * k2
                    val k4 = a3 + deltaTime * k3

                    velocityX += (deltaTime / 6) * (k1 + 2 * k2 + 2 * k3 + k4)

                    println("@@@@    ${if (abs(velocityX) > 0.3) "!!!!!!!!" else "........"} ${velocityX * 100}")
                }

                if (accelerationHistory.size > 4) {
                    accelerationHistory.removeAt(0)
                }

                lastTimestamp = timestamp
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Не используется в этом примере
    }
}