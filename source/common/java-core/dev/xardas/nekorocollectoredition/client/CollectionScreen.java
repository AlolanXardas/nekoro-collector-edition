package dev.xardas.nekorocollectoredition.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import org.lwjgl.glfw.GLFW;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import dev.xardas.nekorocollectoredition.CollectionMenu;
import dev.xardas.nekorocollectoredition.ItemRules;
import dev.xardas.nekorocollectoredition.NekoroCollectorEdition;
import dev.xardas.nekorocollectoredition.NekoroTab;
import dev.xardas.nekorocollectoredition.Payloads;

/**
 * Left: the points, the slot that adds a block to the collection, the convert button, the favourite and hide modes
 * and the take amount. Right: tabs, a search box, the sort order and the collection with a scroll bar, like the
 * creative inventory. Bottom: the player's inventory, where items that cannot be added are dimmed.
 */
public final class CollectionScreen extends AbstractContainerScreen<CollectionMenu> {
    private static final ResourceLocation TEXTURE = NekoroCollectorEdition.id("textures/gui/collector_table.png");
    private static final ResourceLocation SCROLLER = ResourceLocation.withDefaultNamespace("container/creative_inventory/scroller");
    private static final ResourceLocation SCROLLER_DISABLED = ResourceLocation.withDefaultNamespace("container/creative_inventory/scroller_disabled");
    private static final int GRID_X = 60, GRID_Y = 54, COLUMNS = 9, ROWS = 5;
    private static final int SCROLL_X = 226, SCROLL_Y = 54, SCROLL_HEIGHT = 88;
    private static final int DIM_COLOR = 0xB0101010;
    private static final int FAVORITE_COLOR = 0xFFFFD000;
    private static final int HIDDEN_COLOR = 0xFFD02828;
    /** The row of the points, tabs, search box and sort button. */
    private static final int TOP_ROW_Y = 17, TOP_ROW_HEIGHT = 14;

    private enum Tab { ALL, FAVORITES, HIDDEN }

    /** How the category bar divides the collection. */
    private enum Grouping { CREATIVE, SHAPE, MATERIAL }

    /** Inside the favourite / plain / hidden groups. */
    private enum Sort { CATEGORY, NAME, MOD, NEWEST }

    /** What a click on a block in the collection does. */
    private enum Mode { TAKE, FAVORITE, HIDE }

    // kept while the game runs
    private static Tab tab = Tab.ALL;
    private static Sort sort = Sort.CATEGORY;
    private static String query = "";
    private static boolean showUncollected = true;
    private static Grouping grouping = Grouping.CREATIVE;

    private Mode mode = Mode.TAKE;
    private EditBox search;
    private CycleButton amountButton, sortButton;
    private Button convertButton, favoriteButton, hideButton;
    private final List<Button> tabButtons = new ArrayList<>();
    private CategoryBar bar;
    private String shownCategory;

    private Sort tooltipSort;
    private CycleButton showButton, groupButton;
    private Grouping shownGrouping, tooltipGrouping;
    private Boolean tooltipShow, shownUncollected;
    private final List<ItemStack> shown = new ArrayList<>();
    private int shownVersion = -1;
    private String shownQuery = "";
    private Tab shownTab;
    private Sort shownSort;
    private int scrollRow;
    private boolean draggingScroll;

    public CollectionScreen(CollectionMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 246;
        imageHeight = 238;
        inventoryLabelX = CollectionMenu.INVENTORY_X;
        inventoryLabelY = CollectionMenu.INVENTORY_Y - 11;
    }

    private static String key(String name) {
        return "gui." + NekoroCollectorEdition.MOD_ID + "." + name;
    }

