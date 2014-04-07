#!/bin/sh
sh ./bin/shutdown.sh
ant deploy deploy-plugins
sh ./bin/startup.sh
