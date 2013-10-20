package at.bitfire.davdroid.ical4j;

import java.util.List;

import net.fortuna.ical4j.model.ValidationException;
import net.fortuna.ical4j.vcard.Group;
import net.fortuna.ical4j.vcard.Parameter;
import net.fortuna.ical4j.vcard.Property;
import net.fortuna.ical4j.vcard.PropertyFactory;

public class PhoneticLastName extends Property {
	private static final long serialVersionUID = 8637699713562385556L;

	public static final String PROPERTY_NAME = "PHONETIC-LAST-NAME";
	
	protected String phoneticFirstName;
	
	
	public PhoneticLastName(String value) {
		super(PROPERTY_NAME);
		
		phoneticFirstName = value;
	}
	

	@Override
	public String getValue() {
		return phoneticFirstName;
	}

	@Override
	public void validate() throws ValidationException {
	}
	
	public static class Factory implements PropertyFactory<Property> {
		@Override
		public PhoneticLastName createProperty(List<Parameter> params, String value) {
			return new PhoneticLastName(value);
		}

		@Override
		public PhoneticLastName createProperty(Group group, List<Parameter> params, String value) {
			return new PhoneticLastName(value);
		}
	}
}
