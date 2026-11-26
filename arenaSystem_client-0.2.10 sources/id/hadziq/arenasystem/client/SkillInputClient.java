package id.hadziq.arenasystem.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;

// $VF: Compiled from SkillInputClient.java
final class SkillInputClient {
    private static final Identifier CHANNEL = Identifier.fromNamespaceAndPath("arenasystem", "skill_input");
    private static boolean initialized;

    private SkillInputClient() {
    }

    static void initialize() {
        if (!initialized) {
            initialized = true;
            PayloadTypeRegistry.serverboundPlay()
                .register(SkillInputClient.SkillInputPayload.TYPE, SkillInputClient.SkillInputPayload.CODEC);
        }
    }

    static void send(byte action) {
        try {
            ClientPlayNetworking.send(new SkillInputClient.SkillInputPayload(SkillInputProtocol.encode(action)));
        } catch (RuntimeException exception) {
            System.err.println("[ArenaSystem] Could not send class-skill input: " + exception.getMessage());
        }
    }

    // $VF: Compiled from SkillInputClient.java
    private record SkillInputPayload(byte[] data) implements CustomPacketPayload {
        private static final Type<SkillInputClient.SkillInputPayload> TYPE = new Type<>(SkillInputClient.CHANNEL);
        private static final StreamCodec<RegistryFriendlyByteBuf, SkillInputClient.SkillInputPayload> CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeBytes(payload.data), buffer -> {
                int length = buffer.readableBytes();
                if (length > 6) {
                    buffer.skipBytes(length);
                    return new SkillInputClient.SkillInputPayload(new byte[0]);
                } else {
                    byte[] bytes = new byte[length];
                    buffer.readBytes(bytes);
                    return new SkillInputClient.SkillInputPayload(bytes);
                }
            }
        );

        private SkillInputPayload {
            if (data == null) {
                throw new IllegalArgumentException("Skill input payload cannot be null.");
            }

            data = (byte[])data.clone();
        }

        public byte[] data() {
            return (byte[])this.data.clone();
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
