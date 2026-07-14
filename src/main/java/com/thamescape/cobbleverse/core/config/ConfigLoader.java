package com.thamescape.cobbleverse.core.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.thamescape.cobbleverse.core.CoreConstants;
import com.thamescape.cobbleverse.core.util.error.ConfigurationException;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Supplier;

/**
 * Loads and saves JSON config files under {@code config/cobbleverse-server-core/}.
 *
 * <p>Behaviour is deliberately strict: a malformed file is never silently replaced with defaults.
 * Instead the broken file is backed up and a {@link ConfigurationException} is raised so the
 * operator sees a clear error at startup.
 */
public final class ConfigLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger("CobbleverseCore/CONFIG");
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private final Path configDir;

    public ConfigLoader() {
        this(FabricLoader.getInstance().getConfigDir().resolve(CoreConstants.CONFIG_DIR_NAME));
    }

    public ConfigLoader(Path configDir) {
        this.configDir = configDir;
    }

    public Path configDir() {
        return configDir;
    }

    /**
     * Loads {@code fileName} into an instance of {@code type}. If the file does not exist, the
     * supplied defaults are written to disk and returned.
     *
     * @param defaults supplier of a defaults instance, used when the file is absent
     */
    public <T> T loadOrCreate(String fileName, Class<T> type, Supplier<T> defaults) {
        Path file = configDir.resolve(fileName);
        try {
            Files.createDirectories(configDir);
        } catch (IOException e) {
            throw new ConfigurationException("CV-CONFIG-001",
                    "Unable to create config directory: " + configDir, e);
        }

        if (!Files.exists(file)) {
            T value = defaults.get();
            save(fileName, value);
            LOGGER.info("Created default config: {}", fileName);
            return value;
        }

        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            T value = GSON.fromJson(json, type);
            if (value == null) {
                throw new ConfigurationException("CV-CONFIG-002",
                        "Config file is empty or contains only null: " + fileName);
            }
            return value;
        } catch (JsonSyntaxException e) {
            Path backup = backupCorrupt(file);
            throw new ConfigurationException("CV-CONFIG-003",
                    "Malformed JSON in " + fileName + "; broken file backed up to "
                            + (backup == null ? "<backup failed>" : backup.getFileName()), e);
        } catch (IOException e) {
            throw new ConfigurationException("CV-CONFIG-004",
                    "Failed to read config file: " + fileName, e);
        }
    }

    /** Serializes {@code value} to {@code fileName}, creating the directory if needed. */
    public <T> void save(String fileName, T value) {
        Path file = configDir.resolve(fileName);
        try {
            Files.createDirectories(configDir);
            Files.writeString(file, GSON.toJson(value), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ConfigurationException("CV-CONFIG-005",
                    "Failed to write config file: " + fileName, e);
        }
    }

    /**
     * Copies a valid config file to {@code <name>.bak} before a migration overwrites it. Returns the
     * backup path, or {@code null} if the source does not exist.
     */
    public Path backup(String fileName) {
        Path file = configDir.resolve(fileName);
        if (!Files.exists(file)) {
            return null;
        }
        Path backup = configDir.resolve(fileName + ".bak");
        try {
            Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
            return backup;
        } catch (IOException e) {
            LOGGER.warn("Failed to back up config {}: {}", fileName, e.getMessage());
            return null;
        }
    }

    private Path backupCorrupt(Path file) {
        Path backup = file.resolveSibling(file.getFileName() + ".broken");
        try {
            Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
            return backup;
        } catch (IOException e) {
            LOGGER.warn("Failed to back up corrupt config {}: {}", file.getFileName(), e.getMessage());
            return null;
        }
    }
}
