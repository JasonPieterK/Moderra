package com.github.retrooper.packetevents.wrapper.play.server;

import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import java.util.Optional;
import net.kyori.adventure.text.Component;

/** COMPILE-TIME SHIM (names and signatures copied from the javap -public dump of the real class); not in the jar. */
public class WrapperPlayServerTeams extends PacketWrapper<WrapperPlayServerTeams> {
    public WrapperPlayServerTeams(PacketSendEvent event) {
    }

    public Optional<ScoreBoardTeamInfo> getTeamInfo() {
        return Optional.empty();
    }

    public void setTeamInfo(ScoreBoardTeamInfo info) {
    }

    public static class ScoreBoardTeamInfo {
        public Component getDisplayName() {
            return null;
        }

        public void setDisplayName(Component name) {
        }

        public Component getPrefix() {
            return null;
        }

        public void setPrefix(Component prefix) {
        }

        public Component getSuffix() {
            return null;
        }

        public void setSuffix(Component suffix) {
        }
    }
}
