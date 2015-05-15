package at.bitfire.davdroid.webdav;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.Namespace;
import org.simpleframework.xml.NamespaceList;
import org.simpleframework.xml.Root;

import lombok.Getter;
import lombok.Setter;

@Root(name="calendar-query")
@NamespaceList({
		@Namespace(reference="DAV:"),
		@Namespace(prefix="C",reference="urn:ietf:params:xml:ns:caldav")
})
@Namespace(prefix="C",reference="urn:ietf:params:xml:ns:caldav")
public class DavCalendarQuery {
	@Element
	@Getter	@Setter DavProp prop;

	@Element
	@Getter @Setter DavFilter filter;
}
