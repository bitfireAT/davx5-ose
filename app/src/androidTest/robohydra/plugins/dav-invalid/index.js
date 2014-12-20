var roboHydraHeadDAV = require("../headdav");

exports.getBodyParts = function(conf) {
    return {
        heads: [
			/* address-book home set */
            new RoboHydraHeadDAV({
				path: "/dav-invalid/addressbooks/~user1/",
				handler: function(req,res,next) {
					if (req.method == "PROPFIND" && req.rawBody.toString().match(/addressbook-description/)) {
						res.statusCode = 207;
						res.write('\<?xml version="1.0" encoding="utf-8" ?>\
							<multistatus xmlns="DAV:">\
								<response>\
									<href>/dav-invalid/addressbooks/~user1/My%20Contacts:1.vcf/</href>\
									<propstat>\
										<prop xmlns:CARD="urn:ietf:params:xml:ns:carddav">\
											<resourcetype>\
												<collection/>\
												<CARD:addressbook/>\
											</resourcetype>\
											<CARD:addressbook-description>\
												Address Book with dubious characters in path\
											</CARD:addressbook-description>\
										</prop>\
										<status>HTTP/1.1 200 OK</status>\
									</propstat>\
								</response>\
							</multistatus>\
						');
					}
				}
            })
        ]
    };
};
