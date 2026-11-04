package dev.vexsoft.core.paper;

import dev.vexsoft.core.api.configuration.ConfigurationOwner;
import dev.vexsoft.core.api.localization.LocalizationOwner;
import dev.vexsoft.core.api.network.BackendShutdown;
import dev.vexsoft.core.api.service.cache.CacheService;
import dev.vexsoft.core.api.service.configuration.ConfigurationService;
import dev.vexsoft.core.api.service.configuration.PublishedConfigurationService;
import dev.vexsoft.core.api.service.currency.CurrencyLocalizationService;
import dev.vexsoft.core.api.service.currency.CurrencyRegistry;
import dev.vexsoft.core.api.service.globaldata.GlobalDataService;
import dev.vexsoft.core.api.service.localization.LocalizationService;
import dev.vexsoft.core.api.service.localization.LocalizedMessageService;
import dev.vexsoft.core.api.service.localization.ThemeColorService;
import dev.vexsoft.core.api.service.messaging.MessagingService;
import dev.vexsoft.core.api.service.network.AdmissionService;
import dev.vexsoft.core.api.service.network.PlayerDirectoryService;
import dev.vexsoft.core.api.service.placeholder.PlaceholderService;
import dev.vexsoft.core.api.service.player.DataService;
import dev.vexsoft.core.api.service.player.PlayerContainerService;
import dev.vexsoft.core.api.service.player.PlayerIdentityService;
import dev.vexsoft.core.api.service.player.profile.ProfileLeaseService;
import dev.vexsoft.core.api.service.registry.ServiceRegistry;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.common.admission.service.VexAdmissionService;
import dev.vexsoft.core.common.cache.service.VexCacheService;
import dev.vexsoft.core.common.configuration.service.VexConfigurationService;
import dev.vexsoft.core.common.configuration.service.VexPublishedConfigurationService;
import dev.vexsoft.core.common.currency.service.VexCurrencyLocalizationService;
import dev.vexsoft.core.common.currency.service.VexCurrencyRegistry;
import dev.vexsoft.core.common.globaldata.service.GlobalDataCoordinatorService;
import dev.vexsoft.core.common.globaldata.service.VexGlobalDataCoordinatorService;
import dev.vexsoft.core.common.globaldata.service.VexGlobalDataService;
import dev.vexsoft.core.common.identity.service.VexPlayerIdentityService;
import dev.vexsoft.core.common.localization.service.VexLocalizationService;
import dev.vexsoft.core.common.localization.service.VexLocalizedMessageService;
import dev.vexsoft.core.common.localization.service.VexThemeColorService;
import dev.vexsoft.core.common.localization.service.language.VexCorePlayerData;
import dev.vexsoft.core.common.messaging.service.MessageCodecService;
import dev.vexsoft.core.common.messaging.service.MessageTransportService;
import dev.vexsoft.core.common.messaging.service.VexMessageCodecService;
import dev.vexsoft.core.common.messaging.service.VexMessagingService;
import dev.vexsoft.core.common.placeholder.service.PlaceholderRegistryCoordinatorService;
import dev.vexsoft.core.common.placeholder.service.VexPlaceholderRegistryCoordinatorService;
import dev.vexsoft.core.common.player.service.data.PlayerDataCoordinatorService;
import dev.vexsoft.core.common.player.service.data.PlayerDataStoreService;
import dev.vexsoft.core.common.player.service.data.VexDataService;
import dev.vexsoft.core.common.player.service.data.VexPlayerContainerService;
import dev.vexsoft.core.common.service.registry.DefaultServiceRegistry;
import dev.vexsoft.core.paper.actionbar.service.ActionBarCoordinatorService;
import dev.vexsoft.core.paper.actionbar.service.VexActionBarCoordinatorService;
import dev.vexsoft.core.paper.actionbar.service.VexActionBarService;
import dev.vexsoft.core.paper.bootstrap.service.VexPluginBootstrapService;
import dev.vexsoft.core.paper.command.core.VexCoreCommand;
import dev.vexsoft.core.paper.command.core.VexCoreDebugCommand;
import dev.vexsoft.core.paper.command.core.VexCoreResetCommand;
import dev.vexsoft.core.paper.command.service.VexCommandService;
import dev.vexsoft.core.paper.currency.command.VexCoreCurrencyCommand;
import dev.vexsoft.core.paper.dialogs.service.VexDialogService;
import dev.vexsoft.core.paper.directory.service.PlayerDirectoryCoordinatorService;
import dev.vexsoft.core.paper.directory.service.VexPlayerDirectoryCoordinatorService;
import dev.vexsoft.core.paper.directory.service.VexPlayerDirectoryService;
import dev.vexsoft.core.paper.directory.service.messaging.VexPlayerDirectoryListResponseHandler;
import dev.vexsoft.core.paper.directory.service.messaging.VexPlayerDirectoryResponseHandler;
import dev.vexsoft.core.paper.effect.service.VexItemCollectionEffectService;
import dev.vexsoft.core.paper.interactiveui.command.VexCoreInteractiveUiCommand;
import dev.vexsoft.core.paper.interactiveui.service.InteractiveUiCoordinatorService;
import dev.vexsoft.core.paper.interactiveui.service.InteractiveUiDemoService;
import dev.vexsoft.core.paper.interactiveui.service.VexInteractiveUiDemoService;
import dev.vexsoft.core.paper.interactiveui.service.VexInteractiveUiService;
import dev.vexsoft.core.paper.inventory.service.VexInventoryListener;
import dev.vexsoft.core.paper.inventory.service.VexInventoryService;
import dev.vexsoft.core.paper.items.service.ItemService;
import dev.vexsoft.core.paper.items.service.VexItemService;
import dev.vexsoft.core.paper.listener.VexPlayerLifecycleListener;
import dev.vexsoft.core.paper.listeners.listener.VexListenerService;
import dev.vexsoft.core.paper.localization.LocalizationResourceScanner;
import dev.vexsoft.core.paper.localization.command.VexCoreLanguageCommand;
import dev.vexsoft.core.paper.localization.command.VexCoreLocalizationCommand;
import dev.vexsoft.core.paper.localization.service.editor.LocalizationEditorUiService;
import dev.vexsoft.core.paper.localization.service.editor.VexLocalizationEditorUiService;
import dev.vexsoft.core.paper.messages.service.VexSendMessageService;
import dev.vexsoft.core.paper.messaging.service.ProxyPingService;
import dev.vexsoft.core.paper.messaging.service.VexPaperMessageTransportService;
import dev.vexsoft.core.paper.messaging.service.VexProxyPingResponseHandler;
import dev.vexsoft.core.paper.messaging.service.VexProxyPingService;
import dev.vexsoft.core.paper.module.DialogModule;
import dev.vexsoft.core.paper.module.GameplayModule;
import dev.vexsoft.core.paper.module.ItemModule;
import dev.vexsoft.core.paper.module.LocalizationModule;
import dev.vexsoft.core.paper.module.MobModule;
import dev.vexsoft.core.paper.module.ModuleManager;
import dev.vexsoft.core.paper.module.NmsModule;
import dev.vexsoft.core.paper.module.PacketModule;
import dev.vexsoft.core.paper.module.PlatformModule;
import dev.vexsoft.core.paper.module.PlayerModule;
import dev.vexsoft.core.paper.network.service.VexBackendPresenceService;
import dev.vexsoft.core.paper.network.service.VexServerIdentityService;
import dev.vexsoft.core.paper.packets.service.block.BlockDamageOverlayPacketService;
import dev.vexsoft.core.paper.packets.service.block.VexBlockDamageOverlayPacketService;
import dev.vexsoft.core.paper.packets.service.display.BlockDisplayPacketService;
import dev.vexsoft.core.paper.packets.service.display.DisplayPassengerPacketService;
import dev.vexsoft.core.paper.packets.service.display.ItemDisplayPacketService;
import dev.vexsoft.core.paper.packets.service.display.TextDisplayPacketService;
import dev.vexsoft.core.paper.packets.service.display.VexBlockDisplayPacketService;
import dev.vexsoft.core.paper.packets.service.display.VexDisplayPassengerPacketService;
import dev.vexsoft.core.paper.packets.service.display.VexItemDisplayPacketService;
import dev.vexsoft.core.paper.packets.service.display.VexTextDisplayPacketService;
import dev.vexsoft.core.paper.packets.service.effect.LightningPacketService;
import dev.vexsoft.core.paper.packets.service.effect.MobGlowPacketService;
import dev.vexsoft.core.paper.packets.service.effect.MobHitPacketService;
import dev.vexsoft.core.paper.packets.service.effect.VexLightningPacketService;
import dev.vexsoft.core.paper.packets.service.effect.VexMobGlowPacketService;
import dev.vexsoft.core.paper.packets.service.effect.VexMobHitPacketService;
import dev.vexsoft.core.paper.packets.service.interaction.InteractableHologramService;
import dev.vexsoft.core.paper.packets.service.interaction.InteractionPacketService;
import dev.vexsoft.core.paper.packets.service.interaction.VexInteractableHologramService;
import dev.vexsoft.core.paper.packets.service.interaction.VexInteractionPacketService;
import dev.vexsoft.core.paper.packets.service.item.FakeItemMetaService;
import dev.vexsoft.core.paper.packets.service.item.VexFakeItemMetaService;
import dev.vexsoft.core.paper.packets.service.player.CameraPacketService;
import dev.vexsoft.core.paper.packets.service.player.PlayerAnimationPacketService;
import dev.vexsoft.core.paper.packets.service.player.PlayerDummyService;
import dev.vexsoft.core.paper.packets.service.player.SkinService;
import dev.vexsoft.core.paper.packets.service.player.VexCameraPacketService;
import dev.vexsoft.core.paper.packets.service.player.VexPlayerAnimationPacketService;
import dev.vexsoft.core.paper.packets.service.player.VexPlayerDummyListener;
import dev.vexsoft.core.paper.packets.service.player.VexPlayerDummyService;
import dev.vexsoft.core.paper.packets.service.player.VexSkinService;
import dev.vexsoft.core.paper.performance.listener.VexPerformanceBossBarListener;
import dev.vexsoft.core.paper.performance.service.VexPerformanceBossBarService;
import dev.vexsoft.core.paper.performance.service.VexServerPerformanceService;
import dev.vexsoft.core.paper.placeholder.service.VexPaperPlaceholderService;
import dev.vexsoft.core.paper.placeholder.service.VexPlaceholderApiBridgeService;
import dev.vexsoft.core.paper.player.service.VexPaperPlayerService;
import dev.vexsoft.core.paper.plugin.VexLogger;
import dev.vexsoft.core.paper.profile.listener.ProfileItemListener;
import dev.vexsoft.core.paper.profile.listener.ProfileReadinessListener;
import dev.vexsoft.core.paper.profile.service.VexProfileSessionService;
import dev.vexsoft.core.paper.profile.service.VexProfileWorldItemService;
import dev.vexsoft.core.paper.reward.service.VexRewardNotificationService;
import dev.vexsoft.core.paper.reward.service.VexRewardToastService;
import dev.vexsoft.core.paper.scheduler.service.VexScheduleService;
import dev.vexsoft.core.paper.screenui.ScreenUiVersions;
import dev.vexsoft.core.paper.screenui.command.VexCoreUiCommand;
import dev.vexsoft.core.paper.screenui.listener.VexScreenUiListener;
import dev.vexsoft.core.paper.screenui.service.ScreenUiCoordinatorService;
import dev.vexsoft.core.paper.screenui.service.VexScreenUiCoordinatorService;
import dev.vexsoft.core.paper.screenui.service.VexScreenUiService;
import dev.vexsoft.core.paper.screenui.version.ScreenUiVersionDefinition;
import dev.vexsoft.core.paper.service.actionbar.ActionBarService;
import dev.vexsoft.core.paper.service.bootstrap.PluginBootstrapService;
import dev.vexsoft.core.paper.service.commands.CommandService;
import dev.vexsoft.core.paper.service.dialogs.DialogService;
import dev.vexsoft.core.paper.service.effect.ItemCollectionEffectService;
import dev.vexsoft.core.paper.service.interactiveui.InteractiveUiService;
import dev.vexsoft.core.paper.service.inventory.InventoryService;
import dev.vexsoft.core.paper.service.listeners.ListenerService;
import dev.vexsoft.core.paper.service.messages.SendMessageService;
import dev.vexsoft.core.paper.service.network.BackendPresenceService;
import dev.vexsoft.core.paper.service.network.ServerIdentityService;
import dev.vexsoft.core.paper.service.performance.PerformanceBossBarService;
import dev.vexsoft.core.paper.service.performance.ServerPerformanceService;
import dev.vexsoft.core.paper.service.placeholder.PlaceholderApiBridgeService;
import dev.vexsoft.core.paper.service.platform.PlatformService;
import dev.vexsoft.core.paper.service.players.PaperPlayerService;
import dev.vexsoft.core.paper.service.profile.ProfileSessionService;
import dev.vexsoft.core.paper.service.profile.ProfileWorldItemService;
import dev.vexsoft.core.paper.service.reward.RewardNotificationService;
import dev.vexsoft.core.paper.service.reward.RewardToastService;
import dev.vexsoft.core.paper.service.scheduler.ScheduleService;
import dev.vexsoft.core.paper.service.screenui.ScreenUiService;
import dev.vexsoft.core.paper.service.sidebar.SidebarService;
import dev.vexsoft.core.paper.service.signals.SignalService;
import dev.vexsoft.core.paper.service.teleport.PlayerTeleportService;
import dev.vexsoft.core.paper.service.teleport.TransferDisplayService;
import dev.vexsoft.core.paper.service.world.WorldService;
import dev.vexsoft.core.paper.sidebar.service.SidebarCoordinatorService;
import dev.vexsoft.core.paper.sidebar.service.VexSidebarCoordinatorService;
import dev.vexsoft.core.paper.sidebar.service.VexSidebarService;
import dev.vexsoft.core.paper.signals.service.SignalRegistryService;
import dev.vexsoft.core.paper.signals.service.VexSignalRegistryService;
import dev.vexsoft.core.paper.signals.service.VexSignalService;
import dev.vexsoft.core.paper.teleport.service.TeleportCoordinatorService;
import dev.vexsoft.core.paper.teleport.service.VexPlayerTeleportService;
import dev.vexsoft.core.paper.teleport.service.VexTeleportCoordinatorService;
import dev.vexsoft.core.paper.teleport.service.VexTransferDisplayService;
import dev.vexsoft.core.paper.teleport.service.messaging.VexTeleportAbortHandler;
import dev.vexsoft.core.paper.teleport.service.messaging.VexTeleportArrivalHandler;
import dev.vexsoft.core.paper.teleport.service.messaging.VexTeleportCompletionHandler;
import dev.vexsoft.core.paper.teleport.service.messaging.VexTeleportPreparationHandler;
import dev.vexsoft.core.paper.vexui.command.VexUICommand;
import dev.vexsoft.core.paper.vexui.listener.VexUIListener;
import dev.vexsoft.core.paper.vexui.service.VexUIRegistration;
import dev.vexsoft.core.paper.world.service.VexWorldService;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.NonNull;

