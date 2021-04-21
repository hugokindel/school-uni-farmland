package com.ustudents.farmland.scene.menus;

import com.ustudents.engine.audio.Sound;
import com.ustudents.engine.audio.SoundManager;
import com.ustudents.engine.core.Resources;
import com.ustudents.engine.core.event.EventListener;
import com.ustudents.engine.scene.component.audio.SoundComponent;
import com.ustudents.engine.scene.ecs.Entity;
import com.ustudents.farmland.Farmland;
import com.ustudents.farmland.scene.InGameScene;

public class SettingsMenu extends MenuScene {
    @Override
    public void initialize() {
        SoundManager currentSoundManager = Farmland.get().getSoundManager();
        String[] buttonNames = new String[4];
        String[] buttonIds = new String[4];

        if(currentSoundManager.getNoSound()){
            buttonNames[0] = Resources.getLocalizedText("activateSound");
            buttonIds[0] = "activateSound";
        }else{
            buttonNames[0] = Resources.getLocalizedText("deactivateSound");
            buttonIds[0] = "deactivateSound";
        }

        buttonNames[1] = Resources.getLocalizedText("changeLanguage", Resources.getLocalizedText("language"));
        buttonIds[1] = "changeLanguage";

        buttonNames[2] = "Commandes";
        buttonIds[2] = "commands";

        buttonNames[3] = "Retour";
        buttonIds[3] = "goBack";

        EventListener[] eventListeners = new EventListener[buttonNames.length];

        for (int i = 0; i < buttonNames.length; i++) {
            int j = i;
            eventListeners[i] = (dataType, data) -> {
                switch (buttonIds[j]) {
                    case "activateSound":
                        initializeMusic();
                        Sound musicSound = Resources.loadSound("music/main_menu_background.ogg");
                        Entity music = createEntityWithName("backgroundMusic");
                        music.keepOnLoad(true);
                        new SoundComponent(musicSound, true, true).play();
                        changeScene(new SettingsMenu());
                        break;
                    case "deactivateSound":
                        currentSoundManager.stopAll();
                        changeScene(new SettingsMenu());
                        break;
                    case "changeLanguage":
                        Resources.chooseNextLanguage();
                        changeScene(new SettingsMenu());
                        break;
                    case "commands":
                        changeScene(new CommandsMenu());
                        break;
                    case "goBack":
                        changeScene(new MainMenu());
                        break;
                }
            };
        }

        initializeMenu(buttonNames, buttonIds, eventListeners, true, false, false, false);

        super.initialize();
    }
}
