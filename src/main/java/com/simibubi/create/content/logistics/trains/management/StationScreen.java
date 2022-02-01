package com.simibubi.create.content.logistics.trains.management;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.function.Consumer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.logistics.trains.entity.Carriage;
import com.simibubi.create.content.logistics.trains.entity.Train;
import com.simibubi.create.content.logistics.trains.entity.TrainIconType;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.UIRenderHelper;
import com.simibubi.create.foundation.gui.widget.IconButton;
import com.simibubi.create.foundation.networking.AllPackets;
import com.simibubi.create.foundation.utility.animation.LerpedFloat;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.util.Mth;

public class StationScreen extends AbstractStationScreen {

	private EditBox nameBox;
	private EditBox trainNameBox;
	private IconButton newTrainButton;
	private IconButton disassembleTrainButton;
	private IconButton openScheduleButton;

	private int leavingAnimation;
	private LerpedFloat trainPosition;

	private boolean switchingToAssemblyMode;

	public StationScreen(StationTileEntity te, GlobalStation station) {
		super(te, station);
		background = AllGuiTextures.STATION;
		leavingAnimation = 0;
		trainPosition = LerpedFloat.linear()
			.startWithValue(0);
		switchingToAssemblyMode = false;
	}

	@Override
	protected void init() {
		super.init();
		int x = guiLeft;
		int y = guiTop;

		Consumer<String> onTextChanged;

		onTextChanged = s -> nameBox.x = nameBoxX(s, nameBox);
		nameBox = new EditBox(new NoShadowFontWrapper(font), x + 23, y + 4, background.width - 20, 10,
			new TextComponent(station.name));
		nameBox.setBordered(false);
		nameBox.setMaxLength(25);
		nameBox.setTextColor(0x442000);
		nameBox.setValue(station.name);
		nameBox.changeFocus(false);
		nameBox.mouseClicked(0, 0, 0);
		nameBox.setResponder(onTextChanged);
		nameBox.x = nameBoxX(nameBox.getValue(), nameBox);
		addRenderableWidget(nameBox);

		Runnable assemblyCallback = () -> {
			switchingToAssemblyMode = true;
			minecraft.setScreen(new AssemblyScreen(te, station));
		};

		newTrainButton = new WideIconButton(x + 84, y + 65, AllGuiTextures.I_NEW_TRAIN);
		newTrainButton.setToolTip(new TextComponent("New Train"));
		newTrainButton.withCallback(assemblyCallback);
		addRenderableWidget(newTrainButton);

		disassembleTrainButton = new WideIconButton(x + 94, y + 65, AllGuiTextures.I_DISASSEMBLE_TRAIN);
		disassembleTrainButton.active = false;
		disassembleTrainButton.visible = false;
		disassembleTrainButton.withCallback(assemblyCallback);
		addRenderableWidget(disassembleTrainButton);

		openScheduleButton = new IconButton(x + 73, y + 65, AllIcons.I_VIEW_SCHEDULE);
		openScheduleButton.active = false;
		openScheduleButton.visible = false;
		addRenderableWidget(openScheduleButton);

		onTextChanged = s -> trainNameBox.x = nameBoxX(s, trainNameBox);
		trainNameBox = new EditBox(font, x + 23, y + 47, background.width - 20, 10, new TextComponent(""));
		trainNameBox.setBordered(false);
		trainNameBox.setMaxLength(15);
		trainNameBox.setTextColor(0xC6C6C6);
		trainNameBox.changeFocus(false);
		trainNameBox.mouseClicked(0, 0, 0);
		trainNameBox.setResponder(onTextChanged);
		trainNameBox.active = false;

		tickTrainDisplay();
	}

	@Override
	public void tick() {
		tickTrainDisplay();
		if (getFocused() != nameBox) {
			nameBox.setCursorPosition(nameBox.getValue()
				.length());
			nameBox.setHighlightPos(nameBox.getCursorPosition());
		}
		if (getFocused() != trainNameBox || trainNameBox.active == false) {
			trainNameBox.setCursorPosition(trainNameBox.getValue()
				.length());
			trainNameBox.setHighlightPos(trainNameBox.getCursorPosition());
		}
		super.tick();
	}

