package stuff

import org.joml.Matrix4f
import org.joml.Matrix4x3f
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL30.*
import org.lwjgl.openvr.*
import org.lwjgl.openvr.VR.*
import org.lwjgl.openvr.VRCompositor.*
import org.lwjgl.openvr.VRSystem.*
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.system.MemoryUtil.*
import java.awt.Color
import java.awt.image.BufferedImage
import java.awt.image.BufferedImage.TYPE_INT_ARGB
import java.awt.image.BufferedImage.TYPE_INT_RGB
import java.io.File
import java.io.PrintStream
import java.util.*
import javax.imageio.ImageIO
import javax.swing.filechooser.FileSystemView
import kotlin.math.sin

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
    //glfwInitHint(GLFW_COCOA_MENUBAR, GLFW_FALSE)
    glfwInit()
    //glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)

    val width = 1400
    val height = 1500

    val windowHandle = glfwCreateWindow(width, height, "Should be visible", NULL, NULL)
    glfwMakeContextCurrent(windowHandle)
    GL.createCapabilities()
    glClearColor(0.5f, 0.2f, 0.7f, 1f)

    val glObjects = createGlObjects()

    stackPush().use{stack ->

        //val pError = stack.mallocInt(1)
        //val token = VR_InitInternal(pError, EVRApplicationType_VRApplication_Scene)
        val pError = stack.ints(0)
        val token = 1

        println("Error code for init is ${pError[0]}")
        println("Token is $token")
        if (pError[0] == 0) {

            //OpenVR.create(token)

            /*
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
             */


            val leftFramebuffer = createSimpleFramebuffer(width, height)
            val rightFramebuffer = createSimpleFramebuffer(width, height)

            val leftTexture = Texture.callocStack(stack)
            leftTexture.eType(ETextureType_TextureType_OpenGL)
            leftTexture.eColorSpace(EColorSpace_ColorSpace_Gamma)
            // The next line seems weird and dirty, but appears the right way to do this
            leftTexture.handle(leftFramebuffer.textureHandle.toLong())

            val rightTexture = Texture.callocStack(stack)
            rightTexture.eType(ETextureType_TextureType_OpenGL)
            rightTexture.eColorSpace(EColorSpace_ColorSpace_Gamma)
            rightTexture.handle(rightFramebuffer.textureHandle.toLong())

            // Stop after 20 seconds
            val endTime = System.currentTimeMillis() + 5_000

            val testProjectionMatrix = Matrix4f().perspective(
                    2f, width.toFloat() / height.toFloat(), 0.01f, 100f
            )
            println(testProjectionMatrix)

            val vrProjectionMatrix = Matrix4f(
            9.173E-1f,  0.000E+0f,  0.000E+0f,  0.000E+0f,
            0.000E+0f,  8.335E-1f,  0.000E+0f,  0.000E+0f,
            -1.741E-1f, -1.061E-1f, -1.000E+0f, -1.000E+0f,
            0.000E+0f,  0.000E+0f, -1.000E-2f,  0.000E+0f
            )

            val vrViewMatrix = Matrix4f(
                    8.651E-1f, -4.040E-2f, -5.000E-1f, -6.027E-2f,
                    -7.920E-2f,  9.733E-1f, -2.157E-1f, -1.124E+0f,
                    4.954E-1f,  2.262E-1f,  8.387E-1f,  1.328E-1f,
                    0.000E+0f,  0.000E+0f,  0.000E+0f,  1.000E+0f
            ).transpose()

            val vrEyeMatrix = Matrix4f(
                    1.000E+0f,  0.000E+0f,  0.000E+0f, -3.507E-2f,
                    0.000E+0f,  1.000E+0f,  0.000E+0f,  0.000E+0f,
                    0.000E+0f,  0.000E+0f,  1.000E+0f,  3.000E-2f,
                    0.000E+0f,  0.000E+0f,  0.000E+0f,  1.000E+0f
            ).transpose()

            val vrMatrix = Matrix4f(
                    7.936E-1f, -3.706E-2f, -4.586E-1f, -8.746E-2f,
                    -6.601E-2f,  8.112E-1f, -1.798E-1f, -9.369E-1f,
                    -6.376E-1f, -3.224E-1f, -7.288E-1f, -1.027E+0f,
                    -4.954E-3f, -2.262E-3f, -8.387E-3f, -1.628E-3f
            ).transpose()

            val testMatrix = vrProjectionMatrix.mul(vrViewMatrix, Matrix4f()).mul(vrEyeMatrix, Matrix4f())

            val poses = TrackedDevicePose.mallocStack(k_unMaxTrackedDeviceCount, stack)
            val matrixBuffer = HmdMatrix44.callocStack(stack)
            val matrixBuffer2 = HmdMatrix34.callocStack(stack)
            val rng = Random()
            println("Start while loop")
            while (System.currentTimeMillis() < endTime) {

                //VRCompositor_WaitGetPoses(poses, null)
                val timeValue = sin((System.currentTimeMillis() % 100_000) / 1000f) * 0.5f + 0.5f

                /*
                val rawViewMatrix = poses[0].mDeviceToAbsoluteTracking()

                // TODO Next line needs more attention!
                val viewMatrix = vrToJomlMatrix(rawViewMatrix).invert()
                println("viewMatrix:")
                println(viewMatrix)
                println()

                val leftProjectionMatrix = vrToJomlMatrix(VRSystem_GetProjectionMatrix(EVREye_Eye_Left, 0.01f, 100f, matrixBuffer))
                val rightProjectionMatrix = vrToJomlMatrix(VRSystem_GetProjectionMatrix(EVREye_Eye_Right, 0.01f, 100f, matrixBuffer))
                println("left projection matrix:")
                println(leftProjectionMatrix)
                println()

                val leftEyeMatrix = vrToJomlMatrix(VRSystem_GetEyeToHeadTransform(EVREye_Eye_Left, matrixBuffer2))
                val rightEyeMatrix = vrToJomlMatrix(VRSystem_GetEyeToHeadTransform(EVREye_Eye_Right, matrixBuffer2))

                println("Left eye matrix:")
                println(leftEyeMatrix)
                println()

                val leftViewMatrix = leftProjectionMatrix.mul(leftEyeMatrix).mul(viewMatrix)
                val rightViewMatrix = rightProjectionMatrix.mul(rightEyeMatrix).mul(viewMatrix)

                println("leftViewMatrix is:")
                println(leftViewMatrix)
                println()

                val testVector = Vector3f(0f, 0f, -1f)
                println("transform left: ${leftViewMatrix.transformPosition(testVector)}")
                println("transform right: ${rightViewMatrix.transformPosition(testVector)}")
                 */

                // matMVP = m_mat4ProjectionLeft * m_mat4eyePosLeft * m_mat4HMDPose;
                // m_mat4ProjectionLeft is obtained from GetProjectionMatrix
                // m_mat4eyePosLeft is obtained from GetEyeToHeadTransform
                // m_mat4HMDPose is the inverse of DeviceToAbsoluteTracking of Hmd (viewMatrix)

                glBindFramebuffer(GL_FRAMEBUFFER, 0)
                glViewport(0, 0, width, height)
                glClearColor(1f, 1f, 1f, 1f)
                glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)

                //glEnable(GL_DEPTH_TEST)
                glUseProgram(glObjects.cubeProgram)
                glBindVertexArray(glObjects.cubeVao)

                val transformMatrix = Matrix4f()
                transformMatrix.translate(30f * rng.nextFloat() - 15f, 50f * rng.nextFloat() - 25f, 20f * rng.nextFloat() - 10f)
                transformMatrix.rotate(6f * rng.nextFloat(), Vector3f(0f, 0f, 1f))
                transformMatrix.rotate(6f * rng.nextFloat(), Vector3f(1f, 0f, 0f))
                transformMatrix.rotate(6f * rng.nextFloat(), Vector3f(0f, 1f, 0f))

                stackPush().use{innerStack ->
                    val innerMatrixBuffer = innerStack.mallocFloat(16)
                    val currentMatrix = testMatrix.mul(transformMatrix, Matrix4f())
                    currentMatrix.get(innerMatrixBuffer)
                    glUniformMatrix4fv(glObjects.uniformCubeMatrix, false, innerMatrixBuffer)
                }
                // TODO Stop hardcoding 36
                glDrawElements(GL_TRIANGLES, 36, GL_UNSIGNED_INT, 0)

                glBindFramebuffer(GL_FRAMEBUFFER, rightFramebuffer.handle)
                glViewport(0, 0, width, height)
                glClearColor(0f, timeValue, 0f, 1f)
                glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)

                //VRCompositor_Submit(EVREye_Eye_Left, leftTexture, null, EVRSubmitFlags_Submit_TextureWithDepth)
                //VRCompositor_Submit(EVREye_Eye_Right, rightTexture, null, EVRSubmitFlags_Submit_TextureWithDepth)
                glFlush()
                glfwSwapBuffers(windowHandle)
                glfwPollEvents()
                Thread.sleep(11)
            }

            println("End while loop")

            glBindFramebuffer(GL_FRAMEBUFFER, leftFramebuffer.handle)
            val pixelBuffer = memAlloc(4 * width * height)
            glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, pixelBuffer)
            val pixelImage = BufferedImage(width, height, TYPE_INT_ARGB)
            var pixelIndex = 0
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val red = pixelBuffer[pixelIndex++].toInt() and 0xFF
                    val green = pixelBuffer[pixelIndex++].toInt() and 0xFF
                    val blue = pixelBuffer[pixelIndex++].toInt() and 0xFF
                    val alpha = pixelBuffer[pixelIndex++].toInt() and 0xFF
                    val color = Color(red, green, blue, alpha)
                    pixelImage.setRGB(x, y, color.rgb)
                }
            }
            ImageIO.write(pixelImage, "PNG", File("pixels.png"))
            memFree(pixelBuffer)

            glDeleteFramebuffers(leftFramebuffer.handle)
            println("Delete the left framebuffer")
            glDeleteFramebuffers(rightFramebuffer.handle)
            println("Deleted the right framebuffer")
            glDeleteTextures(leftFramebuffer.textureHandle)
            println("Deleted the left framebuffer texture")
            glDeleteTextures(rightFramebuffer.textureHandle)
            println("Deleted the right framebuffer texture")
        } else {
            println("Error meaning is ${VR_GetVRInitErrorAsSymbol(pError[0])}")
        }

        //VR_ShutdownInternal()
        println("Shutdown VR successfully")
    }

    println("OpenGL error: ${glGetError()}")
    glObjects.cleanUp()
    println("OpenGL clean-up error: ${glGetError()}")

    GL.destroy()
    glfwDestroyWindow(windowHandle)
    glfwTerminate()

    println("Reached the end of the main method")
}

