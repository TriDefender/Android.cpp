package tridefender.llama.snapdragon.service

import android.content.Context
import android.util.Log
import tridefender.llama.snapdragon.model.DeviceType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceDetectionService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        private const val TAG = "DeviceDetectionService"
    }
    
    fun detectAvailableDevices(): Map<DeviceType, Boolean> {
        val availableDevices = mutableMapOf<DeviceType, Boolean>()
        
        availableDevices[DeviceType.OPENCL] = checkOpenCL()
        
        val htpDeviceTypes = listOf(
            DeviceType.HTP0, DeviceType.HTP1, DeviceType.HTP2, 
            DeviceType.HTP3, DeviceType.HTP4
        )
        
        for (deviceType in htpDeviceTypes) {
            availableDevices[deviceType] = checkHTPDevice(deviceType)
        }
        
        availableDevices[DeviceType.CPU] = true
        
        val hasHTP = htpDeviceTypes.any { availableDevices[it] == true }
        if (hasHTP) {
            Log.i(TAG, "HTP devices available")
        }
        
        return availableDevices
    }
    
    private fun checkOpenCL(): Boolean {
        return try {
            val file = java.io.File("/system/vendor/lib64/libOpenCL.so")
            file.exists() || Runtime.getRuntime().exec("ls /vendor/lib64/libOpenCL.so").waitFor() == 0
        } catch (e: Exception) {
            Log.d(TAG, "OpenCL check failed: ${e.message}")
            true
        }
    }
    
    private fun checkHTPDevice(deviceType: DeviceType): Boolean {
        return try {
            val cdspPath = "/dev/cdsp"
            java.io.File(cdspPath).exists()
        } catch (e: Exception) {
            Log.d(TAG, "HTP check failed for $deviceType: ${e.message}")
            true
        }
    }
}
