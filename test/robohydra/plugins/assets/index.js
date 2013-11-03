var RoboHydraHeadFilesystem = require("robohydra").heads.RoboHydraHeadFilesystem;

exports.getBodyParts = function(conf) {
    return {
        heads: [
            new RoboHydraHeadFilesystem({
                mountPath: '/assets/',
                documentRoot: '../assets'
            })
        ]
    };
};
