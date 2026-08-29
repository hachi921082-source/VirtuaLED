package com.virtualed.app.autostop

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * 近接センサーの被覆状態を監視する動的登録リスナー。
 * docs/plans/other/auto-stop-conditions/PLAN-1-proximity.md §2 を実装。
 *
 * 近接センサーを持たない端末では sensor が null になり、start() は何もせず黙って
 * no-op する (クラッシュさせない)。
 */
class ProximityMonitor(
    context: Context,
    private val onCoveredChanged: (covered: Boolean) -> Unit,
) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)

    fun start() {
        sensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        // sensor == null (近接センサー非搭載端末) の場合はここで何も登録されず、本機能は黙って無効化される
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val covered = event.values[0] < (sensor?.maximumRange ?: Float.MAX_VALUE)
        onCoveredChanged(covered)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
