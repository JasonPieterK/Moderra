package org.bukkit; public interface World { String getName(); Environment getEnvironment(); enum Environment { NORMAL, NETHER, THE_END, CUSTOM } }
