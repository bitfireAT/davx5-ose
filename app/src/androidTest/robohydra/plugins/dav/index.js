// vim: ts=4:sw=4

var roboHydraHeadDAV = require("../headdav");

exports.getBodyParts = function(conf) {
    return {
        heads: [
			/* base URL, provide default DAV here */
			new RoboHydraHeadDAV({ path: "/dav/" }),

			/* test cookie:
			 * POST /dav/testCookieStore   will cause the mock server to set a cookie
			 * GET  /dav/testCookieStore   will cause the mock server to check the request cookie
			 *                             and return 412 Precondition failed when it's not set correctly
			 */
			new RoboHydraHeadDAV({
				path: "/dav/testCookieStore",
				handler: function(req,res,next) {
					var cookie = 'sess=MY_SESSION_12345';
					if (req.method == "POST") {
						res.statusCode = 200;
						res.headers['Set-Cookie'] = cookie;
						res.send("Setting cookie");
					} else {
						res.statusCode = (req.headers['cookie'] == cookie) ? 200 : 412;
						res.send("Checking cookie");
					}
				}
			}),

            /* multistatus parsing */
            new RoboHydraHeadDAV({
				path: "/dav/collection-response-with-trailing-slash",
				handler: function(req,res,next) {
					if (req.method == "PROPFIND") {
                        res.statusCode = 207;
						res.write('\<?xml version="1.0" encoding="utf-8" ?>\
							<multistatus xmlns="DAV:">\
								<response>\
									<href>/dav/collection-response-with-trailing-slash/</href> \
									<propstat>\
										<prop>\
                                            <current-user-principal>\
                                                <href>/principals/ok</href>\
                                            </current-user-principal>\
                                            <resourcetype>\
                                                <collection/>\
                                            </resourcetype>\
										</prop>\
										<status>HTTP/1.1 200 OK</status>\
									</propstat>\
								</response>\
							</multistatus>\
						');
                    }
                }
            }),
            new RoboHydraHeadDAV({
				path: "/dav/collection-response-without-trailing-slash",
				handler: function(req,res,next) {
					if (req.method == "PROPFIND") {
                        res.statusCode = 207;
						res.write('\<?xml version="1.0" encoding="utf-8" ?>\
							<multistatus xmlns="DAV:">\
								<response>\
									<href>/dav/collection-response-without-trailing-slash</href> \
									<propstat>\
										<prop>\
                                            <current-user-principal>\
                                                <href>/principals/ok</href>\
                                            </current-user-principal>\
                                            <resourcetype>\
                                                <collection/>\
                                            </resourcetype>\
										</prop>\
										<status>HTTP/1.1 200 OK</status>\
									</propstat>\
								</response>\
							</multistatus>\
						');
                    }
                }
            }),

			/* principal URL */
            new RoboHydraHeadDAV({
				path: "/dav/principals/users/test",
				handler: function(req,res,next) {
					if (req.method == "PROPFIND" && req.rawBody.toString().match(/home-?set/)) {
						res.statusCode = 207;
						res.write('\<?xml version="1.0" encoding="utf-8" ?>\
							<multistatus xmlns="DAV:">\
								<response>\
									<href>/dav/principals/users/t%65st</href> \
									<propstat>\
										<prop>\
											<CARD:addressbook-home-set xmlns:CARD="urn:ietf:params:xml:ns:carddav">\
												<href>/dav/addressbooks/test</href>\
											</CARD:addressbook-home-set>\
											<CAL:calendar-home-set xmlns:CAL="urn:ietf:params:xml:ns:caldav">\
												<href>/dav/calendars/test/</href>\
											</CAL:calendar-home-set>\
										</prop>\
										<status>HTTP/1.1 200 OK</status>\
									</propstat>\
								</response>\
							</multistatus>\
						');
					}
				}
            }),

			/* address-book home set */
            new RoboHydraHeadDAV({
				path: "/dav/addressbooks/test/",
				handler: function(req,res,next) {
					if (!req.url.match(/\/$/)) {
						res.statusCode = 302;
						res.headers['location'] = "/dav/addressbooks/test/";
					}
					else if (req.method == "PROPFIND" && req.rawBody.toString().match(/addressbook-description/)) {
						res.statusCode = 207;
						res.write('\<?xml version="1.0" encoding="utf-8" ?>\
							<multistatus xmlns="DAV:">\
								<response>\
									<href>/dav/addressbooks/test/useless-member</href>\
									<propstat>\
										<prop>\
											<resourcetype/>\
										</prop>\
										<status>HTTP/1.1 200 OK</status>\
									</propstat>\
								</response>\
								<response>\
									<href>/dav/addressbooks/test/default-v4.vcf</href>\
									<propstat>\
										<prop xmlns:CARD="urn:ietf:params:xml:ns:carddav">\
											<resourcetype>\
												<collection/>\
												<CARD:addressbook/>\
											</resourcetype>\
											<CARD:addressbook-description>Default Address Book</CARD:addressbook-description>\
											<CARD:supported-address-data>\
                                                <CARD:address-data-type content-type="text/vcard" version="3.0" />\
                                                <CARD:address-data-type content-type="text/vcard" version="4.0" />\
                                            </CARD:supported-address-data>\
										</prop>\
										<status>HTTP/1.1 200 OK</status>\
									</propstat>\
								</response>\
								<response>\
									<href>https://my.server/absolute:uri/my-address-book</href>\
									<propstat>\
										<prop xmlns:CARD="urn:ietf:params:xml:ns:carddav">\
											<resourcetype>\
												<collection/>\
												<CARD:addressbook/>\
											</resourcetype>\
											<CARD:addressbook-description>Absolute URI VCard3 Book</CARD:addressbook-description>\
										</prop>\
										<status>HTTP/1.1 200 OK</status>\
									</propstat>\
								</response>\
							</multistatus>\
						');
					}
				}
            }),

			/* calendar home set */
            new RoboHydraHeadDAV({
				path: "/dav/calendars/test/",
				handler: function(req,res,next) {
					if (req.method == "PROPFIND" && req.rawBody.toString().match(/calendar-description/)) {
						res.statusCode = 207;
						res.write('\<?xml version="1.0" encoding="utf-8" ?>\
							<multistatus xmlns="DAV:" xmlns:CAL="urn:ietf:params:xml:ns:caldav">\
								<response>\
									<href>/dav/calendars/test/shared.forbidden</href>\
									<propstat>\
										<prop>\
											<resourcetype/>\
										</prop>\
										<status>HTTP/1.1 403 Forbidden</status>\
									</propstat>\
								</response>\
								<response>\
									<href>/dav/calendars/test/private.ics</href>\
									<propstat>\
										<prop>\
											<resourcetype>\
												<collection/>\
												<CAL:calendar/>\
											</resourcetype>\
                                            <displayname>Private Calendar</displayname>\
                                            <CAL:calendar-description>This is my private calendar.</CAL:calendar-description>\
										</prop>\
										<status>HTTP/1.1 200 OK</status>\
									</propstat>\
								</response>\
								<response>\
									<href>/dav/calendars/test/work.ics</href>\
									<propstat>\
										<prop>\
											<resourcetype>\
												<collection/>\
												<CAL:calendar/>\
											</resourcetype>\
                                            <current-user-privilege-set>\
                                                <privilege><read/></privilege>\
                                            </current-user-privilege-set>\
                                            <displayname>Work Calendar</displayname>\
											<A:calendar-color xmlns:A="http://apple.com/ns/ical/">0xFF00FF</A:calendar-color>\
										</prop>\
										<status>HTTP/1.1 200 OK</status>\
									</propstat>\
								</response>\
							</multistatus>\
						');
					}
				}
            }),

			/* non-existing member */
            new RoboHydraHeadDAV({
				path: "/dav/collection/new.file",
				handler: function(req,res,next) {
					if (req.method == "PUT") {
						if (req.headers['if-match'])	/* can't overwrite new file */
							res.statusCode = 412;
						else {
							res.statusCode = 201;
                            res.headers["ETag"] = "has-just-been-created";
                        }

					} else if (req.method == "DELETE")
						res.statusCode = 404;
				}
            }),

			/* existing member */
            new RoboHydraHeadDAV({
				path: "/dav/collection/existing.file",
				handler: function(req,res,next) {
					if (req.method == "PUT") {
						if (req.headers['if-none-match'])	/* requested "don't overwrite", but this file exists */
							res.statusCode = 412;
						else if (req.headers['if-match'] && req.queryParams && req.queryParams.conflict)
							/* requested "don't overwrite", but this file exists with newer content */
							res.statusCode = 409;
						else {
							res.statusCode = 204;
                            res.headers["ETag"] = "has-just-been-updated";
                        }

					} else if (req.method == "DELETE")
						res.statusCode = 204;
				}
            }),

			/* existing member with encoded URL as file name */
            new RoboHydraHeadDAV({
				path: "/dav/http%253A%252F%252Fwww.invalid.example%252Fm8%252Ffeeds%252Fcontacts%252Fmaria.mueller%252540gmail.com%252Fbase%252F5528abc5720cecc.vcf",
				handler: function(req,res,next) {
					if (req.method == "GET")
						res.statusCode = 200;
				}
            }),

			/* address-book multiget */
            new RoboHydraHeadDAV({
				path: "/dav/addressbooks/default.vcf/",
				handler: function(req,res,next) {
					if (req.method == "REPORT" && req.rawBody.toString().match(/addressbook-multiget[\s\S]+<prop>[\s\S]+<href>/m &&
                        req.rawBody.toString().match(/<href>\/dav\/addressbooks\/default\.vcf\/2:3@my%2540pc\.vcf<\/href>/m))) {
						res.statusCode = 207;
						res.write('\<?xml version="1.0" encoding="utf-8" ?>\
							<multistatus xmlns="DAV:" xmlns:CARD="urn:ietf:params:xml:ns:carddav">\
								<response>\
									<href>/dav/addressbooks/default.vcf</href>\
									<propstat>\
										<prop>\
                                            <resourcetype><collection/></resourcetype>\
                                            <displayname>My Book</displayname>\
										</prop>\
										<status>HTTP/1.1 200 OK</status>\
									</propstat>\
								</response>\
								<response>\
									<href>/dav/addressbooks/default.vcf/1.vcf</href>\
									<propstat>\
										<prop>\
											<getetag/>\
											<CARD:address-data>BEGIN:VCARD\
											VERSION:3.0\
											NICKNAME:MULTIGET1\
											UID:1\
											END:VCARD\
											</CARD:address-data>\
										</prop>\
										<status>HTTP/1.1 200 OK</status>\
									</propstat>\
								</response>\
								<response>\
									<href>/dav/addressbooks/default.vcf/2:3%40my%2540pc.vcf</href>\
									<propstat>\
										<prop>\
											<getetag/>\
											<CARD:address-data>BEGIN:VCARD\
											VERSION:3.0\
											NICKNAME:MULTIGET2\
											UID:2\
											END:VCARD\
											</CARD:address-data>\
										</prop>\
										<status>HTTP/1.1 200 OK</status>\
									</propstat>\
								</response>\
							</multistatus>\
						');
					}
				}
            }),

			/* address-book multiget where one resource has 404 Not Found */
            new RoboHydraHeadDAV({
				path: "/dav/addressbooks/default-with-404.vcf/",
				handler: function(req,res,next) {
					if (req.method == "REPORT" && req.rawBody.toString().match(/addressbook-multiget[\s\S]+<prop>[\s\S]+<href>/m &&
                        req.rawBody.toString().match(/<href>\/dav\/addressbooks\/default-with-404\.vcf\/notexisting<\/href>/m))) {
						res.statusCode = 207;
						res.write('\<?xml version="1.0" encoding="utf-8" ?>\
							<multistatus xmlns="DAV:" xmlns:CARD="urn:ietf:params:xml:ns:carddav">\
								<response>\
									<href>/dav/addressbooks/default-with-404.vcf</href>\
									<propstat>\
										<prop>\
                                            <resourcetype><collection/></resourcetype>\
										</prop>\
										<status>HTTP/1.1 200 OK</status>\
									</propstat>\
								</response>\
								<response>\
                                    <href>/dav/addressbooks/default-with-404.vcf/notexisting</href>\
                                    <status>HTTP/1.1 404 Not Found</status>\
								</response>\
							</multistatus>\
						');
					}
				}
            }),


        ]
    };
};
