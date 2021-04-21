package com.ustudents.farmland;

import com.ustudents.engine.Game;
import com.ustudents.engine.GameConfig;
import com.ustudents.engine.core.Resources;
import com.ustudents.engine.core.cli.option.annotation.Command;
import com.ustudents.engine.core.cli.print.Out;
import com.ustudents.engine.core.event.EventDispatcher;
import com.ustudents.engine.core.json.Json;
import com.ustudents.engine.core.json.JsonReader;
import com.ustudents.engine.graphic.Color;
import com.ustudents.engine.input.Action;
import com.ustudents.engine.input.Key;
import com.ustudents.engine.input.Mapping;
import com.ustudents.engine.input.MouseButton;
import com.ustudents.engine.network.NetMode;
import com.ustudents.farmland.core.Save;
import com.ustudents.farmland.core.ServerConfig;
import com.ustudents.farmland.core.item.*;
import com.ustudents.farmland.core.player.Player;
import com.ustudents.farmland.network.general.LoadSaveResponse;
import com.ustudents.farmland.scene.menus.MainMenu;
import org.joml.Vector2i;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/** The main class of the project. */
@SuppressWarnings({"unchecked", "unused"})
@Command(name = "farmland", version = "0.0.1", description = "A management game about farming.")
public class Farmland extends Game {
    public Map<String, Item> itemDatabase = new HashMap<>();

    public Map<String, Save> saves = new HashMap<>();

    public String loadedSaveId = null;

    public EventDispatcher loadedSaveChanged = new EventDispatcher();

    public FarmlandConfig config;

    // SERVER SPECIFIC
    public Map<Integer, Integer> serverPlayerIdPerClientId = new ConcurrentHashMap<>();

    public ServerConfig serverConfig;

    // CLIENT SPECIFIC
    public AtomicInteger clientPlayerId = new AtomicInteger(0);

    public AtomicBoolean clientAllPlayersPresents = new AtomicBoolean(false);

    public AtomicBoolean clientGameStarted = new AtomicBoolean(false);

    public String clientServerIp;

    public int clientServerPort;

    @Override
    protected void initialize() {
        changeIcon("ui/farmland_logo.png");
        changeCursor("ui/cursor.png");

        loadConfig();
        loadTextures();
        loadShaders();
        loadSounds();
        loadItems();
        readAllSaves();

        if (getNetMode() == NetMode.DedicatedServer || getNetMode() == NetMode.ListenServer) {
            loadOrCreateServerSave();
        }

        sceneManager.changeScene(new MainMenu());
    }

    @Override
    protected void destroy() {
        writeAllSaves();
        saveConfig();
    }

    @Override
    public void onServerStarted() {
        readServerConfig();
        server.getClientDisconnectedDispatcher().add((dataType, data) -> {
            if (serverPlayerIdPerClientId.containsKey(data.clientId)) {
                Farmland.get().getLoadedSave().players.get(serverPlayerIdPerClientId.get(data.clientId)).type = Player.Type.Robot;
                Farmland.get().getLoadedSave().players.get(serverPlayerIdPerClientId.get(data.clientId)).name += " (Robot)";
                serverPlayerIdPerClientId.remove(data.clientId);
                server.broadcast(new LoadSaveResponse(getLoadedSave()));
            }
        });
    }

    @Override
    public void onServerDestroyed() {
        writeServerConfig();
    }

    public static Farmland get() {
        return (Farmland)Game.get();
    }

    public void readAllSaves() {
        saves = new HashMap<>();

        File folder = new File(Resources.getSavesDirectoryName());
        File[] listOfFiles = folder.listFiles();

        assert listOfFiles != null;

        for (File file : listOfFiles) {
            if (file.isFile()) {
                String path = file.getPath().replace("\\", "/");
                Save save = Json.deserialize(path, Save.class);
                if (save != null) {
                    save.path = path.replace(Resources.getSavesDirectoryName() + "/", "");
                    saves.put(save.name, save);
                } else {
                    Out.printlnError("Cannot load savegame: " + path);
                }
            }
        }
    }

