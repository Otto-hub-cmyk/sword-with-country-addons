package com.minecolonies.traveleraddon;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class TravelerScreen extends AbstractContainerScreen<TravelerMenu> {
    private static final int VISIBLE_ROWS = 6;
    private static final int PANEL_COLOR = 0xF02B2118;
    private static final int PANEL_BORDER = 0xFF8C6A39;
    private static final int SECTION_COLOR = 0xAA17120D;
    private static final int ROW_HOVER = 0x556A5430;
    private static final int ROW_SELECTED = 0xAAE4B15A;
    private static final int TEXT_MAIN = 0xF0E7D2;
    private static final int TEXT_MUTED = 0xC9B89A;
    private static final int TEXT_GOOD = 0x7BD36B;
    private static final int TEXT_BAD = 0xD36B6B;

    private EditBox searchBox;
    private Button marketBuyButton;
    private Button demandBuyButton;
    private List<TravelerCatalog.MarketEntry> results = List.of();
    private int selectedIndex = -1;
    private int scrollOffset = 0;

    public TravelerScreen(final TravelerMenu menu, final Inventory inventory, final Component title) {
        super(menu, inventory, title);
        this.imageWidth = 320;
        this.imageHeight = 222;
    }

    @Override
    protected void init() {
        super.init();
        final int left = this.leftPos;
        final int top = this.topPos;

        this.searchBox = new EditBox(this.font, left + 18, top + 112, 184, 18, Component.translatable("screen.traveleraddon.search_hint"));
        this.searchBox.setBordered(true);
        this.searchBox.setTextColor(TEXT_MAIN);
        this.searchBox.setResponder(this::refreshSearch);
        this.addRenderableWidget(this.searchBox);
        this.setFocused(this.searchBox);

        this.demandBuyButton = addRenderableWidget(Button.builder(Component.translatable("screen.traveleraddon.buy_demands"), button -> {
            TravelerNetwork.CHANNEL.sendToServer(new TravelerDemandPurchasePacket(menu.travelerEntityId()));
            this.minecraft.player.closeContainer();
        }).bounds(left + 214, top + 62, 92, 20).build());

        this.marketBuyButton = addRenderableWidget(Button.builder(Component.translatable("screen.traveleraddon.buy_selected"), button -> {
            if (selectedIndex >= 0 && selectedIndex < results.size()) {
                final TravelerCatalog.MarketEntry entry = results.get(selectedIndex);
                TravelerNetwork.CHANNEL.sendToServer(new TravelerMarketPurchasePacket(menu.travelerEntityId(), entry.registryName()));
                this.minecraft.player.closeContainer();
            }
        }).bounds(left + 214, top + 112, 92, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("screen.traveleraddon.close"), button ->
            this.minecraft.player.closeContainer()
        ).bounds(left + 214, top + 184, 92, 20).build());

        refreshSearch("");
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        final int listLeft = leftPos + 18;
        final int listTop = topPos + 140;
        final int listRight = listLeft + 184;
        final int visibleRows = visibleResultCount();

        if (mouseX >= listLeft && mouseX <= listRight && mouseY >= listTop && mouseY <= listTop + visibleRows * 16) {
            for (int row = 0; row < visibleRows; row++) {
                final int rowTop = listTop + row * 16;
                if (mouseY >= rowTop && mouseY <= rowTop + 14) {
                    selectedIndex = scrollOffset + row;
                    updateButtons();
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double delta) {
        final int maxOffset = Math.max(0, results.size() - VISIBLE_ROWS);
        if (delta < 0 && scrollOffset < maxOffset) {
            scrollOffset++;
            return true;
        }
        if (delta > 0 && scrollOffset > 0) {
            scrollOffset--;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        if (this.searchBox.keyPressed(keyCode, scanCode, modifiers) || this.searchBox.canConsumeInput()) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(final char codePoint, final int modifiers) {
        if (this.searchBox.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    protected void renderBg(final GuiGraphics graphics, final float partialTick, final int mouseX, final int mouseY) {
        final int left = this.leftPos;
        final int top = this.topPos;

        drawPanel(graphics, left, top, left + this.imageWidth, top + this.imageHeight, PANEL_COLOR, PANEL_BORDER);
        drawPanel(graphics, left + 12, top + 24, left + 206, top + 98, SECTION_COLOR, PANEL_BORDER);
        drawPanel(graphics, left + 12, top + 104, left + 206, top + 210, SECTION_COLOR, PANEL_BORDER);
        drawPanel(graphics, left + 210, top + 24, left + 310, top + 210, SECTION_COLOR, PANEL_BORDER);

        final int rows = visibleResultCount();
        for (int row = 0; row < rows; row++) {
            final int absoluteIndex = scrollOffset + row;
            final int rowTop = top + 140 + row * 16;
            final boolean hovered = mouseX >= left + 18 && mouseX <= left + 202 && mouseY >= rowTop && mouseY <= rowTop + 14;
            if (absoluteIndex == selectedIndex) {
                graphics.fill(left + 16, rowTop - 1, left + 204, rowTop + 14, ROW_SELECTED);
            } else if (hovered) {
                graphics.fill(left + 16, rowTop - 1, left + 204, rowTop + 14, ROW_HOVER);
            }
        }
    }

    @Override
    public void render(final GuiGraphics graphics, final int mouseX, final int mouseY, final float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.searchBox.render(graphics, mouseX, mouseY, partialTick);

        if (selectedIndex >= 0 && selectedIndex < results.size()) {
            final TravelerCatalog.MarketEntry selected = results.get(selectedIndex);
            if (isMouseOverList(mouseX, mouseY)) {
                final ItemStack preview = TravelerItemSafety.sanitizePreviewStack(selected.item(), Math.max(1, selected.saleCount()));
                if (!preview.isEmpty()) {
                    graphics.renderTooltip(this.font, preview, mouseX, mouseY);
                }
            }
        }
    }

    @Override
    protected void renderLabels(final GuiGraphics graphics, final int mouseX, final int mouseY) {
        final Font font = this.font;
        graphics.drawString(font, Component.translatable("screen.traveleraddon.title"), 18, 10, TEXT_MAIN, false);

        int textY = 28;
        for (final FormattedCharSequence line : font.split(Component.translatable("screen.traveleraddon.description_v2"), 180)) {
            graphics.drawString(font, line, 18, textY, TEXT_MUTED, false);
            textY += font.lineHeight + 2;
        }

        graphics.drawString(font, Component.translatable("screen.traveleraddon.demand_price"), 18, 74, TEXT_MAIN, false);
        graphics.drawString(font, Component.translatable("screen.traveleraddon.market_title"), 18, 94, TEXT_MAIN, false);
        drawScaledString(
            graphics,
            font,
            Component.translatable("screen.traveleraddon.market_remaining", menu.remainingMarketPurchases()),
            18,
            106,
            TEXT_MUTED,
            0.5F
        );
        graphics.drawString(font, Component.translatable("screen.traveleraddon.townhall_level", menu.townHallLevel()), 216, 34, TEXT_MAIN, false);
        graphics.drawString(
            font,
            Component.translatable("screen.traveleraddon.demand_remaining", menu.remainingDemandPurchases()),
            216,
            50,
            menu.remainingDemandPurchases() > 0 ? TEXT_GOOD : TEXT_BAD,
            false
        );

        final int rows = visibleResultCount();
        for (int row = 0; row < rows; row++) {
            final TravelerCatalog.MarketEntry entry = results.get(scrollOffset + row);
            final int y = 142 + row * 16;
            final int color = selectedIndex == scrollOffset + row ? 0x2E1A06 : TEXT_MAIN;
            final String status = entry.purchasable()
                ? Component.translatable(
                    "screen.traveleraddon.market_price",
                    TravelerCatalog.priceFor(entry.item()),
                    entry.saleCount(),
                    entry.rule().name()
                ).getString()
                : Component.translatable("screen.traveleraddon.blocked").getString();
            final String line = trimToWidth(font, entry.displayName(), 96)
                + " x" + entry.saleCount()
                + status;
            graphics.drawString(font, line, 20, y, color, false);
        }
    }

    private void refreshSearch(final String value) {
        this.results = TravelerCatalog.searchMarket(value);
        this.selectedIndex = this.results.isEmpty() ? -1 : 0;
        this.scrollOffset = 0;
        updateButtons();
    }

    private void updateButtons() {
        final boolean canBuySelected = selectedIndex >= 0
            && selectedIndex < results.size()
            && results.get(selectedIndex).purchasable()
            && menu.remainingMarketPurchases() > 0;
        this.marketBuyButton.active = canBuySelected;
        this.demandBuyButton.active = menu.remainingDemandPurchases() > 0;
    }

    private int visibleResultCount() {
        return Math.min(VISIBLE_ROWS, Math.max(0, results.size() - scrollOffset));
    }

    private boolean isMouseOverList(final double mouseX, final double mouseY) {
        return mouseX >= leftPos + 18
            && mouseX <= leftPos + 202
            && mouseY >= topPos + 140
            && mouseY <= topPos + 140 + visibleResultCount() * 16;
    }

    private static void drawPanel(
        final GuiGraphics graphics,
        final int left,
        final int top,
        final int right,
        final int bottom,
        final int fillColor,
        final int borderColor
    ) {
        graphics.fill(left, top, right, bottom, fillColor);
        graphics.fill(left, top, right, top + 1, borderColor);
        graphics.fill(left, bottom - 1, right, bottom, borderColor);
        graphics.fill(left, top, left + 1, bottom, borderColor);
        graphics.fill(right - 1, top, right, bottom, borderColor);
    }

    private static String trimToWidth(final Font font, final String value, final int width) {
        if (font.width(value) <= width) {
            return value;
        }

        String current = value;
        while (!current.isEmpty() && font.width(current + "...") > width) {
            current = current.substring(0, current.length() - 1);
        }
        return current + "...";
    }

    private static void drawScaledString(
        final GuiGraphics graphics,
        final Font font,
        final Component text,
        final int x,
        final int y,
        final int color,
        final float scale
    ) {
        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, Math.round(x / scale), Math.round(y / scale), color, false);
        graphics.pose().popPose();
    }
}
