package net.logicaltrust.persistent;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import burp.BurpExtender;
import burp.IBurpExtenderCallbacks;
import net.logicaltrust.SimpleLogger;
import net.logicaltrust.model.MockEntry;
import net.logicaltrust.model.MockProtocolEnum;
import net.logicaltrust.model.MockRule;

public class SettingsSaver {
	
	private static final String ID_LIST = "ID_LIST";
	private static final String RECALCULATE_CONTENT_LENGTH = "RECALCULATE_CONTENT_LENGTH";
	private static final String DEBUG_OUTPUT = "DEBUG_OUTPUT";
	private static final String SERVER_PORT = "SERVER_PORT";
	private static final String THRESHOLD = "THRESHOLD";
	private static final String DISPLAY_LARGE_RESPONSES_IN_EDITOR = "DISPLAY_LARGE_RESPONSES_IN_EDITOR";
	private static final String INFORM_ABOUT_LARGE_RESPONSE = "INFORM_ABOUT_LARGE_RESPONSE";
	private static final String DELIM = "|";
	private static final String DELIM_REGEX = "\\|";
	private static final int DEFAULT_PORT = 7654;
	private static final int DEFAULT_THRESHOLD = 2 * 1024 * 1024; //2MB
	private static final int ENTRY_PARAMS = 6;

	private Boolean displayLargeResponsesInEditorCache;
	private Boolean informAboutLargeResponseCache;
	private Integer thresholdCache;
	private Boolean recalculateCache;
	
	private final IBurpExtenderCallbacks callbacks;
	private final SimpleLogger logger;
	
	public SettingsSaver() {
		this.callbacks = BurpExtender.getCallbacks();
		this.logger = BurpExtender.getLogger();
		if (isDebugOn()) {
			logger.enableDebug();
		} else {
			logger.disableDebug();
		}
	}
	
	public void clear() {
		String strIds = callbacks.loadExtensionSetting(ID_LIST);
		if (strIds != null ) {
			Arrays.stream(strIds.split(DELIM_REGEX)).forEach(id -> callbacks.saveExtensionSetting("ENTRY_" + id, null));
		}
		callbacks.saveExtensionSetting(ID_LIST, null);
		callbacks.saveExtensionSetting(RECALCULATE_CONTENT_LENGTH, null);
		callbacks.saveExtensionSetting(DEBUG_OUTPUT, null);
		callbacks.saveExtensionSetting(SERVER_PORT, null);
	}
	
	public void saveEntry(MockEntry entry) {
		logger.debug("Saving entry " + entry.getId());
		callbacks.saveExtensionSetting("ENTRY_" + entry.getId(), entryToString(entry));
	}
	
	public void removeEntry(long id) {
		logger.debug("Removing entry " + id);
		callbacks.saveExtensionSetting("ENTRY_" + id, null);
	}
	
	public void saveIdList(Collection<MockEntry> entries) {
		String ids = entries.stream()
				.map(MockEntry::getId)
				.map(Object::toString)
				.collect(Collectors.joining(DELIM, "", ""));
		logger.debug("Saving entry list " + ids);
		callbacks.saveExtensionSetting(ID_LIST, ids);
	}
	
	public List<MockEntry> loadEntries() {
		logger.debug("Loading entries");
		String strIds = callbacks.loadExtensionSetting(ID_LIST);
		if (strIds == null || strIds.isEmpty()) {
			return new ArrayList<>();
		}
		List<MockEntry> entries = Arrays.stream(strIds.split(DELIM_REGEX)).map(id -> {
			String entryStr = callbacks.loadExtensionSetting("ENTRY_"+ id);
			return entryFromString(entryStr, id);
		}).collect(Collectors.toList());
		logger.debug(entries.isEmpty() ? "No entries loaded" : "Loaded " + entries.size() + " entries");
		return entries;
	}
	
	public void saveRecalculateContentLength(boolean recalc) {
		callbacks.saveExtensionSetting(RECALCULATE_CONTENT_LENGTH, Boolean.toString(recalc));
		recalculateCache = recalc;
	}
	
	public boolean loadRecalculateContentLength() {
		if (recalculateCache == null) {
			String value = callbacks.loadExtensionSetting(RECALCULATE_CONTENT_LENGTH);
			recalculateCache = value == null || Boolean.parseBoolean(value);
		}
		return recalculateCache;
	}
	