class GlObjects(
        val cubeVao: Int,
        val cubePositions: Int,
        val cubeColors: Int,
        val cubeIndices: Int,
        val cubeProgram: Int,
        val cubeVertexShader: Int,
        val cubeFragmentShader: Int,
        val uniformCubeMatrix: Int
) {

    fun cleanUp() {
        glDeleteVertexArrays(cubeVao)
        glDeleteBuffers(cubePositions)
        glDeleteBuffers(cubeColors)
        glDeleteBuffers(cubeIndices)

        glDetachShader(cubeProgram, cubeVertexShader)
        glDetachShader(cubeProgram, cubeFragmentShader)
        glDeleteProgram(cubeProgram)
        glDeleteShader(cubeVertexShader)
        glDeleteShader(cubeFragmentShader)
    }
}

fun createGlObjects(): GlObjects {

    val cubeVao = glGenVertexArrays()
    glBindVertexArray(cubeVao)
    // 2 vertex attributes: position and colors
    glEnableVertexAttribArray(0)
    glEnableVertexAttribArray(1)

    val cubeIndices = glGenBuffers()
    glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, cubeIndices)
    stackPush().use{stack ->
        val pIndices = stack.ints(
                0,1,2, 2,3,0,
                4,5,6, 6,7,4,
                8,9,10, 10,11,8,
                12,13,14, 14,15,12,
                16,17,18, 18,19,16,
                20,21,22, 22,23,20
        )
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, pIndices, GL_STATIC_DRAW)
    }

    val cubePositions = glGenBuffers()
    glBindBuffer(GL_ARRAY_BUFFER, cubePositions)
    stackPush().use{stack ->
        val pPositions = stack.floats(

                // Bottom of the cube
                -100f, -100f, -100f,
                100f, -100f, -100f,
                100f, -100f, 100f,
                -100f, -100f, 100f,

                // Top of the cube
                -100f, 100f, -100f,
                100f, 100f, -100f,
                100f, 100f, 100f,
                -100f, 100f, 100f,

                // Negative X side of the cube
                -100f, -100f, -100f,
                -100f, -100f, 100f,
                -100f, 100f, 100f,
                -100f, 100f, -100f,

                // Positive X side of the cube
                100f, -100f, -100f,
                100f, -100f, 100f,
                100f, 100f, 100f,
                100f, 100f, -100f,

                // Negative Z side of the cube
                -100f, -100f, -100f,
                100f, -100f, -100f,
                100f, 100f, -100f,
                -100f, 100f, -100f,

                // Positive Z side of the cube
                -100f, -100f, 100f,
                100f, -100f, 100f,
                100f, 100f, 100f,
                -100f, 100f, 100f
        )
        glBufferData(GL_ARRAY_BUFFER, pPositions, GL_STATIC_DRAW)
    }
    glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0)

    val cubeColors = glGenBuffers()
    glBindBuffer(GL_ARRAY_BUFFER, cubeColors)
    stackPush().use{stack ->
        val pColors = stack.floats(
                // Bottom side
                1f,0f,0f, 1f,0f,0f, 1f,0f,0f, 1f,0f,0f,

                // Top side
                0f,1f,1f, 0f,1f,1f, 0f,1f,1f, 0f,1f,1f,

                // Negative X side
                0f,1f,0f, 0f,1f,0f, 0f,1f,0f, 0f,1f,0f,

                // Positive X side
                1f,0f,1f, 1f,0f,1f, 1f,0f,1f, 1f,0f,1f,

                // Negative Z side
                0f,0f,1f, 0f,0f,1f, 0f,0f,1f, 0f,0f,1f,

                // Positive Z side
                1f,1f,0f, 1f,1f,0f, 1f,1f,0f, 1f,1f,0f
        )
        glBufferData(GL_ARRAY_BUFFER, pColors, GL_STATIC_DRAW)
    }
    glVertexAttribPointer(1, 3, GL_FLOAT, false, 0, 0)

    // Now it's time for the shaders
    val program = glCreateProgram()
    val vertexShader = loadShader("shaders/test.vert", GL_VERTEX_SHADER)
    val fragmentShader = loadShader("shaders/test.frag", GL_FRAGMENT_SHADER)

    glAttachShader(program, vertexShader)
    glAttachShader(program, fragmentShader)

    glBindAttribLocation(program, 0, "position")
    glBindAttribLocation(program, 1, "color")

    glLinkProgram(program)
    glValidateProgram(program)

    val uniformMatrix = glGetUniformLocation(program, "matrix")
    println("uniformMatrix is $uniformMatrix and program is $program and vertex shader is $vertexShader")
    println("cubeVao is $cubeVao and cubePositions is $cubePositions and cubeIndices is $cubeIndices")
    return GlObjects(
            cubeVao, cubePositions, cubeColors, cubeIndices,
            program, vertexShader, fragmentShader, uniformMatrix
    )
}

