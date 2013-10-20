package at.bitfire.davdroid.ical4j;

import java.util.List;

import net.fortuna.ical4j.model.ValidationException;
import net.fortuna.ical4j.vcard.Group;
import net.fortuna.ical4j.vcard.Parameter;
import net.fortuna.ical4j.vcard.Property;
import net.fortuna.ical4j.vcard.PropertyFactory;

public class PhoneticFirstName extends Property {
	private static final long serialVersionUID = 8096989375023262021L;

	public static final String PROPERTY_NAME = "PHONETIC-FIRST-NAME";
	
	protected String phoneticFirstName;
	
	
	public PhoneticFirstName(String value) {
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
		public PhoneticFirstName createProperty(List<Parameter> params, String value) {
			return new PhoneticFirstName(value);
		}

		@Override
		public PhoneticFirstName createProperty(Group group, List<Parameter> params, String value) {
			return new PhoneticFirstName(value);
		}
	}
}
