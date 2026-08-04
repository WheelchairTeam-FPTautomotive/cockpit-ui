@file:Suppress("SpellCheckingInspection")
package com.wheelchair.cockpit.vhal

import android.content.Context
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.car.drivingstate.CarUxRestrictionsManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CarPropertyHelper(
    private val context: Context,
    private val onSignalChanged: (Int, Any) -> Unit,
    private val onUxRestrictionsChanged: (Boolean) -> Unit
) {

    private var car: Car? = null
    private var carPropertyManager: CarPropertyManager? = null
    private var uxRestrictionsManager: CarUxRestrictionsManager? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    // Telemetry Stream Abstraction: Expose reactive StateFlow streams
    private val _speedFlow = MutableStateFlow(0f)
    val speedFlow: StateFlow<Float> = _speedFlow.asStateFlow()

    private val _hvacOnFlow = MutableStateFlow(false)
    val hvacOnFlow: StateFlow<Boolean> = _hvacOnFlow.asStateFlow()

    private val _hvacTempFlow = MutableStateFlow(24.0f)
    val hvacTempFlow: StateFlow<Float> = _hvacTempFlow.asStateFlow()

    // Debounce: ignore VHAL callbacks for 2s after a write to prevent emulator stale-value overwrite
    private var hvacWritePendingUntil: Long = 0L

    private val _uxRestrictionsFlow = MutableStateFlow(false)
    val uxRestrictionsFlow: StateFlow<Boolean> = _uxRestrictionsFlow.asStateFlow()

    private val vhalCallback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            Log.d("CarPropertyHelper", "VHAL property change: id=${value.propertyId}, value=${value.value}")
            
            // Push VHAL updates safely to state streams
            when (value.propertyId) {
                VehiclePropertyIds.PERF_VEHICLE_SPEED, VehiclePropertyIds.PERF_VEHICLE_SPEED_DISPLAY -> {
                    val rawSpeed = when (val v = value.value) {
                        is Float -> v
                        is Int -> v.toFloat()
                        is Double -> v.toFloat()
                        is Number -> v.toFloat()
                        else -> 0f
                    }
                    _speedFlow.value = kotlin.math.abs(rawSpeed) * 3.6f
                }
                VehiclePropertyIds.HVAC_AC_ON -> {
                    if (value.value is Boolean) {
                        // Skip stale callbacks for 2s after a user-initiated write
                        if (System.currentTimeMillis() > hvacWritePendingUntil) {
                            _hvacOnFlow.value = value.value as Boolean
                        } else {
                            Log.d("CarPropertyHelper", "Ignored stale HVAC_AC_ON callback during pending write")
                        }
                    }
                }
            }
            onSignalChanged(value.propertyId, value.value)
        }

        override fun onErrorEvent(propId: Int, areaId: Int) {
            Log.e("CarPropertyHelper", "VHAL property error: id=$propId, areaId=$areaId")
        }
    }

    init {
        connectCarService()
    }

    private fun connectCarService() {
        try {
            // Establish connection to Car Service daemon using the main looper handler
            car = Car.createCar(context, mainHandler, Car.CAR_WAIT_TIMEOUT_WAIT_FOREVER) { connectedCar, ready ->
                if (ready) {
                    Log.i("CarPropertyHelper", "Car Service connected successfully.")
                    try {
                        carPropertyManager = connectedCar.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
                        uxRestrictionsManager = connectedCar.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
                        
                        registerVhalListeners()
                        registerUxListeners()
                    } catch (e: Exception) {
                        Log.e("CarPropertyHelper", "Error getting car managers", e)
                    }
                } else {
                    Log.w("CarPropertyHelper", "Car Service disconnected.")
                }
            }
        } catch (e: Exception) {
            Log.e("CarPropertyHelper", "Failed connecting to VHAL: ${e.message}", e)
        }
    }

    private fun registerUxListeners() {
        try {
            uxRestrictionsManager?.registerListener { restrictions ->
                val isRestricted = restrictions.isRequiresDistractionOptimization || (restrictions.activeRestrictions != 0)
                Log.i("CarPropertyHelper", "UX Restriction updated: isRestricted=$isRestricted")
                _uxRestrictionsFlow.value = isRestricted
                onUxRestrictionsChanged(isRestricted)
            }
            val initial = uxRestrictionsManager?.getCurrentCarUxRestrictions()
            if (initial != null) {
                val isRestricted = initial.isRequiresDistractionOptimization || (initial.activeRestrictions != 0)
                Log.i("CarPropertyHelper", "Initial UX Restriction state: isRestricted=$isRestricted")
                _uxRestrictionsFlow.value = isRestricted
                onUxRestrictionsChanged(isRestricted)
            }
        } catch (e: Exception) {
            Log.e("CarPropertyHelper", "Failed to register UX listener", e)
        }
    }

    @Suppress("DEPRECATION")
    private fun registerVhalListeners() {
        val manager = carPropertyManager ?: return
        
        // 1. Subscribe to Speed property updates (5Hz sample rate)
        try {
            val registered = manager.registerCallback(
                vhalCallback,
                VehiclePropertyIds.PERF_VEHICLE_SPEED,
                CarPropertyManager.SENSOR_RATE_NORMAL
            )
            Log.i("CarPropertyHelper", "Registered PERF_VEHICLE_SPEED listener: $registered")
        } catch (e: Exception) {
            Log.e("CarPropertyHelper", "Failed registering SPEED listener", e)
        }

        try {
            manager.registerCallback(
                vhalCallback,
                VehiclePropertyIds.PERF_VEHICLE_SPEED_DISPLAY,
                CarPropertyManager.SENSOR_RATE_NORMAL
            )
            Log.i("CarPropertyHelper", "Registered PERF_VEHICLE_SPEED_DISPLAY listener")
        } catch (e: Exception) {
            Log.w("CarPropertyHelper", "PERF_VEHICLE_SPEED_DISPLAY not available: ${e.message}")
        }

        // Try reading initial speed value immediately
        try {
            val speedValue = manager.getProperty<Float>(VehiclePropertyIds.PERF_VEHICLE_SPEED, 0)
            if (speedValue != null) {
                Log.i("CarPropertyHelper", "Initial SPEED value: ${speedValue.value}")
                val speedKmh = kotlin.math.abs(speedValue.value) * 3.6f
                _speedFlow.value = speedKmh
                onSignalChanged(VehiclePropertyIds.PERF_VEHICLE_SPEED, speedValue.value)
            }
        } catch (e: Exception) {
            Log.w("CarPropertyHelper", "Initial SPEED read warning: ${e.message}")
        }

        // 2. Subscribe to HVAC Air Conditioner state updates
        try {
            val registeredHVAC = manager.registerCallback(
                vhalCallback,
                VehiclePropertyIds.HVAC_AC_ON,
                CarPropertyManager.SENSOR_RATE_ONCHANGE
            )
            Log.i("CarPropertyHelper", "Registered HVAC_AC_ON listener: $registeredHVAC")
        } catch (e: Exception) {
            Log.e("CarPropertyHelper", "Failed registering HVAC listener", e)
        }
    }



    fun setHvacState(areaId: Int, turnOn: Boolean) {
        val manager = carPropertyManager ?: return
        val areasToUpdate = if (areaId == 0) listOf(1, 4, 16, 64, 5, 85) else listOf(areaId)

        var anySuccess = false
        for (area in areasToUpdate) {
            // Set POWER_ON first (required by spec before AC_ON can accept writes)
            try {
                manager.setBooleanProperty(VehiclePropertyIds.HVAC_POWER_ON, area, turnOn)
                Log.i("CarPropertyHelper", "Set HVAC_POWER_ON AreaId=$area State=$turnOn")
            } catch (e: Exception) {
                Log.w("CarPropertyHelper", "HVAC_POWER_ON AreaId=$area failed (may be read-only on emulator): ${e.message}")
            }
            // Set AC_ON independently — emulator may accept this even if POWER_ON failed
            try {
                manager.setBooleanProperty(VehiclePropertyIds.HVAC_AC_ON, area, turnOn)
                Log.i("CarPropertyHelper", "Set HVAC_AC_ON AreaId=$area State=$turnOn")
                anySuccess = true
            } catch (e: Exception) {
                Log.w("CarPropertyHelper", "HVAC_AC_ON AreaId=$area failed: ${e.message}")
            }
        }

        if (!anySuccess) {
            Log.e("CarPropertyHelper", "Failed to set HVAC_AC_ON for all areas — updating UI only")
        }
        // Set debounce window so callback doesn't overwrite our intended state
        hvacWritePendingUntil = System.currentTimeMillis() + 2000L
        _hvacOnFlow.value = turnOn // Always update UI
    }

    fun setHvacTemperature(areaId: Int, temperature: Float) {
        val manager = carPropertyManager ?: return
        val areasToUpdate = if (areaId == 0) listOf(1, 4, 16, 64, 5, 85) else listOf(areaId)

        var anySuccess = false
        for (area in areasToUpdate) {
            try {
                manager.setFloatProperty(VehiclePropertyIds.HVAC_TEMPERATURE_SET, area, temperature)
                Log.i("CarPropertyHelper", "Set HVAC_TEMPERATURE_SET AreaId=$area Temp=$temperature")
                anySuccess = true
            } catch (e: Exception) {
                Log.w("CarPropertyHelper", "HVAC_TEMPERATURE_SET AreaId=$area failed: ${e.message}")
            }
        }

        if (!anySuccess) {
            Log.e("CarPropertyHelper", "Failed to set HVAC_TEMPERATURE_SET for all areas — updating UI only")
        }
        _hvacTempFlow.value = temperature // Always update UI
    }

    fun setDoorLock(areaId: Int, lock: Boolean) {
        val manager = carPropertyManager ?: return
        val areasToUpdate = if (areaId == 0) listOf(1, 4, 16, 64) else listOf(areaId) // 1: Row1L, 4: Row1R, 16: Row2L, 64: Row2R
        for (area in areasToUpdate) {
            try {
                manager.setBooleanProperty(VehiclePropertyIds.DOOR_LOCK, area, lock)
                Log.i("CarPropertyHelper", "Set DOOR_LOCK AreaId=$area Lock=$lock")
            } catch (e: Exception) {
                Log.e("CarPropertyHelper", "Failed to write DOOR_LOCK for AreaId=$area: ${e.message}")
            }
        }
    }

    fun setMirrorFold(areaId: Int, fold: Boolean) {
        val manager = carPropertyManager ?: return
        // 1 = ROW_1_LEFT, 4 = ROW_1_RIGHT
        val areasToUpdate = if (areaId == 0) listOf(1, 4) else listOf(areaId)
        for (area in areasToUpdate) {
            try {
                manager.setBooleanProperty(VehiclePropertyIds.MIRROR_FOLD, area, fold)
                Log.i("CarPropertyHelper", "Set MIRROR_FOLD AreaId=$area Fold=$fold")
            } catch (e: Exception) {
                Log.e("CarPropertyHelper", "Failed to write MIRROR_FOLD for AreaId=$area: ${e.message}")
            }
        }
    }

    fun shutdown() {
        try {
            uxRestrictionsManager?.unregisterListener()
            carPropertyManager?.unregisterCallback(vhalCallback)
        } catch (e: Exception) {
            Log.w("CarPropertyHelper", "Error during cleanup", e)
        }
        car?.disconnect()
        car = null
        carPropertyManager = null
        uxRestrictionsManager = null
    }
}
