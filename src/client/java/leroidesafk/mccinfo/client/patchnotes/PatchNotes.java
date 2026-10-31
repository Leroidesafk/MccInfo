package leroidesafk.mccinfo.client.patchnotes;

import leroidesafk.mccinfo.client.ModConfig;
import leroidesafk.mccinfo.client.relay.Permissions;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

// Patch notes sent by the relay, newest first
public final class PatchNotes {
	private static final Gson GSON = new Gson();
	private static final int MAX_TEXT = 1000;

	record Section(String name, List<String> changes) {
	}

	record Note(String version, String title, String date, List<Section> sections) {
	}

	private record Message(List<Note> notes) {
	}

	private static List<Note> notes = List.of();

	private PatchNotes() {
	}

	public static List<Note> all() {
		return notes;
	}

	// Patch note of the installed version
	static Note current() {
		return notes.stream().filter(note -> note.version().equals(Permissions.BASE_VERSION)).findFirst().orElse(null);
	}

	public static void receive(JsonObject object) {
		Message message = GSON.fromJson(object, Message.class);
		if (message == null || message.notes() == null) {
			return;
		}
		List<Note> received = new ArrayList<>();
		for (Note note : message.notes()) {
			Note cleaned = clean(note);
			if (cleaned != null) {
				received.add(cleaned);
			}
		}
		notes = List.copyOf(received);
		// Announce it once per version
		Note current = current();
		if (current != null && !Permissions.BASE_VERSION.equals(ModConfig.get().lastPatchNote)) {
			PatchNoteToast.show(current, false);
		}
	}

	public static void preview(JsonObject object) {
		if (!object.has("note") || !object.get("note").isJsonObject()) {
			return;
		}
		Note note = clean(GSON.fromJson(object.get("note"), Note.class));
		if (note != null) {
			PatchNoteToast.show(note, true);
		}
	}

	// Drops empty sections and notes, and § color codes
	private static Note clean(Note note) {
		if (note == null || note.version() == null || note.sections() == null) {
			return null;
		}
		List<Section> sections = new ArrayList<>();
		for (Section section : note.sections()) {
			if (section != null && section.changes() != null) {
				List<String> changes = section.changes().stream().filter(Objects::nonNull).map(PatchNotes::clean).filter(change -> !change.isEmpty()).toList();
				if (!changes.isEmpty()) {
					sections.add(new Section(clean(section.name()), changes));
				}
			}
		}
		return sections.isEmpty() ? null : new Note(clean(note.version()), clean(note.title()), clean(note.date()), List.copyOf(sections));
	}

	private static String clean(String text) {
		if (text == null) {
			return "";
		}
		String stripped = text.replace('§', ' ').strip();
		return stripped.length() > MAX_TEXT ? stripped.substring(0, MAX_TEXT) : stripped;
	}
}
