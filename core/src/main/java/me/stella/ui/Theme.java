package me.stella.ui;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/** Capybara's palette, shared by components and MiniMessage templates ({@code <primary>}, {@code <accent>}, ...). */
public final class Theme {

    public static final TextColor PRIMARY = TextColor.color(0xE3B98A);
    public static final TextColor ACCENT = TextColor.color(0x7FD1AE);
    public static final TextColor HIGHLIGHT = TextColor.color(0xFFE08A);
    public static final TextColor GOOD = TextColor.color(0x8BE28B);
    public static final TextColor BAD = TextColor.color(0xFF7A7A);
    public static final TextColor MUTED = NamedTextColor.GRAY;
    public static final TextColor DIM = NamedTextColor.DARK_GRAY;
    public static final TextColor BAR_FROM = TextColor.color(0xF4B860);
    public static final TextColor BAR_TO = ACCENT;

    public static final TagResolver TAGS = TagResolver.resolver(
            Placeholder.styling("primary", PRIMARY),
            Placeholder.styling("accent", ACCENT),
            Placeholder.styling("highlight", HIGHLIGHT),
            Placeholder.styling("good", GOOD),
            Placeholder.styling("bad", BAD),
            Placeholder.styling("muted", MUTED),
            Placeholder.styling("dim", DIM));

    private Theme() {
    }
}
