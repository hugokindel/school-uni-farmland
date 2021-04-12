package com.ustudents.farmland.network;

import com.ustudents.engine.core.cli.print.Out;
import com.ustudents.engine.network.messages.Message;
import com.ustudents.farmland.Farmland;

// PROCESSED ON SERVER
public class EndTurnMessage extends Message {
    public EndTurnMessage() {

    }

    @Override
    public void process() {
        Farmland.get().getLoadedSave().endTurn();
    }

    @Override
    public boolean shouldBeHandledOnMainThread() {
        return true;
    }
}
