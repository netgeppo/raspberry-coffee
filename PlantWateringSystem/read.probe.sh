#!/usr/bin/env bash
CP=build/libs/PlantWateringSystem-1.0-all.jar
#
JAVA_OPTIONS=
if [ "$1" == "debug" ]
then
  # For remote debugging:
  JAVA_OPTIONS="$JAVA_OPTIONS -client -agentlib:jdwp=transport=dt_socket,server=y,address=4000"
fi
# For remote JVM Monitoring
# JAVA_OPTIONS="$JAVA_OPTIONS -Dcom.sun.management.jmxremote.port=1234 -Dcom.sun.management.jmxremote.ssl=false -Dcom.sun.management.jmxremote.authenticate=false -Djava.rmi.server.hostname=raspberrypi-boat"
java $JAVA_OPTIONS -cp $CP main.STH10
