package me.ryanhamshire.GriefPrevention.command;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.Messages;
import me.ryanhamshire.GriefPrevention.PlayerData;
import me.ryanhamshire.GriefPrevention.TextMode;
import me.ryanhamshire.GriefPrevention.Visualization;
import org.spongepowered.api.command.CommandException;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.command.spec.CommandExecutor;
import org.spongepowered.api.entity.living.player.Player;

public class CommandAbandonClaim implements CommandExecutor {

    private boolean deleteTopLevelClaim;

    public CommandAbandonClaim(boolean deleteTopLevelClaim) {
        this.deleteTopLevelClaim = deleteTopLevelClaim;
    }

    @Override
    public CommandResult execute(CommandSource src, CommandContext ctx) {
        Player player;
        try {
            player = GriefPrevention.checkPlayer(src);
        } catch (CommandException e) {
            src.sendMessage(e.getText());
            return CommandResult.success();
        }
        PlayerData playerData = GriefPrevention.instance.dataStore.getPlayerData(player.getUniqueId());

        // which claim is being abandoned?
        Claim claim = GriefPrevention.instance.dataStore.getClaimAt(player.getLocation(),
                true /* ignore height */, null);
        if (claim == null) {
            GriefPrevention.sendMessage(player, TextMode.Instr, Messages.AbandonClaimMissing);
        }

        // verify ownership
        else if (claim.allowEdit(player) != null) {
            GriefPrevention.sendMessage(player, TextMode.Err, Messages.NotYourClaim);
        }

        // warn if has children and we're not explicitly deleting a top level
        // claim
        else if (claim.children.size() > 0 && !deleteTopLevelClaim) {
            GriefPrevention.sendMessage(player, TextMode.Instr, Messages.DeleteTopLevelClaim);
            return CommandResult.empty();
        }

        else {
            // delete it
            claim.removeSurfaceFluids(null);
            GriefPrevention.instance.dataStore.deleteClaim(claim, true);

            // if in a creative mode world, restore the claim area
            if (GriefPrevention.instance.creativeRulesApply(claim.getLesserBoundaryCorner())) {
                GriefPrevention.AddLogEntry(
                        player.getName() + " abandoned a claim @ " + GriefPrevention.getfriendlyLocationString(claim.getLesserBoundaryCorner()));
                GriefPrevention.sendMessage(player, TextMode.Warn, Messages.UnclaimCleanupWarning);
                GriefPrevention.instance.restoreClaim(claim, 20L * 60 * 2);
            }

            // adjust claim blocks when abandoning a top level claim
            if (claim.parent == null) {
                playerData.setAccruedClaimBlocks(
                        playerData.getAccruedClaimBlocks() - (int) Math.ceil((claim.getArea() * (1 - GriefPrevention.instance.config_claims_abandonReturnRatio))));
            }

            // tell the player how many claim blocks he has left
            int remainingBlocks = playerData.getRemainingClaimBlocks();
            GriefPrevention.sendMessage(player, TextMode.Success, Messages.AbandonSuccess, String.valueOf(remainingBlocks));

            // revert any current visualization
            Visualization.Revert(player);

            playerData.warnedAboutMajorDeletion = false;
        }

        return CommandResult.success();
    }
}
