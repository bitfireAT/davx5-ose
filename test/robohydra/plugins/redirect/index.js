var RoboHydraHead = require("robohydra").heads.RoboHydraHead;

exports.getBodyParts = function(conf) {
    return {
        heads: [
            new RoboHydraHead({
				path: "/redirect/301",
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
				path: "/redirect/302",
				handler: function(req,res,next) {
					res.statusCode = 302;
                    var location = req.queryParams['to'] || '/assets/test.random';
					res.headers = {
						Location: location
					}
					res.end();
				}
            })
        ]
    };
};
