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
	
	protected boolean isStarred;
	
	
	public Starred(String value) {
		super(PROPERTY_NAME);
		
		isStarred = Integer.parseInt(value) > 0;
	}

	@Override
	public String getValue() {
		return isStarred ? "1" : "0";		
	}

	@Override
	public void validate() throws ValidationException {
	}

	
	public static class Factory implements PropertyFactory<Property> {
		@Override
		public Starred createProperty(List<Parameter> params, String value) {
			return new Starred(value);
		}

		@Override
		public Starred createProperty(Group group, List<Parameter> params, String value) {
			return new Starred(value);
		}
	}
}
