package org.bukkit; public interface Registry<T extends Keyed> extends Iterable<T> { T get(NamespacedKey k);
 Registry<org.bukkit.potion.PotionEffectType> EFFECT = null; Registry<org.bukkit.attribute.Attribute> ATTRIBUTE = null; Registry<Sound> SOUNDS = null; }