public final class VexCorePlugin extends JavaPlugin implements ConfigurationOwner, LocalizationOwner {

    private DefaultServiceRegistry services;
    private VexServiceRegistry coreServices;
    private ModuleManager modules;
    private VexLogger logger;
    private ScheduledTask playerAutosaveTask;
    private boolean initialized;
    private long startupNanos;

    @Override
    public void onLoad() {
        long loadStartedAt = System.nanoTime();

        services = new DefaultServiceRegistry();
        getServer().getServicesManager().register(ServiceRegistry.class, services, this, ServicePriority.Normal);
        coreServices = services.scoped(this);
        modules = new ModuleManager(coreServices);
        modules.enable(new PlatformModule());
        coreServices.register(ScheduleService.class, VexScheduleService.class);
        coreServices.register(ActionBarCoordinatorService.class, VexActionBarCoordinatorService.class);
        coreServices.register(ActionBarService.class, VexActionBarService.class);
        coreServices.register(SidebarCoordinatorService.class, VexSidebarCoordinatorService.class);
        coreServices.register(SidebarService.class, VexSidebarService.class);
        coreServices.register(ListenerService.class, VexListenerService.class);
        coreServices.register(CacheService.class, VexCacheService.class);
        coreServices.register(
            PlaceholderRegistryCoordinatorService.class,
            VexPlaceholderRegistryCoordinatorService.class
        );
        coreServices.register(PlaceholderService.class, VexPaperPlaceholderService.class);
        coreServices.register(SignalRegistryService.class, VexSignalRegistryService.class);
        coreServices.register(SignalService.class, VexSignalService.class);
        coreServices.register(ConfigurationService.class, VexConfigurationService.class);
        coreServices.register(ThemeColorService.class, VexThemeColorService.class);
        coreServices.register(MessageCodecService.class, VexMessageCodecService.class);
        coreServices.registerQueuedServices();
        modules.enable(new PlayerModule(this));
        coreServices.register(MessageTransportService.class, VexPaperMessageTransportService.class);
        coreServices.register(ServerIdentityService.class, VexServerIdentityService.class);
        // Gameplay rewards require native profile sessions during their own service registration phase.
        coreServices.register(AdmissionService.class, VexAdmissionService.class);
        coreServices.register(TransferDisplayService.class, VexTransferDisplayService.class);
        coreServices.register(ProfileSessionService.class, VexProfileSessionService.class);
        coreServices.register(PublishedConfigurationService.class, VexPublishedConfigurationService.class);
        coreServices.register(GlobalDataCoordinatorService.class, VexGlobalDataCoordinatorService.class);
        coreServices.register(GlobalDataService.class, VexGlobalDataService.class);
        coreServices.register(PlayerIdentityService.class, VexPlayerIdentityService.class);
        coreServices.register(PlaceholderApiBridgeService.class, VexPlaceholderApiBridgeService.class);
        coreServices.register(DataService.class, VexDataService.class);
        coreServices.register(PlayerContainerService.class, VexPlayerContainerService.class);
        coreServices.registerQueuedServices();
        modules.enable(new LocalizationModule());
        modules.enable(new GameplayModule());
        // Commands are bound after dependent plugins install their own owner-scoped currency facades.
        coreServices.register(CurrencyRegistry.class, VexCurrencyRegistry.class);
        coreServices.register(CurrencyLocalizationService.class, VexCurrencyLocalizationService.class);
        modules.enable(new PacketModule(this));
        modules.enable(new NmsModule(this));
        modules.enable(new DialogModule());
        modules.enable(new ItemModule(this));
        coreServices.register(LocalizationService.class, VexLocalizationService.class);
        coreServices.register(LocalizedMessageService.class, VexLocalizedMessageService.class);
        coreServices.register(SendMessageService.class, VexSendMessageService.class);
        coreServices.register(MessagingService.class, VexMessagingService.class);
        coreServices.register(PlayerDirectoryCoordinatorService.class, VexPlayerDirectoryCoordinatorService.class);
        coreServices.register(PlayerDirectoryService.class, VexPlayerDirectoryService.class);
        coreServices.register(WorldService.class, VexWorldService.class);
        coreServices.register(BackendPresenceService.class, VexBackendPresenceService.class);
        coreServices.register(ProfileWorldItemService.class, VexProfileWorldItemService.class);
        coreServices.register(TeleportCoordinatorService.class, VexTeleportCoordinatorService.class);
        coreServices.register(PlayerTeleportService.class, VexPlayerTeleportService.class);
        coreServices.register(ProxyPingService.class, VexProxyPingService.class);
        coreServices.register(ServerPerformanceService.class, VexServerPerformanceService.class);
        coreServices.register(PerformanceBossBarService.class, VexPerformanceBossBarService.class);
        coreServices.register(CommandService.class, VexCommandService.class);
        coreServices.register(ScreenUiCoordinatorService.class, VexScreenUiCoordinatorService.class);
        coreServices.register(ScreenUiVersionDefinition.class, ScreenUiVersions.select());
        coreServices.register(ScreenUiService.class, VexScreenUiService.class);
        coreServices.register(InteractiveUiService.class, VexInteractiveUiService.class);
        coreServices.register(InteractiveUiDemoService.class, VexInteractiveUiDemoService.class);
        coreServices.register(DialogService.class, VexDialogService.class);
        VexUIRegistration.core(coreServices);
        coreServices.register(RewardToastService.class, VexRewardToastService.class);
        coreServices.register(RewardNotificationService.class, VexRewardNotificationService.class);
        coreServices.register(InventoryService.class, VexInventoryService.class);
        coreServices.register(LocalizationEditorUiService.class, VexLocalizationEditorUiService.class);
        coreServices.register(ItemService.class, VexItemService.class);
        coreServices.register(PluginBootstrapService.class, VexPluginBootstrapService.class);
        coreServices.register(PaperPlayerService.class, VexPaperPlayerService.class);
        coreServices.register(TextDisplayPacketService.class, VexTextDisplayPacketService.class);
        coreServices.register(ItemDisplayPacketService.class, VexItemDisplayPacketService.class);
        coreServices.register(ItemCollectionEffectService.class, VexItemCollectionEffectService.class);
        coreServices.register(BlockDisplayPacketService.class, VexBlockDisplayPacketService.class);
        coreServices.register(SkinService.class, VexSkinService.class);
        coreServices.register(PlayerDummyService.class, VexPlayerDummyService.class);
        coreServices.register(CameraPacketService.class, VexCameraPacketService.class);
        coreServices.register(BlockDamageOverlayPacketService.class, VexBlockDamageOverlayPacketService.class);
        coreServices.register(InteractionPacketService.class, VexInteractionPacketService.class);
        coreServices.register(DisplayPassengerPacketService.class, VexDisplayPassengerPacketService.class);
        coreServices.register(InteractableHologramService.class, VexInteractableHologramService.class);
        coreServices.register(MobHitPacketService.class, VexMobHitPacketService.class);
        coreServices.register(PlayerAnimationPacketService.class, VexPlayerAnimationPacketService.class);
        coreServices.register(MobGlowPacketService.class, VexMobGlowPacketService.class);
        coreServices.register(LightningPacketService.class, VexLightningPacketService.class);
        coreServices.register(FakeItemMetaService.class, VexFakeItemMetaService.class);
        coreServices.registerQueuedServices();
        modules.enable(new MobModule());
        coreServices.require(MessagingService.class).register(VexProxyPingResponseHandler.class);
        coreServices.require(MessagingService.class).register(VexTeleportArrivalHandler.class);
        coreServices.require(MessagingService.class).register(VexTeleportCompletionHandler.class);
        coreServices.require(MessagingService.class).register(VexTeleportPreparationHandler.class);
        coreServices.require(MessagingService.class).register(VexTeleportAbortHandler.class);
        coreServices.require(MessagingService.class).register(VexPlayerDirectoryResponseHandler.class);
        coreServices.require(MessagingService.class).register(VexPlayerDirectoryListResponseHandler.class);
        coreServices.require(DataService.class).register(VexCorePlayerData.class);
        coreServices.require(CommandService.class).register(VexCoreCommand.class);
        coreServices.require(CommandService.class).register(VexCoreCurrencyCommand.class);
        coreServices.require(CommandService.class).register(VexCoreLanguageCommand.class);
        coreServices.require(CommandService.class).register(VexCoreDebugCommand.class);
        coreServices.require(CommandService.class).register(VexUICommand.class);
        coreServices.require(CommandService.class).register(VexCoreUiCommand.class);
        coreServices.require(CommandService.class).register(VexCoreInteractiveUiCommand.class);
        coreServices.require(CommandService.class).register(VexCoreResetCommand.class);
        coreServices.require(CommandService.class).register(VexCoreLocalizationCommand.class);
        initialized = true;
        startupNanos = System.nanoTime() - loadStartedAt;
    }

