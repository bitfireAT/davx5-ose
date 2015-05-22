package at.bitfire.davdroid.resource;

import android.util.Log;

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.data.CalendarOutputter;
import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.ComponentList;
import net.fortuna.ical4j.model.PropertyList;
import net.fortuna.ical4j.model.ValidationException;
import net.fortuna.ical4j.model.component.VJournal;
import net.fortuna.ical4j.model.component.VToDo;
import net.fortuna.ical4j.model.property.Created;
import net.fortuna.ical4j.model.property.Description;
import net.fortuna.ical4j.model.property.DtStamp;
import net.fortuna.ical4j.model.property.Location;
import net.fortuna.ical4j.model.property.ProdId;
import net.fortuna.ical4j.model.property.Summary;
import net.fortuna.ical4j.model.property.Uid;
import net.fortuna.ical4j.model.property.Version;
import net.fortuna.ical4j.util.SimpleHostInfo;
import net.fortuna.ical4j.util.UidGenerator;

import org.apache.commons.lang.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import at.bitfire.davdroid.Constants;
import at.bitfire.davdroid.syncadapter.DavSyncAdapter;
import lombok.Getter;
import lombok.Setter;

public class Note extends Resource {
	private final static String TAG = "davdroid.Note";

	@Getter @Setter	Created created;
	@Getter @Setter	String summary, description;


	public Note(String name, String ETag) {
		super(name, ETag);
	}

	public Note(long localId, String name, String ETag)
	{
		super(localId, name, ETag);
	}

	@Override
	public void initialize() {
		UidGenerator generator = new UidGenerator(new SimpleHostInfo(DavSyncAdapter.getAndroidID()), String.valueOf(android.os.Process.myPid()));
		uid = generator.generateUid().getValue();
		name = uid + ".ics";
	}


	@Override
	public void parseEntity(InputStream entity, AssetDownloader downloader) throws IOException, InvalidResourceException {
		net.fortuna.ical4j.model.Calendar ical;
		try {
			CalendarBuilder builder = new CalendarBuilder();
			ical = builder.build(entity);

			if (ical == null)
				throw new InvalidResourceException("No iCalendar found");
		} catch (ParserException e) {
			throw new InvalidResourceException(e);
		}

		ComponentList notes = ical.getComponents(Component.VJOURNAL);
		if (notes == null || notes.isEmpty())
			throw new InvalidResourceException("No VJOURNAL found");
		VJournal note = (VJournal)notes.get(0);

		if (note.getUid() != null)
			uid = note.getUid().getValue();

		if (note.getCreated() != null)
			created = note.getCreated();

		if (note.getSummary() != null)
			summary = note.getSummary().getValue();
		if (note.getDescription() != null)
			description = note.getDescription().getValue();
	}


	@Override
	public String getMimeType() {
		return "text/calendar";
	}

	@Override
	public ByteArrayOutputStream toEntity() throws IOException {
		final net.fortuna.ical4j.model.Calendar ical = new net.fortuna.ical4j.model.Calendar();
		ical.getProperties().add(Version.VERSION_2_0);
		ical.getProperties().add(Constants.ICAL_PRODID);

		final VJournal note = new VJournal();
		ical.getComponents().add(note);
		final PropertyList props = note.getProperties();

		if (uid != null)
			props.add(new Uid(uid));

		if (summary != null)
			props.add(new Summary(summary));
		if (description != null)
			props.add(new Description(description));

		CalendarOutputter output = new CalendarOutputter(false);
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		try {
			output.output(ical, os);
		} catch (ValidationException e) {
			Log.e(TAG, "Generated invalid iCalendar");
		}
		return os;
	}

}
