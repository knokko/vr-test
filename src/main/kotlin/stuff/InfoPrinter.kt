package stuff

import org.lwjgl.openvr.OpenVR
import org.lwjgl.openvr.VR.*
import org.lwjgl.openvr.VRCompositor.VRCompositor_GetVulkanInstanceExtensionsRequired
import org.lwjgl.openvr.VRSystem.VRSystem_GetTrackedDeviceClass
import org.lwjgl.system.MemoryStack.stackPush

fun main() {
    println("Runtime installed? ${VR_IsRuntimeInstalled()}")
    println("Runtime path = ${VR_RuntimePath()}")
    println("Has head-mounted display? ${VR_IsHmdPresent()}")

    stackPush().use{stack ->

        val pError = stack.mallocInt(1)
        val token = VR_InitInternal(pError, 0)

        println("Error code for init is ${pError[0]}")
        println("Token is $token")
        if (pError[0] == 0) {

            OpenVR.create(token)

            println("Device classes:")
            for (deviceIndex in 0 until k_unMaxTrackedDeviceCount)
                println(VRSystem_GetTrackedDeviceClass(deviceIndex))
            println()

            VRCompositor_GetVulkanInstanceExtensionsRequired(0)
        } else {

            println("Error meaning is ${VR_GetVRInitErrorAsSymbol(pError[0])}")
        }

        VR_ShutdownInternal()
        println("Shutdown successfully")
    }

    stackPush().use{stack ->

        val pError = stack.mallocInt(1)
        val token = VR_InitInternal(pError, EVRApplicationType_VRApplication_Utility)
    }
}