package net.minecraft.network.protocol.game; public class ClientboundBlockUpdatePacket implements net.minecraft.network.protocol.Packet<Object> {
  public ClientboundBlockUpdatePacket(net.minecraft.core.BlockPos p, net.minecraft.world.level.block.state.BlockState s){}
  public ClientboundBlockUpdatePacket(net.minecraft.world.level.BlockGetter g, net.minecraft.core.BlockPos p){} }
