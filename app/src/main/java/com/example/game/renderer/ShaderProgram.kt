package com.example.game.renderer

import android.opengl.GLES30
import android.util.Log

class ShaderProgram {
    var programId: Int = 0
        private set

    var uMvpMatrixLoc: Int = -1
    var uModelMatrixLoc: Int = -1
    var uCameraPosLoc: Int = -1
    var uBaseColorLoc: Int = -1
    var uGlowColorLoc: Int = -1
    var uFogColorLoc: Int = -1
    var uShininessLoc: Int = -1
    var uEmissiveLoc: Int = -1
    var uAlphaLoc: Int = -1

    fun compile() {
        val vs = loadShader(GLES30.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fs = loadShader(GLES30.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
        programId = GLES30.glCreateProgram().also { prog ->
            GLES30.glAttachShader(prog, vs)
            GLES30.glAttachShader(prog, fs)
            GLES30.glLinkProgram(prog)
            val linkStatus = IntArray(1)
            GLES30.glGetProgramiv(prog, GLES30.GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] == 0) {
                Log.e("CobraShader", "Program link error: ${GLES30.glGetProgramInfoLog(prog)}")
            }
        }
        GLES30.glDeleteShader(vs)
        GLES30.glDeleteShader(fs)

        uMvpMatrixLoc = GLES30.glGetUniformLocation(programId, "uMvpMatrix")
        uModelMatrixLoc = GLES30.glGetUniformLocation(programId, "uModelMatrix")
        uCameraPosLoc = GLES30.glGetUniformLocation(programId, "uCameraPos")
        uBaseColorLoc = GLES30.glGetUniformLocation(programId, "uBaseColor")
        uGlowColorLoc = GLES30.glGetUniformLocation(programId, "uGlowColor")
        uFogColorLoc = GLES30.glGetUniformLocation(programId, "uFogColor")
        uShininessLoc = GLES30.glGetUniformLocation(programId, "uShininess")
        uEmissiveLoc = GLES30.glGetUniformLocation(programId, "uEmissive")
        uAlphaLoc = GLES30.glGetUniformLocation(programId, "uAlpha")
    }

    fun use() {
        GLES30.glUseProgram(programId)
    }

    private fun loadShader(type: Int, code: String): Int {
        return GLES30.glCreateShader(type).also { shader ->
            GLES30.glShaderSource(shader, code)
            GLES30.glCompileShader(shader)
            val compiled = IntArray(1)
            GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compiled, 0)
            if (compiled[0] == 0) {
                Log.e("CobraShader", "Shader compile error: ${GLES30.glGetShaderInfoLog(shader)}")
            }
        }
    }

    companion object {
        private const val VERTEX_SHADER = """#version 300 es
            layout(location = 0) in vec3 aPosition;
            layout(location = 1) in vec3 aNormal;

            uniform mat4 uMvpMatrix;
            uniform mat4 uModelMatrix;

            out vec3 vWorldPos;
            out vec3 vNormal;

            void main() {
                vec4 worldPos = uModelMatrix * vec4(aPosition, 1.0);
                vWorldPos = worldPos.xyz;
                vNormal = mat3(uModelMatrix) * aNormal;
                gl_Position = uMvpMatrix * vec4(aPosition, 1.0);
            }
        """

        private const val FRAGMENT_SHADER = """#version 300 es
            precision mediump float;

            in vec3 vWorldPos;
            in vec3 vNormal;

            uniform vec3 uCameraPos;
            uniform vec3 uBaseColor;
            uniform vec3 uGlowColor;
            uniform vec3 uFogColor;
            uniform float uShininess;
            uniform float uEmissive;
            uniform float uAlpha;

            out vec4 fragColor;

            void main() {
                vec3 N = normalize(vNormal);
                vec3 L = normalize(vec3(0.45, 0.85, 0.35));
                vec3 V = normalize(uCameraPos - vWorldPos);
                vec3 H = normalize(L + V);

                float diff = max(dot(N, L), 0.0);
                float fill = max(dot(N, normalize(vec3(-0.6, 0.4, -0.5))), 0.0) * 0.28;
                float spec = pow(max(dot(N, H), 0.0), max(uShininess, 2.0)) * 0.55;
                float fresnel = pow(1.0 - max(dot(N, V), 0.0), 2.5);

                vec3 lit = uBaseColor * (0.28 + diff * 0.72 + fill)
                         + vec3(1.0) * spec
                         + uGlowColor * (uEmissive * 0.55 + fresnel * 0.45);

                float dist = length(uCameraPos - vWorldPos);
                float fogFactor = clamp((dist - 48.0) / 72.0, 0.0, 0.72);
                vec3 finalRgb = mix(lit, uFogColor, fogFactor);

                fragColor = vec4(finalRgb, uAlpha);
            }
        """
    }
}
