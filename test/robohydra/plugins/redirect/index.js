require('../simple');

var RoboHydraHead = require('robohydra').heads.RoboHydraHead;

exports.getBodyParts = function(conf) {
    return {
        heads: [
            // well-known URIs
            new SimpleResponseHead({
                path: '/.well-known/caldav',
                status: 302,
                headers: { Location: '/dav/' }
            }),
            new SimpleResponseHead({
                path: '/.well-known/caldav',
                status: 302,
                headers: { Location: '/dav/' }
            }),

            // generic redirections
            new RoboHydraHead({
				path: '/redirect/301',
				handler: function(req,res,next) {
					res.statusCode = 301;
                    var location = req.queryParams['to'] || '/assets/test.random';
					res.headers = {
						Location: location
					}
					res.end();
				}
            }),
            new RoboHydraHead({
				path: '/redirect/302',
				handler: function(req,res,next) {
					res.statusCode = 302;
                    var location = req.queryParams['to'] || '/assets/test.random';
					res.headers = {
						Location: location
					}
					res.end();
				}
            }),

            // special redirections
            new SimpleResponseHead({
                path: '/redirect/relative',
                status: 302,
                headers: { Location: '/new/location' }
            }),
            new SimpleResponseHead({
                path: '/redirect/without-location',
                status: 302
            })

        ]
    };
};