    public void writeAllSaves() {
        for (Save save : saves.values()) {
            Json.serialize(Resources.getSavesDirectoryName() + "/" + save.path, save);
        }
    }

    public Map<String, Save> getSaves() {
        return saves;
    }

    public Save getSaveWithFilename(String id) {
        for (Save save : saves.values()) {
            if (save.path.replace(".json", "").equals(id)) {
                return save;
            }
        }

        return null;
    }

    public Save getLoadedSave() {
        if (loadedSaveId == null) {
            return null;
        }

        return saves.get(loadedSaveId);
    }

    public void replaceLoadedSave(Save save) {
        replaceLoadedSave(save, 0);
    }

    public void replaceLoadedSave(Save save, int playerId) {
        saves.put(loadedSaveId, save);
        Farmland.get().getLoadedSave().localPlayerId = playerId;
        loadedSaveChanged.dispatch();
    }

    public void loadSave(String saveId) {
        loadSave(saveId, 0);
    }

    public void loadSave(String saveId, int playerId) {
        Farmland.get().loadedSaveId = saveId;
        Farmland.get().getLoadedSave().localPlayerId = playerId;
        loadedSaveChanged.dispatch();
    }

    public void unloadSave() {
        Farmland.get().loadedSaveId = null;
        loadedSaveChanged.dispatch();
    }

    public Map<String, Item> getItemDatabase() {
        return itemDatabase;
    }

    public Map<String, Item> getResourceDatabase() {
        return itemDatabase.values()
                .stream()
                .filter(i -> i instanceof Crop || i instanceof Animal)
                .collect(Collectors.toMap(Item::getId, Item::get));
    }

    public Item getItem(String id) {
        return itemDatabase.get(id);
    }

    public int getPlayerId(int clientId) {
        return serverPlayerIdPerClientId.get(clientId);
    }

    public List<Integer> getListOfConnectedPlayers() {
        List<Integer> list = new ArrayList<>();

        for (Map.Entry<Integer, Integer> connectedPlayer : serverPlayerIdPerClientId.entrySet()) {
            list.add(connectedPlayer.getValue());
        }

        return list;
    }

    public void setPlayerIdForClientId(int clientId, int playerId) {
        serverPlayerIdPerClientId.put(clientId, playerId);
    }

    public void serverBroadcastSave() {
        if (Game.get().getNetMode() == NetMode.DedicatedServer) {
            Farmland.get().getServer().broadcast(new LoadSaveResponse(Farmland.get().getLoadedSave()));
        }
    }

    private void readServerConfig() {
        if (new File(Resources.getDataDirectory() + "/server.json").exists()) {
            serverConfig = Json.deserialize(Resources.getDataDirectory() + "/server.json", ServerConfig.class);
        } else {
            serverConfig = new ServerConfig("Mon serveur", 1);
        }
    }

    private void writeServerConfig() {
        Json.serialize(Resources.getDataDirectory() + "/server.json", serverConfig);
    }

    private void loadItems() {
        loadItemDatabase("animals", Animal.class);
        loadItemDatabase("crops", Crop.class);
        loadItemDatabase("decorations", Decoration.class);
        loadItemDatabase("properties", Property.class);
    }

    private <T extends Item> void loadItemDatabase(String filename, Class<T> type) {
        List<Object> database =
                JsonReader.readArray(Resources.getDataDirectory() + "/items/" + filename + ".json");
        assert database != null;

        for (Object object : database) {
            T item = Json.deserialize((Map<String, Object>)object, type);
            assert item != null;

            itemDatabase.put(item.id, item);
        }
    }

    private void loadConfig() {
        config = Json.deserialize(Resources.getConfig().game, FarmlandConfig.class);
        if(Resources.getConfig().commands.isEmpty()){
            initializeCommands(Resources.getConfig());
        }
        reloadCustomizableCommands();
    }

