#!/bin/sh
sh ./bin/shutdown.sh
ant deploy deploy-plugins
if [ $? -eq 0 ]
    then
        sh ./bin/startup.sh
    else
        echo "Service was not restarted"
fi

