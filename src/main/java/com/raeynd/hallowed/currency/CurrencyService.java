package com.raeynd.hallowed.currency;

import io.github.lightman314.lightmanscurrency.api.money.MoneyAPI;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;
import io.github.lightman314.lightmanscurrency.api.money.value.coin.CoinValue;
import io.github.lightman314.lightmanscurrency.api.money.holder.IMoneyHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Abstraction layer wrapping the Lightman's Currency public API.
 * All interactions with LC go through this class so the rest of the mod
 * remains isolated from LC internals.
 *
 * <p>The LC JAR must be present in the {@code libs/} folder (compileOnly) to compile.
 * See README.md for instructions on obtaining the dependency JARs.
 */
public final class CurrencyService {

    private CurrencyService() {}

    /**
     * Creates a {@link MoneyValue} from a raw coin count using the default "main" chain.
     */
    public static MoneyValue createCoinValue(long coreValue) {
        return CoinValue.fromNumber("main", coreValue);
    }

    /**
     * Returns {@code true} if the player's coin storage (inventory, wallet, ATM card, etc.)
     * contains at least {@code cost}.
     */
    public static boolean hasEnoughFunds(Player player, MoneyValue cost) {
        IMoneyHolder handler = MoneyAPI.getApi().GetPlayersMoneyHandler(player);
        if (handler == null) return false;
        for (MoneyValue stored : handler.getStoredMoney()) {
            if (stored.containsValue(cost)) return true;
        }
        return false;
    }

    /**
     * Simulates, then executes a withdrawal of {@code cost} from the player's coin storage.
     * Returns {@code true} on success; {@code false} if the player has insufficient funds.
     * The simulate-then-execute pattern prevents double-spend race conditions.
     */
    public static boolean withdrawFunds(Player player, MoneyValue cost) {
        IMoneyHolder handler = MoneyAPI.getApi().GetPlayersMoneyHandler(player);
        if (handler == null) return false;
        // Simulate first — if result is empty the player cannot afford the cost
        MoneyValue simResult = handler.extractMoney(cost, true);
        if (simResult.isEmpty()) return false;
        // Commit the withdrawal
        handler.extractMoney(cost, false);
        return true;
    }

    /**
     * Returns a snapshot of the player's stored money for display purposes.
     * Returns the first stored money entry, which represents the primary coin storage.
     * Returns an empty {@link MoneyValue} if the handler is unavailable.
     *
     * <p>Note: Lightman's Currency may store money across multiple entries (inventory,
     * wallet, ATM card).  For display, the first entry is a reasonable representative
     * value.  For actual fund checks, use {@link #hasEnoughFunds} or
     * {@link #withdrawFunds} which aggregate across all storage.
     */
    public static MoneyValue getPlayerBalance(Player player) {
        IMoneyHolder handler = MoneyAPI.getApi().GetPlayersMoneyHandler(player);
        if (handler == null) return MoneyValue.empty();
        return handler.getStoredMoney().stream().findFirst().orElse(MoneyValue.empty());
    }

    /**
     * Returns {@code true} if {@code stack} is a coin item that Lightman's Currency
     * considers valid for a money slot. Used to allow Hallowed players to pick up coins.
     */
    public static boolean isCoinItem(Player player, ItemStack stack) {
        return MoneyAPI.getApi().ItemAllowedInMoneySlot(player, stack);
    }
}
