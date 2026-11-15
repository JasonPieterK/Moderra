#!/usr/bin/env python3
"""Finds the mapped Minecraft / loader jars in the Gradle caches and writes `javap -public` dumps of the classes listed below.
Usage: dump-mc-api.py <mc-version> <outdir> [extra search dir ...]   (python3 stdlib only)"""
import os, subprocess, sys, zipfile

CLASSES = """
net.minecraft.world.inventory.ChestMenu
net.minecraft.world.inventory.MenuType
net.minecraft.world.inventory.AbstractContainerMenu
net.minecraft.world.inventory.ClickType
net.minecraft.world.inventory.Slot
net.minecraft.world.SimpleContainer
net.minecraft.world.Container
net.minecraft.world.MenuProvider
net.minecraft.world.SimpleMenuProvider
net.minecraft.world.item.ItemStack
net.minecraft.world.item.Items
net.minecraft.core.component.DataComponents
net.minecraft.core.component.DataComponentType
net.minecraft.network.chat.Component
net.minecraft.server.level.ServerPlayer
net.minecraft.world.entity.player.Player
net.minecraft.network.protocol.game.ServerboundSignUpdatePacket
net.minecraft.network.protocol.game.ServerboundSetCommandBlockPacket
net.minecraft.world.level.BaseCommandBlock
net.minecraft.world.level.block.entity.CommandBlockEntity
net.minecraft.server.level.ServerLevel
net.minecraft.world.level.Level
net.minecraft.resources.ResourceKey
net.minecraft.resources.ResourceLocation
net.minecraft.resources.Identifier
net.minecraft.server.network.ServerGamePacketListenerImpl
net.minecraft.server.network.ServerCommonPacketListenerImpl
net.minecraft.network.Connection
net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket
net.minecraft.network.protocol.common.custom.CustomPacketPayload
net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
net.fabricmc.fabric.api.networking.v1.PacketSender
net.fabricmc.fabric.api.event.player.UseBlockCallback
net.minecraftforge.event.ServerChatEvent
net.minecraftforge.event.network.CustomPayloadEvent
net.minecraftforge.network.ChannelBuilder
net.minecraftforge.network.SimpleChannel
net.minecraftforge.network.PacketDistributor
net.minecraftforge.network.Channel
net.minecraftforge.network.NetworkEvent
net.minecraftforge.event.entity.player.PlayerEvent
net.minecraftforge.event.entity.player.PlayerContainerEvent
net.minecraftforge.eventbus.api.bus.BusGroup
net.minecraft.world.item.component.ItemLore
net.minecraft.world.item.Item
net.minecraft.world.inventory.ContainerInput
net.minecraft.world.inventory.MenuConstructor
net.minecraft.world.entity.player.Inventory
net.minecraft.world.level.block.entity.SignTextSlot
net.minecraft.world.level.block.entity.SignBlockEntity
net.minecraft.core.BlockPos
net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket
net.minecraft.network.protocol.game.ClientboundOpenSignEditorPacket
net.fabricmc.loader.api.FabricLoader
net.minecraftforge.fml.ModList
net.minecraft.world.item.component.ItemLore
net.minecraft.core.component.DataComponents
net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
net.minecraft.world.level.block.entity.SignText
net.minecraft.world.inventory.AnvilMenu
net.minecraft.world.inventory.ItemCombinerMenu
net.minecraft.network.protocol.game.ServerboundRenameItemPacket
net.minecraft.world.level.block.Blocks
net.minecraft.world.level.block.Block
net.minecraft.world.entity.Entity
net.minecraft.world.level.block.state.BlockState
net.minecraft.world.level.block.state.BlockBehaviour$BlockStateBase
net.minecraft.nbt.CompoundTag
net.minecraft.world.level.block.entity.BlockEntity
net.minecraft.core.RegistryAccess
net.minecraft.core.HolderLookup$Provider
net.minecraft.core.BlockPos
net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket
net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket$Parameters
net.minecraft.network.protocol.game.ClientboundSetScorePacket
net.minecraft.network.protocol.game.ClientboundSetObjectivePacket
net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket
net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket$Entry
net.minecraft.network.protocol.game.ClientboundSystemChatPacket
net.minecraft.network.protocol.game.ClientboundPlayerChatPacket
net.minecraft.world.scores.PlayerTeam
net.fabricmc.loader.api.ModContainer
net.fabricmc.loader.api.metadata.ModMetadata
eu.pb4.placeholders.api.Placeholders
eu.pb4.placeholders.api.PlaceholderHandler
eu.pb4.placeholders.api.PlaceholderResult
eu.pb4.placeholders.api.PlaceholderContext
""".split()

MARK = "net/minecraft/world/inventory/ChestMenu.class"


def candidate_jars(roots):
    for root in roots:
        for dp, dn, fn in os.walk(root):
            for f in fn:
                if f.endswith(".jar") and not f.endswith(("-sources.jar", "-javadoc.jar")):
                    p = os.path.join(dp, f)
                    try:
                        if os.path.getsize(p) > 100_000:
                            yield p
                    except OSError:
                        pass


def main():
    ver, out = sys.argv[1], sys.argv[2]
    home = os.path.expanduser("~")
    roots = [r for r in [os.path.join(home, ".gradle"), os.getcwd()] + sys.argv[3:] if os.path.isdir(r)]
    os.makedirs(out, exist_ok=True)
    index, mc_jars, log = {}, [], []
    for jar in candidate_jars(roots):
        try:
            names = zipfile.ZipFile(jar).namelist()
        except Exception:
            continue
        nameset = set(names)
        if MARK in nameset:
            mc_jars.append(jar)
        for c in CLASSES:
            if c.replace(".", "/") + ".class" in nameset:
                index.setdefault(c, []).append(jar)
    log.append("Jars containing ChestMenu (mapped Minecraft candidates):")
    log += ["  " + j for j in mc_jars] or ["  NONE FOUND"]
    for c in CLASSES:
        jars = index.get(c)
        if not jars:
            log.append("MISSING  %s" % c)
            continue
        jars.sort(key=lambda j: (ver not in j, len(j)))  # prefer jars whose path names this Minecraft version
        r = subprocess.run(["javap", "-public", "-cp", jars[0], c], capture_output=True, text=True)
        with open(os.path.join(out, c + ".txt"), "w") as f:
            f.write("// jar: %s\n%s%s" % (jars[0], r.stdout, r.stderr if r.returncode else ""))
        log.append("%s  %s  (%s)" % ("OK      " if r.returncode == 0 else "JAVAP-ERR", c, jars[0]))
    # inner classes of interest (record sign packet etc.) are listed by javap in the outer dump; dump all nested names too
    with open(os.path.join(out, "_index.txt"), "w") as f:
        f.write("\n".join(log) + "\n")
    print("\n".join(log))


if __name__ == "__main__":
    main()