    @Override
    public void onEnable() {
        long enableStartedAt = System.nanoTime();

        if (!initialized) {
            getLogger().severe("VexCore cannot be enabled because its loading phase failed");
            getServer().getPluginManager().disablePlugin(this);

            return;
        }

        modules.startAll();
        coreServices.require(PlaceholderApiBridgeService.class).enable();
        coreServices.require(MessageTransportService.class).start();
        coreServices.require(PlayerDirectoryService.class).getOnlinePlayers();
        coreServices.require(BackendPresenceService.class).start();
        coreServices.require(ServerPerformanceService.class).start();
        coreServices.require(PerformanceBossBarService.class).start();
        coreServices.require(ListenerService.class).register(VexScreenUiListener.class, coreServices);
        coreServices.require(ListenerService.class).register(VexUIListener.class, coreServices);
        getLogger().info("Network server ID initialized as " + coreServices.require(ServerIdentityService.class)
            .getServerId()
            .value());
        coreServices.require(ListenerService.class).register(VexPerformanceBossBarListener.class, coreServices);
        PlatformService platform = services.require(PlatformService.class);
        PlayerDataCoordinatorService players = services.require(PlayerDataCoordinatorService.class);

        coreServices.require(ListenerService.class).register(VexPlayerLifecycleListener.class, coreServices);
        coreServices.require(ListenerService.class).register(ProfileReadinessListener.class, coreServices);
        coreServices.require(ListenerService.class).register(ProfileItemListener.class, coreServices);
        coreServices.require(ListenerService.class).register(VexInventoryListener.class, coreServices);
        coreServices.require(ListenerService.class).register(VexPlayerDummyListener.class, coreServices);
        long autosaveSeconds = services.require(PlayerDataStoreService.class).getPlayerAutosaveIntervalSeconds();
        playerAutosaveTask = getServer().getAsyncScheduler().runAtFixedRate(
            this,
            task -> {
                var profiles = coreServices.require(ProfileSessionService.class);
                var schedules = coreServices.require(ScheduleService.class);
                var ready = players.getLoadedPlayers().stream().filter(profiles::isReady).toList();
                long periodTicks = autosaveSeconds * 20;
                for (int index = 0; index < ready.size(); index++) {
                    var player = ready.get(index);
                    long delay = 1 + index * periodTicks / ready.size();
                    player.findPlatformPlayer(Player.class).ifPresent(platformPlayer -> schedules.runForLater(
                        platformPlayer,
                        delay,
                        () -> profiles.save(player).exceptionally(throwable -> {
                            getLogger().log(Level.SEVERE, "Unable to autosave Vex player " + player.getUniqueId(), throwable);
                            return null;
                        }),
                        null
                    ));
                }
            },
            autosaveSeconds,
            autosaveSeconds,
            TimeUnit.SECONDS
        );
        long startupMillis = TimeUnit.NANOSECONDS.toMillis(startupNanos + System.nanoTime() - enableStartedAt);

        getLogger().info("VexCore successfully enabled on " + platform.getPlatform() + " in " + startupMillis + " ms");
    }

