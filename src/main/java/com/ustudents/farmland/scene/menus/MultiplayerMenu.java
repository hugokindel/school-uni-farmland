package com.ustudents.farmland.scene.menus;

import com.ustudents.engine.core.event.EventListener;

public class MultiplayerMenu extends MenuScene {
    @Override
    public void initialize() {
        String[] buttonNames = {"Rejoindre serveur"};
        String[] buttonIds = {"joinButton"};
        EventListener[] eventListeners = new EventListener[buttonNames.length];

        for (int i = 0; i < buttonNames.length; i++) {
            int j = i;
            eventListeners[i] = (dataType, data) -> {
                switch (buttonIds[j]) {
                    case "joinButton":
                        changeScene(new ServersListMenu());
                        break;
                }
            };
        }

        initializeMenu(buttonNames, buttonIds, eventListeners, false, false, true);

        super.initialize();
    }
}
