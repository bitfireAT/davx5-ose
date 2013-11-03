var roboHydra = require("robohydra"),
	roboHydraHeads = roboHydra.heads,
	roboHydraHead = roboHydraHeads.RoboHydraHead;

RoboHydraHeadDAV = roboHydraHeads.roboHydraHeadType({
	name: 'WebDAV Server',
	mandatoryProperties: [ 'path', 'handler' ],

	parentPropBuilder: function() {
		var myHandler = this.handler;
		return {
			path: this.path,
			handler: function(req,res,next) {
				// default DAV behavior
				res.headers['DAV'] = 'addressbook, calendar-access';
				res.statusCode = 500;

				// DAV operations that work on all URLs
				if (req.method == "OPTIONS") {
					res.statusCode = 204;
					res.headers['Allow'] = 'OPTIONS, PROPFIND, GET, PUT, DELETE';

				} else if (req.method == "PROPFIND" && req.rawBody.toString().match(/current-user-principal/)) {
					res.statusCode = 207;
					res.write('\<?xml version="1.0" encoding="utf-8" ?>\
						<multistatus xmlns="DAV:">\
							<response>\
								<href>' + req.url + '</href> \
								<propstat>\
									<prop>\
										<current-user-principal>\
											<href>/dav/principals/users/test</href>\
										</current-user-principal>\
									</prop>\
									<status>HTTP/1.1 200 OK</status>\
								</propstat>\
							</response>\
						</multistatus>\
					');
					
				} else
					myHandler(req,res,next);

				res.end();
			}
		}
	}
});

module.exports = RoboHydraHeadDAV;