    @Override
    public void onDisable() {
        if (coreServices != null) {
            coreServices.find(InteractiveUiCoordinatorService.class).ifPresent(InteractiveUiCoordinatorService::close);
        }
        if (playerAutosaveTask != null) {
            playerAutosaveTask.cancel();
        }

        if (services != null) {
            services.find(PlayerDataCoordinatorService.class).ifPresent(players -> {
                ProfileSessionService profiles = coreServices.require(ProfileSessionService.class);
                boolean saved = drainPlayerData(players, profiles, services.require(PlayerDataStoreService.class),
                    services.require(ProfileLeaseService.class), exception -> recoverPlayers(players, exception));
                disconnectStoppedPlayers(getServer().isStopping(), saved, getServer().getOnlinePlayers());
            });
        }

        if (modules != null) {
            if (coreServices != null) {
                coreServices.unregisterOwnedServices();
            }

            modules.disableAll();
        }

        getServer().getServicesManager().unregisterAll(this);
    }

    @Override
    public @NonNull VexLogger getLogger() {
        if (logger == null) {
            logger = new VexLogger(getName(), "<dark_gray>[<gradient:#8A2BE2:#00BFFF>VexCore</gradient>]</dark_gray> ");
        }

        return logger;
    }

    @Override
    public String getServiceOwnerName() {
        return getName();
    }