    public void initializeCommands(GameConfig config){
        Map<String, Action> actions = config.commands;
        Action action;
        if(!actions.containsKey("goUp")){
            action = new Action();
            Mapping KeyW = new Mapping("keyboard");
            Mapping KeyUp = new Mapping("keyboard");
            KeyW.bindDownAction(Key.W);
            KeyUp.bindDownAction(Key.Up);
            action.addMapping(KeyW);
            action.addMapping(KeyUp);
            config.commands.put("goUp", action);
        }
        if(!actions.containsKey("goDown")){
            action = new Action();
            Mapping KeyS = new Mapping("keyboard");
            Mapping KeyDown = new Mapping("keyboard");
            KeyS.bindDownAction(Key.S);
            KeyDown.bindDownAction(Key.Down);
            action.addMapping(KeyS);
            action.addMapping(KeyDown);
            config.commands.put("goDown", action);
        }
        if(!actions.containsKey("goLeft")){
            action = new Action();
            Mapping KeyA = new Mapping("keyboard");
            Mapping KeyLeft = new Mapping("keyboard");
            KeyA.bindDownAction(Key.A);
            KeyLeft.bindDownAction(Key.Left);
            action.addMapping(KeyA);
            action.addMapping(KeyLeft);
            config.commands.put("goLeft", action);
        }
        if(!actions.containsKey("goRight")){
            action = new Action();
            Mapping KeyD = new Mapping("keyboard");
            Mapping KeyRight = new Mapping("keyboard");
            KeyD.bindDownAction(Key.D);
            KeyRight.bindDownAction(Key.Right);
            action.addMapping(KeyD);
            action.addMapping(KeyRight);
            config.commands.put("goRight", action);
        }

        if(!actions.containsKey("showTerritory")) {
            action = new Action();
            Mapping KeyCtrlG = new Mapping("keyboard");
            Mapping KeyCtrlD = new Mapping("keyboard");
            KeyCtrlG.bindDownAction(Key.LeftControl);
            KeyCtrlD.bindDownAction(Key.RightControl);
            action.addMapping(KeyCtrlG);
            action.addMapping(KeyCtrlD);
            config.commands.put("showTerritory", action);
        }

        if(!actions.containsKey("putItem")) {
            action = new Action();
            Mapping leftMouseButton = new Mapping("mouse");
            leftMouseButton.bindDownAction(MouseButton.Left);
            action.addMapping(leftMouseButton);
            config.commands.put("putItem", action);
        }

        if(!actions.containsKey("getItem")) {
            action = new Action();
            Mapping RightMouseButton = new Mapping("mouse");
            RightMouseButton.bindDownAction(MouseButton.Right);
            action.addMapping(RightMouseButton);
            config.commands.put("getItem", action);
        }

        if(!actions.containsKey("debugMenu")) {
            action = new Action();
            Mapping debug = new Mapping("keyboard");
            debug.bindPressedAction(Key.F1);
            action.addMapping(debug);
            config.commands.put("debugMenu", action);
        }

        if(!actions.containsKey("showPerfomance")) {
            action = new Action();
            Mapping showPerfomance = new Mapping("keyboard");
            showPerfomance.bindPressedAction(Key.F2);
            action.addMapping(showPerfomance);
            config.commands.put("showPerfomance", action);
        }
    }

    public void reloadCustomizableCommands(){
        Map<String, Action> actions = Resources.getConfig().commands;
        if(actions.get("goUp").getFirstBindInMapping() <= 0){
            actions.get("goUp").addFirstBindInMapping(Key.W, "down");
        }

        if(actions.get("goDown").getFirstBindInMapping() <= 0){
            actions.get("goDown").addFirstBindInMapping(Key.S, "down");
        }
        if(actions.get("goLeft").getFirstBindInMapping() <= 0){
            actions.get("goLeft").addFirstBindInMapping(Key.A, "down");
        }
        if(actions.get("goRight").getFirstBindInMapping() <= 0){
            actions.get("goRight").addFirstBindInMapping(Key.D, "down");
        }

        if(actions.get("showTerritory").getFirstBindInMapping() <= 0){
            actions.get("showTerritory").addFirstBindInMapping(Key.D, "down");
        }

        if(actions.get("putItem").getFirstBindInMapping() <= 0){
            actions.get("putItem").addFirstBindInMapping(MouseButton.Left, "down");
        }

        if(actions.get("getItem").getFirstBindInMapping() <= 0){
            actions.get("getItem").addFirstBindInMapping(MouseButton.Right, "down");
        }

        if(actions.get("debugMenu").getFirstBindInMapping() <= 0){
            actions.get("debugMenu").addFirstBindInMapping(Key.F1, "pressed");
        }

        if(actions.get("showPerfomance").getFirstBindInMapping() <= 0){
            actions.get("showPerfomance").addFirstBindInMapping(Key.F2, "pressed");
        }

    }

