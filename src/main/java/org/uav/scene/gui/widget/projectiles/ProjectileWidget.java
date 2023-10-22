package org.uav.scene.gui.widget.projectiles;

import org.uav.config.Config;
import org.uav.model.SimulationState;
import org.uav.scene.GuiWidget;
import org.uav.scene.gui.GuiAnchorPoint;
import org.uav.scene.gui.GuiElement;
import org.uav.scene.shader.Shader;

import java.awt.image.BufferedImage;

public class ProjectileWidget implements GuiWidget {
    private final GuiElement guiElement;
    private final ProjectileLayer projectileLayer;
    private final SimulationState simulationState;


    public ProjectileWidget(BufferedImage background, SimulationState simulationState, Config config) {
        this.simulationState = simulationState;
        projectileLayer = new ProjectileLayer();
        guiElement = new GuiElement.GuiElementBuilder()
                .setPosition(1f, 0.8f, 0.6f, 1f)
                .setAnchorPoint(GuiAnchorPoint.TOP_RIGHT)
                .setScale(config.getGraphicsSettings().getGuiScale())
                .setResolution(config.getGraphicsSettings().getWindowWidth(), config.getGraphicsSettings().getWindowHeight())
                .setHidden(false)
                .addLayer(background)
                .addLayer(400, 200, projectileLayer)
                .build();
    }

    @Override
    public void draw(Shader shader) {
        guiElement.draw(shader);
    }

    public void update() {
        if(guiElement.getHidden()) return;
        projectileLayer.update(simulationState);
    }
}
