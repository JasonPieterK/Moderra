package net.minecraft.server.level; public class ServerPlayer extends net.minecraft.world.entity.player.Player {
  public net.minecraft.server.network.ServerGamePacketListenerImpl connection;
  public ServerLevel level(){return null;}
  public java.util.OptionalInt openMenu(net.minecraft.world.MenuProvider p){return null;}
  public void closeContainer(){}
  public void sendSystemMessage(net.minecraft.network.chat.Component c){}
  public net.minecraft.commands.CommandSourceStack createCommandSourceStack(){return null;} }