    @Override
    public Path getLocalizationDirectory() {
        return getConfigurationDirectory().resolve("languages");
    }

    @Override
    public Path getConfigurationDirectory() {
        Path pluginsDirectory = getDataFolder().toPath().toAbsolutePath().normalize().getParent();

        if (pluginsDirectory == null) {
            throw new IllegalStateException("Unable to resolve the plugins directory");
        }

        return pluginsDirectory.resolve("VexSoft").resolve(getName()).normalize();
    }

    @Override
    public Collection<String> getLocalizationResources() {
        return LocalizationResourceScanner.scan(getFile().toPath());
    }

    @Override
    public Optional<InputStream> getLocalizationResource(final String resourcePath) {
        return Optional.ofNullable(getResource(resourcePath));
    }

    @Override
    public Optional<InputStream> getConfigurationResource(final String resourcePath) {
        return Optional.ofNullable(getResource(resourcePath));
    }

    @Override
    public String getMessagePrefixKey() {
        return "general.prefix";
    }

    @Override
    public void reportLocalizationWarning(final String message, final Throwable cause) {
        reportWarning(message, cause);
    }

    @Override
    public void reportConfigurationWarning(final String message, final Throwable cause) {
        reportWarning(message, cause);
    }

    static void disconnectStoppedPlayers(
        final boolean stopping, final boolean saved, final Collection<? extends Player> online
    ) {
        if (!stopping || !saved) {
            return;
        }
        // Native shutdown disconnects after plugins have closed. Signal the proxy while Core is available,
        // after persistence, so fallback never depends on a request to this stopping backend.
        List.copyOf(online).forEach(player -> player.kick(BackendShutdown.reason(), PlayerKickEvent.Cause.PLUGIN));
    }