	private void tickTrainDisplay() {
		Train train = displayedTrain.get();

		if (train == null) {
			if (trainNameBox.active) {
				trainNameBox.active = false;
				removeWidget(trainNameBox);
			}

			leavingAnimation = 0;
			newTrainButton.active = true;
			newTrainButton.visible = true;
			Train imminentTrain = station.getImminentTrain();

			if (imminentTrain != null) {
				displayedTrain = new WeakReference<>(imminentTrain);
				newTrainButton.active = false;
				newTrainButton.visible = false;
				disassembleTrainButton.active = false;
				disassembleTrainButton.visible = true;
				openScheduleButton.active = false;
				openScheduleButton.visible = true;

				trainNameBox.active = true;
				trainNameBox.setValue(imminentTrain.name.getString());
				trainNameBox.x = nameBoxX(trainNameBox.getValue(), trainNameBox);
				addRenderableWidget(trainNameBox);

				int trainIconWidth = getTrainIconWidth(imminentTrain);
				int targetPos = background.width / 2 - trainIconWidth / 2;
				float f = (float) (imminentTrain.navigation.distanceToDestination / 15f);
				trainPosition.startWithValue(targetPos - (targetPos + 5) * f);
			}
			return;
		}

		int trainIconWidth = getTrainIconWidth(train);
		int targetPos = background.width / 2 - trainIconWidth / 2;

		if (leavingAnimation > 0) {
			disassembleTrainButton.active = false;
			float f = 1 - (leavingAnimation / 80f);
			trainPosition.setValue(targetPos + f * f * f * (background.width - targetPos + 5));
			leavingAnimation--;
			if (leavingAnimation > 0)
				return;

			displayedTrain = new WeakReference<>(null);
			disassembleTrainButton.visible = false;
			openScheduleButton.active = false;
			openScheduleButton.visible = false;
			return;
		}

		if (train.navigation.destination != station && train.currentStation != station) {
			leavingAnimation = 80;
			return;
		}

		disassembleTrainButton.active = train.currentStation == station; // TODO te.canAssemble
		openScheduleButton.active = train.runtime.schedule != null;

		float f = (float) (train.navigation.distanceToDestination / 30f);
		trainPosition.setValue(targetPos - (targetPos + trainIconWidth) * f);
	}

	private int nameBoxX(String s, EditBox nameBox) {
		return guiLeft + background.width / 2 - (Math.min(font.width(s), nameBox.getWidth()) + 10) / 2;
	}

