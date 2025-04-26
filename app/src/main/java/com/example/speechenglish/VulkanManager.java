package com.example.speechenglish;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

public class VulkanManager {
    private static final String TAG = "VulkanManager";

    static {
        try {
            System.loadLibrary("vulkan_manager");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "加载native库失败: " + e.getMessage());
        }
    }

    private long nativeHandle;
    private static volatile VulkanManager instance; // 使用volatile关键字
    private boolean vulkanAvailable;
    private String initErrorMessage;

    private VulkanManager() {
        initVulkan();
    }

    private void initVulkan() {
        try {
            nativeHandle = nativeInit();
            if (nativeHandle != 0) {
                vulkanAvailable = nativeIsVulkanAvailable(nativeHandle);
                if (!vulkanAvailable) {
                    initErrorMessage = "Vulkan设备初始化失败,将使用CPU模式";
                    Log.w(TAG, initErrorMessage);
                } else {
                    Log.i(TAG, "Vulkan初始化成功");
                }
            } else {
                vulkanAvailable = false;
                initErrorMessage = "Vulkan实例创建失败,将使用CPU模式";
                Log.w(TAG, initErrorMessage);
            }
        } catch (Exception e) {
            vulkanAvailable = false;
            initErrorMessage = "Vulkan初始化异常: " + e.getMessage() + ", 将使用CPU模式";
            Log.e(TAG, initErrorMessage, e);
        }
    }

    public static VulkanManager getInstance() {
        if (instance == null) { // 第一次检查
            synchronized (VulkanManager.class) { // 加锁
                if (instance == null) { // 第二次检查
                    instance = new VulkanManager(); // 初始化实例
                }
            }
        }
        return instance;
    }

    public boolean isVulkanAvailable() {
        return vulkanAvailable;
    }

    public String getInitErrorMessage() {
        return initErrorMessage;
    }

    public void release() {
        if (nativeHandle != 0) {
            try {
                nativeRelease(nativeHandle);
            } catch (Exception e) {
                Log.e(TAG, "释放Vulkan资源时出错", e);
            } finally {
                nativeHandle = 0;
                vulkanAvailable = false;
            }
        }
    }

    /**
     * 检查设备是否支持Vulkan特性
     */
    public static boolean checkVulkanSupport(Context context) {
        PackageManager pm = context.getPackageManager();

        // 检查Vulkan版本支持
        if (pm.hasSystemFeature("android.hardware.vulkan.version", 0x400003)) {
            // 检查Vulkan计算着色器支持
            if (pm.hasSystemFeature("android.hardware.vulkan.compute")) {
                return true;
            } else {
                Log.w(TAG, "设备不支持Vulkan计算着色器");
            }
        } else {
            Log.w(TAG, "设备不支持Vulkan 1.1");
        }

        return false;
    }

    private native long nativeInit();
    private native boolean nativeIsVulkanAvailable(long handle);
    private native void nativeRelease(long handle);
}
