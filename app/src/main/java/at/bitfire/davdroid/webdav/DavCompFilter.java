package at.bitfire.davdroid.webdav;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.Namespace;
import org.simpleframework.xml.Root;

import lombok.Getter;
import lombok.Setter;

@Namespace(prefix="C",reference="urn:ietf:params:xml:ns:caldav")
public class DavCompFilter {
	public DavCompFilter(String name) {
		this.name = name;
	}

	@Attribute(required=false)
	String name;

	@Element(required=false,name="comp-filter")
	@Getter @Setter	DavCompFilter compFilter;

}