	@Override
	protected void renderWindow(PoseStack ms, int mouseX, int mouseY, float partialTicks) {
		super.renderWindow(ms, mouseX, mouseY, partialTicks);
		int x = guiLeft;
		int y = guiTop;

		String text = nameBox.getValue();

		if (!nameBox.isFocused())
			AllGuiTextures.STATION_EDIT_NAME.render(ms, nameBoxX(text, nameBox) + font.width(text) + 5, y + 1);

		Train train = displayedTrain.get();
		if (train == null) {
			TextComponent header = new TextComponent("Station is Idle");
			font.draw(ms, header, x + 97 - font.width(header) / 2, y + 47, 0x7A7A7A);
			return;
		}

		float position = trainPosition.getValue(partialTicks);

		ms.pushPose();
		RenderSystem.enableBlend();
		ms.translate(position, 0, 0);
		TrainIconType icon = train.icon;
		int offset = 0;

		List<Carriage> carriages = train.carriages;
		for (int i = carriages.size() - 1; i > 0; i--) {
			RenderSystem.setShaderColor(1, 1, 1, Math.min(1f,
				Math.min((position + offset - 10) / 30f, (background.width - 40 - position - offset) / 30f)));

			if (i == carriages.size() - 1 && train.doubleEnded) {
				offset += icon.render(TrainIconType.FLIPPED_ENGINE, ms, x + offset, y + 20) + 1;
				continue;
			}
			Carriage carriage = carriages.get(i);
			offset += icon.render(carriage.bogeySpacing, ms, x + offset, y + 20) + 1;
		}

		RenderSystem.setShaderColor(1, 1, 1,
			Math.min(1f, Math.min((position + offset - 10) / 30f, (background.width - 40 - position - offset) / 30f)));
		offset += icon.render(TrainIconType.ENGINE, ms, x + offset, y + 20);
		RenderSystem.disableBlend();
		ms.popPose();

		RenderSystem.setShaderColor(1, 1, 1, 1);

		UIRenderHelper.drawStretched(ms, x + 21, y + 43, 150, 46, -100, AllGuiTextures.STATION_TEXTBOX_MIDDLE);
		AllGuiTextures.STATION_TEXTBOX_TOP.render(ms, x + 21, y + 42);
		AllGuiTextures.STATION_TEXTBOX_BOTTOM.render(ms, x + 21, y + 86);

		ms.pushPose();
		ms.translate(Mth.clamp(position + offset - 13, 25, 159), 0, 0);
		AllGuiTextures.STATION_TEXTBOX_SPEECH.render(ms, x, y + 38);
		ms.popPose();

		text = trainNameBox.getValue();
		if (!trainNameBox.isFocused())
			AllGuiTextures.STATION_EDIT_TRAIN_NAME.render(ms, nameBoxX(text, trainNameBox) + font.width(text) + 5,
				y + 44);
	}

	@Override
	public boolean mouseClicked(double pMouseX, double pMouseY, int pButton) {
		if (!nameBox.isFocused() && pMouseY > guiTop && pMouseY < guiTop + 14 && pMouseX > guiLeft
			&& pMouseX < guiLeft + background.width) {
			nameBox.setFocus(true);
			nameBox.setHighlightPos(0);
			setFocused(nameBox);
			return true;
		}
		if (trainNameBox.active && !trainNameBox.isFocused() && pMouseY > guiTop + 45 && pMouseY < guiTop + 58
			&& pMouseX > guiLeft + 25 && pMouseX < guiLeft + 168) {
			trainNameBox.setFocus(true);
			trainNameBox.setHighlightPos(0);
			setFocused(trainNameBox);
			return true;
		}
		return super.mouseClicked(pMouseX, pMouseY, pButton);
	}

	@Override
	public boolean keyPressed(int pKeyCode, int pScanCode, int pModifiers) {
		boolean hitEnter = getFocused() instanceof EditBox && (pKeyCode == 257 || pKeyCode == 335);

		if (hitEnter && nameBox.isFocused()) {
			nameBox.setFocus(false);
			syncStationName();
			return true;
		}

		if (hitEnter && trainNameBox.isFocused()) {
			trainNameBox.setFocus(false);
			syncTrainName();
			return true;
		}

		return super.keyPressed(pKeyCode, pScanCode, pModifiers);
	}

	private void syncTrainName() {
		Train train = displayedTrain.get();
		if (train != null && !trainNameBox.getValue()
			.equals(train.name.getString()))
			AllPackets.channel.sendToServer(new TrainEditPacket(train.id, trainNameBox.getValue(), train.icon.getId()));
	}

	private void syncStationName() {
		if (!nameBox.getValue()
			.equals(station.name))
			AllPackets.channel.sendToServer(StationEditPacket.configure(te.getBlockPos(), false, nameBox.getValue()));
	}

	@Override
	public void removed() {
		super.removed();
		AllPackets.channel
			.sendToServer(StationEditPacket.configure(te.getBlockPos(), switchingToAssemblyMode, nameBox.getValue()));
		Train train = displayedTrain.get();
		if (!switchingToAssemblyMode && train != null)
			AllPackets.channel.sendToServer(new TrainEditPacket(train.id, trainNameBox.getValue(), train.icon.getId()));
	}

}