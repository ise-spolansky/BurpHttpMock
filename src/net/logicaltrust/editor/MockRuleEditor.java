package net.logicaltrust.editor;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;

import burp.BurpExtender;
import burp.IExtensionHelpers;
import burp.IResponseInfo;
import burp.ITextEditor;
import net.logicaltrust.SimpleLogger;
import net.logicaltrust.model.MockEntry;
import net.logicaltrust.persistent.MockRepository;
import net.logicaltrust.persistent.SettingsSaver;

public class MockRuleEditor {

	private final ITextEditor textEditor;
	private final JPanel mainPanel;
	private final JButton saveTextButton;
	private final JButton discardTextButton;
	private final JCheckBox recalcBox;
	
	private MockEntry currentEntry;
	private final SimpleLogger logger;
	private final MockRepository mockHolder;
	private final IExtensionHelpers helpers;
	private final SettingsSaver settingSaver;

	private static final Pattern CONTENT_LENGTH_PATTERN = Pattern.compile("^Content-Length: .*$", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);

	public MockRuleEditor(ITextEditor textEditor, MockRepository mockHolder, SettingsSaver settingSaver) {
		this.logger = BurpExtender.getLogger();
		this.textEditor = textEditor;
		this.mockHolder = mockHolder;
		this.helpers = BurpExtender.getCallbacks().getHelpers();
		this.settingSaver = settingSaver;
		this.textEditor.setEditable(false);
		
		mainPanel = new JPanel();
		mainPanel.setBorder(new TitledBorder(new EmptyBorder(0, 0, 0, 0), "Response editor", TitledBorder.LEADING, TitledBorder.TOP, null, null));
		mainPanel.setLayout(new BorderLayout());

		JPanel textButtonPanel = new JPanel();
		textButtonPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 5));
		
		saveTextButton = new JButton("Save");
		discardTextButton = new JButton("Discard");
		recalcBox = new JCheckBox("Recalculate Content-Length");
		recalcBox.setSelected(settingSaver.loadRecalculateContentLength());
		
		textButtonPanel.add(saveTextButton);
		textButtonPanel.add(discardTextButton);
		textButtonPanel.add(recalcBox);
		
		JPanel textEditorPanel = new JPanel();
		textEditorPanel.setLayout(new BorderLayout());
		textEditorPanel.setBorder(new EmptyBorder(5, 0, 0, 0));
		textEditorPanel.add(textEditor.getComponent());
		mainPanel.add(textEditorPanel);
		mainPanel.add(textButtonPanel, BorderLayout.SOUTH);
		
		saveTextButton.addActionListener(e -> saveChanges());
		discardTextButton.addActionListener(e -> discardChanges());
		recalcBox.addActionListener(e -> settingSaver.saveRecalculateContentLength(recalcBox.isSelected()));
	}

	public void discardChanges() {
		logger.debug("Message discarded");
		if (textEditor.isTextModified()) {
			textEditor.setText(currentEntry.getEntryInput());
		}
	}
	
	public void saveChanges() {
		byte[] text = textEditor.getText();
		if (recalcBox.isSelected()) {
			logger.debug("Recalculating content length");
			text = recalculateContentLength(text);
		}
		mockHolder.updateResponse(currentEntry.getId()+"", text);
		loadResponse(currentEntry);
	}

	private byte[] recalculateContentLength(byte[] text) {
		IResponseInfo response = helpers.analyzeResponse(text);
		int contentLength = text.length - response.getBodyOffset();
		String responseStr = new String(text, StandardCharsets.UTF_8);
		Matcher matcher = CONTENT_LENGTH_PATTERN.matcher(responseStr);
		String replaced = matcher.replaceFirst("Content-Length: " + contentLength);
		return replaced.getBytes(StandardCharsets.UTF_8);
	}

	public void loadResponse(MockEntry entry) {
		this.currentEntry = entry;
		if (!settingSaver.loadDisplayLargeResponsesInEditor() && entry.getEntryInput().length > settingSaver.loadThreshold()) {
			this.textEditor.setEditable(false);
			this.textEditor.setText("Response is too large.".getBytes(StandardCharsets.UTF_8));
		} else {
			this.textEditor.setEditable(true);
			this.textEditor.setText(entry.getEntryInput());
		}
	}
	
	public void unloadResponse() {
		this.currentEntry = null;
		this.textEditor.setEditable(false);
		this.textEditor.setText(null);
	}
	
	public boolean hasUnsavedChanges() {
		return currentEntry != null && textEditor.isTextModified();
	}
	
	public Component getComponent() {
		return mainPanel;
	}
	
}