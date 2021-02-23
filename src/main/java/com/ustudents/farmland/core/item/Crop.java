package com.ustudents.farmland.core.item;

import com.ustudents.engine.core.json.annotation.JsonSerializable;
import com.ustudents.engine.core.json.annotation.JsonSerializableConstructor;

import java.util.Map;

@JsonSerializable
public class Crop extends Item {
	@JsonSerializable
    public Integer numberOfTurnsToGrow;
}
