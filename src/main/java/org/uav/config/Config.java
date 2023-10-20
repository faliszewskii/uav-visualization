package org.uav.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.Value;
import org.uav.input.CameraMode;

import java.util.List;

@Value
@NoArgsConstructor(force = true, access = AccessLevel.PRIVATE)
public class Config {

    BindingsConfig bindingsConfig;
    ServerSettings serverSettings;
    DroneSettings droneSettings;
    SceneSettings sceneSettings;
    GraphicsSettings graphicsSettings;
    Ports ports;

    @Value
    @NoArgsConstructor(force = true, access = AccessLevel.PRIVATE)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BindingsConfig {
        boolean generateOnStartUp;
        String source;
    }

    @Value
    @NoArgsConstructor(force = true, access = AccessLevel.PRIVATE)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ServerSettings {
        String serverAddress;
        String assetsSourceUrl;
        boolean downloadMissingAssets;
        String assetsToUse;
        int heartBeatIntervalMs;
        int serverTimoutMs;
    }

    @Value
    @NoArgsConstructor(force = true, access = AccessLevel.PRIVATE)
    public static class DroneSettings {
        String droneName;
        String droneConfig;
        CameraMode defaultCamera;
        List<String> modes;
    }

    @Value
    @NoArgsConstructor(force = true, access = AccessLevel.PRIVATE)
    public static class SceneSettings {
        boolean drawInWorldDemandedPositionalCoords;
        float sunAngleDayCycle;
        float sunAngleYearCycle;
        float[] cameraFPP;
        float[] cameraTPP;
    }

    @Value
    @NoArgsConstructor(force = true, access = AccessLevel.PRIVATE)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GraphicsSettings {
        int windowWidth;
        int windowHeight;
        FullScreenMode fullScreenMode;
        Integer monitor;
        float guiScale;
        float fov;
        boolean useGammaCorrection;
        float gammaCorrection;
        boolean useShadows;
        int shadowsTextureResolution;
        int shadowsRenderingDistance;
    }

    @Value
    @NoArgsConstructor(force = true, access = AccessLevel.PRIVATE)
    public static class Ports {
        int notifications;
        int droneRequester;
        int droneStatuses;
        int projectileStatuses;
    }
}
