/**
 * <a href="https://github.com/SpongePowered/Configurate">Configurate</a> loaders and serializers for NBT
 * (Named Binary Tag) using <a href="https://github.com/KyoriPowered/adventure">Kyori Adventure NBT</a>.
 *
 * @provides org.spongepowered.configurate.loader.ConfigurationFormat
 */
module dev.hboyd.configurateNBT {
    requires transitive net.kyori.adventure;
    requires transitive net.kyori.adventure.nbt;
    requires transitive net.kyori.option;
    requires transitive net.kyori.examination.api;
    requires transitive org.jetbrains.annotations;
    requires transitive org.jspecify;
    requires transitive org.checkerframework.checker.qual;
    requires transitive org.spongepowered.configurate;

    exports dev.hboyd.configurateNBT;
    exports dev.hboyd.configurateNBT.serializer;
}