    private void loadTextures() {
        Resources.loadSpritesheet("animals/chicken.json");
        Resources.loadSpritesheet("animals/cow.json");
        Resources.loadSpritesheet("animals/goat.json");
        Resources.loadSpritesheet("animals/pig.json");
        Resources.loadSpritesheet("animals/sheep.json");

        Resources.loadSpritesheet("crops/corn.json");
        Resources.loadSpritesheet("crops/grapes.json");
        Resources.loadSpritesheet("crops/orange.json");
        Resources.loadSpritesheet("crops/pineapple.json");
        Resources.loadSpritesheet("crops/shuttle.json");
        Resources.loadSpritesheet("crops/strawberry.json");
        Resources.loadSpritesheet("crops/tomato.json");
        Resources.loadSpritesheet("crops/watermelon.json");

        Resources.loadSpritesheet("decoration/cities.json");
        Resources.loadSpritesheet("decoration/mountain.json");

        Resources.loadSpritesheet("property/crops.json");
        Resources.loadSpritesheet("property/fence.json");

        Resources.loadTexture("terrain/grass.png");

        Resources.loadTexture("ui/breeder.png");
        Resources.loadTexture("ui/breeder2.png");
        Resources.loadSpritesheet("ui/button_default.json");
        Resources.loadSpritesheet("ui/button_down.json");
        Resources.loadSpritesheet("ui/button_focused.json");
        Resources.loadTexture("ui/cursor.png");
        Resources.loadTexture("ui/defeat.png");
        Resources.loadTexture("ui/farmer.png");
        Resources.loadTexture("ui/farmer2.png");
        Resources.loadTexture("ui/farmer2breeder.png");
        Resources.loadTexture("ui/farmer2breeder2.png");
        Resources.loadTexture("ui/farmerbreeder.png");
        Resources.loadTexture("ui/farmerbreeder2.png");
        Resources.loadTexture("ui/farmland_logo.png");
        Resources.loadTexture("ui/farmland_title.png");
        Resources.loadTexture("ui/frame.png");
        Resources.loadTexture("ui/gold.png");
        Resources.loadSpritesheet("ui/map_cell_cursor.json");
        Resources.loadSpritesheet("ui/map_territory_indicator_white.json");
        Resources.loadTexture("ui/player.png");
        Resources.loadTexture("ui/victory.png");
        Resources.loadSpritesheet("ui/window_default.json");
    }

    private void loadShaders() {
        Resources.loadShader("spritebatch");
    }

    private void loadSounds() {
        Resources.loadSound("music/main_menu_background.ogg");
    }

    private void loadOrCreateServerSave() {
        if (!saves.containsKey("save-server.json")) {
            Save save = new Save(serverConfig.name, new Vector2i(16, 16),
                    System.currentTimeMillis(), serverConfig.numberOfBots);
            save.path = "save-server.json";
            save.capacity = serverConfig.capacity;

            for (int i = 0; i < save.capacity; i++) {
                save.addPlayer("", "", Color.RED, Player.Type.Undefined);
            }

            saves.put("save-server.json", save);
        }

        loadSave("save-server.json", 0);
    }

    private void saveConfig() {
        Resources.getConfig().game = Json.serialize(config);
    }
}
