package stuff

import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL30.*
import org.lwjgl.openvr.*
import org.lwjgl.openvr.VR.*
import org.lwjgl.openvr.VRCompositor.*
import org.lwjgl.openvr.VRSystem.*
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.system.MemoryUtil
import org.lwjgl.system.MemoryUtil.NULL
import org.lwjgl.system.MemoryUtil.memUTF8
import java.awt.Color

fun main() {
    println("Runtime installed? ${VR_IsRuntimeInstalled()}")
    println("Runtime path = ${VR_RuntimePath()}")
    println("Has head-mounted display? ${VR_IsHmdPresent()}")

    // Apparently, creating a cross-platform OpenGL context without window is very hard
    // So the work-around is to just create an invisible window
    glfwSetErrorCallback { errorCode, description ->
        println("GLFW error $errorCode: ${memUTF8(description)}")
    }
    glfwInitHint(GLFW_COCOA_MENUBAR, GLFW_FALSE)
    glfwInit()
    glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)

    val windowHandle = glfwCreateWindow(1, 1, "Should be invisible", NULL, NULL)
    glfwMakeContextCurrent(windowHandle)
    GL.createCapabilities()
    glClearColor(1f, 1f, 1f, 1f)

    stackPush().use{stack ->

        val pError = stack.mallocInt(1)
        val token = VR_InitInternal(pError, EVRApplicationType_VRApplication_Scene)

        println("Error code for init is ${pError[0]}")
        println("Token is $token")
        if (pError[0] == 0) {

            OpenVR.create(token)

            println("Device classes:")
            for (deviceIndex in 0 until k_unMaxTrackedDeviceCount) {
                val deviceClass = VRSystem_GetTrackedDeviceClass(deviceIndex)
                if (deviceClass != 0) {
                    val connected = VRSystem_IsTrackedDeviceConnected(deviceIndex)
                    println("Device class is $deviceClass and connected is $connected")
                }
            }
            println("These were all device classes")

            val pWidth = stack.mallocInt(1)
            val pHeight = stack.mallocInt(1)
            VRSystem_GetRecommendedRenderTargetSize(pWidth, pHeight)
            val width = pWidth[0]
            val height = pHeight[0]

            println("Recommended render target size is ($width, $height)")

            val leftFramebuffer = createSimpleFramebuffer(width, height)
            glBindFramebuffer(GL_FRAMEBUFFER, leftFramebuffer.handle)
            glClearColor(1f, 1f, 0f, 1f)
            glClear(GL_COLOR_BUFFER_BIT)

            run {
                //glBindFramebuffer(GL_FRAMEBUFFER, leftFramebuffer.handle)
                val pixelBuffer = MemoryUtil.memAlloc(width * height * 3)
                glReadPixels(0, 0, width, height, GL_RGB, GL_UNSIGNED_BYTE, pixelBuffer)
                val firstColor = Color(pixelBuffer[0].toInt() and 0xFF, pixelBuffer[1].toInt() and 0xFF,
                        pixelBuffer[2].toInt() and 0xFF)
                MemoryUtil.memFree(pixelBuffer)
                //glBindFramebuffer(GL_FRAMEBUFFER, 0)

                println("The later color is $firstColor")
            }

            val leftTexture = Texture.callocStack(stack)
            leftTexture.eType(ETextureType_TextureType_OpenGL)
            leftTexture.eColorSpace(EColorSpace_ColorSpace_Gamma)
            // The next line seems weird and dirty, but appears the right way to do this
            leftTexture.handle(leftFramebuffer.textureHandle.toLong())

            // Stop after 20 seconds
            val endTime = System.currentTimeMillis() + 2_000

            val poses = TrackedDevicePose.mallocStack(k_unMaxTrackedDeviceCount, stack)
            poses.eTrackingResult(123)
            println("Start while loop")
            while (System.currentTimeMillis() < endTime) {
                println("Poses: ${VRCompositor_WaitGetPoses(poses, null)}")
                println("Submit left: ${VRCompositor_Submit(EVREye_Eye_Left, leftTexture, null, EVRSubmitFlags_Submit_TextureWithDepth)}")
                // TODO Use a right texture as well
                VRCompositor_Submit(EVREye_Eye_Right, leftTexture, null, EVRSubmitFlags_Submit_TextureWithDepth)
                VRCompositor_PostPresentHandoff()
            }

            println("Poses info: connected is ${poses.bDeviceIsConnected()} and valid is ${poses.bPoseIsValid()}")
            println("Poses info: tracking result is ${poses.eTrackingResult()} and velocity is ${poses.vAngularVelocity()}")

            println("End while loop")

            leftTexture.free()

            glDeleteFramebuffers(leftFramebuffer.handle)
            GL11.glDeleteTextures(leftFramebuffer.textureHandle)
        } else {

            println("Error meaning is ${VR_GetVRInitErrorAsSymbol(pError[0])}")
        }

        VR_ShutdownInternal()
        println("Shutdown successfully")
    }

    println("OpenGL error:" + glGetError())

    GL.destroy()
    glfwDestroyWindow(windowHandle)
    glfwTerminate()
}

class Framebuffer(val handle: Int, val textureHandle: Int)

fun createSimpleFramebuffer(width: Int, height: Int) : Framebuffer {
    val oldFramebuffer = stackPush().use { stack ->
        val pOldFramebuffer = stack.mallocInt(1)
        glGetIntegerv(GL_DRAW_FRAMEBUFFER_BINDING, pOldFramebuffer)
        pOldFramebuffer[0]
    }

    val framebuffer = glGenFramebuffers()
    glBindFramebuffer(GL_FRAMEBUFFER, framebuffer)

    val texture = glGenTextures()
    glBindTexture(GL_TEXTURE_2D, texture)
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, width, height, 0, GL_RGB, GL_UNSIGNED_BYTE, NULL)
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0)

    val renderBuffer = glGenRenderbuffers()
    glBindRenderbuffer(GL_RENDERBUFFER, renderBuffer)
    glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH24_STENCIL8, width, height)
    glBindRenderbuffer(GL_RENDERBUFFER, 0)
    glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_RENDERBUFFER, renderBuffer)

    if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)
        System.err.println("Framebuffer creation failed")

    // Just for test rendering
    glClearColor(1f, 0f, 1f, 1f)
    glClear(GL_COLOR_BUFFER_BIT)

    // Rebind the previously bound framebuffer because this method shouldn't introduce side effects
    glBindFramebuffer(GL_FRAMEBUFFER, oldFramebuffer)

    return Framebuffer(framebuffer, texture)
}