    static boolean drainPlayerData(
        final PlayerDataCoordinatorService players,
        final ProfileSessionService profiles,
        final PlayerDataStoreService storage,
        final ProfileLeaseService leases,
        final Consumer<Throwable> recovery
    ) {
        CompletableFuture<Void> drain;
        try {
            drain = CompletableFuture.allOf(players.getLoadedPlayers().stream()
                .map(profiles::retire).toArray(CompletableFuture[]::new));
        } catch (RuntimeException failure) {
            drain = CompletableFuture.failedFuture(failure);
        }
        drain = drain.thenCompose(ignored -> players.saveAll())
            .thenCompose(ignored -> storage.getStore().checkpoint())
            .orTimeout(30L, TimeUnit.SECONDS);
        CompletableFuture<Void> recovered = drain.handleAsync((ignored, exception) -> {
            if (exception != null) {
                recovery.accept(exception);
            }
            return null;
        });
        storage.drainBeforeClose(recovered);
        leases.drainBeforeClose(drain);
        players.drainBeforeClose(recovered);

        // Paper closes the plugin JAR after onDisable returns. Keep its classes and services available to the drain.
        recovered.join();
        return !drain.isCompletedExceptionally();
    }

    private void recoverPlayers(final PlayerDataCoordinatorService players, final Throwable exception) {
        getLogger().log(Level.SEVERE, "Unable to save every VexPlayer during shutdown", exception);
        Path recovery = getDataFolder().toPath()
            .resolve("player-recovery")
            .resolve(Instant.now().toString().replace(':', '-'));

        try {
            players.exportRecovery(recovery);
            getLogger().severe("Dirty player recovery snapshots written to " + recovery
                + ". Review these files before allowing affected players to reconnect.");
        } catch (RuntimeException recoveryFailure) {
            getLogger().log(Level.SEVERE, "Unable to write player recovery snapshots", recoveryFailure);
        }
    }

    private void reportWarning(final String message, final Throwable cause) {
        if (cause == null) {
            getLogger().warning(message);
        } else {
            getLogger().log(Level.WARNING, message, cause);
        }
    }
}
