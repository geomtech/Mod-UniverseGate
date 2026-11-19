package fr.geomtech.universegate.platform;

/**
 * Client-only platform services. Only touch this class from client code.
 */
public final class ClientServices {

    public static final ClientPlatformHelper CLIENT = Services.load(ClientPlatformHelper.class);

    private ClientServices() {}
}
