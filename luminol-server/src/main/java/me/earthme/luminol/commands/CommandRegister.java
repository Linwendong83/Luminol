package me.earthme.luminol.commands;

import me.earthme.luminol.commands.bar.BarCommand;
import me.earthme.luminol.commands.memoryleak.MemoryLeakCommand;

public class CommandRegister {
    /**
     * Register commands after config loading
     * This method is called after system configuration is fully loaded,
     * used to register commands that depend on complete configuration
     */
    public static void register() {
        new BarCommand().register();
        new MemoryLeakCommand().register();
    }
}
