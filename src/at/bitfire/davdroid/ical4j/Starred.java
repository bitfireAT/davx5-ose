package at.bitfire.davdroid.ical4j;

import java.util.List;

import net.fortuna.ical4j.model.ValidationException;
import net.fortuna.ical4j.vcard.Group;
import net.fortuna.ical4j.vcard.Parameter;
import net.fortuna.ical4j.vcard.Property;
import net.fortuna.ical4j.vcard.PropertyFactory;

public class Starred extends Property {
	private static final long serialVersionUID = -8703412738139555307L;

	public static final String PROPERTY_NAME = "DAVDROID-STARRED";
	
	public Starred() {
		super(PROPERTY_NAME);
	}

	@Override
	public String getValue() {
		return "1";
	}

	@Override
	public void validate() throws ValidationException {
	}

	
	public static class Factory implements PropertyFactory<Property> {
		@Override
		public Starred createProperty(List<Parameter> params, String value) {
			return new Starred();
		}

		@Override
		public Starred createProperty(Group group, List<Parameter> params, String value) {
			return new Starred();
		}
	}
}
