package tridefender.llama.snapdragon.model

data class DeviceInfo(
    val name: String,
    val type: DeviceType,
    val available: Boolean,
    val description: String = ""
)

enum class DeviceType { CPU, OPENCL, HTP0, HTP1, HTP2, HTP3, HTP4 }