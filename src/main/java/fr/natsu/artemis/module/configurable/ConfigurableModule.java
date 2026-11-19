package fr.natsu.artemis.module.configurable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;

import fr.natsu.artemis.Artemis;
import fr.natsu.artemis.module.serverrule.ServerRules;
import fr.natsu.artemis.net.ApolloNetwork;
import fr.natsu.artemis.net.ApolloProtos;

/**
 * Apollo's configurable settings ({@code lunarclient.apollo.configurable.v1}). Right after a player
 * registers, the Apollo plugin sends one {@code OverrideConfigurableSettingsMessage} that says, for
 * every Apollo module, whether it is enabled and the values of its client-side options
 * ({@code tnt-ticks}, the server rules, ...). Lunar mod settings ({@code lunar_client_mod}) target
 * Lunar's own mods and are only logged.
 *
 * <p>Wire format: {@code configurable_settings} (#1, repeated ConfigurableSettings), each with
 * {@code lunar_client_mod} (#1) | {@code apollo_module} (#2), {@code enable} (#3, bool) and
 * {@code properties} (#4, map&lt;string, google.protobuf.Value&gt;).</p>
 */
public final class ConfigurableModule {

    private static final String OVERRIDE = "lunarclient.apollo.configurable.v1.OverrideConfigurableSettingsMessage";

    /** Apollo module id → enabled. Absent = not told yet, treated as enabled. */
    private static final Map<String, Boolean> ENABLED = new ConcurrentHashMap<>();
    /** Apollo module id → option key → value. */
    private static final Map<String, Map<String, Object>> PROPERTIES = new ConcurrentHashMap<>();

    private ConfigurableModule() {
    }

    public static void register(ApolloNetwork network) {
        network.register(OVERRIDE, ConfigurableModule::onOverride);
        network.onDisconnect(ConfigurableModule::reset);
    }

    public static boolean isEnabled(String module) {
        return ENABLED.getOrDefault(module, Boolean.TRUE);
    }

    /** Whether the server explicitly reported the module as enabled. */
    public static boolean isExplicitlyEnabled(String module) {
        return ENABLED.getOrDefault(module, Boolean.FALSE);
    }

    public static boolean getBoolean(String module, String key, boolean fallback) {
        Object value = get(module, key);
        return value instanceof Boolean ? (Boolean) value : fallback;
    }

    public static int getInt(String module, String key, int fallback) {
        Object value = get(module, key);
        return value instanceof Double ? (int) Math.round((Double) value) : fallback;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> getList(String module, String key) {
        Object value = get(module, key);
        return value instanceof List ? (List<Object>) value : null;
    }

    public static Object get(String module, String key) {
        Map<String, Object> properties = PROPERTIES.get(module);
        return properties == null ? null : properties.get(key);
    }

    public static Map<String, Boolean> modules() {
        return ENABLED;
    }

    public static Map<String, Object> properties(String module) {
        Map<String, Object> properties = PROPERTIES.get(module);
        return properties == null ? java.util.Collections.<String, Object>emptyMap() : properties;
    }

    private static void reset() {
        ENABLED.clear();
        PROPERTIES.clear();
        ServerRules.applyNametagRange();
    }

    private static void onOverride(Any message) throws Exception {
        CodedInputStream in = message.getValue().newCodedInput();
        int tag;
        while ((tag = in.readTag()) != 0) {
            if (WireFormat.getTagFieldNumber(tag) == 1) {
                applySettings(in.readBytes());
            } else {
                in.skipField(tag);
            }
        }
        ServerRules.applyNametagRange();
    }

    private static void applySettings(ByteString data) throws Exception {
        CodedInputStream in = data.newCodedInput();
        String lunarMod = null;
        String apolloModule = null;
        boolean enable = false;
        Map<String, Object> properties = new HashMap<>();
        int tag;
        while ((tag = in.readTag()) != 0) {
            switch (WireFormat.getTagFieldNumber(tag)) {
                case 1:
                    lunarMod = in.readString();
                    break;
                case 2:
                    apolloModule = in.readString();
                    break;
                case 3:
                    enable = in.readBool();
                    break;
                case 4:
                    readProperty(in.readBytes(), properties);
                    break;
                default:
                    in.skipField(tag);
            }
        }

        if (apolloModule != null) {
            ENABLED.put(apolloModule, enable);
            PROPERTIES.computeIfAbsent(apolloModule, k -> new ConcurrentHashMap<>()).putAll(withoutNulls(properties));
            Artemis.LOGGER.info("[Configurable] module {} {} {}", apolloModule, enable ? "enabled" : "disabled",
                properties.isEmpty() ? "" : properties);
        } else if (lunarMod != null) {
            Artemis.LOGGER.info("[Configurable] Lunar mod setting ignored on Forge: {} {}", lunarMod, properties);
        }
    }

    private static Map<String, Object> withoutNulls(Map<String, Object> properties) {
        properties.values().removeIf(v -> v == null);
        return properties;
    }

    private static void readProperty(ByteString data, Map<String, Object> out) throws Exception {
        CodedInputStream in = data.newCodedInput();
        String key = null;
        Object value = null;
        int tag;
        while ((tag = in.readTag()) != 0) {
            switch (WireFormat.getTagFieldNumber(tag)) {
                case 1:
                    key = in.readString();
                    break;
                case 2:
                    value = ApolloProtos.parseValue(in.readBytes());
                    break;
                default:
                    in.skipField(tag);
            }
        }
        if (key != null) {
            out.put(key, value);
        }
    }
}
