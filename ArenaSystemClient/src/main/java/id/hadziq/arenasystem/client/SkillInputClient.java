package id.hadziq.arenasystem.client;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Sends explicit class-skill actions to the Paper companion plugin.
 *
 * Unlike the old implementation, this does not simulate a vanilla hand swap.
 */
final class SkillInputClient {
    private static final Identifier CHANNEL =
            Identifier.fromNamespaceAndPath("arenasystem", "skill_input");

    private static boolean initialized;

    private SkillInputClient() {
    }

    static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        PayloadTypeRegistry.serverboundPlay().register(SkillInputPayload.TYPE, SkillInputPayload.CODEC);
    }

    static void send(byte action) {
        try {
            ClientPlayNetworking.send(new SkillInputPayload(SkillInputProtocol.encode(action)));
        } catch (RuntimeException exception) {
            System.err.println("[ArenaSystem] Could not send class-skill input: " + exception.getMessage());
        }
    }

    private record SkillInputPayload(byte[] data) implements CustomPacketPayload {
        private static final Type<SkillInputPayload> TYPE = new Type<>(CHANNEL);

        private static final StreamCodec<RegistryFriendlyByteBuf, SkillInputPayload> CODEC =
                StreamCodec.of(
                        (buffer, payload) -> buffer.writeBytes(payload.data),
                        buffer -> {
                            int length = buffer.readableBytes();
                            if (length > SkillInputProtocol.MESSAGE_BYTES) {
                                buffer.skipBytes(length);
                                return new SkillInputPayload(new byte[0]);
                            }

                            byte[] bytes = new byte[length];
                            buffer.readBytes(bytes);
                            return new SkillInputPayload(bytes);
                        });

        private SkillInputPayload {
            if (data == null) {
                throw new IllegalArgumentException("Skill input payload cannot be null.");
            }
            data = data.clone();
        }

        @Override
        public byte[] data() {
            return data.clone();
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
