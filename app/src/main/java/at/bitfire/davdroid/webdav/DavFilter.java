package at.bitfire.davdroid.webdav;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.Namespace;

import lombok.Getter;
import lombok.Setter;

@Namespace(prefix="C",reference="urn:ietf:params:xml:ns:caldav")
public class DavFilter {
	@Element(required=false)
	@Getter	@Setter	DavCompFilter compFilter;
}
