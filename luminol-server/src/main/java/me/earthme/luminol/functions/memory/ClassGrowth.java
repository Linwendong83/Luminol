package me.earthme.luminol.functions.memory;

public record ClassGrowth(String className, long instanceDelta, long byteDelta, long currentInstances, long currentBytes) {
}

