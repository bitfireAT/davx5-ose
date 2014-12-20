var roboHydra = require("robohydra"),
	roboHydraHeads = roboHydra.heads,
	roboHydraHead = roboHydraHeads.RoboHydraHead;

SimpleResponseHead = roboHydraHeads.roboHydraHeadType({
	name: 'Simple HTTP Response',
	mandatoryProperties: [ 'path', 'status' ],
	optionalProperties: [ 'headers', 'body' ],

	parentPropBuilder: function() {
        var head = this;
		return {
			path: this.path,
			handler: function(req,res,next) {
				res.statusCode = head.status;
                if (typeof head.headers != 'undefined')
                    res.headers = head.headers;
                if (typeof head.body != 'undefined')
                    res.write(head.body);
                else
                    res.write();
				res.end();
			}
		}
	}
});

module.exports = SimpleResponseHead;
