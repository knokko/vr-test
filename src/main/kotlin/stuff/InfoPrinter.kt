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
import org.lwjgl.system.MemoryUtil.*
import java.awt.Color
import java.io.File
import java.io.PrintStream
import javax.swing.filechooser.FileSystemView

fun main() {

    val home = FileSystemView.getFileSystemView().homeDirectory

    System.setOut(PrintStream(File("$home/vrOut.txt")))
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

            // The initial color will be red
            glClearColor(1f, 0f, 0f, 1f)
            glClear(GL_COLOR_BUFFER_BIT)

            val pixelBuffer = memAlloc(width * height * 3)

            // This prints the color of the corner pixel, nice for debugging
            fun printPixelColor() {
                glReadPixels(0, 0, width, height, GL_RGB, GL_UNSIGNED_BYTE, pixelBuffer)
                val firstColor = Color(pixelBuffer[0].toInt() and 0xFF, pixelBuffer[1].toInt() and 0xFF,
                        pixelBuffer[2].toInt() and 0xFF)
                println("The color is $firstColor")
            }

            fun printState() {
                println("Bound texture is ${glGetInteger(GL_TEXTURE_BINDING_2D)}")
                println("Bound framebuffer is ${glGetInteger(GL_FRAMEBUFFER_BINDING)}")
            }

            println("Initial:")
            printState()

            println("Should be red now")
            printPixelColor()

            val leftTexture = Texture.callocStack(stack)
            leftTexture.eType(ETextureType_TextureType_OpenGL)
            leftTexture.eColorSpace(EColorSpace_ColorSpace_Gamma)
            // The next line seems weird and dirty, but appears the right way to do this
            leftTexture.handle(leftFramebuffer.textureHandle.toLong())

            val poses = TrackedDevicePose.mallocStack(k_unMaxTrackedDeviceCount, stack)

            // Submit green texture to headset
            val posesResultGreen = VRCompositor_WaitGetPoses(poses, null)
            glClearColor(0f, 1f, 0f, 1f)
            glClear(GL_COLOR_BUFFER_BIT)
            println("Should be green now")
            printPixelColor()
            printState()
            val leftResultGreen = VRCompositor_Submit(EVREye_Eye_Left, leftTexture, null, EVRSubmitFlags_Submit_TextureWithDepth)
            val rightResultGreen = VRCompositor_Submit(EVREye_Eye_Right, leftTexture, null, EVRSubmitFlags_Submit_TextureWithDepth)
            glFlush()

            // Submit blue texture to headset
            val posesResultRed = VRCompositor_WaitGetPoses(poses, null)
            glBindTexture(GL_TEXTURE_2D, leftFramebuffer.textureHandle)
            glBindFramebuffer(GL_FRAMEBUFFER, leftFramebuffer.handle)
            glClearColor(0f, 0f, 1f, 1f)
            glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
            println("Should be blue now")
            printPixelColor()
            printState()
            val leftResultRed = VRCompositor_Submit(EVREye_Eye_Left, leftTexture, null, EVRSubmitFlags_Submit_TextureWithDepth)
            val rightResultRed = VRCompositor_Submit(EVREye_Eye_Right, leftTexture, null, EVRSubmitFlags_Submit_TextureWithDepth)
            glFlush()

            println("Green results are ($posesResultGreen, $leftResultGreen, $rightResultGreen)")
            println("Red results are ($posesResultRed, $leftResultRed, $rightResultRed)")

            // Now we just keep submitting the same frame for 10 seconds long
            val endTime = System.currentTimeMillis() + 10_000
            while (System.currentTimeMillis() < endTime) {

                val posesResult = VRCompositor_WaitGetPoses(poses, null)
                val leftResult = VRCompositor_Submit(EVREye_Eye_Left, leftTexture, null, EVRSubmitFlags_Submit_TextureWithDepth)
                val rightResult = VRCompositor_Submit(EVREye_Eye_Right, leftTexture, null, EVRSubmitFlags_Submit_TextureWithDepth)
                if (posesResult != 0 || leftResult != 0 || rightResult != 0) {
                    throw RuntimeException("Results are ($posesResult, $leftResult, $rightResult)")
                }

            }

            println("Final state")
            printState()

            // Cleaning up OpenGL resources
            leftTexture.free()

            memFree(pixelBuffer)

            glDeleteFramebuffers(leftFramebuffer.handle)
            glDeleteTextures(leftFramebuffer.textureHandle)
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
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, NULL)
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