	public void saveDebugOutput(boolean debug) {
		callbacks.saveExtensionSetting(DEBUG_OUTPUT, Boolean.toString(debug));
		if (debug) {
			logger.enableDebug();
		} else {
			logger.disableDebug();
		}
	}

	public void saveThreshold(int threshold) {
		callbacks.saveExtensionSetting(THRESHOLD, threshold + "");
		thresholdCache = threshold;
	}

	public int loadThreshold() {
		if (thresholdCache == null) {
			String threshold = callbacks.loadExtensionSetting(THRESHOLD);
			if (threshold != null) {
				try {
					thresholdCache = Integer.parseInt(threshold);
				} catch (NumberFormatException e) {
					logger.debugForce("Invalid threshold " + threshold);
				}
			} else {
				thresholdCache = DEFAULT_THRESHOLD;
			}
		}
		return thresholdCache;
	}

	public void saveDisplayLargeResponsesInEditor(boolean display) {
		callbacks.saveExtensionSetting(DISPLAY_LARGE_RESPONSES_IN_EDITOR, Boolean.toString(display));
		displayLargeResponsesInEditorCache = display;
	}

	public boolean loadDisplayLargeResponsesInEditor() {
		if (displayLargeResponsesInEditorCache == null) {
			String display = callbacks.loadExtensionSetting(DISPLAY_LARGE_RESPONSES_IN_EDITOR);
			displayLargeResponsesInEditorCache = Boolean.parseBoolean(display);
		}
		return displayLargeResponsesInEditorCache;
	}

	public void saveInformAboutLargeResponse(boolean inform) {
		callbacks.saveExtensionSetting(INFORM_ABOUT_LARGE_RESPONSE, Boolean.toString(inform));
		informAboutLargeResponseCache = inform;
	}

	public boolean loadInformLargeResponsesInEditor() {
		if (informAboutLargeResponseCache == null) {
			String inform = callbacks.loadExtensionSetting(INFORM_ABOUT_LARGE_RESPONSE);
			informAboutLargeResponseCache = inform == null || Boolean.parseBoolean(inform);
		}
		return informAboutLargeResponseCache;
	}
	
	public boolean isDebugOn() {
		return Boolean.parseBoolean(callbacks.loadExtensionSetting(DEBUG_OUTPUT));
	}
	
	public void savePort(int port) {
		callbacks.saveExtensionSetting(SERVER_PORT, port + "");
	}
	
	public int loadPort() {
		String port = callbacks.loadExtensionSetting(SERVER_PORT);
		if (port != null) {
			try {
				return Integer.parseInt(port);
			} catch (NumberFormatException e) {
				logger.debugForce("Invalid port " + port);
			}
		}
		return DEFAULT_PORT;
	}
	
	private MockEntry entryFromString(String str, String id) {
		String[] split = str.split(DELIM_REGEX);
		
		if (split.length != ENTRY_PARAMS) {
			logger.debugForce("Invalid entry, id: " + id +", value: " + Arrays.toString(split));
		}
		
		byte[] response = decode(split[5]);
		MockRule rule = new MockRule(MockProtocolEnum.valueOf(decodeToString(split[1])), 
				decodeToString(split[2]), 
				decodeToString(split[3]), 
				decodeToString(split[4]));
		MockEntry entry = new MockEntry(Boolean.parseBoolean(split[0]), rule, response);
		entry.setId(Long.parseLong(id));
		
		return entry;
	}
	
	private String entryToString(MockEntry entry) {
		MockRule rule = entry.getRule();
		return entry.isEnabled() + DELIM +
				encode(rule.getProtocol().name()) + DELIM +
				encode(rule.getHost()) + DELIM +
				encode(rule.getPort() + "") + DELIM +
				encode(rule.getPath()) + DELIM +
				encode(entry.getEntryInput());
	}
	
	private byte[] decode(String encoded) {
		return Base64.getDecoder().decode(encoded);
	}
	
	private String decodeToString(String encoded) {
		return new String(decode(encoded), StandardCharsets.UTF_8);
	}
	
	private String encode(byte[] bytes) {
		return Base64.getEncoder().encodeToString(bytes);
	}
	
	private String encode(String element) {
		return encode(element.getBytes(StandardCharsets.UTF_8));
	}

}
