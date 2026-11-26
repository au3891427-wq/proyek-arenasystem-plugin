package id.hadziq.arenasystem.client.mixin;

import com.mojang.blaze3d.platform.InputConstants.Key;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// $VF: Compiled from KeyMappingAccessor.java
@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {
    @Accessor("key")
    Key arenasystem$getBoundKey();
}