fun loadShader(path: String, type: Int): Int {

    val shader = glCreateShader(type)
    val shaderSource = StringBuilder()

    val resource = GlObjects::class.java.classLoader.getResource(path) ?: throw Error("Couldn't load resource $path")
    val resourceScanner = Scanner(resource.openStream())
    while (resourceScanner.hasNextLine()) {
        shaderSource.append(resourceScanner.nextLine())
        shaderSource.append('\n')
    }
    resourceScanner.close()

    glShaderSource(shader, shaderSource)
    glCompileShader(shader)

    val compileStatus = glGetShaderi(shader, GL_COMPILE_STATUS)
    if (compileStatus != GL_TRUE) {
        println("Failed to compile the shader $path:")
        println(glGetShaderInfoLog(shader))
        throw Error("Failed to compile shader $path, see log above for details")
    }

    return shader
}

fun vrToJomlMatrix(vrMatrix: HmdMatrix34): Matrix4f {
    return Matrix4f(
            vrMatrix.m(0), vrMatrix.m(4), vrMatrix.m(8), 0f,
            vrMatrix.m(1), vrMatrix.m(5), vrMatrix.m(9), 0f,
            vrMatrix.m(2), vrMatrix.m(6), vrMatrix.m(10), 0f,
            vrMatrix.m(3), vrMatrix.m(7), vrMatrix.m(11), 1f
    )
}

fun vrToJomlMatrix(vrMatrix: HmdMatrix44): Matrix4f {
    return Matrix4f(vrMatrix.m())
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