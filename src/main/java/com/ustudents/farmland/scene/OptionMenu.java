package com.ustudents.farmland.scene;

import com.ustudents.engine.Game;
import com.ustudents.engine.scene.Scene;
import com.ustudents.engine.graphic.imgui.ImGuiUtils;
import imgui.ImGui;
import imgui.flag.ImGuiCond;

public class OptionMenu extends Scene {
    @Override
    public void initialize() {

    }

    @Override
    public void update(float dt) {

    }

    @Override
    public void render() {
        ImGuiUtils.setNextWindowWithSizeCentered(300, 300, ImGuiCond.Appearing);
        ImGui.begin("Options Menu");

        if (ImGui.button("Main Menu")) {
            Game.get().getSceneManager().changeScene(MainMenu.class);
        }

        ImGui.end();
    }

    @Override
    public void destroy() {

    }
}
