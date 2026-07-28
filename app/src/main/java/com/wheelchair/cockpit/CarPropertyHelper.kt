@file:Suppress("SpellCheckingInspection")
package com.wheelchair.cockpit

import android.content.Context
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.util.Log

class CarPropertyHelper(private val context: Context, private val onSignalChanged: (Int, Any) -> Unit) {

    private var car: Car? = null
    private var carPropertyManager: CarPropertyManager? = null

    init {
        connectCarService()
    }

    private fun connectCarService() {
        try {
            // Establish connection to Car Service daemon
            car = Car.createCar(context, null, Car.CAR_WAIT_TIMEOUT_WAIT_FOREVER) { connectedCar, ready ->
                if (ready) {
                    Log.i("CarPropertyHelper", "Car Service connected successfully.")
                    carPropertyManager = connectedCar.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
                    registerVhalListeners()
                } else {
                    Log.w("CarPropertyHelper", "Car Service disconnected.")
                }
            }
        } catch (e: Exception) {
            Log.e("CarPropertyHelper", "Failed connecting to VHAL: ${e.message}", e)
        }
    }

    @Suppress("DEPRECATION")
    private fun registerVhalListeners() {
        val manager = carPropertyManager ?: return
        
        try {
            // Subscribe to Speed property updates (Global AreaId = 0)
            manager.registerCallback(
                vhalCallback,
                VehiclePropertyIds.PERF_VEHICLE_SPEED,
                CarPropertyManager.SENSOR_RATE_NORMAL
            )

            // Subscribe to HVAC Air Conditioner state updates
            manager.registerCallback(
                vhalCallback,
                VehiclePropertyIds.HVAC_AC_ON,
                CarPropertyManager.SENSOR_RATE_ONCHANGE
            )
            Log.i("CarPropertyHelper", "Successfully registered listeners for VHAL signals.")
        } catch (e: Exception) {
            Log.e("CarPropertyHelper", "Subscription registration failed: ${e.message}")
        }
    }

    private val vhalCallback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            Log.d("CarPropertyHelper", "VHAL property change: id=${value.propertyId}, value=${value.value}")
            onSignalChanged(value.propertyId, value.value)
        }

        override fun onErrorEvent(propId: Int, areaId: Int) {
            Log.e("CarPropertyHelper", "VHAL property error: id=$propId, areaId=$areaId")
        }
    }

    fun setHvacState(areaId: Int, turnOn: Boolean) {
        val manager = carPropertyManager ?: return
        try {
            manager.setBooleanProperty(VehiclePropertyIds.HVAC_AC_ON, areaId, turnOn)
            Log.i("CarPropertyHelper", "Set HVAC HVAC_AC_ON AreaId=$areaId State=$turnOn")
        } catch (e: Exception) {
            Log.e("CarPropertyHelper", "Failed to write HVAC property: ${e.message}")
        }
    }

    fun shutdown() {
        car?.disconnect()
        car = null
        carPropertyManager = null
    }
}
