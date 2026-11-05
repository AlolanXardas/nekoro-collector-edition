package dev.xardas.nekorocollectoredition.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.lwjgl.glfw.GLFW;

import dev.xardas.nekorocollectoredition.NekoroCollectorEdition;
import dev.xardas.nekorocollectoredition.bag.BagMenu;
import dev.xardas.nekorocollectoredition.bag.PickupMode;

/**
 * A bag looks like a large chest. A small button left of the title switches what the bag picks up (All, Matching,
 * Off); the right side of the title row stays free for the sort and transfer buttons of Nekoro Quality of Life.
 */
public final class BagScreen extends AbstractContainerScreen<BagMenu> {
    private static final ResourceLocation CHEST = ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static final ResourceLocation BUTTON = ResourceLocation.withDefaultNamespace("widget/button");
    private static final ResourceLocation BUTTON_HOVER = ResourceLocation.withDefaultNamespace("widget/button_highlighted");

    private static final ResourceLocation SCROLLER = ResourceLocation.withDefaultNamespace("container/creative_inventory/scroller");
    private static final ResourceLocation TRACK = ResourceLocation.withDefaultNamespace("widget/button_disabled");
    /** The scroll bar of bags bigger than the window, right of it. */
    private static final int BAR_X = 177, BAR_Y = 17;

    private int wantedScroll = -1;
    private boolean dragging;
    private PickupButton pickupButton;
    private PickupMode shownPickup;

    public BagScreen(BagMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageHeight = 114 + menu.shownRows() * 18;
        inventoryLabelY = imageHeight - 94;
        titleLabelX = 20;
    }

    @Override
    protected void init() {
        super.init();
        pickupButton = addRenderableWidget(new PickupButton(leftPos + 7, topPos + 5));
        shownPickup = null;
        updateTooltip();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateTooltip();
        if (wantedScroll == menu.scroll()) wantedScroll = -1;
    }

    private void updateTooltip() {
        if (shownPickup == menu.pickupMode()) return;
        shownPickup = menu.pickupMode();
        pickupButton.setTooltip(Tooltip.create(Component.translatable("gui.nekorocollectoredition.pickup",
                Component.translatable("gui.nekorocollectoredition.pickup." + shownPickup.id()))));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int top = menu.shownRows() * 18 + 17;
        graphics.blit(CHEST, leftPos, topPos, 0, 0, imageWidth, top);
        graphics.blit(CHEST, leftPos, topPos + top, 0, 126, imageWidth, 96);
        // the last row of a bag may not be full: its unused squares are covered with the plain window from the title row
        for (int i = 0; i < menu.shownRows() * 9; i++) {
            Slot slot = menu.slots.get(i);
            if (slot.isActive()) continue;
            int x = leftPos + slot.x - 1, y = topPos + slot.y - 1;
            graphics.blit(CHEST, x, y, 8, 3, 18, 14);
            graphics.blit(CHEST, x, y + 14, 8, 3, 18, 4);
        }
        int max = menu.maxScroll();
        if (max > 0) {
            int height = menu.shownRows() * 18;
            graphics.blitSprite(TRACK, leftPos + BAR_X, topPos + BAR_Y, 14, height);
            int y = topPos + BAR_Y + 1 + (height - 17) * shownScroll() / max;
            graphics.blitSprite(SCROLLER, leftPos + BAR_X + 1, y, 12, 15);
        }
    }

    /** The row asked for last, until the server's answer arrives. */
    private int shownScroll() {
        return wantedScroll >= 0 ? wantedScroll : menu.scroll();
    }

    private void scrollTo(int row) {
        int target = Mth.clamp(row, 0, menu.maxScroll());
        if (target == shownScroll()) return;
        wantedScroll = target;
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, BagMenu.BUTTON_SCROLL + target);
    }

    private boolean overBar(double mouseX, double mouseY) {
        return menu.maxScroll() > 0 && mouseX >= leftPos + BAR_X && mouseX < leftPos + BAR_X + 14
                && mouseY >= topPos + BAR_Y && mouseY < topPos + BAR_Y + menu.shownRows() * 18;
    }

    private void dragBar(double mouseY) {
        double part = (mouseY - topPos - BAR_Y - 8) / (menu.shownRows() * 18 - 17);
        scrollTo((int) Math.round(part * menu.maxScroll()));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && overBar(mouseX, mouseY)) {
            dragging = true;
            dragBar(mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging) {
            dragBar(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (menu.maxScroll() > 0 && scrollY != 0) {
            scrollTo(shownScroll() - (int) Math.signum(scrollY));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /** An arrow into a tray: green for All, yellow for Matching, grey for Off. Left click: next, right click: back. */
    private final class PickupButton extends AbstractButton {
        private int pressedWith;

        PickupButton(int x, int y) {
            super(x, y, 10, 10, Component.empty());
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
        public void onPress() {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
                    pressedWith == GLFW.GLFW_MOUSE_BUTTON_RIGHT ? BagMenu.BUTTON_PICKUP_BACK : BagMenu.BUTTON_PICKUP_NEXT);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.blitSprite(isHoveredOrFocused() ? BUTTON_HOVER : BUTTON, getX(), getY(), width, height);
            int x = getX() + 2, y = getY() + 2;
            int color = switch (menu.pickupMode()) {
                case ALL -> 0xFF60E060;
                case MATCHING -> 0xFFE8C840;
                case OFF -> 0xFF909090;
            };
            graphics.fill(x + 2, y, x + 4, y + 3, color);
            graphics.fill(x + 1, y + 3, x + 5, y + 4, color);
            graphics.fill(x + 2, y + 4, x + 4, y + 5, color);
            graphics.fill(x, y + 5, x + 1, y + 6, color);
            graphics.fill(x + 5, y + 5, x + 6, y + 6, color);
            graphics.fill(x, y + 6, x + 6, y + 7, color);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
