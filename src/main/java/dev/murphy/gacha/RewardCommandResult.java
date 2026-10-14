package dev.murphy.gacha;

import net.minecraft.commands.CommandResultCallback;

/** Identifies reward command callbacks so a replaced callback can retain the same outcome. */
public final class RewardCommandResult implements CommandResultCallback {
    private final boolean[] succeeded;
    private final CommandResultCallback delegate;

    public RewardCommandResult() { this(new boolean[1], CommandResultCallback.EMPTY); }
    private RewardCommandResult(boolean[] succeeded, CommandResultCallback delegate) {
        this.succeeded = succeeded; this.delegate = delegate;
    }
    public RewardCommandResult preserving(CommandResultCallback delegate) {
        return new RewardCommandResult(succeeded, delegate);
    }
    @Override public void onResult(boolean success, int value) {
        if (success) succeeded[0] = true;
        delegate.onResult(success, value);
    }
    public boolean succeeded() { return succeeded[0]; }
}
