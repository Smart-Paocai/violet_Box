package com.violet.safe.util;

import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;

/**
 * Creates a minimal GLES2 context to read GL_RENDERER (actual GPU 名称)，
 * 避免误用 Build.HARDWARE（主板代号）。
 */
public final class GpuInfoQuery {

    private GpuInfoQuery() {
    }

    public static String queryGlRenderer() {
        EGLDisplay display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (display == EGL14.EGL_NO_DISPLAY) {
            return null;
        }
        int[] version = new int[2];
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
            return null;
        }

        int[] attribList = new int[]{
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_NONE
        };
        EGLConfig[] configs = new EGLConfig[1];
        int[] numConfigs = new int[1];
        if (!EGL14.eglChooseConfig(display, attribList, 0, configs, 0, 1, numConfigs, 0)
                || numConfigs[0] == 0) {
            EGL14.eglTerminate(display);
            return null;
        }

        int[] ctxAttrib = new int[]{
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
        };
        EGLContext context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT, ctxAttrib, 0);
        if (context == null || context == EGL14.EGL_NO_CONTEXT) {
            EGL14.eglTerminate(display);
            return null;
        }

        int[] surfAttrib = new int[]{
                EGL14.EGL_WIDTH, 16,
                EGL14.EGL_HEIGHT, 16,
                EGL14.EGL_NONE
        };
        EGLSurface surface = EGL14.eglCreatePbufferSurface(display, configs[0], surfAttrib, 0);
        if (surface == null || surface == EGL14.EGL_NO_SURFACE) {
            EGL14.eglDestroyContext(display, context);
            EGL14.eglTerminate(display);
            return null;
        }

        if (!EGL14.eglMakeCurrent(display, surface, surface, context)) {
            EGL14.eglDestroySurface(display, surface);
            EGL14.eglDestroyContext(display, context);
            EGL14.eglTerminate(display);
            return null;
        }

        String renderer = GLES20.glGetString(GLES20.GL_RENDERER);

        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
        EGL14.eglDestroySurface(display, surface);
        EGL14.eglDestroyContext(display, context);
        EGL14.eglTerminate(display);

        return renderer;
    }
}
