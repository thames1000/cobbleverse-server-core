package com.thamescape.cobbleverse.core.reward.currency;

import com.thamescape.cobbleverse.core.util.CommandRunner;
import com.thamescape.cobbleverse.core.util.ServerHolder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * A currency owned by another mod, driven through configurable command templates (e.g. CobbleDollars).
 * Deposits/withdrawals run the configured command as the server console; balance reads are not
 * supported. Templates are read live so config reloads take effect.
 *
 * <p>A blank template means the operation is unsupported: the provider reports failure rather than
 * silently doing nothing.
 */
public final class CommandCurrencyProvider implements CurrencyProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger("CobbleverseCore/REWARD");

    private final String currencyId;
    private final Supplier<String> depositTemplate;
    private final Supplier<String> withdrawTemplate;

    public CommandCurrencyProvider(String currencyId,
                                   Supplier<String> depositTemplate,
                                   Supplier<String> withdrawTemplate) {
        this.currencyId = currencyId;
        this.depositTemplate = depositTemplate;
        this.withdrawTemplate = withdrawTemplate;
    }

    @Override
    public String id() {
        return currencyId;
    }

    @Override
    public boolean supportsBalance() {
        return false;
    }

    @Override
    public BigDecimal balance(UUID player) {
        return BigDecimal.ZERO;
    }

    @Override
    public boolean deposit(UUID player, BigDecimal amount) {
        return run(depositTemplate.get(), player, amount, "deposit");
    }

    @Override
    public boolean withdraw(UUID player, BigDecimal amount) {
        return run(withdrawTemplate.get(), player, amount, "withdraw");
    }

    private boolean run(String template, UUID player, BigDecimal amount, String op) {
        if (template == null || template.isBlank()) {
            LOGGER.warn("Currency '{}' {} unsupported: no command template configured", currencyId, op);
            return false;
        }
        MinecraftServer server = ServerHolder.get();
        if (server == null) {
            LOGGER.warn("Cannot {} currency '{}': server not available", op, currencyId);
            return false;
        }
        String command = CommandRunner.fill(template, Map.of(
                "player", resolveName(server, player),
                "uuid", player.toString(),
                "amount", amount.toPlainString()));
        CommandRunner.runConsole(server, command);
        return true;
    }

    private static String resolveName(MinecraftServer server, UUID uuid) {
        ServerPlayerEntity online = server.getPlayerManager().getPlayer(uuid);
        if (online != null) {
            return online.getGameProfile().getName();
        }
        return server.getUserCache() != null
                ? server.getUserCache().getByUuid(uuid).map(com.mojang.authlib.GameProfile::getName)
                        .orElse(uuid.toString())
                : uuid.toString();
    }
}
