var RoboHydraHead = require("robohydra").heads.RoboHydraHead;

exports.getBodyParts = function(conf) {
    return {
        heads: [
            new RoboHydraHead({
				path: "/redirect",
				handler: function(req,res,next) {
					res.statusCode = 302;
					res.headers = {
						location: 'http://www.example.com'
					}
					res.end();
				}
            })
        ]
    };
};
