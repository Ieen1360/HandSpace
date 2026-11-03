package com.handspace.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * Leitura passiva de giroscópio e acelerômetro, apenas para diagnóstico.
 *
 * Os valores nunca controlam a interface. Se um sensor não existir, [hasGyroscope] ou
 * [hasAccelerometer] retornam false e a leitura fica null: nenhum valor é inventado.
 * A taxa é SENSOR_DELAY_UI, suficiente para diagnóstico e leve para o tracking.
 */
class DeviceSensorManager(context: Context) : SensorEventListener {

    private val sensorManager: SensorManager? = context.getSystemService(SensorManager::class.java)
    private val gyroscope: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val accelerometer: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val gyroValues = FloatArray(3)
    private val accelValues = FloatArray(3)
    private var hasGyroReading = false
    private var hasAccelReading = false
    private var listening = false

    val hasGyroscope: Boolean get() = gyroscope != null
    val hasAccelerometer: Boolean get() = accelerometer != null

    /** Última leitura do giroscópio em rad/s, ou null se indisponível ou ainda sem leitura. */
    fun gyroscopeValues(): FloatArray? = if (hasGyroReading) gyroValues else null

    /** Última leitura do acelerômetro em m/s², ou null se indisponível ou ainda sem leitura. */
    fun accelerometerValues(): FloatArray? = if (hasAccelReading) accelValues else null

    fun start() {
        val manager = sensorManager ?: return
        if (listening) return
        listening = true
        gyroscope?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        accelerometer?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    fun stop() {
        if (!listening) return
        listening = false
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> {
                event.values.copyInto(gyroValues, 0, 0, 3)
                hasGyroReading = true
            }
            Sensor.TYPE_ACCELEROMETER -> {
                event.values.copyInto(accelValues, 0, 0, 3)
                hasAccelReading = true
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