    @Override
    protected void init() {
        super.init();
        search = new EditBox(font, leftPos + 115, topPos + TOP_ROW_Y + 3, 74, 10, Component.translatable(key("search")));
        search.setBordered(false);
        search.setMaxLength(50);
        search.setTextColor(0xFFFFFF);
        search.setHint(Component.translatable(key("search")).withStyle(ChatFormatting.GRAY));
        search.setValue(query);
        search.setResponder(text -> query = text);
        addRenderableWidget(search);

        sortButton = addRenderableWidget(new CycleButton(leftPos + 225, topPos + TOP_ROW_Y, TOP_ROW_HEIGHT, TOP_ROW_HEIGHT, back -> {
            Sort[] all = Sort.values();
            sort = all[Math.floorMod(sort.ordinal() + (back ? -1 : 1), all.length)];
        }));
        sortButton.sortIcon = true;
        tooltipSort = null;
        // with show_all_items: show also what is not collected yet, or only the collection
        showButton = addRenderableWidget(new CycleButton(leftPos + 209, topPos + TOP_ROW_Y, TOP_ROW_HEIGHT, TOP_ROW_HEIGHT,
                back -> showUncollected = !showUncollected));
        showButton.eyeIcon = true;
        tooltipShow = null;
        // the categories: as in the creative inventory, by shape or by material
        groupButton = addRenderableWidget(new CycleButton(leftPos + 193, topPos + TOP_ROW_Y, TOP_ROW_HEIGHT, TOP_ROW_HEIGHT, back -> {
            Grouping[] all = Grouping.values();
            grouping = all[Math.floorMod(grouping.ordinal() + (back ? -1 : 1), all.length)];
            scrollRow = 0;
        }));
        groupButton.groupIcon = true;
        tooltipGrouping = null;
        shownGrouping = null;

        tabButtons.clear();
        for (Tab t : Tab.values()) {
            tabButtons.add(addRenderableWidget(Button.builder(Component.empty(), button -> {
                        tab = t;
                        scrollRow = 0;
                        updateLabels();
                    })
                    .bounds(leftPos + 59 + t.ordinal() * 18, topPos + TOP_ROW_Y, 16, TOP_ROW_HEIGHT)
                    .tooltip(Tooltip.create(Component.translatable(key("tab." + t.name().toLowerCase(Locale.ROOT) + ".tooltip"))))
                    .build()));
        }
        bar = new CategoryBar(Component.translatable(key("category.all")), NekoroCollectorEdition.id("textures/gui/category_all.png"));
        bar.init(leftPos, topPos, this::addRenderableWidget);
        bar.onSelect(() -> scrollRow = 0);
        shownVersion = -1;

        convertButton = addRenderableWidget(Button.builder(Component.translatable(key("convert")),
                        b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, CollectionMenu.BUTTON_CONVERT))
                .bounds(leftPos + 7, topPos + 56, 44, 16)
                .tooltip(Tooltip.create(Component.translatable(key("convert.tooltip"))))
                .build());
        favoriteButton = addRenderableWidget(Button.builder(Component.empty(), b -> switchMode(Mode.FAVORITE))
                .bounds(leftPos + 7, topPos + 76, 21, 16)
                .tooltip(Tooltip.create(Component.translatable(key("favorite.tooltip"))))
                .build());
        hideButton = addRenderableWidget(Button.builder(Component.empty(), b -> switchMode(Mode.HIDE))
                .bounds(leftPos + 30, topPos + 76, 21, 16)
                .tooltip(Tooltip.create(Component.translatable(key("hide.tooltip"))))
                .build());
        amountButton = addRenderableWidget(new CycleButton(leftPos + 7, topPos + 96, 44, 16, ClientCollection::nextAmount));
        amountButton.setTooltip(Tooltip.create(Component.translatable(key("amount.tooltip"))));
        updateLabels();
    }

    private void switchMode(Mode next) {
        mode = mode == next ? Mode.TAKE : next;
        updateLabels();
    }

    /** The selected tab and mode are shown in yellow. */
    private void updateLabels() {
        for (Tab t : Tab.values()) {
            Component label = Component.translatable(key("tab." + t.name().toLowerCase(Locale.ROOT) + ".short"));
            tabButtons.get(t.ordinal()).setMessage(t == tab ? label.copy().withStyle(ChatFormatting.YELLOW) : label);
        }
        favoriteButton.setMessage(Component.translatable(key("favorite.short")).withStyle(mode == Mode.FAVORITE ? ChatFormatting.YELLOW : ChatFormatting.WHITE));
        hideButton.setMessage(Component.translatable(key("hide.short")).withStyle(mode == Mode.HIDE ? ChatFormatting.YELLOW : ChatFormatting.WHITE));
        amountButton.setMessage(Component.literal("\u00d7" + ClientCollection.amountLabel(ClientCollection.amount())));
        showButton.visible = ClientCollection.showAll();
        if (!Boolean.valueOf(showUncollected).equals(tooltipShow)) {
            tooltipShow = showUncollected;
            showButton.setTooltip(Tooltip.create(Component.translatable(key("show"),
                    Component.translatable(key(showUncollected ? "show.all" : "show.collected")))));
        }
        if (tooltipGrouping != grouping) {
            tooltipGrouping = grouping;
            groupButton.setTooltip(Tooltip.create(Component.translatable(key("group"),
                    Component.translatable(key("group." + grouping.name().toLowerCase(Locale.ROOT))))));
        }
        if (tooltipSort != sort) {
            tooltipSort = sort;
            sortButton.setTooltip(Tooltip.create(Component.translatable(key("sort"),
                    Component.translatable(key("sort." + sort.name().toLowerCase(Locale.ROOT))))));
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        // the rules (with the amounts) may arrive after the window opened
        updateLabels();
    }

    private void refresh() {
        String text = query.trim().toLowerCase(Locale.ROOT);
        if (shownVersion == ClientCollection.version() && text.equals(shownQuery) && shownTab == tab && shownSort == sort
                && bar.selectedKey().equals(shownCategory) && Boolean.valueOf(showUncollected).equals(shownUncollected)
                && grouping == shownGrouping) return;
        shownUncollected = showUncollected;
        shownVersion = ClientCollection.version();
        shownQuery = text;
        shownTab = tab;
        shownSort = sort;
        record Entry(ItemStack stack, int group, int added, String id, String mod, String name, String category) { }
        List<Entry> entries = new ArrayList<>();
        List<Item> items = new ArrayList<>(ClientCollection.items());
        if (ClientCollection.showAll() && showUncollected && tab == Tab.ALL) {
            // everything that could be added, after the collection
            for (Item item : BuiltInRegistries.ITEM) {
                if (item != net.minecraft.world.item.Items.AIR && !ClientCollection.has(item)) items.add(item);
            }
        }
        // the categories show only what is in the selected tab (All, Favorites or Hidden)
        java.util.Set<Item> inTabs = new java.util.HashSet<>();
        for (Item item : items) {
            // categories without anything matching the search are hidden too
            if (inTab(item) && allowed(item) && matches(new ItemStack(item), text)) inTabs.add(item);
        }
        // by shape and by material the categories come from what is in the window, so they are made again each time
        if (grouping != Grouping.CREATIVE) {
            bar.setCategories(grouping == Grouping.SHAPE ? Groups.byShape(inTabs) : Groups.byMaterial(inTabs), false);
        } else if (shownGrouping != Grouping.CREATIVE) {
            bar.setCategories(List.of(), true);
        }
        shownGrouping = grouping;
        bar.update(inTabs);
        shownCategory = bar.selectedKey();
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            // favourites first, hidden ones last, everything else between them
            // (with show_all_items, what is not collected comes after the collection, before the hidden ones)
            int group = ClientCollection.isFavorite(item) ? 0 : ClientCollection.isHidden(item) ? 3 : ClientCollection.has(item) ? 1 : 2;
            if (!inTab(item) || !allowed(item) || !bar.test(item)) continue;
            ItemStack stack = new ItemStack(item);
            if (matches(stack, text)) {
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
                entries.add(new Entry(stack, group, i, id.toString(), id.getNamespace(), stack.getHoverName().getString().toLowerCase(Locale.ROOT),
                        category(id)));
            }
        }
        Comparator<Entry> order = Comparator.comparingInt(Entry::group);
        order = switch (sort) {
            case CATEGORY -> order.thenComparing(Entry::category).thenComparing(Entry::id);
            // by mod, and inside each mod by category, so the chests of one mod stay together
            case MOD -> order.thenComparing(Entry::mod).thenComparing(Entry::category).thenComparing(Entry::id);
            case NAME -> order.thenComparing(Entry::name).thenComparing(Entry::id);
            case NEWEST -> order.thenComparing(Comparator.comparingInt(Entry::added).reversed());
        };
        entries.sort(order);
        shown.clear();
        entries.forEach(e -> shown.add(e.stack()));
        scrollRow = Mth.clamp(scrollRow, 0, maxScroll());
    }

    /** What the config no longer allows stays saved, but is not shown (and cannot be taken). */
    private static boolean allowed(Item item) {
        return ItemRules.client == null || ItemRules.client.allows(item);
    }

    private static boolean inTab(Item item) {
        return switch (tab) {
            case ALL -> true;
            case FAVORITES -> ClientCollection.isFavorite(item);
            case HIDDEN -> ClientCollection.isHidden(item);
        };
    }

    /**
     * The words of the name backwards: light_blue_wool -> "wool blue light", oak_pressure_plate -> "plate pressure oak".
     * Sorted like that, all wool is together, then all pressure plates and so on, whichever mod adds them.
     */
    private static String category(ResourceLocation id) {
        String[] words = id.getPath().split("[_/]");
        StringBuilder out = new StringBuilder();
        for (int i = words.length - 1; i >= 0; i--) {
            if (!out.isEmpty()) out.append(' ');
            out.append(words[i]);
        }
        return out.toString();
    }

    /** By name, or "@mod" by the mod's id (as in JEI). */
    private static boolean matches(ItemStack stack, String text) {
        if (text.isEmpty()) return true;
        if (text.startsWith("@")) {
            return BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().contains(text.substring(1));
        }
        return stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(text);
    }

    private int maxScroll() {
        return Math.max(0, (shown.size() + COLUMNS - 1) / COLUMNS - ROWS);
    }

    /** The collection entry under the mouse, or -1. */
    private int entryAt(double mouseX, double mouseY) {
        int x = (int) Math.floor(mouseX) - leftPos - GRID_X + 1;
        int y = (int) Math.floor(mouseY) - topPos - GRID_Y + 1;
        if (x < 0 || y < 0 || x >= COLUMNS * 18 || y >= ROWS * 18) return -1;
        int index = (scrollRow + y / 18) * COLUMNS + x / 18;
        return index < shown.size() ? index : -1;
    }

    private boolean overScrollBar(double mouseX, double mouseY) {
        return mouseX >= leftPos + SCROLL_X && mouseX < leftPos + SCROLL_X + 12
                && mouseY >= topPos + SCROLL_Y && mouseY < topPos + SCROLL_Y + SCROLL_HEIGHT;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        refresh();
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        bar.render(graphics, mouseX, mouseY);

        int hovered = entryAt(mouseX, mouseY);
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLUMNS; col++) {
                int index = (scrollRow + row) * COLUMNS + col;
                if (index >= shown.size()) break;
                int x = leftPos + GRID_X + col * 18, y = topPos + GRID_Y + row * 18;
                ItemStack stack = shown.get(index);
                if (ClientCollection.isFavorite(stack.getItem())) graphics.renderOutline(x - 1, y - 1, 18, 18, FAVORITE_COLOR);
                else if (ClientCollection.isHidden(stack.getItem())) graphics.renderOutline(x - 1, y - 1, 18, 18, HIDDEN_COLOR);
                graphics.renderItem(stack, x, y);
                if (!ClientCollection.has(stack.getItem())) {
                    graphics.fillGradient(RenderType.guiOverlay(), x, y, x + 16, y + 16, DIM_COLOR, DIM_COLOR, 0);
                }
                if (index == hovered) renderSlotHighlight(graphics, x, y, 0);
            }
        }

        int max = maxScroll();
        int scrollerY = topPos + SCROLL_Y + (max > 0 ? (SCROLL_HEIGHT - 15) * scrollRow / max : 0);
        graphics.blitSprite(max > 0 ? SCROLLER : SCROLLER_DISABLED, leftPos + SCROLL_X, scrollerY, 12, 15);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0x404040, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);
        String copies = ClientCollection.copies();
        graphics.drawString(font, copies, 29 - font.width(copies) / 2, TOP_ROW_Y + 3, 0x404040, false);
    }

    /** Items that cannot be added are dimmed in the player's inventory. */
    @Override
    protected void renderSlot(GuiGraphics graphics, Slot slot) {
        super.renderSlot(graphics, slot);
        ItemRules rules = ItemRules.client;
        if (rules != null && slot.container instanceof Inventory && slot.hasItem() && !rules.canAdd(slot.getItem())) {
            graphics.fillGradient(RenderType.guiOverlay(), slot.x, slot.y, slot.x + 16, slot.y + 16, DIM_COLOR, DIM_COLOR, 0);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int hovered = entryAt(mouseX, mouseY);
        Component category = bar.tooltip(mouseX, mouseY);
        if (category != null) {
            graphics.renderTooltip(font, category, mouseX, mouseY);
        } else if (hovered >= 0 && menu.getCarried().isEmpty()) {
            ItemStack stack = shown.get(hovered);
            List<Component> lines = new ArrayList<>(net.minecraft.client.gui.screens.Screen.getTooltipFromItem(minecraft, stack));
            if (!ClientCollection.has(stack.getItem())) lines.add(Component.translatable(key("not_collected")).withStyle(ChatFormatting.GRAY));
            graphics.renderTooltip(font, lines, stack.getTooltipImage(), mouseX, mouseY);
        } else {
            renderTooltip(graphics, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!search.isMouseOver(mouseX, mouseY)) search.setFocused(false);
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && bar.click(mouseX, mouseY)) {
            scrollRow = 0;
            return true;
        }
        int index = entryAt(mouseX, mouseY);
        if (index >= 0 && (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT)) {
            Item item = shown.get(index).getItem();
            // shown with show_all_items, but not collected: nothing to take or mark
            if (!ClientCollection.has(item)) return true;
            switch (mode) {
                case TAKE -> ClientPlatform.INSTANCE.sendToServer(new Payloads.Take(item, ClientCollection.amount()));
                case FAVORITE -> ClientCollection.toggle(item, true);
                case HIDE -> ClientCollection.toggle(item, false);
            }
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && overScrollBar(mouseX, mouseY)) {
            draggingScroll = true;
            scrollTo(mouseY);
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && search.isMouseOver(mouseX, mouseY)) {
            search.setValue("");
            search.setFocused(true);
            setFocused(search);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScroll) {
            scrollTo(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingScroll = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void scrollTo(double mouseY) {
        int max = maxScroll();
        if (max == 0) return;
        double part = (mouseY - topPos - SCROLL_Y - 7.5) / (SCROLL_HEIGHT - 15);
        scrollRow = Mth.clamp((int) Math.round(part * max), 0, max);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0 && bar.scroll(mouseX, mouseY, scrollY)) return true;
        // over the inventory, scrolling is left to other mods (e.g. moving items)
        if (hoveredSlot == null && maxScroll() > 0 && scrollY != 0) {
            scrollRow = Mth.clamp(scrollRow - (int) Math.signum(scrollY), 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // while typing, letters must not close the window or press hotbar keys
        if (search.isFocused() && keyCode != GLFW.GLFW_KEY_ESCAPE) {
            search.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** Switches between values: left click goes to the next one, right click back. */
    private final class CycleButton extends Button {
        private final Consumer<Boolean> cycle;
        private int pressedWith;
        /** Drawn like the sort button of Nekoro Quality of Life: three lines getting shorter. */
        private boolean sortIcon;
        /** An eye, crossed out while only the collection is shown. */
        private boolean eyeIcon;
        /** The grouping of the categories: tabs (creative), stairs (shape) or bricks (material). */
        private boolean groupIcon;

        CycleButton(int x, int y, int width, int height, Consumer<Boolean> cycle) {
            super(x, y, width, height, Component.empty(), b -> { }, DEFAULT_NARRATION);
            this.cycle = cycle;
        }

        @Override
        protected boolean isValidClickButton(int button) {
            return button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT;
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            pressedWith = button;
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
            int color = 0xFFE0E0E0;
            if (groupIcon) {
                String[] icon = switch (grouping) {
                    case CREATIVE -> new String[] { "##.##.##", "##.##.##", "########", "#......#", "#......#", "#......#", "########" };
                    case SHAPE -> new String[] { "##......", "##......", "####....", "####....", "######..", "######..", "########" };
                    case MATERIAL -> new String[] { "########", "#..#...#", "########", "#...#..#", "########", "#..#...#", "########" };
                };
                int x = getX() + 3, y = getY() + 3;
                for (int row = 0; row < icon.length; row++) {
                    for (int col = 0; col < 8; col++) {
                        if (icon[row].charAt(col) == '#') graphics.fill(x + col, y + row, x + col + 1, y + row + 1, color);
                    }
                }
                return;
            }
            if (eyeIcon) {
                int x = getX() + 3, y = getY() + 4;
                String[] eye = { "..####..", ".#....#.", "#..##..#", "#..##..#", ".#....#.", "..####.." };
                for (int row = 0; row < eye.length; row++) {
                    for (int col = 0; col < 8; col++) {
                        if (eye[row].charAt(col) == '#') graphics.fill(x + col, y + row, x + col + 1, y + row + 1, color);
                    }
                }
                if (!showUncollected) {
                    for (int i = 0; i < 8; i++) graphics.fill(x + 7 - i, y - 1 + i, x + 8 - i, y + i, 0xFFFF5555);
                }
                return;
            }
            if (!sortIcon) return;
            int x = getX() + 3, y = getY() + 3;
            graphics.fill(x, y, x + 8, y + 2, color);
            graphics.fill(x, y + 3, x + 6, y + 5, color);
            graphics.fill(x, y + 6, x + 4, y + 8, color);
        }

        @Override
        public void onPress() {
            cycle.accept(pressedWith == GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            updateLabels();
        }
    }
}
