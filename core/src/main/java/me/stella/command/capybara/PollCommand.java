package me.stella.command.capybara;

import java.util.Locale;
import java.util.Map;
import me.stella.Capybara;
import me.stella.CapybaraNative;
import me.stella.CapybaraScan;
import me.stella.command.suggestion.SubCommandHandler;
import me.stella.command.suggestion.SuggestionProvider;
import me.stella.command.suggestion.Suggestions;
import me.stella.command.annotations.SubCommandMeta;
import me.stella.scan.model.ScanJob;
import me.stella.ui.Messages;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Tallies every item of a storage region - no held item needed, usable from the console. */
@SubCommandMeta(root = "capybara", name = "poll", usage = "<inv|ec|vault|ah|world> [world]", minArgs = 1,
        permission = "capybara.poll", description = "Tally every item stored in a region")
final class PollCommand implements SubCommandHandler {

    private static final Map<Integer, SuggestionProvider> SUGGESTIONS = Map.of(
            0, Suggestions.of("inv", "ec", "vault", "ah", "world"),
            1, Suggestions.when(0, "world", Suggestions.worlds()));

    @Override
    public Map<Integer, SuggestionProvider> suggestions() {
        return SUGGESTIONS;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        CapybaraNative natives = CapybaraScan.get();
        ScanJob job = switch (args[0].toLowerCase(Locale.ROOT)) {
            case "inv" -> {
                Bukkit.getOnlinePlayers().forEach(Player::saveData);
                yield job("Inventory poll", "players", ScanJob.Work.of(natives::pollInventory,
                        data -> Reports.poll("Inventory Poll", "inv", data)));
            }
            case "ec" -> {
                Bukkit.getOnlinePlayers().forEach(Player::saveData);
                yield job("Ender chest poll", "players", ScanJob.Work.of(natives::pollEnderChest,
                        data -> Reports.poll("Ender Chest Poll", "ec", data)));
            }
            case "vault" -> {
                if (!CapybaraScan.supportPlayerVaults()) {
                    sender.sendMessage(Messages.pluginMissing("PlayerVaults"));
                    yield null;
                }
                yield job("Vault poll", "vault files", ScanJob.Work.of(natives::pollVault,
                        data -> Reports.poll("Vault Poll", "vault", data)));
            }
            case "ah", "auction" -> {
                if (!CapybaraScan.supportZAuctionHouse()) {
                    sender.sendMessage(Messages.pluginMissing("zAuctionHouseV3"));
                    yield null;
                }
                yield job("Auction poll", "listings", ScanJob.Work.of(natives::pollAuctionHouse,
                        data -> Reports.poll("Auction House Poll", "ah", data)));
            }
            case "world" -> worldJob(sender, natives, args);
            default -> {
                sender.sendMessage(Messages.unknownRegion(args[0]));
                yield null;
            }
        };
        if (job != null) {
            Capybara.scans().submit(sender, job);
        }
    }

    private static ScanJob worldJob(CommandSender sender, CapybaraNative natives, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Messages.usage("capybara", PollCommand.class.getAnnotation(SubCommandMeta.class)));
            return null;
        }
        World world = Bukkit.getWorld(args[1]);
        if (world == null) {
            sender.sendMessage(Messages.unknownWorld(args[1]));
            return null;
        }
        String name = world.getName();
        return new ScanJob("World poll · " + name, "all items", "regions", ScanJob.Counter.ITEM_TYPES,
                ScanJob.Work.of(progress -> natives.pollWorld(name, progress),
                        data -> Reports.poll("World Poll (" + name + ")", "world_" + name, data)));
    }

    private static ScanJob job(String title, String unit, ScanJob.Work work) {
        return new ScanJob(title, "all items", unit, ScanJob.Counter.ITEM_TYPES, work);
    }
}
