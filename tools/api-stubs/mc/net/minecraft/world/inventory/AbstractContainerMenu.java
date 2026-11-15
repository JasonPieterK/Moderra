package net.minecraft.world.inventory; public abstract class AbstractContainerMenu {
  public void clicked(int a, int b, ClickType t, net.minecraft.world.entity.player.Player p){} public void clicked(int a, int b, ContainerInput t, net.minecraft.world.entity.player.Player p){}
  public final java.util.List<Slot> slots=null;
  public void sendAllDataToRemote(){} public void broadcastChanges(){} public void setCarried(net.minecraft.world.item.ItemStack s){}
  public abstract net.minecraft.world.item.ItemStack quickMoveStack(net.minecraft.world.entity.player.Player p, int i);
  public void removed(net.minecraft.world.entity.player.Player p){} }
