package fr.geomtech.universegate;

import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;
import org.mockito.MockedStatic;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

/** Registers mod blocks before vanilla freezes its registries, as a loader would. */
public final class TestMinecraftBootstrap implements LauncherSessionListener {

    @Override
    public void launcherSessionOpened(LauncherSession session) {
        SharedConstants.tryDetectVersion();
        try (MockedStatic<Bootstrap> bootstrap = mockStatic(Bootstrap.class, CALLS_REAL_METHODS)) {
            // Preparing the registry hook initializes BuiltInRegistries before bootStrap is entered.
            bootstrap.when(() -> Bootstrap.checkBootstrapCalled(any())).thenAnswer(invocation -> null);
            try (MockedStatic<BuiltInRegistries> registries = mockStatic(BuiltInRegistries.class, invocation ->
                    invocation.getMethod().getName().equals("bootStrap") ? null : invocation.callRealMethod())) {
                registries.when(BuiltInRegistries::bootStrap).thenAnswer(invocation -> {
                    ModBlocks.register();
                    return invocation.callRealMethod();
                });
                Bootstrap.bootStrap();
            }
        }
    }
}
